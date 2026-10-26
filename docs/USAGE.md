# CouchZap user guide

See the [README](../README.md) for the current download and installation steps.

## Languages

Choose a language from the welcome screen or **Settings → Language**. Selection is optional on first launch; the default is English. Your choice is saved and applied immediately, including after restarting the app.

| Menu order | Language | Text direction | Channels and settings |
| --- | --- | --- | --- |
| 1 | English | Left to right | Left side |
| 2 | Español | Left to right | Left side |
| 3 | हिन्दी | Left to right | Left side |
| 4 | ไทย | Left to right | Left side |
| 5 | فارسی | Right to left | Right side |
| 6 | العربية | Right to left | Right side |

Each language includes interface messages, settings, updates, diagnostics and known channel categories. Custom channel/group names and playlist identities are preserved.

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
- **Light welcome motion:** a one-time intro that completes on the first key or touch while still handling that input. System animation and visual-effects settings are respected.
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
| Left (Right in RTL) from a channel row | Reach the filter buttons, then search |
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

Tap the video to show/hide controls, double-tap to play/pause, swipe left/right to switch channels and swipe up to open the channel list. Use the gear button for settings. The rotation button below it switches between landscape and portrait; your manual orientation is kept for the session, including through language and layout changes. Rotation controls are phone-only.

## Playlists and programme guide

M3U metadata includes `tvg-id`, `tvg-logo`, `group-title`, User-Agent/Referer fields and HLS/DASH manifest hints. Duplicate stream URLs are removed; channel numbers follow playlist order and stay unchanged when the displayed list is sorted by name. TXT accepts `Name,URL` entries or bare stream URLs.

```m3u
#EXTM3U x-tvg-url="https://example.org/guide.xml.gz"
#EXTINF:-1 tvg-id="example.news" tvg-logo="https://example.org/logo.png" group-title="News",Example News
https://example.org/live.m3u8
```

The URLs above are placeholders. Import your guide through **Settings → Programme guide** and use matching `tvg-id` values. A guide URL in the playlist header can be used through **Refresh guide**; guides are not downloaded automatically. Guide import keeps programmes relevant to the next 48 hours and rejects oversized files. Audio tracks, subtitles and programme information appear only when the source supplies them.

Android 7–9 also have a basic file-browser fallback if the system document picker is missing. Android 10+ require an enabled system Files/document-picker app.

## Updates and diagnostic logs

Open **Settings → Check for updates**. Downloads come only from this repository’s stable GitHub Releases. CouchZap checks SHA-256, the application ID, version and the release signing certificate before offering installation. On Android 8+, allow CouchZap to install updates when Android asks; on Android 7, the device’s unknown-sources setting may be needed. Updates run only when requested.

Open **Settings → Share diagnostic log** to preview and share a text report, or save it to a file. Import failures also offer a shortcut to that report. Nothing is sent automatically. Logs stay in private app storage and retain a bounded recent history; they contain Android/device/version information and sanitized exception traces, rather than whole-system logcat or your playlist contents. Attach the report to a GitHub issue or send it to the maintainer.

Android 7 lacks the ISRG Root X1 certificate required by the public directory. CouchZap includes the official public root for the iptv-org and GitHub domains while keeping certificate-chain and hostname verification enabled. Other destinations continue to use system trust.
