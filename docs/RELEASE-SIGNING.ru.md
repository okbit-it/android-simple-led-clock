# Постоянная release-подпись Simple LED Clock

Подготовленная версия: 1.0.3, versionCode 7, applicationId `ru.okbit.ledclock`.
Один новый сертификат для legacy (API 14+) и modern (API 23+), targetSdk 35.
Создание ключа и публикация требуют отдельного согласования. Команды создания ниже —
инструкция для последующего этапа; при подготовке ветки они не выполнялись.

## Переход со старых APK

APK 1.0.2 подписаны Android Debug. Простая замена подписи на постоянную не позволяет
обновить существующую установку. Повышение versionCode до 7 это ограничение не снимает.
Согласованный переход: удалить прежнюю версию, затем установить новую. При удалении
теряются настройки и пресеты. Экспорт не реализован, `allowBackup=false`; не обещайте
пользователям автоматическое восстановление. Старые ключи не удалять и не заменять.

После перехода все дальнейшие обновления должны сохранять package и постоянный ключ,
а versionCode должен возрастать. Варианты legacy/modern имеют один сертификат и код 7,
заменяют друг друга и не устанавливаются параллельно. Ротация ключа на современных
Android не обеспечивает универсальную миграцию старых Android 4–8.

## Хранение вне Git

Предпочтительно создать ключ на защищённой машине владельца. Для сборки на Linux
пример выделенного каталога: `$HOME/.local/share/android-signing/simple-led-clock`.
Каталог должен быть вне любых Git-репозиториев и иметь права 700; keystore и properties —
600 (или строже). Храните их на зашифрованном диске. Символические ссылки проверяются
по конечному пути. Конфигурация требует файловую систему с POSIX-правами; для Windows
нужен отдельно согласованный способ проверки ACL, не отключайте проверку защиты.

Рабочий каталог ключа и его родитель не должны быть доступны для записи посторонним.
Не храните ключ даже в игнорируемой папке проекта. `.gitignore` — дополнительная защита,
а не способ хранения секретов. Не помещайте их в архив исходников, Docker-образ,
кэш CI, Telegram, GitHub/GitLab или артефакты проверки.

## Отдельное создание ключа — только после подтверждения

Нужен JDK 17 и его `keytool`. Пример ниже создаёт новый выделенный каталог и прекращает
работу, если каталог уже существует. Он не импортирует, не заменяет старые ключи и
не подписывает APK. Не запускайте команды с `set -x` или записью терминальной сессии.

```sh
(
    set -eu
    umask 077
    signing_parent="$HOME/.local/share/android-signing"
    signing_dir="$signing_parent/simple-led-clock"
    mkdir -p "$signing_parent"
    mkdir "$signing_dir"
    chmod 700 "$signing_dir"
    keytool -genkeypair \
        -keystore "$signing_dir/release.p12" \
        -storetype PKCS12 \
        -alias simple-led-clock-release \
        -keyalg RSA -keysize 3072 -sigalg SHA256withRSA \
        -validity 10950 \
        -dname "CN=OKBIT,O=OKBIT"
    chmod 600 "$signing_dir/release.p12"
)
```

Пароль создать в менеджере паролей и ввести в интерактивные запросы keytool.
Для PKCS12 используйте один сильный пароль хранилища и ключа. Не добавляйте
`-storepass`/`-keypass` со значениями в команду. Для properties удобно использовать
случайный длинный пароль из ASCII букв, цифр, `_` и `-`: так не требуется экранирование
обратных слешей и пробелов в Java Properties. Ключ RSA 3072 и срок около 30 лет
согласованы; CN/O строго OKBIT.

Проверьте публичные сведения сертификата (пароль вводится интерактивно):

```sh
keytool -list -v -storetype PKCS12 \
    -keystore "$HOME/.local/share/android-signing/simple-led-clock/release.p12" \
    -alias simple-led-clock-release
```

Сохраните SHA-256 сертификата как публичный идентификатор. Не путайте его с SHA-256
самого keystore или APK. Получение настоящего отпечатка возможно только после создания
ключа; заранее выдумывать значение нельзя.

## Внешний файл параметров подписи

Создайте в защищённом каталоге новый `signing.properties` с правами 600, используя
редактор без синхронизации, журналирования, swap/backup-файлов и истории изменений.
Пример структуры ниже — только шаблон; заполненные значения не копировать в Git:

```properties
storeFile=/ABSOLUTE/EXTERNAL/PATH/release.p12
storePassword=<FROM_PASSWORD_MANAGER>
keyAlias=simple-led-clock-release
keyPassword=<SAME_PASSWORD>
certificateSha256=<ACTUAL_CERTIFICATE_SHA256>
```

Путь keystore — абсолютный, вне Git. SHA-256 состоит из 64 HEX-символов; двоеточия
из вывода keytool допустимы. Закрепление отпечатка предотвращает случайную подпись
другим ключом; после создания сохраните его также в публичной документации проекта.
Файл properties не является шифрованным: ограниченные права и шифрование диска
обязательны, для резервных копий требуется отдельное шифрование.

В окружение сборки передаётся только путь:

```sh
CLOCK_RELEASE_SIGNING_FILE="$HOME/.local/share/android-signing/simple-led-clock/signing.properties" \
    sh ./gradlew --no-daemon --no-configuration-cache \
    :app:assembleLegacyRelease :app:assembleModernRelease
```

Нужен настроенный Android SDK (через ANDROID_HOME или локальный local.properties).
Для серверной локальной цепочки инструментов можно использовать существующий
`sh ./scripts/build-local.sh` с теми же задачами вместо `sh ./gradlew`.
Не передавайте пароли через `-P`, `-D`, переменные окружения или команды shell.
Не используйте `--debug`, build scan и configuration cache при работе с секретами.
С конфигурацией подписи включённый configuration cache намеренно запрещён.

## Защита выпуска

- `release` использует только signingConfig `release`, никогда debug fallback.
- `:app:validateReleaseSigning` проверяет внешний файл, права и доступ к private key.
- Проверяются PKCS12, действительность сертификата, CN/O=OKBIT, RSA 3072 и SHA-256.
- Известный сертификат Android Debug 1.0.2 запрещён отдельно.
- Проверка обязательна перед package/sign/bundle/assemble и validateSigning обеих сборок.
- Без параметров разрешены проверки исходников, но выпуск APK/AAB блокируется.
- v1 и v2 включены явно; v1 нужна для Android 4–6. Keystore PKCS12 используется только
  на машине сборки, Android устанавливает подписанный APK и не читает keystore.

Не обходите проверку через `-x`, не распространяйте debug APK или промежуточные unsigned
файлы. Сборка должна начинаться с доверенного чистого checkout без старых APK в outputs.
После сборки отдельно проверьте именно распространяемые файлы:
`apksigner verify --verbose --print-certs`, `zipalign -c -P 16 -v 4`, фактический манифест,
minSdk/targetSdk/versionCode, PNG launcher icon и SHA-256. У обоих APK должен совпасть
новый отпечаток сертификата и отличаться от Android Debug. Сборка и lint не заменяют
проверку установки/запуска на устройствах API 14 и 23+.

## Проверки без нового ключа

Оставьте CLOCK_RELEASE_SIGNING_FILE незаданной. Тесты и lint не требуют подписи:

```sh
sh ./gradlew --no-daemon --no-configuration-cache \
    :app:testModernReleaseUnitTest :app:lintLegacyRelease :app:lintModernRelease
sh ./gradlew --no-daemon --no-configuration-cache \
    :app:testLegacyReleaseUnitTest -PlegacyAndroid
sh ./gradlew --no-daemon --no-configuration-cache :app:validateReleaseSigning
```

Последняя команда обязана завершиться ошибкой отсутствующей release-конфигурации.
Успешный запуск тестов не является проверкой подписанной сборки без существующего ключа.

## Резервное копирование и восстановление

1. Сохраните две зашифрованные резервные копии `release.p12` на независимых носителях,
   одну физически отдельно. Перед копированием проверьте отпечаток сертификата.
2. Пароль храните в менеджере паролей с отдельным защищённым восстановлением.
   Копия properties с паролями требует такого же шифрования, как keystore.
3. Запишите alias, PKCS12, CN/O, публичный отпечаток, срок действия, владельца и даты
   проверки резервных копий. Только сертификат без закрытого ключа для подписи непригоден.
4. Проверьте восстановление на доверенной машине: восстановите в новый каталог 700,
   установите файлу 600, сравните SHA-256 keystore с оригиналом и отпечаток через keytool.
5. Воссоздайте properties с корректным абсолютным путём и проверьте
   `:app:validateReleaseSigning`. Эта проверка также подтверждает чтение private key.
6. После восстановления отдельно соберите тестовый выпуск и проверьте подпись APK.
   Не создавайте новый ключ взамен потерянного под тем же alias: это другой сертификат.

Потеря пароля/закрытого ключа лишает возможности обычных дальнейших обновлений.
Не удаляйте старую копию до успешной проверки восстановления.

## Источники

- https://developer.android.com/studio/publish/app-signing
- https://source.android.com/docs/security/features/apksigning/v3
- https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication/apk-signature
