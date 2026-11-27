# RuTube Client — Agent Notes

## Project overview
A full-featured, ad-free RuTube client for Android (phone/tablet) and Android TV.
It is built as a multi-module Android project that uses RuTube's internal website
API and a custom Media3/ExoPlayer-based player. No account login is required:
subscriptions, history, bookmarks and downloads are stored locally on the device.

## Tech stack
- **Language / UI**: Kotlin, Jetpack Compose, Compose for TV
- **Build**: Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21
- **Player**: Media3 / ExoPlayer 1.9.4 (HLS, MediaSessionService, DownloadManager)
- **Networking**: OkHttp + kotlinx.serialization
- **Local storage**: Room (history, subscriptions, bookmarks, search history, downloads metadata)
- **Settings**: DataStore Preferences
- **Cast**: Google Cast SDK (optional, mobile only)
- **Images**: Coil
- **Min SDK**: 26 (Android 8.0)
- **Compile / Target SDK**: 35
- **DI**: manual (`AppContainer` / `TvAppContainer`)

## Module structure
```
core:model          Pure Kotlin data models (Video, VideoDetails, Comment, TvShow, etc.)
core:network        RuTube API client + DTOs, unit-tested against real responses
core:database       Room database + LibraryRepository
core:player         ExoPlayer factory, MediaSession background service,
                    offline-routing media source, quality controller
app-mobile          Phone/tablet app (Compose, navigation, catalog, downloads, cast, PiP)
app-tv              Android TV app (Compose for TV, catalog, D-pad player controls)
```

## Verified RuTube endpoints
The client talks to RuTube's public website API. The following endpoints were
verified against the live service and are used by the app:

| Feature | Endpoint |
|---|---|
| Home feed | `GET /api/v2/video/recommendation/main?page=N` |
| Categories list | `GET /api/video/category/` |
| Category videos | `GET /api/video/category/{id}/?page=N` |
| Search | `GET /api/search/video/?query=...&page=N` |
| Video details | `GET /api/video/{id}/` |
| Stream URL | `GET /api/play/options/{id}/?format=json` |
| Comments | `GET /api/comments/video/{id}/?page=N` |
| Channel videos | `GET /api/video/person/{authorId}/?page=N` |
| TV shows / series catalog | `GET /api/metainfo/tv/?page=N` |
| TV show details | `GET /api/metainfo/tv/{id}/` |
| TV show seasons | `GET /api/metainfo/tv/{id}/season` |
| TV show episodes | `GET /api/metainfo/tv/{id}/video?season=N&page=N` |
| Top movies | `GET /api/v2/video/recommendation/main/top_movie?limit=20&page=N` |
| Top serials | `GET /api/v2/video/recommendation/main/top_serial?limit=20&page=N` |
| Top cartoons | `GET /api/v2/video/recommendation/main/top_cartoon?limit=20&page=N` |
| Top anime | `GET /api/v2/video/recommendation/main/top_anime?limit=20&page=N` |

The stream URL is a signed HLS master-playlist (`video_balancer.m3u8`). It is
short-lived and is re-resolved right before playback and again when the CDN
answers HTTP 403 (signature expired).

## Why it's ad-free
Ads are delivered to RuTube's official player through the `advert` field of the
play/options response (AdFox VAST tags). This client never requests those tags;
it plays the HLS stream directly with its own player. No ad-blocking logic is
needed.

## Build & install
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug
```
Outputs:
- `app-mobile/build/outputs/apk/debug/app-mobile-debug.apk`
- `app-tv/build/outputs/apk/debug/app-tv-debug.apk`

Install:
```bash
adb install app-mobile/build/outputs/apk/debug/app-mobile-debug.apk
adb install app-tv/build/outputs/apk/debug/app-tv-debug.apk
```

## Tests
Run the network-layer tests (real captured API responses):
```bash
./gradlew :core:network:test
```

## Smoke test performed
Launched on an Android 35 (Pixel 6) emulator: home feed loaded with real RuTube
content, navigation between sections worked, and tapping a video opened the
player with playback started successfully. No runtime crashes.

Catalog feature smoke-tested on a Samsung Galaxy Tab A9 (SM-X115): the new
"Каталог" tab loaded series/TV shows with posters, switching to "Фильмы",
"Мультики" and "Аниме" loaded curated recommendation videos, tapping a series
opened the detail screen with episodes, and tapping an episode started playback.
Auto-fullscreen on landscape rotation and manual fullscreen button were also
verified on the video screen.

## Runtime notes
- **Card clicks**: use `Modifier.clickable(role = Role.Button)` on Material3
  cards.
- **Netflix-style home**: the mobile home uses a hero banner + horizontal
  rows (snap-fling `LazyRow`) instead of a grid, plus a "Продолжить просмотр"
  row derived from local history with a progress bar. The hero's "Мой список"
  toggles a bookmark. `HomeViewModel` takes `api` + `repository`. The TV home
  mirrors this with `TvHeroBanner` and `TvContinueWatchingRow`. Components
  live in `mobile/ui/components/NetflixComponents.kt` and `tv/ui/TvComponents.kt`.
- **Pull-to-refresh (mobile)**: custom `PullToRefresh` modifier in
  `mobile/ui/components/PullToRefresh.kt` (nestedScroll-based, not the
  experimental PullToRefreshBox which interfered with child clicks). The
  indicator is touch-transparent so clicks pass through. Applied to Home and
  Channel screens only.
- **Fullscreen playback (mobile)**: the video screen has a fullscreen toggle
  overlay in the top-right corner of the player. The icon is shown only while
  the ExoPlayer controller is visible and hides together with it. Entering
  fullscreen manually locks the activity to landscape, hides the system bars
  and fills the screen. Back or the same button restores portrait. Rotating
  the device to landscape automatically enters fullscreen; rotating back to
  portrait automatically exits fullscreen unless fullscreen was enabled
  manually.
- **Background audio / PiP (mobile)**: `PlaybackService` owns the player and the
  media session; UI connects via `MediaController`. The video screen disconnects
  the controller on navigation back, but the service keeps playing when the app
  is backgrounded or in PiP.
- **Downloads (mobile)**: Media3 `DownloadManager` with `AppDownloadService`. A
  fresh signed HLS URL is resolved before each download; exactly one video
  rendition is selected via `TrackSelectionParameters`. Completed downloads are
  routed to the downloads cache and played offline.
- **Quality selection**: uses ExoPlayer track-selection parameters; works both
  for streaming and downloaded HLS.
- **Cast**: optional; guarded so the app works without Google Play services.
- **TV player**: uses a local ExoPlayer and custom D-pad controls; it does not
  rely on the playback service because background playback is not required on TV.
- **Voice search (TV)**: `MainActivity` handles `ACTION_SEARCH` / `SearchManager.QUERY`
  and forwards the query to `TvSearchScreen`.
- **Autoplay next episode**: series episodes pass `nextVideoId` via the video
  route (`video/{id}?next={nextId}`). When playback ends, a 5-second countdown
  overlay offers "Смотреть сейчас" / "Отмена"; a "Следующая серия" button is
  also available in the controls (mobile + TV).
- **"Потому что вы смотрели X"**: home rows on mobile and TV load recent
  channel videos for the last two history entries.
- **Subtitles / audio tracks**: selected through ExoPlayer `currentTracks` and
  `TrackSelectionParameters`; buttons appear when the HLS manifest exposes
  multiple text/audio tracks.
- **Hero muted preview**: the hero banner resolves the featured video's HLS URL
  and plays it muted, looping, with a 2-second delay; the static backdrop fades
  out once the first frame renders.
- **"Вы всё ещё смотрите?"**: after 3 hours of continuous playback an
  AlertDialog (mobile) or full-screen overlay (TV) asks the user to continue;
  "Стоп" pauses the player.
- **Smart downloads**: downloading a series episode also queues the next episode
  (mobile).
- **Ratings**: thumbs-up / thumbs-down buttons on the video screen persist to
  Room via `RatingEntity` (mobile + TV).
- **Live search suggestions**: the mobile search field shows matching recent
  local queries as chips while typing.

## What is NOT implemented
- Login / account sync (everything is local by design).
- Remote search suggestions (local search history is used instead).
- Push notifications for subscriptions.

## Architecture decisions
- `core:model` and `core:network` are pure Kotlin JVM modules so the API layer
  can be unit-tested quickly without an Android runtime.
- Manual DI instead of Hilt/Koin to keep the build simple and avoid KSP
  version friction.
- Two separate `app` modules share the `core` modules; TV-specific UI and
  player controls live in `app-tv`.
