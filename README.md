# Simple LED Clock

Актуальная версия исходников: **1.0.2**, versionCode **6**.
Подготовлено для приватного репозитория; APK и GitHub Releases пока не опубликованы.

Офлайн-часы для Android с сегментными стилями, цветами, настройками яркости,
ночным расписанием, датчиком освещения, пресетами и защитным сдвигом изображения.
Русский и английский интерфейс. Пакет: `ru.okbit.ledclock`.

## Сборка

JDK 17, Android SDK Platform 35 и Build Tools 35.0.0; Gradle 8.11.1,
AGP 8.9.3, Kotlin 2.1.20. Настройте Android SDK через `ANDROID_HOME`
или локальный `local.properties` (не включайте его в Git).

```sh
sh ./gradlew :app:assembleModernRelease :app:assembleLegacyRelease
sh ./gradlew :app:testModernReleaseUnitTest :app:lintModernRelease :app:lintLegacyRelease
sh ./gradlew :app:testLegacyReleaseUnitTest -PlegacyAndroid
```

Modern: Android 6.0+ (minSdk 23). Legacy: Android 4.0+ (minSdk 14).
Оба варианта имеют versionCode 6, targetSdk 35 и заменяют друг друга при установке.
PNG launcher icon исправляет воспроизведённый сбой установки на проверенном устройстве.

Поставляемые APK сохраняют прежний сертификат разработки для совместимости обновлений.
Приватный ключ в исходники не входит. Самостоятельная сборка использует собственный
локальный debug-сертификат и не гарантирует обновление поверх поставляемого APK.

Проверка modern 1.0.2 на HONOR / Android 16 ранее подтверждена пользователем;
это не проверка всех Android-устройств. Внутренние отчёты сборок и история разработки не входят в этот репозиторий.

## Лицензия

SPDX-License-Identifier: GPL-3.0-only

Авторский код Simple LED Clock распространяется под GNU GPL версии 3 **только**.
Полный текст находится в `LICENSE`. Gradle Wrapper и другие сторонние компоненты
сохраняют собственные лицензии и уведомления об авторских правах.

Лицензии зависимостей и границы их распространения: `THIRD_PARTY_NOTICES.md`.
