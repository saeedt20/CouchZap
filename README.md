# CouchZap

**Your playlist. Your remote. Your couch.**

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-native-7F52FF?logo=kotlin&logoColor=white)](#project-structure)
[![Playback](https://img.shields.io/badge/Playback-Media3-6750A4)](#features)

CouchZap is a lightweight IPTV player for Android TV boxes, with remote-friendly
channel browsing and a touchscreen interface for phones. Import your own playlist,
choose a channel, and start watching.

The app includes no channels, subscriptions, or streaming accounts. You supply the
playlist and stream sources.

## Features

- **Local playlist import** — M3U, M3U8, and TXT files through Android's document picker.
- **Remote navigation** — D-pad browsing, channel keys, and numeric channel selection.
- **TV and phone interfaces** — Auto detection plus manual TV and Phone modes.
- **Media3 playback** — HLS, RTSP, and compatible progressive media streams.
- **Playback overlays** — channel name, time, buffering status, and available stream details.
- **Display modes** — Fit, Fill, 16:9, and 4:3.
- **Local persistence** — cached channels, last selected channel, and interface preferences.
- **Background handling** — playback stops when the activity leaves the foreground.

TV mode uses landscape orientation and an inset channel panel to accommodate
overscan. Phone mode supports rotation, touch controls, and swipe gestures.

## Getting started

1. Build and install the APK using the instructions below.
2. Open CouchZap and select **Import playlist**.
3. Choose a local `.m3u`, `.m3u8`, or `.txt` file.
4. Browse channels with the remote or touchscreen and select one to play.

To replace a playlist, open **Settings → Re-import playlist**. To change the
interface, open **Settings → Interface mode** and choose **Auto**, **TV**, or **Phone**.

On Android 7–9, a basic storage browser is available when the system document picker
is missing. Android 10 and later require an installed system document picker.

## Controls

### TV remote

| Button | Action during playback |
| --- | --- |
| OK / Up | Open the channel list |
| D-pad / OK in the list | Navigate / play the focused channel |
| Right / CH+ / Page Up | Next channel |
| Left / CH− / Page Down | Previous channel |
| Down / Info | Show playback controls |
| Number keys | Enter a channel number |
| Play/Pause | Toggle playback |
| Menu | Open settings |
| Back | Close the active overlay; press twice with overlays closed to exit |

Playback controls hide after five seconds without interaction. Actual key mappings
can vary between remotes and Android TV boxes.

### Touchscreen

| Gesture | Action |
| --- | --- |
| Tap the video | Show or hide playback controls |
| Double-tap | Toggle playback |
| Swipe left / right | Next / previous channel |
| Swipe up | Open the channel list |
| Gear button | Open settings |

## Playlist examples

### M3U

```m3u
#EXTM3U
#EXTINF:-1 group-title="News",Example News
https://example.org/news/live.m3u8
#EXTINF:-1 group-title="Music",Example Music
https://example.org/music/live.m3u8
```

### TXT

```text
Example News,https://example.org/news/live.m3u8
Example Music,https://example.org/music/live.m3u8
```

These URLs are placeholders. Replace them with stream sources you can access.
The parser also accepts bare stream URLs and supported User-Agent/Referer metadata.
Duplicate stream URLs are removed and imported channels receive sequential numbers.

## Requirements

| Component | Requirement |
| --- | --- |
| Device | Android 7.0 or later (API 24+) |
| JDK | 17 |
| Android SDK | Platform 35 |
| Android Build Tools | 36.0.0 |
| Android Gradle Plugin | 9.1.1 |
| Gradle | 9.3.1, supplied through the wrapper |

Codec support and playback performance depend on the device and stream. Internet
access is required for network streams.

## Build

Clone the repository and open it in Android Studio:

```sh
git clone https://github.com/saeedt20/couchzap.git
cd couchzap
```

Install the SDK components listed above. Configure the SDK through Android Studio,
the `ANDROID_HOME` environment variable, or an untracked `local.properties` file.

Build the debug APK, run the tests, and check lint:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

No API key, backend service, or custom debug keystore is required. The Gradle wrapper
verifies the distribution checksum. Debug builds use standard Android debug signing;
release builds require your own signing configuration for distribution.

## Install

Transfer the debug APK to your device and install it, or use Android Debug Bridge:

```sh
adb devices
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

The application ID is `com.couchzap.iptv`. Future updates must use the same application
ID and signing key. Uninstalling removes the app's cached playlist and settings.

## Project structure

```text
app/src/main/
├── java/com/couchzap/iptv/
│   ├── MainActivity.kt    # Lifecycle, navigation, and interface coordination
│   ├── data/              # Playlist parsing and cached storage
│   ├── player/            # Media3 playback and stream information
│   ├── ui/                # Overlays, channel list, file browser, numeric input
│   └── util/              # Preferences, key mappings, and animations
└── res/                   # Layouts, strings, themes, and vector assets

app/src/test/              # Unit and Robolectric regression tests
app/src/androidTest/       # Device instrumentation test
gradle/                   # Version catalog and Gradle wrapper
```

## Testing

Unit and Robolectric tests cover playlist parsing, stream closure, preservation of
the cached playlist on empty or failed imports, interface mode selection, and remote
menu behavior.

```sh
./gradlew testDebugUnitTest lintDebug
```

Before using a new build on a TV box, check playlist import, channel switching,
audio/video playback, remote focus, aspect modes, and stopping/resuming after Home.
For phones, also check rotation and touchscreen controls. Automated tests do not
verify hardware decoders or live stream availability.

## Current limitations

- No EPG, favorites, playlist URL subscriptions, or provider account login.
- DASH and SmoothStreaming media-source modules are not included.
- HLS URLs without a recognizable `.m3u8` path can be treated as progressive media.
- Numeric channel entry supports up to four digits.
- Dedicated Play and Pause keys currently share the toggle behavior.
- URL validation checks supported schemes; malformed URLs can still be imported.
- DRM and subtitle controls are outside the current interface.

## Data and privacy

Imported channels and settings are stored in the app's private local storage. The
app has no analytics, advertising, or account-login integration. Stream playback
connects to the servers specified in your playlist. HTTP streams and cross-protocol
redirects are allowed, so transport security depends on the stream source.

Local SDK paths, editor state, tool caches, environment files, signing keys, build
outputs, and playlist files are excluded from version control. Keep credentials and
personal playlists out of source files and issue attachments as well.

## Feedback

Report bugs or suggest improvements through [GitHub Issues](https://github.com/saeedt20/couchzap/issues).
Include the Android version, steps to reproduce, and relevant logs with credentials,
private stream URLs, and personal details removed.
