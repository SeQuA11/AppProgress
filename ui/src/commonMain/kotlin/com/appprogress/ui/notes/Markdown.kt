package com.appprogress.ui.notes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appprogress.notes.LinkKind
import com.appprogress.notes.NoteMarkup

/** Куда ведёт щелчок по ссылке в заметке. */
sealed interface NoteLink {
    data class Wiki(val title: String) : NoteLink
    data class App(val kind: LinkKind, val id: String, val date: String?) : NoteLink
    data class File(val name: String) : NoteLink
    data class Url(val url: String) : NoteLink
}

/** Состояние объекта, на который ссылается заметка: «В работе · до 25.10», выполнена ли задача. */
data class LinkInfo(val status: String, val done: Boolean = false, val missing: Boolean = false)

private val HEADING = Regex("""(?m)^(#{1,6})\s.*$""")
private val BOLD = Regex("""\*\*[^*\n]+\*\*|__[^_\n]+__""")
private val ITALIC = Regex("""(?<![*\p{L}\p{N}])\*[^*\n]+\*(?!\*)|(?<![_\p{L}\p{N}])_[^_\n]+_(?![_\p{L}\p{N}])""")
private val STRIKE = Regex("""~~[^~\n]+~~""")
private val CODE = Regex("""`[^`\n]+`""")
private val TAG = Regex("""(?<![\p{L}\p{N}_#/&=:.])#[\p{L}\p{N}_-]*\p{L}[\p{L}\p{N}_-]*""")
private val CHECK_MARK = Regex("""(?m)^\s*[-*] \[[ xXхХ]]""")
private val QUOTE = Regex("""(?m)^>.*$""")
private val URL = Regex("""https?://[^\s)\]]+""")

private val headingSizes = listOf(26.sp, 22.sp, 19.sp, 17.sp, 16.sp, 15.sp)

/**
 * Подсветка Markdown прямо в поле ввода: текст не меняется (разметку видно и можно править),
 * но заголовки крупнее, жирный — жирный, ссылки и теги выделены цветом.
 */
class MarkdownHighlight(private val colors: ColorScheme) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val out = buildAnnotatedString {
            append(s)
            fun style(r: IntRange, st: SpanStyle) = addStyle(st, r.first, r.last + 1)
            val dim = SpanStyle(color = colors.onSurfaceVariant.copy(alpha = 0.6f))
            HEADING.findAll(s).forEach { m ->
                val level = m.groupValues[1].length
                style(m.range, SpanStyle(fontSize = headingSizes[level - 1], fontWeight = FontWeight.SemiBold))
                style(m.range.first until m.range.first + level, dim)
            }
            QUOTE.findAll(s).forEach { style(it.range, SpanStyle(fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant)) }
            BOLD.findAll(s).forEach { style(it.range, SpanStyle(fontWeight = FontWeight.Bold)) }
            ITALIC.findAll(s).forEach { style(it.range, SpanStyle(fontStyle = FontStyle.Italic)) }
            STRIKE.findAll(s).forEach { style(it.range, SpanStyle(textDecoration = TextDecoration.LineThrough, color = colors.onSurfaceVariant)) }
            CODE.findAll(s).forEach { style(it.range, SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant)) }
            CHECK_MARK.findAll(s).forEach { style(it.range, SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) }
            TAG.findAll(s).forEach { style(it.range, SpanStyle(color = colors.secondary, fontWeight = FontWeight.Medium)) }
            URL.findAll(s).forEach { style(it.range, SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)) }
            listOf(NoteMarkup.WIKI_LINK, NoteMarkup.APP_LINK, NoteMarkup.FILE, NoteMarkup.IMAGE).forEach { re ->
                re.findAll(s).forEach { style(it.range, SpanStyle(color = colors.primary, fontWeight = FontWeight.Medium)) }
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

/** Строка Markdown → AnnotatedString с кликабельными ссылками (для режима просмотра). */
fun inlineMarkdown(
    line: String,
    colors: ColorScheme,
    resolve: (LinkKind, String) -> LinkInfo?,
    onLink: (NoteLink) -> Unit,
): AnnotatedString = buildAnnotatedString {
    data class Token(val range: IntRange, val kind: String, val m: MatchResult)
    val patterns = listOf(
        "image" to NoteMarkup.IMAGE, "app" to NoteMarkup.APP_LINK, "wiki" to NoteMarkup.WIKI_LINK, "file" to NoteMarkup.FILE,
        "url" to URL, "code" to CODE, "bold" to BOLD, "strike" to STRIKE, "italic" to ITALIC, "tag" to TAG,
    )
    val linkStyle = TextLinkStyles(SpanStyle(color = colors.primary, fontWeight = FontWeight.Medium))
    var pos = 0
    while (pos < line.length) {
        val next = patterns.mapNotNull { (kind, re) -> re.find(line, pos)?.let { Token(it.range, kind, it) } }
            .minWithOrNull(compareBy({ it.range.first }, { -it.range.last }))
        if (next == null) { append(line.substring(pos)); break }
        append(line.substring(pos, next.range.first))
        val g = next.m.groupValues
        fun link(text: String, target: NoteLink, style: TextLinkStyles = linkStyle) =
            withLink(LinkAnnotation.Clickable(target.toString(), style) { onLink(target) }) { append(text) }
        when (next.kind) {
            "image" -> link("[картинка ${g[1]}]", NoteLink.File(g[2]))
            "app" -> {
                val kind = LinkKind.of(g[2])!!
                val info = resolve(kind, g[3])
                val icon = when (kind) {
                    LinkKind.Card -> if (info?.done == true) "☑ " else "☐ "
                    LinkKind.Event -> "◷ "
                    LinkKind.Mail -> "✉ "
                    LinkKind.Note -> "↗ "
                }
                val style = if (info?.missing == true) TextLinkStyles(SpanStyle(color = colors.error, textDecoration = TextDecoration.LineThrough)) else linkStyle
                link(icon + g[1], NoteLink.App(kind, g[3], g[4].ifBlank { null }), style)
                info?.status?.takeIf { it.isNotBlank() }?.let { st ->
                    withStyle(SpanStyle(color = colors.onSurfaceVariant, fontSize = 13.sp)) { append(" · $st") }
                }
            }
            "wiki" -> link(g[2].ifBlank { g[1] }, NoteLink.Wiki(g[1].trim()))
            "file" -> link("⎙ ${g[1]}", NoteLink.File(g[2]))
            "url" -> link(g[0], NoteLink.Url(g[0]), TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)))
            "code" -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant)) { append(g[0].trim('`')) }
            "bold" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[0].substring(2, g[0].length - 2)) }
            "strike" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[0].substring(2, g[0].length - 2)) }
            "italic" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[0].substring(1, g[0].length - 1)) }
            "tag" -> withStyle(SpanStyle(color = colors.secondary, fontWeight = FontWeight.Medium)) { append(g[0]) }
        }
        pos = next.range.last + 1
    }
}

/** Режим просмотра: заголовки, списки, чек-листы с галочками, цитаты, код, картинки. */
@Composable
fun MarkdownView(
    text: String,
    resolve: (LinkKind, String) -> LinkInfo?,
    onLink: (NoteLink) -> Unit,
    onToggleCheckbox: (lineIndex: Int) -> Unit,
    loadImage: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp, color = colors.onSurface)
    fun inline(s: String) = inlineMarkdown(s, colors, resolve, onLink)
    val lines = text.split('\n')
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val index = i
            when {
                line.trimStart().startsWith("```") -> {
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code.appendLine(lines[i]); i++ }
                    Text(
                        code.toString().trimEnd(),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().background(colors.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp),
                    )
                }
                line.isBlank() -> Box(Modifier.heightIn(min = 8.dp))
                line.trim() == "---" || line.trim() == "***" -> HorizontalDivider(Modifier.padding(vertical = 6.dp), color = colors.outlineVariant)
                HEADING.matches(line) -> {
                    val level = line.takeWhile { it == '#' }.length
                    Text(
                        inline(line.drop(level).trim()),
                        style = body.copy(fontSize = headingSizes[level - 1], fontWeight = FontWeight.SemiBold, lineHeight = headingSizes[level - 1] * 1.3f),
                        modifier = Modifier.padding(top = if (level <= 2) 10.dp else 4.dp),
                    )
                }
                NoteMarkup.CHECKBOX.matches(line) -> {
                    val m = NoteMarkup.CHECKBOX.find(line)!!
                    val checked = m.groupValues[2].isNotBlank()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = (m.groupValues[1].length * 8).dp)) {
                        Checkbox(checked = checked, onCheckedChange = { onToggleCheckbox(index) }, modifier = Modifier.size(32.dp))
                        Text(
                            inline(m.groupValues[3]),
                            style = body.copy(
                                textDecoration = if (checked) TextDecoration.LineThrough else null,
                                color = if (checked) colors.onSurfaceVariant else colors.onSurface,
                            ),
                        )
                    }
                }
                Regex("""^\s*[-*+]\s+.*""").matches(line) -> Bullet(line, "•", inline(line.trimStart().drop(2)), body)
                Regex("""^\s*\d+[.)]\s+.*""").matches(line) -> {
                    val num = line.trimStart().takeWhile { it.isDigit() }
                    Bullet(line, "$num.", inline(line.trimStart().dropWhile { it.isDigit() }.drop(1).trimStart()), body)
                }
                line.startsWith(">") -> Row {
                    Box(Modifier.width(3.dp).heightIn(min = 24.dp).background(colors.primary.copy(alpha = 0.5f)))
                    Text(inline(line.drop(1).trimStart()), style = body.copy(fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant), modifier = Modifier.padding(start = 10.dp))
                }
                NoteMarkup.IMAGE.matches(line.trim()) -> {
                    val m = NoteMarkup.IMAGE.find(line)!!
                    AttachmentImage(m.groupValues[2], loadImage, onClick = { onLink(NoteLink.File(m.groupValues[2])) })
                }
                else -> Text(inline(line), style = body)
            }
            i++
        }
    }
}

@Composable
private fun Bullet(line: String, marker: String, content: AnnotatedString, style: androidx.compose.ui.text.TextStyle) {
    val indent = line.takeWhile { it == ' ' }.length
    Row(Modifier.padding(start = (indent * 8).dp)) {
        Text(marker, style = style, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
        Text(content, style = style)
    }
}

@Composable
private fun AttachmentImage(name: String, load: suspend (String) -> ImageBitmap?, onClick: () -> Unit) {
    val image = produceState<ImageBitmap?>(null, name) { value = load(name) }.value
    if (image == null) {
        Text("[картинка $name]", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Image(
        image, contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier = Modifier.heightIn(max = 420.dp).clickable(onClick = onClick),
    )
}

