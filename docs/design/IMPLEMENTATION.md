# Native design implementation

Historical 3.0-beta implementation record. The redesign is now stable in **3.1.0**; see the [current release notes](../releases/v3.1.0.md) and [3.1 acceptance record](../releases/v3.1.0-verification.md). Results and release-status statements below describe the beta checkpoint.

Baseline: `30cf8de44ca78ae5ad120a9a59177ac20608af6f` (main, 2.2.1, version code 15).
Contract: the user-supplied [COUCHZAP_DESIGN_SPEC.md](SPEC.md), prepared 15 October 2026.

Implementation is on `codex/native-design`. The redesign was released as **3.0-beta**, version code **16**, for early testing; **2.2.1 remains the stable release**. On 16 October 2026 the user authorized aligning the committed version with the existing beta APK, correcting CI and completing the release documentation. SDK/toolchain, private signing material and operational data/player/security code remain outside these corrections. The only added production dependency is AndroidX DynamicAnimation 1.1.0.

## Checkpoint 1: Channels and Info

- Shared semantic colors, dimensions, typography and cached native focus chrome; retained spring scale with composed semantic callbacks and detach/recycle resets.
- TV controls use a single 5% safe rectangle, 400dp Channels shell and bottom Info dock. Touch uses its own control composition.
- Channel names/source numbers and current playback status remain data-driven. Playing, temporary focus and favourite have independent presentation.
- Native Groups modal uses original source IDs, query-constrained counts, independent radio selection, bounded remote traversal and scoped return focus.
- Channels/Info transitions retain desired visibility during reversal; list touch streams are isolated from playback gestures.
- Bundled static fonts and upstream licenses: [font manifest](FONTS.md).

Checkpoint results: Kotlin compilation passed. All 27 remote/search/catalog regression cases passed, with no failures/errors/skips. The native API 28 viewing checkpoint passed in English, Persian and Arabic. Six fixture captures were written to `/workspace/couchzap-design-captures/phase1/` and inspected (English Channels and Arabic Info). The compact TV dock measured within 176–220dp, with the exact 864dp width and 27dp bottom safe inset. Native fixture images do not establish physical SurfaceView/compositor/decoder performance.

## Checkpoint 2: Settings and depth

- One owned transparent Dialog retains only Settings content; XML initializes video/HUD scene layers before playback.
- Playback / Playlist / App rail, separate Exit, mirrored physical navigation, retained nested routes, stable action keys and window-local focus.
- Safe and Off never write video/surface/subtitle properties; Full derives scene depth, opaque corner masks, scrim and guarded public API blur from one reversible spring.
- Public capability/power/blur gates, two-transition frame fallback, lifecycle normalization, guarded Back/Menu/confirm and header-only touch drag.
- Native checkable On/Off row, choice rows, effects selection, all six language names and retained update progress presentation.

Checkpoint results: 32 policy/motion/window/focus/language/visual cases passed, with no failures/errors/skips. Native API 28 Settings captures cover Playback, sound On, Playlist, App, language and picture size in English/Persian/Arabic under `/workspace/couchzap-design-captures/phase2/`. English Playback and Arabic language captures inspected. Physical blur, SurfaceView depth and decoder/compositor frame behavior remain unverified without hardware.

## Checkpoint 3: Welcome and sources

- Original native mark, optional native-language header action, 44/56 TV columns with 40dp gap and contained action metadata; compact touch uses a vertical scrollable composition.
- Intro is bounded to 320ms, including 0/40/80ms starts and a single subtle bolt accent. The first event settles and performs its action.
- Retained file/link/provider routes; system picker and API 24 fallback keep existing ownership and permission behavior.
- URI field is LTR, labeled, at least 56dp; inline validation retains text, and IME Done/Import share one guarded callback.
- UI-only virtual catalog retains original labels/codes and query matching, stable provider/kind/code keys, pending sequential focus, fixed search, loading/error/Retry/empty states. Only visible rows are mounted.
- Existing replacement confirmation initially focuses Cancel; all source acceptance and import commit/cancel behavior remain owned by the existing operational code.

Checkpoint results: 46 tests passed across catalog, onboarding, intro, language and native welcome rendering; six catalog cases exercise API 24/36 and English/Persian/Arabic with 240 options and held navigation. A further native source-state checkpoint and complete provider-route tests passed. Native welcome in all six scripts and source/loading/failure/Retry/empty captures are under `/workspace/couchzap-design-captures/phase3/`; English catalog and Arabic inline link error were inspected.

## Checkpoint 4: Adaptive presentation

- Window bounds and effective input mode are independent. TV uses its safe rectangle and rail; compact touch uses a grouped page; expanded touch keeps phone typography and targets.
- Phone Info has four primary and three secondary controls, two-line titles, bounded metadata and a separate scrolling technical area. Actual video/audio fields use two LTR columns. Controls remain reachable in short windows and at large font sizes.
- Channels retain query/caret under IME insets. On a short phone with the keyboard visible, Close moves beside search and the filter state is retained while chrome compacts.
- Sound/favourite are checkable, choices expose selected radio state, pane titles and headings are scoped, covered scenes lose accessibility/input ownership, and native full-window highlights are explicitly disabled.
- Typeface IDs are explicit so release shrinking retains all 12 static font weights; focus/row binding avoids repeated capability/package queries.
- Header drag uses screen coordinates and releases its tracker on close/stop; matching releases and pending catalog focus remain guarded.
- Groups use the Activity safe rectangle. Dismissal cleanup runs once; Cancel restores its real origin, selection returns to channels, and a stale dismissal cannot clear a newer modal.

Native adaptive checkpoint passed 360×800, 412×915, 800×360, 600×800, 840×800 and 960×540 TV at 1.0/1.3/2.0 font scales in English/Persian/Arabic. API 36 typed IME insets retain the caret and a usable row/Close on a short phone. API 24/36 stop/resume tests retain the exact URI page, field object, text, caret and focus. Five forced-Full progress fixtures cover 0/25/50/75/100%; these are presentation fixtures on API 28, not recordings of platform blur or live video.

## Changed-file map

| Area | Files / responsibility |
| --- | --- |
| Shared presentation | `ui/design/`: adaptive metrics, static fonts/tokens, native action/choice/focus components, springs, bounded Info content, virtual catalog options |
| Viewing | `ChannelListAdapter.kt`, `OsdController.kt`, `ui/channels/GroupsPicker.kt`, channel/Info XML and semantic drawable/color resources |
| Settings | `SettingsPanel.kt`, `SettingRow.kt`, `ui/settings/`: retained pages/window, rail graph, depth/mask/blur policy, frame guard and header drag |
| Welcome / sources | `view_import.xml`, `ImportFragmentlessScreen.kt`, `WelcomeIntro.kt`, `PlaylistMenus.kt`, file-row styling in `SimpleFileBrowser.kt` / `item_file.xml` |
| Activity seams | `MainActivity.kt`: UI controller construction, actual Settings view registry, overlay dispatch/ownership, insets/layout, root row bindings, presentation-only guide observer and lifecycle cleanup |
| Support UI | `UpdateMenu.kt`: retained progress/Cancel renderer and window-local focus; `DiagnosticMenu.kt`: readable monospace report metrics |
| Language / preferences | `AppLanguage.kt`: compound-row labels and detached host; `Prefs.kt`: only `effects_mode`; six localized `cz_strings.xml` files and plural counts |
| Build / beta metadata | DynamicAnimation 1.1.0 in the existing catalog/app dependencies; `app/build.gradle.kts` version 3.0-beta/code 16; `.github/workflows/android.yml` beta version parsing, branch checks, test/lint artifacts and published APK payload comparison |
| Review | `README.md`, this report, `FONTS.md`, selected native captures and adapted/added regression tests |

## Scope review

The original design baseline manifest covers 37 data/player/security/release/script files. The user-authorized beta CI correction changes only `.github/workflows/android.yml` among those 37 files; the other 36 still match the pinned hashes. The manifest remains the original baseline rather than being rewritten to hide the exception. An additional MainActivity comparison found import/picker/fallback, cache loading, source acceptance, zap/debounce/commit/play, player callbacks, track selection, guide import and hard exit unchanged. Player creation/lifecycle calls remain in their existing paths.

The beta correction changes version metadata and verification/documentation only. Application ID, SDK/toolchain, installer, wrapper, distribution reconstruction recipe and network security remain unchanged. The existing signed beta APK is retained; the private signing backup is not used. The source tag is aligned with the committed beta metadata. No merge to main or stable-release promotion is part of this correction. Release assembly clears `COUCHZAP_KEYSTORE` to produce an unsigned comparison artifact.

## Final verification

| Check | Result |
| --- | --- |
| `:app:assembleDebug` | Passed |
| `:app:assembleRelease` | Fresh aligned beta build passed in 2m 38s; all 680 app payload entries match the published signed beta, excluding signatures/VCS metadata |
| `:app:testDebugUnitTest` | 156 passed, 0 failures/errors/skips in the independent 16 October source review; [machine-readable result](verification/final-tests-summary.json). Corrected beta metadata is checked again by branch CI. |
| `:app:lintDebug` | Passed: 0 errors, 207 warnings |
| `:app:lintRelease` | Passed: 0 errors, 208 warnings |
| `git diff --check` and local documentation links | Passed |
| Protected-file SHA-256 comparison | 36 unchanged; only the explicitly authorized CI workflow differs from the original manifest |
| Release font/resource audit | All 12 added static fonts retained byte-for-byte, with all four upstream license notices |

The final production build and both lint tasks completed successfully in 8m 2s. The final full test run also exercises every one of 240 catalog entries down and back up, filtered/held-key paths, rapid category transitions, six-language Settings navigation and return state. Lint still reports warnings; this is not a warning-free build. SDK/toolchain remain minSdk 24 and compile/target 35. The committed version now matches the published APK: 3.0-beta/code 16. The 16 October independent complete suite finished successfully in 4 minutes. The earlier interrupted run is historical and has been superseded.

### Review artifacts

- Selected native previews are source review files under [captures/](captures/): [TV welcome](captures/welcome-tv-en.png), [Arabic welcome](captures/welcome-tv-ar.png), [Hindi phone welcome](captures/welcome-phone-hi.png), [Playback settings](captures/settings-tv-en.png), [Arabic language choices](captures/language-tv-ar.png), [Arabic Groups](captures/groups-tv-ar.png) and [expanded TV Info at font scale 2](captures/info-expanded-tv.png).
- The complete native PNG inventory is `/workspace/couchzap-design-captures/final/`. It includes welcome/language scripts, Channels/Groups/Info, Settings roots and child pages, source/catalog loading/error/Retry/empty, update/diagnostic/About, short-phone IME and the adaptive matrix. These use fixture channel metadata and backdrops.
- Deterministic Full presentation frames: `/workspace/couchzap-design-captures/final/native-progress-full-{0,25,50,75,100}.png`. These API 28 fixtures exercise presentation progress through a test hook; they do not prove platform blur or live SurfaceView behavior.
- Build/lint log: `/workspace/couchzap-design-final-window-release.log`. Completed independent suite log: `/workspace/couchzap-3beta-review/tests.log`; summary: [final-tests-summary.json](verification/final-tests-summary.json). The earlier `/workspace/couchzap-design-final-complete-tests.log` was interrupted and is not final evidence. Machine-readable artifact hashes and audits: `/workspace/couchzap-design-verification/artifacts.json`.
- Local Debug APK: `app/build/outputs/apk/debug/app-debug.apk`; unsigned Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`. No release signing material is packaged. The unsigned artifact is for review and is not an update signed with the user's production key.

### Effective tiers and remaining device checks

| Tested policy or motion fixture | Result |
| --- | --- |
| API 24/25/28 with animations enabled | Safe, including an explicit Full preference |
| API 31+ capable fixture with public blur enabled | Full: video/HUD 0.94, 18dp TV rounding, 0.5 scrim; exact reset and interrupted reversal verified |
| Low RAM, insufficient memory, no hardware acceleration, power save, blur unavailable, surface failure or session downgrade | Safe; Full preference cannot bypass these guards |
| Safe lifecycle and reversal | Zero video adapter writes, including close/destroy cleanup |
| System animations disabled or explicit Off | Off |
| Simulated video-transform exception during Full | Normalize once, remove blur and continue Safe without further video writes |

Policy and motion cases run on Robolectric API 24 and 36; native layout fixtures cover API 28 and 36. The test matrix is not a physical-device certification. No Android device is attached through ADB. Live Full blur/SurfaceView composition, HDR/decoder continuity, surface lifecycle callbacks, measured frame-time targets, the physical XDSR 685 remote, real 720p/1080p/4K outputs and real-device motion recordings remain unverified. The earlier user-reported Android 16/XDSR installation test predates this redesign and is not counted as validation of this branch. Auto selects Safe on Android 7 by policy; native hardware performance still needs measurement.


### Portable continuation evidence

The [Claude handoff](CLAUDE_HANDOFF.md), [original design contract](SPEC.md), selected native previews and [verification summaries](verification/) are included in this source branch. Absolute `/workspace/` paths above refer to the original machine and are not expected to exist after cloning elsewhere. The historical test summaries are explicitly not a final whole-tree verdict. Build artifacts, caches, private signing files and the full local capture inventory are not included.

### Published beta verification

See the [3.0 beta release notes](../releases/v3.0-beta.md) and [beta APK audit](verification/beta-release-audit.json). The original signed APK checksum is retained. CI compares all app payload entries with the published beta, excluding only signature records and VCS build metadata using the existing comparison script. Branch runs upload raw test XML and lint XML as downloadable artifacts. Historical font/build hashes in `artifacts.json` describe the earlier 2.2.1-metadata design build; they are not the signed beta checksum.

### Stable-update test fixtures

The first independent beta CI run ([38889879629](https://github.com/saeedt20/CouchZap/actions/runs/38889879629)) passed both APK builds and both lint tasks, then reported two update UI test failures out of 156. The tests had inherited the application's now-prerelease installed-version name. Stable-to-stable update UI fixtures now explicitly install the stable version name in Robolectric; all original progress, Cancel/focus, single-request and return-state assertions remain. No production updater code is changed. The actual beta updater's inability to compare installed prerelease names is documented in the release notes; beta updates currently require manual downloads.
