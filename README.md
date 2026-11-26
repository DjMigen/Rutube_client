# RuTube Client

Неофициальный, бесплатный и **без рекламы** клиент RuTube для Android.

Приложение работает на телефонах, планшетах и Android TV. Вход в аккаунт не требуется — подписки, история просмотров, закладки и загрузки хранятся только на устройстве.

## Возможности

- **Главная лента** — рекомендации RuTube с фильтрами по категориям.
- **Поиск** — по видео с историей запросов.
- **Плеер** — воспроизведение через ExoPlayer/Media3, выбор качества, скорость, полноэкранный режим, PiP.
- **Каналы** — просмотр видео канала, локальные подписки.
- **Библиотека** — история, «Смотреть позже», скачанные видео.
- **Фоновое воспроизведение** — аудио продолжает играть при сворачивании.
- **Google Cast** — отправка видео на ТВ (опционально).
- **Android TV** — отдельное TV-приложение с управлением пультом.

## Почему без рекламы

Официальный плеер RuTube получает рекламу через VAST-теги из поля `advert` API. Этот клиент использует только прямую HLS-ссылку на видео, поэтому рекламные теги никогда не запрашиваются.

## Технологии

- Kotlin + Jetpack Compose / Compose for TV
- Media3 / ExoPlayer
- OkHttp + kotlinx.serialization
- Room, DataStore
- Google Cast SDK (опционально)
- Минимальная версия Android: **8.0 (API 26)**
- Target/compile SDK: **35**

## Сборка

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug
```

APK после сборки:
- Mobile: `app-mobile/build/outputs/apk/debug/app-mobile-debug.apk`
- TV: `app-tv/build/outputs/apk/debug/app-tv-debug.apk`

### Релизная сборка

Релизная подпись настроена на чтение паролей из `local.properties` (файл не попадает в Git).

```bash
./gradlew :app-mobile:assembleRelease
```

Результат:
- `app-mobile/build/outputs/apk/release/app-mobile-release.apk`

## Установка

```bash
adb install app-mobile/build/outputs/apk/debug/app-mobile-debug.apk
adb install app-tv/build/outputs/apk/debug/app-tv-debug.apk
```

## Тесты

```bash
./gradlew :core:network:test
```

## Архитектура

```
core:model      — чистые Kotlin-модели
 core:network    — клиент RuTube API + юнит-тесты
 core:database   — Room + локальное хранилище
 core:player     — ExoPlayer, PlaybackService, офлайн-воспроизведение
app-mobile      — мобильное приложение
app-tv          — Android TV приложение
```

## Лицензия

MIT
