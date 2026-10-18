rootProject.name = "AppProgress"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// shared     — модели, база данных, бизнес-логика (без UI)
// ui         — экраны на Compose
// desktopApp — точка входа для Windows/Linux и сборка установщиков
include(":shared", ":ui", ":desktopApp")
