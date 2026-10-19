import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Точка входа для Windows и Linux. Отсюда же собираются установщики (.msi, .exe, .deb).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.coroutines.swing)
    implementation(libs.jna.platform)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.appprogress.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "AppProgress"
            packageVersion = "0.1.0"
            description = "Planner: notes, boards, calendar"
            vendor = "AppProgress"
            // Установщик кладёт урезанную Java только с нужными модулями. Часть списка дала задача
            // suggestRuntimeModules, остальное она не видит, потому что эти модули подгружаются динамически:
            //   jdk.charsets   — письма в windows-1251 и KOI8-R;
            //   jdk.localedata — русские названия месяцев в датах;
            //   jdk.crypto.ec  — TLS с почтовыми серверами (шифры на эллиптических кривых);
            //   java.net.http  — обращение к локальному ИИ (Ollama).
            modules(
                "java.instrument", "java.sql", "jdk.unsupported", "java.desktop", "java.naming",
                "java.net.http", "jdk.charsets", "jdk.localedata", "jdk.crypto.ec",
            )
            windows {
                menuGroup = "AppProgress"
                shortcut = true
                dirChooser = true
                // Постоянный id: по нему Windows понимает, что новая версия обновляет старую, а не ставится рядом.
                upgradeUuid = "8d3b2c9e-6a41-4f0e-9b7d-2e5c1a7f4b90"
            }
            linux {
                shortcut = true
            }
        }
    }
}
