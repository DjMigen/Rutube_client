# RuTube Client — Agent Notes

## Overview
Multi-module Android app: ad-free RuTube client for phones/tablets and Android TV.
No login — subscriptions, history, bookmarks, downloads are stored locally (Room).
Uses RuTube's public website API and a custom Media3/ExoPlayer player.

## Build & install
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug
```
APKs: `app-mobile/build/outputs/apk/debug/app-mobile-debug.apk`,
`app-tv/build/outputs/apk/debug/app-tv-debug.apk`.
Install: `adb install <apk>`.

## Tests
```bash
# model heuristics + network DTOs + playback session (mocked player, virtual time)
./gradlew :core:model:test :core:network:test :core:player:testDebugUnitTest
# single test class
./gradlew :core:player:testDebugUnitTest --tests "ru.rutubeclient.core.player.PlaybackSessionTest"
```
Only `core:model`, `core:network` and `core:player` have tests. `core:model`/
`core:network` are pure JVM modules (fast, no Android runtime);
`core:player`/`core:database`/`core:presentation` are Android libs.

## Module boundaries
- `core:model` — pure Kotlin data models (`Video`, `VideoDetails`, `TvShow`, …)
  plus series heuristics (`Series.kt`: `parseSeriesInfo`, `seasonOf`, `episodeOf`,
  `episodesForSeason`) — the single source of truth for episode filtering/sorting;
  screens and `SeriesDiscovery` must use it instead of re-deriving rules.
- `core:network` — `RuTubeApi` (interface + `HttpRuTubeApi`) + DTOs + loaders
  (`HomeFeedLoader`, `CatalogLoader`, `SeriesDiscovery`, `VideoSession`-free).
- `core:database` — Room + `LibraryRepository` (single entry point for all local data).
- `core:player` — `PlaybackSession` (playback policy), `PlayerFactory`, offline-routing
  media source, `QualityController`.
- `core:presentation` — ViewModels shared by both apps (`HomeViewModel`,
  `CatalogViewModel`, `TvShowViewModel`, `ChannelViewModel`, `ContinueItem`),
  plus shared `Routes` (nav strings + `videoKey` — always build the video
  ViewModel key through it) and `vmFactory`. Platform deltas are constructor
  params (`categoryLimit`/`alwaysRecommendations`, `pagination`), not subclasses.
- `app-mobile` — phone/tablet UI, navigation, downloads, cast.
- `app-tv` — TV UI, D-pad player controls.

Entrypoints: `app-mobile/.../MainActivity.kt` (`AppNavHost`),
`app-tv/.../MainActivity.kt` (`TvNavHost`). DI is manual: `AppContainer` (5 vals)
and `TvAppContainer` (3 vals), constructed in `Application.onCreate`. ViewModels are
built per nav-destination in MainActivity (~17 sites); the video route keys its
ViewModel with `"video-$id-$fromStart-$next"` — dropping that key silently reuses
a stale ViewModel.

## Architecture constraints (read before editing)
- **Manual DI, no Hilt/Koin.** Don't introduce a DI framework.
- **Shared ViewModels live in `core:presentation`** — fix bugs there once, both
  apps get the fix. Only genuinely platform-specific VMs stay in the apps
  (Search, Library, Subscriptions, Video).
- **`PlaybackSession` owns the player** and all playback policy (stream attach,
  resume, retry-once on 403, 5s position saves, autoplay, still-watching, sleep
  timer). Screens render `state`/`events` and send intents; they never program
  Media3 policy. Created per nav-destination, closed on dispose. StreamResolver
  is wired in MainActivity. See `docs/adr/0001`. **Do not reintroduce a
  shared/long-lived player or background playback** (deliberately removed in
  `06d06ac`).
- **Offline routing**: `PlayerFactory.create(context, provider)`. Mobile passes
  `DownloadsManager` (implements `OfflineContentProvider`); TV passes `null`.
  `PlaybackEnvironment` (global var) was removed — pass the provider explicitly.
- **Signed HLS URLs expire.** Re-resolve before playback and on HTTP 403. Enforced
  in `PlaybackSession` and in `DownloadsManager` (re-resolves before each download).
- **Downloads**: Media3 `DownloadManager` + `AppDownloadService`. `DownloadsManager`
  fetches the HLS master playlist, parses `EXT-X-STREAM-INF`, and downloads the
  chosen variant's media-playlist URL directly (exact quality, small files); it
  re-resolves + re-enqueues once on failure (expired signature). Smart download
  queues the next episode.
  `AppDownloadService` resolving deps via `(application as App).container` is the
  sanctioned service-locator pattern for system-created components — don't add
  more such lookups elsewhere (screens/VMs get deps via constructors).
- **Orientation**: `configChanges` in both manifests → composition survives
  rotation; fullscreen policy lives in `VideoScreen`.
- **Cast** is optional and guarded for missing Play Services.
- **TV voice search**: `MainActivity` handles `ACTION_SEARCH`/`SearchManager.QUERY`
  and forwards to `TvSearchScreen`.
- **Autoplay route param**: series pass `nextVideoId` via `video/{id}?next={nextId}`.

## Verified RuTube endpoints
| Feature | Endpoint |
|---|---|
| Home feed | `GET /api/v2/video/recommendation/main?page=N` |
| Categories | `GET /api/video/category/` |
| Category videos | `GET /api/video/category/{id}/?page=N` |
| Search | `GET /api/search/video/?query=...&page=N` |
| Video details | `GET /api/video/{id}/` |
| Stream URL | `GET /api/play/options/{id}/?format=json` |
| Comments | `GET /api/comments/video/{id}/?page=N` |
| Channel videos | `GET /api/video/person/{authorId}/?page=N` |
| TV shows | `GET /api/metainfo/tv/?page=N` |
| TV show details | `GET /api/metainfo/tv/{id}/` |
| TV seasons | `GET /api/metainfo/tv/{id}/season` |
| TV episodes | `GET /api/metainfo/tv/{id}/video?season=N&page=N` |
| Top movies/serials/cartoons/anime | `GET /api/v2/video/recommendation/main/top_{movie,serial,cartoon,anime}?limit=20&page=N` |

Stream URL is a short-lived signed HLS master playlist (`video_balancer.m3u8`).

## Ad-free (one line)
Ads come via the `advert` field (AdFox VAST) of play/options; this client never
requests those tags and plays the HLS stream directly. No ad-blocking logic exists.

## Not implemented
Login/account sync, remote search suggestions, push notifications for subscriptions.

## Testing quirks
- `core:network` tests run against captured JSON responses (mockwebserver).
- `core:player` tests use mockk + turbine + virtual time. Session scopes must be
  parented to `backgroundScope` (see `PlaybackSessionTest.newSession`) so the
  infinite save loop is cancelled at test end instead of stalling `runTest`.
- VMs/screens are untested: they take concrete `LibraryRepository`/`DownloadsManager`.

## Docs
- `GLOSSARY.md` — domain terms.
- `docs/adr/` — ADR-0001: playback session owns the screen player; episode
  switching stays navigation-based.

## Runtime gotchas
- **Card clicks**: `Modifier.clickable(role = Role.Button)` on Material3 cards.
- **Pull-to-refresh (mobile)**: custom `PullToRefresh` modifier (nestedScroll);
  indicator is touch-transparent. Home + Channel only.
- **Hero muted preview**: resolves the featured video's HLS URL, plays muted
  looping after a 2s delay; backdrop fades once the first frame renders.