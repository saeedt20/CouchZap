# Changelog

## 3.1.1 — 2026-10-17 (stable)

Four owner-reported refinements on the stable 3.1.0 base. Persian/Arabic Settings row chevrons use fixed left-pointing assets bound deterministically from the saved app language, including after relaunch. The mobile Persian/Arabic About header points right; TV keeps left. TV choice pages initially focus the configured value. A phone rotation button below the quick Settings gear toggles landscape/portrait with a retained session override. Independent review fixes the rotation/Settings target overlap and hides quick actions while Settings or channels own the screen; real regression checks replace unconditional assertions. Version code 19, established release certificate. All 331 tests pass; both lint variants have zero errors; the newly signed APK matches all 685 application entries in both independent unsigned builds. Physical-device checks remain not run. Supersedes stable 3.1.0. See the [acceptance report](docs/releases/v3.1.1-verification.md).

## 3.1.0 — 2026-10-17 (stable)

Graduate the native redesign from beta. Fix first-tap activation of mobile Settings options while preserving D-pad focus; use left-pointing Persian/Arabic Settings chevrons, top-right next-channel previews and localized Info clock digits; simplify About in all six languages with creator/MIT credits and a dedication. Restore the English preview safe inset and remote scrolling through About. Version code 18, established signing certificate. All 213 tests pass; both lint variants have zero errors; signed APK matches the independent app payload. Physical-device checks remain not run. See [release notes](docs/releases/v3.1.0.md) and [acceptance record](docs/releases/v3.1.0-verification.md). Supersedes stable 2.2.1 and the 3.0 prereleases.

## 3.0-beta.2 — 2026-10-16 (prerelease)

Ten owner-reported beta fixes on the native redesign: keyboard dismissal after channel search selection, favourite star without the green selected line, no chevron on the Channel switch sound toggle, separated compact-phone Settings sections, aligned mobile Info buttons, rewritten About with creator credit and MIT license (root LICENSE added), first-tap activation for mobile buttons, right-pointing FA/AR settings chevrons, Persian/Arabic localized digits, and a native download progress bar. Version code 17. See [release notes](docs/releases/v3.0-beta.2.md). Beta, not stable; 2.2.1 remains the stable release.

## 2.2.1 — 2026-10-15

Fix remote Up/Down jumping across the public country/language/category picker on Android TV, reported on the XDSR 685 HDR with Android 7. Follow the focused control inside the active panel and link catalog rows in displayed order, including after filtering. See [release notes](docs/releases/v2.2.1.md).

## 2.2.0 — 2026-10-15

Open the welcome screen directly without a separate required language step. Add complete Spanish, Hindi, Thai and Arabic interface and channel-category translations, with English, Spanish, Hindi, Thai, Persian and Arabic in that order. Arabic follows Persian RTL text and right-docked panels. Update README screenshots and release preparation. See [release notes](docs/releases/v2.2.0.md).

## 2.1.4 — 2026-10-15

Preserve continuous search typing, translate Persian channel groups, dock Persian panels on the right and clarify favourite state. See [release notes](docs/releases/v2.1.4.md).

## 2.1.2 — 2026-10-15

Fix the white focus overlay after returning from settings on newer Android versions; confirmed on an Android 16 phone. Shift TV channel content slightly left, restore spacing between Language and Check for updates, and keep the channel-switch sound toggle compact with one remote focus target. See [release notes](docs/releases/v2.1.2.md).

## 2.1.0 — 2026-10-14

Refresh the Persian/English interface, public-source selection, playlist ordering, retained settings navigation and optional CRT channel-switch sound. See [release notes](docs/releases/v2.1.0.md).

## 2.0.2 — 2026-10-14

Fix Android 7 public-playlist TLS trust; add verified in-app GitHub updates, private diagnostic sharing and live Android 7 network regression checks. See [release notes](docs/releases/v2.0.2.md).

## 2.0.1 — 2026-10-14

Fix Android 7 launch-time font inflation crash; defer player initialization and add installed API 24 startup regression checks. See [release notes](docs/releases/v2.0.1.md).

## 2.0.0 — 2026-10-13

See the [2.0.0 release notes](docs/releases/v2.0.0.md) for the interface redesign, public playlist support, favourites, XMLTV, playback options and remote-navigation fixes.

## 1.0

Initial native Android TV and phone player with local playlist import, Media3 playback, remote/touch navigation and local cache.
