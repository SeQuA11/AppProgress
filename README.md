# AppProgress

Планировщик для Windows (позже — Linux и Android): заметки, канбан, календарь с напоминаниями,
студия для таблиц/графиков/диаграмм, почта и локальный ИИ-ассистент.

Сейчас готово: **каркас приложения и заметки** (создание, автосохранение, поиск, закрепление, удаление),
светлая/тёмная тема. Остальные разделы видны в меню с пометкой «скоро».

## Что нужно

- **JDK 21+** (уже установлен)
- Больше ничего: Gradle скачается сам через `gradlew`

## Команды

```bash
./gradlew :desktopApp:run                 # запустить приложение
./gradlew test                            # все тесты + скриншоты экранов в desktopApp/build/screenshots
./gradlew :desktopApp:createDistributable # папка с AppProgress.exe и встроенной Java
./gradlew :desktopApp:packageMsi          # установщик Windows (.msi)
```

Данные хранятся в `%APPDATA%\AppProgress\appprogress.db` (SQLite).

## Структура

| Модуль | Что внутри |
|---|---|
| `shared/` | Модели, локальная база (SQLDelight → SQLite), репозитории. Без интерфейса |
| `ui/` | Экраны на Compose Multiplatform — общие для desktop и будущего Android |
| `desktopApp/` | Точка входа для Windows/Linux, сборка установщиков, скриншот-тесты |
| `gradle/libs.versions.toml` | Версии всех библиотек в одном месте |

Схема базы — в файлах `shared/src/commonMain/sqldelight/**/*.sq`: из них SQLDelight генерирует Kotlin-код запросов.
