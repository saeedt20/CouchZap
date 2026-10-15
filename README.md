<p align="center">
  <img src="docs/brand/couchzap.svg" alt="CouchZap couch and lightning logo" width="112">
</p>

<h1 align="center">CouchZap</h1>
<p align="center"><strong>Take a seat. Start watching.</strong><br>A calm, remote-friendly IPTV player for Android TV and phones.</p>

<p align="center">
  <a href="https://github.com/saeedt20/CouchZap/releases/latest">Download APK</a> ·
  <a href="#screenshots">Screenshots</a> ·
  <a href="#controls">Remote guide</a> ·
  <a href="https://github.com/saeedt20/CouchZap/issues">Report an issue</a>
</p>

CouchZap brings your playlist to the living room with a sage-green interface, clear focus states and uncluttered playback controls. Add a local file or playlist link, or optionally start with public channels from [iptv-org](https://github.com/iptv-org/iptv) or [Free-TV](https://github.com/Free-TV/IPTV).

Native Kotlin and Android views · Android 7.0+ · Media3 playback · No account required

## Download and start

1. Download **CouchZap-2.1.4.apk** from [Releases](https://github.com/saeedt20/CouchZap/releases/latest). A [SHA-256 checksum](distribution/CouchZap-2.1.4.apk.sha256) accompanies the signed APK.
2. Install it on your Android TV, TV box or phone. Allow installation from the app used to open the APK if Android asks.
3. On the welcome screen, optionally choose a language, then **Import playlist** for a local M3U, M3U8 or TXT file, or **Start with public channels**.
4. Select a channel and enjoy.

Use **Settings → Playlist sources** to import an HTTP(S) link, choose another source or refresh the current playlist. Choose a provider, then a country/language/category for iptv-org or a country/themed list for Free-TV. Choose source order or natural name order; source channel numbers remain unchanged. Public imports are opt-in.

Public channel availability depends on the source and your location. CouchZap downloads playlists when requested; it does not bundle streams or provide a subscription service.

## Screenshots

These images render the app's actual Android views using Robolectric 4.16.1 native graphics on API 28 (TV/welcome) and API 36 (phone). Channel data and the illustrative background are fixtures. They are not live broadcasts or physical-device captures; motion is disabled for stable screenshots. Updated channel/settings images show the Persian panels on the right and English panels on the left.

| Screen | English | فارسی |
| --- | --- | --- |
| Welcome | ![English welcome](docs/screenshots/v2.1.2/stage4-welcome-en.png) | ![ورود فارسی](docs/screenshots/v2.1.2/stage4-welcome-fa.png) |
| Channels | ![English channels](docs/work/phase07/screenshots/stage4-channels-en.png) | ![فهرست فارسی در سمت راست](docs/work/phase07/screenshots/stage4-channels-fa.png) |
| Info | ![English TV Info](docs/work/phase07/screenshots/stage7-info-en-tv-api28-favourite-off.png) | ![اطلاعات فارسی در گوشی](docs/work/phase07/screenshots/stage7-info-fa-phone-api36-favourite-off.png) |
| Settings | ![English settings](docs/work/phase07/screenshots/stage4-settings-en.png) | ![تنظیمات فارسی در سمت راست](docs/work/phase07/screenshots/stage4-settings-fa.png) |

<details>
<summary>Phone welcome, Android 16 settings and Persian search</summary>

<img src="docs/screenshots/v2.1.2/stage3-welcome-phone-en.png" alt="English phone welcome" width="270">
<img src="docs/screenshots/v2.1.2/stage3-welcome-phone-fa.png" alt="Persian phone welcome" width="270">

<img src="docs/work/phase07/screenshots/ui-fix-settings-phone-en-api36.png" alt="English Android 16 settings after Back" width="270">
<img src="docs/work/phase07/screenshots/ui-fix-settings-phone-fa-api36.png" alt="Persian Android 16 settings after Back" width="270">

<img src="docs/work/phase08/screenshots/stage8-fa-phone-api36-search.png" alt="Multiword Persian search and translated channel group" width="270">
<img src="docs/work/phase08/screenshots/stage8-fa-phone-api36-aspect.png" alt="Persian picture ratios" width="270">

</details>

<details>
<summary>Favourite button: absent and added</summary>

<img src="docs/work/phase07/screenshots/stage7-info-fa-phone-api36-favourite-off.png" alt="Favourite absent: outlined star" width="270">
<img src="docs/work/phase07/screenshots/stage7-info-fa-phone-api36-favourite-on.png" alt="Favourite added: filled star" width="270">

</details>

## Features

- **A calmer interface:** couch-and-lightning identity, warm sage accents, generous buttons and visible remote focus.
- **A better channel browser:** search that retains typing and the cursor while results update, groups, favourites, channel logos and a distinct playback-status label. The panel docks right in Persian and Arabic, and left in English, Spanish, Hindi and Thai. Focus and the currently selected channel remain separate, including during asynchronous connection errors. Up from the first visible channel wraps to the last; Down wraps back to the first.
- **Flexible playlist sources:** local files, HTTP(S) links and selectable public iptv-org and Free-TV directories. Cached channels remain available between launches; failed imports preserve the previous playlist.
- **Useful Info controls:** channel name, number, clock, play/pause, an outlined/filled favourite star and a second level for available codec, resolution and bitrate information.
- **Programme guide:** XMLTV from a file or link, including gzip files. Shows now/next and programme progress when guide IDs match the playlist.
- **Playback options:** HLS, DASH, RTSP and compatible progressive streams, available audio/subtitle tracks, Fit/Fill/16:9/4:3 picture modes, reconnect attempts and a retry/next-channel error panel.
- **Updates from Settings:** check the latest stable GitHub release, download it with progress, verify its checksum and CouchZap signing certificate, then open Android’s installer. Installation always requires your confirmation.
- **Diagnostic reports:** preview, share or save an app-only log from Settings. It contains device/version details and error classes/stacks; playlist URLs, channel names, tokens and account data are excluded. Boxes without a sharing/file app can copy the report to the clipboard.
- **Six interface languages:** choose English, Español, हिन्दी, ไทย, فارسی or العربية, in that order, from the welcome screen or Settings. First launch opens the welcome screen directly, with English as the default. Persian and Arabic use RTL text and right-docked channels/settings; English, Spanish, Hindi and Thai use LTR text and left-docked panels. Known IPTV groups are translated without changing playlist identities. Settings subpages retain parent focus and scroll when you press Back.
- **Light welcome motion:** a one-time intro that completes on the first key or touch while still handling that input. System animation settings are respected; Android 7 panels open immediately.
- **Optional CRT click:** enable **Settings → Playback → Channel switch sound** for an original, quiet 160ms sample. It is off by default, previews when enabled and plays only on committed channel changes; browsing, retries and reconnects stay silent. Media volume and mute are respected.
- **Everyday conveniences:** last watched channel, numeric selection for large playlists, a 15/30/60/90-minute sleep timer and saved interface preferences.
- **TV and phone support:** automatic device detection, manual TV/Phone modes, TV overscan padding, responsive phone layout and touch gestures.

Favourites and the current channel survive playlist refreshes when `tvg-id`, or the fallback name and group identity, remain stable. Refresh is manual; cached playlists and guides do not update automatically.

## Controls

### TV remote

| Button | Action |
| --- | --- |
| OK / Up during playback | Open channels |
| Up / Down in channels | Move focus; wrap at the first/last visible row |
| OK on a channel | Play it |
| Hold OK on a channel | Add/remove favourite |
| Left from a channel row | Reach the filter buttons, then search |
| Right / CH+ / Page Up during playback | Next channel |
| Left / CH− / Page Down during playback | Previous channel |
| Down / Info | Show controls; press Info again for stream details |
| Number keys / Delete / OK | Enter / correct / confirm a channel number |
| Play / Pause / Play-Pause | Play / pause / toggle |
| Last Channel | Return to the previous watched channel |
| Menu | Open settings |
| Back | Close the active panel; press twice with panels closed to exit |

Arrow keys move between buttons when a control panel has focus. Key mappings can vary between remotes. The expanded Info panel also includes a previous-watched-channel button.

### Phone

Tap the video to show/hide controls, double-tap to play/pause, swipe left/right to switch channels and swipe up to open the channel list. Use the gear button for settings.

## Playlists and programme guide

M3U metadata includes `tvg-id`, `tvg-logo`, `group-title`, User-Agent/Referer fields and HLS/DASH manifest hints. Duplicate stream URLs are removed; channel numbers follow playlist order and stay unchanged when the displayed list is sorted by name. TXT accepts `Name,URL` entries or bare stream URLs.

```m3u
#EXTM3U x-tvg-url="https://example.org/guide.xml.gz"
#EXTINF:-1 tvg-id="example.news" tvg-logo="https://example.org/logo.png" group-title="News",Example News
https://example.org/live.m3u8
```

The URLs above are placeholders. Import your guide through **Settings → Programme guide** and use matching `tvg-id` values. A guide URL in the playlist header can be used through **Refresh guide**; guides are not downloaded automatically. Guide import keeps programmes relevant to the next 48 hours and rejects oversized files. Audio tracks, subtitles and programme information appear only when the source supplies them.

Android 7–9 also have a basic file-browser fallback if the system document picker is missing. Android 10+ require an enabled system Files/document-picker app.

## Build from source

| Tool | Version |
| --- | --- |
| JDK | 21 |
| Android SDK / target SDK | 35 |
| Android Build Tools | 36.0.0 |
| Android Gradle Plugin | 9.1.1 |
| Gradle wrapper | 9.3.1 |
| Minimum Android | 7.0 / API 24 |

```sh
git clone https://github.com/saeedt20/CouchZap.git
cd CouchZap
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Configure the SDK in Android Studio, `ANDROID_HOME` or an untracked `local.properties`. On Windows use `gradlew.bat`. Debug output: `app/build/outputs/apk/debug/app-debug.apk`.

For a signed, optimized release, set these environment variables in your shell or secure build environment:

| Variable | Purpose |
| --- | --- |
| `COUCHZAP_KEYSTORE` | Absolute path to your private release keystore |
| `COUCHZAP_STORE_PASSWORD` | Keystore password |
| `COUCHZAP_KEY_ALIAS` | Key alias; defaults to `couchzap` |
| `COUCHZAP_KEY_PASSWORD` | Key password; defaults to the store password |

```sh
./gradlew :app:assembleRelease :app:lintRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without a configured keystore, the release output is unsigned. Published releases use a dedicated release key. Updates must use the same key; Android cannot install it over a build signed with a different key. Uninstalling removes local playlists and preferences. Signing keys and passwords are excluded from this repository. Maintainers can read the [reviewed-release publishing notes](docs/releases/publishing.md).

## Validation and limitations

The 2.1.4 implementation passed **116 unit/Robolectric cases** with zero failures, errors or skips, debug/release lint with zero errors (125 debug and 124 release warnings remain), and optimized release assembly. Coverage includes API 24, 28 and 36, continuous multiword/composing-text search, delayed remote navigation, Persian group filtering and labels, saved mode/aspect values, bilingual panel docking, favourite state, settings scrollbars and the Android 16 focus-halo regression. Native screenshots use API 28 and API 36 with fixture data. See the [bilingual UI checks](docs/work/phase07-bilingual-panels.md), [localization/search checks](docs/work/phase08-localization-search.md) and [2.1.4 release notes](docs/releases/v2.1.4.md).

The user tested the 2.1.3 UI fixes and the 2.1.4 candidate and approved publication. The published APK preserves the exact bytes of the tested 2.1.4 candidate and the existing release certificate. An [independent GitHub build](https://github.com/saeedt20/CouchZap/actions/runs/38660365176) passed and matched all 658 app payload entries; only signing records and VCS metadata are excluded from that comparison. Publication also checks the same signed APK on Android TV 7: clean installation, first language choice, public import, sound toggle, diagnostics and update UI. The previously user-confirmed Android 16 white-halo fix, Android 7 font-crash fix and TLS trust configuration are retained; the [2.1.0 network integration results](https://github.com/saeedt20/CouchZap/actions/runs/38611939876) document the earlier network checks. Physical TV remote behavior, hardware decoding, startup/frame timing and perceived sound loudness still require device checks.

Automated checks do not validate physical TV remotes, hardware decoders or live stream availability. Playback depends on the device's codec support and source availability. There is no provider-account login, DRM configuration, recording, catch-up service or SmoothStreaming module. Opaque HLS/DASH links use manifest hints or a server's HEAD content type; servers that supply neither may require a conventional manifest URL.

## Updates and diagnostic logs

Open **Settings → Check for updates**. Downloads come only from this repository’s stable GitHub Releases. CouchZap checks SHA-256, the application ID, version and the release signing certificate before offering installation. On Android 8+, allow CouchZap to install updates when Android asks; on Android 7, the device’s unknown-sources setting may be needed. Updates run only when requested.

Open **Settings → Share diagnostic log** to preview and share a text report, or save it to a file. Import failures also offer a shortcut to that report. Nothing is sent automatically. Logs stay in private app storage and retain a bounded recent history; they contain Android/device/version information and sanitized exception traces, rather than whole-system logcat or your playlist contents. Attach the report to a GitHub issue or send it to the maintainer.

Android 7 lacks the ISRG Root X1 certificate required by the public directory. CouchZap includes the official public root for the iptv-org and GitHub domains while keeping certificate-chain and hostname verification enabled. Other destinations continue to use system trust.

## Data and attribution

Playlists, guides, favourites and preferences stay in private app storage. CouchZap has no analytics, advertising or account service. It connects to playlist, guide, artwork and stream servers that you select; public directories connect to the chosen iptv-org or Free-TV source. HTTP sources are supported, so transport security depends on the source.

The [original CRT sample](docs/assets/crt_sound.md) is synthesized locally without third-party recordings and dedicated under CC0 1.0.

Thanks to [Free-TV](https://github.com/Free-TV/IPTV), [iptv-org](https://github.com/iptv-org/iptv) for its public playlist directory and metadata API, [AndroidX Media3](https://github.com/androidx/media) for playback, [AndroidSVG](https://github.com/BigBadaboom/androidsvg) for SVG artwork, and [Robolectric](https://github.com/robolectric/robolectric) for Android regression testing.

For bugs, include device/Android version, reproduction steps and sanitized logs in [GitHub Issues](https://github.com/saeedt20/CouchZap/issues).
