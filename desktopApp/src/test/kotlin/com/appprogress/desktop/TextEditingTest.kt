package com.appprogress.desktop

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.appprogress.ui.notes.TextEditing
import kotlin.test.Test
import kotlin.test.assertEquals

class TextEditingTest {
    private fun typed(before: String, insert: String = "\n"): Pair<TextFieldValue, TextFieldValue> {
        val old = TextFieldValue(before, TextRange(before.length))
        val text = before + insert
        return old to TextFieldValue(text, TextRange(text.length))
    }

    @Test
    fun enterContinuesChecklistAndNumberedList() {
        val (o1, n1) = typed("- [x] купить хлеб")
        assertEquals("- [x] купить хлеб\n- [ ] ", TextEditing.continueList(o1, n1).text)
        val (o2, n2) = typed("  2. второй")
        assertEquals("  2. второй\n  3. ", TextEditing.continueList(o2, n2).text)
    }

    @Test
    fun enterOnEmptyItemEndsTheList() {
        val (o, n) = typed("- [ ] дело\n- [ ] ")
        val r = TextEditing.continueList(o, n)
        assertEquals("- [ ] дело\n", r.text)
        assertEquals(TextRange(r.text.length), r.selection)
    }

    @Test
    fun plainTextIsNotTouched() {
        val (o, n) = typed("обычная строка")
        assertEquals(n, TextEditing.continueList(o, n))
    }

    @Test
    fun wrapAndPrefixes() {
        assertEquals("**слово**", TextEditing.wrap(TextFieldValue("слово", TextRange(0, 5)), "**").text)
        val v = TextFieldValue("раз\nдва", TextRange(0, 7))
        assertEquals("- [ ] раз\n- [ ] два", TextEditing.toggleLinePrefix(v, "- [ ] ").text)
        // Повторное нажатие снимает префикс, а другой префикс заменяется.
        assertEquals("раз\nдва", TextEditing.toggleLinePrefix(TextFieldValue("- [ ] раз\n- [ ] два", TextRange(0, 18)), "- [ ] ").text)
        assertEquals("# заголовок", TextEditing.toggleLinePrefix(TextFieldValue("## заголовок", TextRange(3)), "# ").text)
    }
}
