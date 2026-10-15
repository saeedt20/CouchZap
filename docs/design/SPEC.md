# CouchZap — Native Android Design and Implementation Specification

**Status:** approved design direction; implementation contract for Codex.
**Prepared:** 15 October 2026.
**Authoritative repository:** https://github.com/saeedt20/CouchZap
**Baseline:** main, v2.2.1, versionCode 15.
**Inspected commit:** 30cf8de44ca78ae5ad120a9a59177ac20608af6f.
**Primary experience:** Android TV / Android Box, D-pad, 10-foot viewing.
**Secondary experience:** native adaptive phone, tablet and phone landscape layouts.

## 0. Instructions to the implementing Codex agent

Implement this specification in the existing Kotlin/XML Views application. Read this whole document before changing code. Treat the confirmed decisions and preservation rules as requirements. The earlier generated pictures establish mood only; they are not trustworthy specifications for text, data, proportions, focus behavior or animation.

The deliverable is a coherent, working native UI, including non-ideal states. Complete the four implementation phases in order, with useful review checkpoints. Do not stop at recoloring resources, producing screenshots, or implementing only the welcome screen.

### 0.1 Confirmed product decisions

1. Use GitHub main v2.2.1 as the sole baseline. There are no newer local changes to incorporate.
2. Retain Kotlin, XML/native Views, ViewBinding, AppCompat, RecyclerView and Media3. Use AndroidX DynamicAnimation for spring physics.
3. Implementation order:
   1. TV playback/Info and Channels.
   2. Settings, nested pages and signature transition.
   3. Welcome and import/source flows.
   4. Mobile adaptation and refinement.
4. TV Settings is a docked two-column overlay. In LTR, the master/category rail is on the left and detail pane on the right. Mirror the composition and horizontal traversal in Persian/Arabic.
5. Preserve the couch-and-lightning logo, sage identity and calm tone. Use warm charcoal, controlled translucency and precise luminous focus.
6. Full depth on capable devices: active video container and surrounding UI recede to 94%, video appears rounded, backdrop darkens and subtly blurs, Settings springs into place.
7. Safe depth on older/low-power boxes: video, subtitles and decoder surface remain at 100% and untouched; only ordinary surrounding HUD/UI recedes, with a dark scrim.
8. Keep all six languages in this order: English, Español, हिन्दी, ไทย, فارسی, العربية.
9. Channel switch sound uses a consistent value/action row showing On or Off and a chevron, rather than a visible platform switch.
10. Preserve existing playback, storage, imports, localization, remote mappings and safety checks. This work changes presentation and presentation coordination.

### 0.2 Verification before edits

Run read-only repository checks first:

~~~sh
git status --short
git rev-parse HEAD
git log -1 --oneline
rg --files -g AGENTS.md -g '*.kt' -g '*.xml' -g '*.gradle.kts' -g 'libs.versions.toml'
~~~

Read applicable AGENTS.md files if any exist in the implementation checkout. The inspected tree contained none. Do not discard user work or reset a dirty checkout. Compare a newer HEAD with the inspected commit before applying file-specific instructions; retain subsequent fixes. Do not silently implement against a stale historical tag.

This document names existing files explicitly and marks proposed files as NEW. New class names are design recommendations; preserve their responsibilities if the surrounding repository conventions require different names.

### 0.3 Explicit exclusions

- No Compose, TV Compose, WebView, HTML/CSS renderer, navigation-library migration, dependency sweep or wholesale MainActivity rewrite.
- No change to application ID, minSdk 24, targetSdk/compileSdk 35, versionCode/versionName, signing configuration, certificate validation, installer behavior or release publishing as part of the design work.
- No player replacement, TextureView substitution, stream pipeline change, decoder configuration change, surface recreation for an animation, or pausing playback when opening Settings.
- No changes to playlist/EPG schemas, stable channel identities, source numbering, search semantics, persistence formats, retry/debounce logic or cancellation semantics.
- Do not edit shell/Python scripts, Gradle wrapper, CI workflows, release assets or network-security configuration.
- No account system, subscriptions, ads, analytics, automatic public imports, automatic update checks, recording, catch-up or invented stream metadata.
- No new sound for focus, navigation or panel transitions. Preserve the existing opt-in CRT channel sound.
- Do not copy another application's appearance or use Apple assets/fonts. The motion should have comparable continuity and physical coherence.
- Do not implement expensive screenshots, per-frame bitmap blurs, hidden GPU benchmarks, reflection into hidden SurfaceView APIs or application-overlay permissions.

## 1. Baseline architecture and exact change boundaries

### 1.1 Verified architecture

The inspected app uses AppCompatActivity, ActivityMainBinding, included XML bindings, native programmatic Settings pages and RecyclerView. app/build.gradle.kts enables viewBinding and has no Compose setup. The video is a SurfaceView inside Media3 AspectRatioFrameLayout; SubtitleView is an ordinary sibling View. MainActivity coordinates playback and the UI in one activity.

Important existing behavior:

- SettingsPanel retains pages, focus and scroll; pages are not separate activities.
- ChannelListNavigation retains stable IDs and pending destinations while key repeats outrun layout; preserve this mechanism.
- Anim.showOverlay currently fades/slides 10dp over 150ms on API 26+; API 24/25 is immediate; hideOverlay is immediate.
- The activity already has TV/Phone/Auto interface modes, TV detection, landscape enforcement in TV mode, live RTL changes and responsive geometry.
- Native default focus highlights are deliberately disabled on API 26+ to prevent a full-screen focus halo. Native Button stateListAnimator/elevation are deliberately suppressed.
- UpdateMenu and DiagnosticMenu already use SettingsPanel. Their operations and lifecycle must survive the visual refactor.

### 1.2 Existing UI files to refactor

Paths are relative to repository root.

| Existing path | Authorized changes | Preserve |
| --- | --- | --- |
| app/src/main/res/layout/activity_main.xml | Layer hosts, Settings shell/master/detail, scene/HUD wrappers, error presentation, safe-area structure | SurfaceView identity, video_frame, subtitle_view, includes and logical action identities |
| app/src/main/res/layout/view_channel_list.xml | Header, filters, search, list/footer geometry and affordances | Search widget behavior, RecyclerView and filter actions |
| app/src/main/res/layout/item_channel.xml | New row hierarchy, focus chrome, independent status/favourite treatment | Runtime names, source numbers, stable identity, artwork binding |
| app/src/main/res/layout/view_osd.xml | TV bottom Info dock, compact mobile controls, expanded detail section | All existing control actions and callback bindings |
| app/src/main/res/layout/view_import.xml | TV two-column welcome, mobile vertical welcome, language control and integrated action metadata | Import/public/language entry callbacks and one-time intro |
| app/src/main/res/layout/item_file.xml | File row and folder/parent affordances | File selection/permission handling |
| app/src/main/res/values/colors.xml, dimens.xml, themes.xml | Add semantic tokens and component styles; bridge old resource names during migration | Compatible AppCompat theme and explicit native-halo suppression |
| app/src/main/res/values-v26/themes.xml | Retain native highlight suppression for the activity and new Settings host | Android 16 focus-halo fix |
| app/src/main/res/drawable/bg_row.xml, bg_focus.xml, bg_osd.xml | Replace broad generic selectors with role-specific surfaces/focus chrome | Press/focus/selection accessibility semantics |
| app/src/main/res/drawable/ic_*.xml | Normalize UI glyph geometry; add missing native vectors | Original ic_brand.xml / ic_brand_flash.xml artwork and launcher identity |
| app/src/main/java/com/couchzap/iptv/ui/SettingRow.kt | Evolve into reusable native setting row with icon/label/value/chevron and semantic state | One focus target per row |
| app/src/main/java/com/couchzap/iptv/ui/SettingsPanel.kt | Two-column rendering, root categories, retained page stack, host-aware focus lookup | page(), back(), choices(), button(), text() behavior, leave callbacks and return memory |
| app/src/main/java/com/couchzap/iptv/ui/ChannelListAdapter.kt | Bind presentation state, compose focus callbacks, perform complete recycle reset | stable IDs, selection/favourite callbacks and playlist data |
| app/src/main/java/com/couchzap/iptv/ui/ChannelListNavigation.kt | Only geometry/focus integration if required | Pending destination, remembered ID, bounded retry and first/last wrapping behavior |
| app/src/main/java/com/couchzap/iptv/ui/OsdController.kt | Layout adaptation, focus binder integration, presentation-only timing coordination | Control callbacks, live technical metadata and programme calculations |
| app/src/main/java/com/couchzap/iptv/ui/ImportFragmentlessScreen.kt | Adaptive geometry, presentation of progress and actions | Existing import/public/language callbacks |
| app/src/main/java/com/couchzap/iptv/ui/WelcomeIntro.kt | New restrained intro and robust settling | First input both finishes intro and performs its action |
| app/src/main/java/com/couchzap/iptv/ui/PlaylistMenus.kt | Native row builders, efficient visible lists, host-aware page rendering | Provider URLs, filtering, confirmations, jobs, cancellation and source acceptance |
| app/src/main/java/com/couchzap/iptv/ui/SimpleFileBrowser.kt | Row styling and focus presentation | Existing API 24–28 fallback and file access behavior |
| app/src/main/java/com/couchzap/iptv/util/Anim.kt | Delegate overlay presentation to reusable motion controllers; preserve shutter/favourite contracts | Animator-disable checks, interruption tokens and lifecycle cleanup |
| app/src/main/java/com/couchzap/iptv/util/AppLanguage.kt | Update label bindings for new rows and detached Settings host; translate new UI strings | Language codes/order/default, live application, RTL behavior and technical LTR isolation |
| app/src/main/java/com/couchzap/iptv/util/Prefs.kt | Add only effects-mode preference and any presentation-only preference explicitly specified here | Existing keys and values |
| app/src/main/java/com/couchzap/iptv/support/UpdateMenu.kt, DiagnosticMenu.kt | Update row/page construction and focus origin lookup if needed | Request ownership, checks, privacy, cancellation, sharing and installation logic |
| app/build.gradle.kts, gradle/libs.versions.toml | Add DynamicAnimation 1.1.0 only | Existing plugin/toolchain/dependency versions |

MainActivity.kt may change only at the presentation seams below. Avoid moving unrelated code just to make the diff look cleaner.

| Existing MainActivity seam | Required adaptation |
| --- | --- |
| setupChannelListOverlay(), updateChannelListAdapter() | New filter presentation and GroupsPicker controller; preserve filtering and selection |
| openChannelListOverlay(), closeChannelListOverlay() | Safe-area layout, input ownership and cancellable entry/exit |
| setupSettingsMenu(), toggleSettingsMenu(), closeSettingsMenu() | Delegate visibility/motion to a single SettingsWindowHost and new SettingsPanel root; retain callbacks |
| openWelcomeFlow(), languageMenu() | Preserve exact origin and nested-back behavior across the new host |
| applyInterfaceMode(), updateResponsiveLayout(), onConfigurationChanged() | Central adaptive resolver, insets and pane geometry |
| dispatchKeyEvent(), dispatchPanelDirection(), handleBack() | Narrow UI dispatch integration; retain naked-playback mappings and press/release guards |
| setupGesturesAndTouch(), dispatchTouchEvent() | Scope gestures to active UI owner; new Settings dismissal gesture only |
| updatePlaybackErrorFocus() | Treat entering/open/exiting Settings and Groups as covering input owners |
| onStop(), onDestroy() | Settle/cancel motion and dismiss owned window safely; preserve existing player lifecycle |

### 1.3 Protected Kotlin layers

Keep the following operational implementations unchanged:

- app/src/main/java/com/couchzap/iptv/data/: Channel, PlaylistRepository, PlaylistParser, PlaylistSource, PublicCatalog, PublicChannelOrder, HttpStreams, EpgRepository and Programme.
- app/src/main/java/com/couchzap/iptv/player/: PlayerController, StreamTypeResolver, StreamInfoFormatter, ChannelPlaybackStatus and CrtSwitchSound.
- app/src/main/java/com/couchzap/iptv/support/ReleaseUpdater.kt and DiagnosticLog.kt.
- app/src/main/java/com/couchzap/iptv/util/Keys.kt and GroupLabels.kt.
- MainActivity playback methods such as playCurrentChannel(), executeZap(), zapNext(), zapPrev(), jumpToChannelNumber(), importSource()/importUri()/importFile(), import/guide commit and cancellation paths, timer effects and hardExit().

Call these existing operations from new UI callbacks. If an apparent design requirement would require changing them, implement a UI adapter first. Record a genuine blocker instead of silently expanding scope.

### 1.4 Proposed new native presentation files

All names below are NEW, not claims about existing source.

| Proposed package/file | Single responsibility |
| --- | --- |
| ui/design/CouchZapTokens.kt | Runtime semantic colors/dimensions and typography access |
| ui/design/AdaptiveUi.kt | Effective TV/touch mode, window geometry, layout classes and safe insets |
| ui/design/FocusMotionBinder.kt | One focus/press animation owner per interactive component |
| ui/design/FocusChromeDrawable.kt | Cached focus ring, optional inexpensive luminous outer strokes |
| ui/design/UiFocusSnapshot.kt | Stable row/page/scroll/input-origin memory |
| ui/design/ChoiceRow.kt | Native single-selection row with native name/secondary text/check |
| ui/design/ActionRow.kt | Native action with title, metadata and optional icon/value |
| ui/settings/SettingsWindowHost.kt | One activity-owned native Dialog window, input ownership and dismissal |
| ui/settings/SettingsMotionController.kt | Interruptible normalized spring and layer transforms |
| ui/settings/SettingsCategoryController.kt | Master/detail presentation without changing logical routes |
| ui/settings/EffectsPolicy.kt | Auto/Full/Safe/Off resolution and capability/fallback reasons |
| ui/settings/WindowBlurApi31.kt | API-gated cross-window blur integration and listener cleanup |
| ui/settings/VideoCornerMaskView.kt | Visible rounded video corners using ordinary opaque corner masks |
| ui/channels/GroupsPicker.kt | Native category picker, counts and scoped focus return |
| res/layout/view_settings_shell.xml | Reusable shell; original action IDs remain unique |
| res/layout/item_setting.xml, item_choice.xml, item_group.xml | Shared native component structures |
| res/values/cz_colors.xml, cz_dimens.xml, cz_styles.xml, cz_ids.xml | Semantic native resources |

Do not create a new activity, service, second player, new domain state store or navigation graph.

## 2. Art direction and design system

### 2.1 Identity

The visual signature is a framed viewing scene, warm near-black surfaces, quiet sage accents, an inset luminous focus edge and exact typography. Ornament must have an interaction purpose. Use the original logo as a crisp native vector; use its lightning detail only for the existing one-time welcome accent. No permanent logo in the live video or giant decorative couch behind text.

Depth consists of: video/canvas → restrained scrim → translucent shell → grouped content plane → focused control. Separate these layers through luminance, border treatment and motion, not stacks of shadows.

### 2.2 Color tokens

Eight-digit values below use Android #AARRGGBB order. Kotlin UInt examples convert with .toInt() when passed as ColorInt. Do not paste Android values into CSS or treat them as #RRGGBBAA.

| Token/resource | RGB / Android ARGB | Role |
| --- | --- | --- |
| cz_canvas | #0D100F / #FF0D100F | App canvas and exposed area around recessed video |
| cz_surface_base | #171C19 / #FF171C19 | Opaque fallback panel |
| cz_surface_shell | #1C221E / #FF1C221E | Shell solid color |
| cz_surface_shell_translucent | #F21C221E | Full-tier shell, 94.9% alpha |
| cz_surface_rail | #181D1A / #FF181D1A | Category rail |
| cz_surface_content | #222923 / #FF222923 | Group/content plane |
| cz_surface_content_translucent | #EB222923 | Full-tier grouped plane |
| cz_surface_hover | #2A342C / #FF2A342C | Touch/pointer hover where applicable |
| cz_surface_focus | #303D32 / #FF303D32 | Focused control fill |
| cz_surface_selected | #253028 / #FF253028 | Persistently selected control without focus |
| cz_text_primary | #F2F0E8 / #FFF2F0E8 | Main labels and titles |
| cz_text_secondary | #BEC7BF / #FFBEC7BF | Categories, values, supporting copy |
| cz_text_tertiary | #9EAAA0 / #FF9EAAA0 | Optional metadata; never sole essential instruction |
| cz_sage | #B7C9AC / #FFB7C9AC | Brand, small selected markers and progress |
| cz_sage_bright | #DDE9D5 / #FFDDE9D5 | Focus ring core |
| cz_sage_ink | #182117 / #FF182117 | Text only on deliberately light sage surfaces |
| cz_border | #465348 / #FF465348 | Quiet component boundaries |
| cz_divider | #334037 / #FF334037 | 1dp integrated list separators |
| cz_focus_halo | #26B7C9AC | Optional outer 15% sage stroke |
| cz_scrim_rgb | #000000 / #FF000000 | Animate View alpha separately |
| cz_warning | #E7C783 / #FFE7C783 | Relevant warning state |
| cz_error | #E6A49C / #FFE6A49C | Failure text/icon; not whole-screen fill |
| cz_disabled_text | #7C887F / #FF7C887F | Disabled action label with explicit state |

Rules:

- Prefer opaque surfaces in Safe tier. Full tier may use only the specified shell/content translucency.
- Text itself stays opaque. Do not set a parent row alpha below 1 to create disabled text if it also dims a selected check or needed metadata.
- Essential text must reach 4.5:1 contrast against the final composited surface; large display text at least 3:1. Focus boundaries must remain discernible independently of hue.
- Validate bright and dark moving video backgrounds, not just black screenshot fixtures.
- No pale sage full-row flood for ordinary focus. A light sage fill is reserved for a rare primary action if readability requires it, never the global focus treatment.
- Keep existing accent_gold/accent_blue resource names as temporary aliases to cz_sage until their usages can be migrated without unrelated changes.
- Use aliases for text_white/bg_black only where semantically correct. Do not make the activity canvas and all panels the same color.

### 2.3 Typography

Bundle static, licensed font files under res/font; no runtime font download. Primary Latin face: Inter, weights 400/500/600. Persian/Arabic: Vazirmatn 400/500/600. Hindi: Noto Sans Devanagari 400/500/600. Thai: Noto Sans Thai 400/500/600. Select via localized presentation typography; mixed source channel names retain native fallback glyphs. Preserve upstream licenses in font notices.

Use official font sources, pinned in the implementation notes. Static TTFs are required for API 24; do not rely on variable-font axes or API 26-only font loading. Use ResourcesCompat.getFont() / compatible AppCompat resource families and cache Typeface objects. If asset retrieval fails, retain system sans-serif fallback and report the missing asset; never ship a broken font reference. Do not change player subtitle font preferences or glyph shaping.

| Role | TV sp / line height sp / weight | Phone sp / line height sp / weight |
| --- | --- | --- |
| Welcome display | 38 / 46 / 600 | 30 / 38 / 600 |
| Screen heading | 28 / 36 / 600 | 26 / 34 / 600 |
| Pane heading | 22 / 30 / 600 | 22 / 30 / 600 |
| Primary control/row | 20 / 28 / 500 | 17 / 24 / 500 |
| Channel title | 20 / 28 / 500 | 17 / 24 / 500 |
| Body/explanation | 18 / 26 / 400 | 16 / 24 / 400 |
| Value/secondary | 17 / 24 / 400 | 14 / 21 / 400 |
| Small label/footer | 16 / 22 / 500 | 13 / 19 / 500 |
| Optional micro metadata | 14 / 20 / 400 | 12 / 18 / 400 |
| Numeric channel entry | 48 / 56 / 500 | 40 / 48 / 500 |

All sizes are sp; dimensions are dp. Use TextViewCompat.setLineHeight() where needed for compatibility. Essential TV instructions never use the micro role. Increase row height for fontScale > 1 rather than reducing text to fit.

Latin display tracking is -0.01em; control/body tracking 0; uppercase Latin section label tracking +0.06em. Do not uppercase or add tracking to Arabic, Persian, Hindi or Thai. Avoid synthetic bold. Use weight 600 only for headings/current pane emphasis; 500 for controls.

Set includeFontPadding according to script-safe measurement: Latin can be false; Persian/Arabic/Devanagari/Thai must retain enough ascent/descent or equivalent explicit padding. Verify combining marks at font scales 1.0, 1.3 and 2.0. Never cut diacritics to achieve identical pixel baselines.

Technical metadata, URLs, version strings and channel numbers remain LTR using textDirection plus BidiFormatter isolation where mixed into an RTL sentence. A source's arbitrary channel title is not translated, normalized or manually reversed for appearance.

### 2.4 Spacing, geometry, corners and elevation

Base rhythm: 4dp. Allowed ordinary spacing: 4, 8, 12, 16, 20, 24, 32, 40, 48dp.

| Component metric | TV | Phone |
| --- | --- | --- |
| Essential control minimum target | 56 × 56dp | 48 × 48dp |
| Channel row minimum height | 68dp | 68dp |
| Standard setting/choice row minimum | 64dp | 60dp |
| Two-line setting minimum | 84dp | 76dp |
| Source row with two metadata lines | 104dp | 96dp |
| Group row minimum | 68dp | 64dp |
| Button control radius | 10dp | 12dp |
| Integrated group radius | 14dp | 16dp |
| Settings shell radius | 18dp | 0dp full-screen; 18dp sheet |
| Modal radius | 18dp | 20dp |
| Logo tile radius | 6dp | 8dp |
| Full-tier recessed video radius | 18dp visible target | 16dp visible target |
| Ordinary icon visual size | 24dp | 22–24dp |
| Channel logo tile | 40dp | 36dp |
| Row horizontal content padding | 16dp | 16dp |
| Panel content padding | 20dp | 20dp |
| Integrated divider inset | Align with text start | Align with text start |

Heights are minimums, not fixed containers that clip large fonts. Long explanations use expandable content and scrolling. Titles can wrap where specified; compact values use ellipsis plus an accessible full label.

Elevation/Z: surface 0dp; focused control translationZ 6dp Full, 2dp Safe; shell maximum 12dp. Use a valid outline for a custom component if drawing a native shadow. Keep native stateListAnimator null. No software blur shadows. Depth must still read when shadows are not rendered.

## 3. Adaptive architecture: TV and touch layouts

### 3.1 Resolve mode independently from width

Preserve existing Prefs interface_mode strings Auto, TV and Phone, plus the current detection using UiModeManager/FEATURE_LEANBACK/touchscreen availability. A wide phone is not automatically TV. A touch-capable Android box can still be TV when its mode or system configuration says TV.

Resolve:

1. Effective input/layout mode: existing explicit override, then current detection.
2. Actual available window bounds in dp after WindowInsetsCompat display-cutout/system-bar handling.
3. Compact touch: width < 600dp.
4. Medium touch: 600–839dp.
5. Expanded touch: width >= 840dp and height >= 480dp.
6. Short touch landscape: height < 480dp; prioritize unobstructed video and scrollable panels.

Keep the activity's existing configuration-change contract. Recalculate and relayout without reloading playlists, replacing the player or losing nested settings state.

### 3.2 TV safe area and reference composition

Design against a 960 × 540dp TV canvas. Use 5% horizontal/vertical protection for essential controls:

- safeHorizontal = ceil(max(24dp, availableWidthDp × 0.05)).
- safeVertical = ceil(max(24dp, availableHeightDp × 0.05)).
- At 960 × 540dp this is 48dp horizontal, 27dp vertical.
- Apply once to essential overlay/control geometry; do not add the same margin in both host and child.
- Keep video/canvas full bleed. Preserve aspect ratio handling.
- Include focus expansion/halo inside the safe rectangle. Pixel resolution does not change dp typography.

TV Channels panel: content shell width 400dp at reference size, including its own padding; clamp to min(440dp, available safe width). Dock to logical start: left LTR, right RTL. Height is available safe height, 486dp at the reference size. Its background may extend to the outer display edge, but its controls stay safe.

TV Settings panel: width min(720dp, safeWidth), height safeHeight, docked to logical start inside the safe rectangle. At 960 × 540dp, bounds are x=48, y=27, width=720, height=486 in LTR; RTL x=192. Full-screen transparent host surrounds it.

Shell horizontal padding 20dp. Inner master rail 164dp, then 16dp gap, 1dp divider, 16dp gap, and remaining detail width. At reference width, detail is 483dp. Header height min 64dp, footer 32dp, inner vertical gaps 12dp. Make the body scroll within its pane.

If a forced-TV window cannot provide at least 600dp Settings width or 360dp height, collapse to one-column category → detail navigation using the same retained logical stack. Do not shrink typography below TV minimums.

TV welcome: safe rectangle; two columns with 40dp gap, intro 44%, action region 56% of remaining width. Header above both. Action targets min 88dp high with contained secondary lines.

TV Info: bottom dock within safe bounds, maximum width min(864dp, safeWidth), not a tall top-left phone card. Detailed geometry is in section 6.

### 3.3 Touch compositions

Compact phone:

- Welcome uses a compact top header, vertical message/actions, 20dp horizontal gutters and a scrolling body on short displays.
- Channels and Settings use full-height native screens/sheets; system bar and IME insets are respected. Settings category root is one column. Nested pages use an in-panel back header.
- Info controls sit at the bottom, with a responsive main row and a secondary row when space is insufficient.
- Do not display D-pad footer hints or a permanent focus ring in normal touch mode.

Medium touch:

- Channels uses a 380–440dp side sheet in landscape when at least 280dp of video can remain visible; otherwise full-height/full-width.
- Settings stays one column unless both width >= 840dp and height >= 480dp.
- Welcome may use two columns at width >= 700dp if height permits; never choose this from width alone.

Expanded touch:

- Settings may use the master/detail presentation, with 180dp master width and detail at least 360dp.
- Touch remains direct selection; no hover-dependent actions. A hardware keyboard/remote can activate the same focus graph.

Use a shared presentation model and shared actions, with composition-specific hosts. Do not maintain separate phone and TV copies of business state.

### 3.4 Insets and input modality

Use current WindowInsetsCompat handling. Do not use deprecated real-display metrics as the sole layout input. Apply IME insets only to editable content/its active host; keep currently edited fields and submit actions visible.

Track the last meaningful input type: D-pad/keyboard, touch, pointer or accessibility. Touch suppresses animated keyboard chrome without discarding selected/playing markers. A D-pad press restores an appropriate visible focus target before moving. Do not require a sacrificial extra OK press after touching the screen.

Configuration change during a spring: settle the desired visibility, recalculate geometry, restore logical focus/scroll, then allow subsequent interactions to animate. Never reuse stale pixel translation or a removed View reference.

## 4. Focus, selection and input contracts

### 4.1 Independent state dimensions

Every component must represent these independently:

- enabled/disabled;
- focused/unfocused;
- pressed/released;
- selected/unselected where it is a saved choice or active filter;
- playing/current channel, plus its existing connection state;
- favourite/not favourite, where applicable.

Focus is temporary input location. Playing is a property of the current stream. A focused unplayed channel must never look like it is already playing.

| State | Fill | Edge/marker | Transform |
| --- | --- | --- | --- |
| Default | Transparent or base content surface | No luminous edge | Scale 1, Z 0 |
| Selected, unfocused | cz_surface_selected | 3dp sage marker or persistent check, according to component | Scale 1 |
| Playing, unfocused | Normal row | Explicit status text/dot plus independent 3dp playing marker | Scale 1 |
| Focused, unselected | cz_surface_focus | 2dp cz_sage_bright ring | Component focus scale |
| Focused + selected/playing | Focus fill/ring | Keep check/status/playing marker visible too | Same focus scale |
| Pressed while focused | Slightly brighter focus fill | Ring stays visible | Scale retreats toward 1, then returns on release |
| Disabled | Base surface | Explicit disabled semantics | No scale/luminescence |

Never rely on color alone. Checkmarks, status text and position carry meaning. Do not prefix a Unicode check to arbitrary Button text and call it a complete radio component.

### 4.2 Focus mechanics and physics

Use one FocusMotionBinder per component, bound once. Compose the current semantic onFocus callback with motion: ChannelListAdapter.onFocused and OsdController's lastFocused/action-label behavior must still run. Do not replace an existing setOnFocusChangeListener and lose its behavior.

| Motion | Full target | Safe target | Spring stiffness / damping |
| --- | --- | --- | --- |
| Channel row focus | scaleX/Y 1.025, Z 6dp | 1.015, Z 2dp | 950 / 0.88 |
| Settings/detail row focus | 1.012, Z 6dp | 1.008, Z 2dp | 1100 / 0.90 |
| Category rail focus | 1.015, Z 4dp | 1.008, Z 2dp | 1100 / 0.92 |
| Welcome action focus | 1.035, Z 6dp | 1.020, Z 2dp | 850 / 0.86 |
| Compact icon button focus | 1.080, Z 6dp | 1.040, Z 2dp | 1100 / 0.88 |
| Press feedback | scale target 1.000 while held | 1.000 | 1500 / 1.00 |
| Focus release | Return to 1 and Z 0 | Same | 1150 / 0.95 |

These are AndroidX SpringForce values, not Compose spring parameters or durations. Target normal focus settling is approximately 150–240ms; measure it with actual displacement/thresholds. A spring has no fixed duration.

Geometry:

- Animate render transforms, not width/height or RecyclerView item layout parameters.
- Pivot at component center. Integrated row focus remains within panel insets; reserve at least 8dp internal expansion clearance.
- Set clipChildren/clipToPadding false only on the necessary local list/component wrappers. Do not disable clipping on every app root.
- Draw the ring inside the component outline. Halo is two cached outer strokes: 4dp at 10% sage and 6dp at 5% sage, Full tier only. No BlurMaskFilter, software layer or animated glow pulse.
- Focus ring alpha enters over 100ms and leaves over 90ms; interpolate from its current alpha with PathInterpolator(0.2f, 0f, 0f, 1f).
- One bounded focus/press animation owns scale. Focus loss during a press always resolves to unfocused scale 1. Disabled while focused moves focus before rendering disabled.
- Keep native defaultFocusHighlightEnabled false on relevant API 26+ roots and custom controls. Keep platform Button stateListAnimator null. The custom binder owns Z and restores it.
- On detach/recycle: cancel, clear current animation token, scale=1, translationX/Y=0, translationZ=0, alpha=1; rebind persistent state from data. No lingering halo from recycled rows.
- Motion is disabled when system animation scale is zero or Visual effects is Off. Selected markers still render.

Reference for a retained scalar spring; connect applyValue to component rendering:

~~~kotlin
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class RetargetableUiSpring(
    initial: Float,
    springStiffness: Float,
    springDampingRatio: Float,
    private val applyValue: (Float) -> Unit
) {
    private val holder = FloatValueHolder(initial)
    private val animation = SpringAnimation(holder).apply {
        spring = SpringForce(initial).apply {
            stiffness = springStiffness
            dampingRatio = springDampingRatio
        }
        setMinimumVisibleChange(0.001f)
        addUpdateListener { _, value, _ -> applyValue(value) }
    }

    fun target(value: Float, animationsEnabled: Boolean) {
        // Main thread only. Retarget a running spring; preserve its velocity.
        if (animationsEnabled) {
            animation.animateToFinalPosition(value)
        } else {
            settle(value)
        }
    }

    fun settle(value: Float) {
        animation.cancel()
        holder.value = value
        animation.setStartValue(value)
        applyValue(value)
    }

    fun cancel() = animation.cancel()
}
~~~

Use a smaller minimumVisibleChange of 0.0005f for scale-valued springs. Every spring end callback must verify desired state/generation before finalizing UI; a canceled old close cannot dismiss a new open.

### 4.3 Existing remote mappings remain

| Context/key | Required behavior |
| --- | --- |
| Playback OK or Up | Open Channels; opening press/release cannot activate a row |
| Playback Right / CH+ / Page Up | Existing next-channel action |
| Playback Left / CH− / Page Down | Existing previous-channel action |
| Playback Down / Info | Open Info controls; subsequent Info expands stream details |
| Playback numeric / Delete / OK | Existing number entry, correction and commit |
| Playback media keys / Last Channel | Existing play/pause and previously watched channel actions |
| Menu | Toggle Settings once per initial key-down; consume matching release/repeats |
| Channels Up/Down | Move within visible filtered channels; first ↔ last wraps |
| Channels OK | Commit target on matching uncanceled release; close list using existing action |
| Channels hold OK | Toggle favourite once; do not also tune on release |
| Back | Most local owner handles once; never leaks into underlying double-Back exit |

Remote focus must never tune a channel by itself. Preserve the existing long-press timeout/confirm-ID guard and pending target behavior.

### 4.4 Focus ownership

Only one owner receives navigation at a time:

1. System picker/IME when active.
2. Groups modal or confirmation within its current host.
3. Settings host, including entering/exiting states.
4. Channels.
5. Active Info controls.
6. Uncovered playback error.
7. Naked playback.

This is priority arbitration, not permission to stack unrelated screens. Opening Channels closes Settings; opening Settings closes Channels. Transition arbitration is specified in section 9.

Do not globally call FocusFinder across the activity. Search within the active pane/modal only. While a panel covers an error, Retry/Next stay nonfocusable and cannot steal focus when a network callback arrives.

Focus snapshots store logical page key, category key, row/action key, scroll offset, channel stable ID and input modality. Store weak View references only as an optimization; rebuild the target from its stable key after re-layout or reparenting. A localized label is not a stable key.

In Settings's separate window, use hostRoot.findFocus() first. Activity.currentFocus alone does not represent the Dialog window's focused child. Pass an explicit focus lookup into SettingsPanel and helpers.

### 4.5 Channel/filter traversal

Preserve the existing navigation semantics: the horizontal key toward logical start from a channel row returns to the active filter; Up from filters reaches search/header; Down enters the remembered channel. Left/Right traverse filter choices according to their physical placement. In RTL, mirror this.

Keep the pressed/released key guard across any row/list recreation. Rapid repeated Up/Down must continue to update ChannelListNavigation.pendingId without parking focus on the activity root.

Settings and language lists do not wrap. Channel lists retain first/last wrapping. The search field owns Left/Right editing while the caret is active; do not intercept those keys for pane switching.

## 5. Native component contracts

### 5.1 ChannelRow

One focusable/clickable root, no separately focusable star in a TV row. Slots in reading order:

1. 40dp logo tile, fitCenter with 4dp inset; neutral fallback vector.
2. 8dp gap and source-number slot capped at 36dp, numeric role 16sp TV.
3. 12dp gap and flexible text column.
4. Optional status/icon slot; reserve no width when absent.

Primary name uses channel title role, one line with end ellipsis. Secondary category uses value role. If a channel name includes a resolution or suffix from the playlist, preserve it. Do not regex-strip a title to manufacture a chip.

Only show a separate resolution badge when actual current stream metadata is available from existing callbacks, and only for the current channel. Otherwise omit it. Do not claim resolution for unplayed channels. Existing Channel has no dedicated resolution field.

Playing/current status can be Connecting, Buffering, Playing, Paused, Unavailable or Ended, from ChannelPlaybackStatus. Render status alongside category only if space permits; on narrow phone rows prefer a compact status glyph plus an accessible full state. Names and current-state text have priority over optional category.

Favourite is a nonfocusable outlined/filled star at the trailing edge on phones and where room allows on TV. TV hold OK is the action; phone long press or existing Info favourite action remains. Do not create an extra remote stop per channel row.

### 5.2 SettingRow

Evolve existing SettingRow into a compound native View with:

- optional 24dp icon;
- label text and optional supporting/metadata text;
- optional trailing value;
- optional direction-aware chevron;
- one focusable root, component focus chrome and click callback;
- explicit stateDescription/contentDescription where supported.

Types: navigation row, value-choice row, immediate boolean-action row, informative row, destructive/exit action. Informative rows are not focusable unless users need to select/copy their content.

Never put another focusable child inside the row. If the label needs multiple lines, grow the row. Trailing values are capped at 35% row width; never squeeze the label into an unusable fragment.

For boolean action rows such as sound, keep accessible checkable semantics even though the visible control is text On/Off. Use a Switch or ToggleButton accessibility class via delegate, isCheckable/isChecked, and announce the new state. The icon/chevron is decorative.

### 5.3 ChoiceRow

A single-selection group uses radio semantics. Native name/value is primary; explanation/English language name is secondary. Selected row gets a check inside the trailing 24dp radio-circle footprint; unselected rows show an empty circle. Focus ring is independent of that check.

Set collection selection metadata and one selected item. Do not mix arbitrary star/check prefixes into text or rely on a highlighted row as the only saved selection cue.

### 5.4 ActionRow and integrated groups

ActionRow supports icon, title, supporting copy and trailing arrow. It is used for welcome actions, import choices, Retry and other semantic actions. It does not store domain state.

Integrated groups share one content plane and inset separators. Avoid outlining every unfocused row as a separate card. A focused row may draw its own independent ring above the group.

### 5.5 Modal host

Groups and confirmations have a scoped focus trap, title, optional description, body and clear dismissal. Default focus is the existing selection or safest relevant action. Cancel/Back returns to the triggering control with scroll intact.

Inside Settings, confirmations use the existing host/detail surface; do not open a native Dialog for every subpage. Groups is outside Settings and may use one owned native Dialog styled from the same tokens. It is never open concurrently with Settings.

## 6. Phase 1 — TV playback/Info and Channels

### 6.1 Unobstructed playback

Video remains full bleed with the user's saved aspect-ratio mode. No permanent decorative application chrome. SubtitleView stays above video and below menus; menus must not change selected subtitle/audio tracks.

Preserve numeric-entry ghost, next-channel ghost and existing toast/notice operations. Restyle them as small safe-area surfaces. Numeric digits use tabular figures, LTR and the numeric typography role. An informative ghost must not acquire focus.

### 6.2 TV Info dock

Refactor view_osd.xml / OsdController presentation into a bottom dock inside the safe rectangle:

- Width at 960dp: 864dp, centered; compact state natural height approximately 176–200dp depending on programme data.
- Full-tier surface uses cz_surface_shell_translucent; Safe is opaque. Padding 20dp, radius 16dp.
- Top line: 40dp logo, 12dp gap, channel name flexing, trailing clock. Source number and stream status/actual resolution on the next line.
- Programme now/next text appears only when provided by existing EPG matching. Maximum two visible text lines in compact state; expanded view may show more. Programme progress has a 3dp sage track and current calculated progress.
- No fake episode artwork, schedule, codec, bitrate or quality label.
- Action row visual order in LTR: Channels, Previous, Play/Pause, Next, Favourite, Picture size, Settings. Expanded Info adds Previous watched channel. Mirror reading order in RTL.
- Action targets 56dp, 8dp gaps; icon visual size 24dp; focused action label appears in a stable 24dp-high footer, without moving adjacent controls.
- Play/Pause accessible label follows the actual action. Favourite uses existing outline/filled vectors and existing persistence.
- Expanded technical region is below the metadata/action grouping, with two columns for actual available technical information. Hide unavailable values; technical blocks remain LTR.
- Info expansion may increase height to at most safeHeight minus 48dp. Scroll explanations/technical text if necessary rather than pushing controls offscreen.

D-pad: Left/Right moves along visible action buttons; Down from the metadata region enters actions. Up from actions may reach an expanded details control only if it is interactive; do not focus every text field. Preserve OsdController's lastFocused restoration. Back closes Info.

Keep existing 5-second inactivity behavior; reset on interaction. Do not hide during a held key/press, active text selection or accessibility reading. Passive channel-change Info never captures focus. If TalkBack is enabled, allow the active controls to remain until dismissed.

Entry: alpha 0→1, translateY 12→0dp over 180ms using (0.2, 0, 0, 1). Exit: alpha 1→0, translateY 0→8dp over 130ms using (0.4, 0, 1, 1), then GONE. Retarget from current values. Safe may use only alpha/8dp translation. Reduced motion settles immediately.

### 6.3 Channels panel structure

Top-to-bottom inside the safe shell:

1. Fixed header: title Channels, runtime visible count as a quiet second field, close target.
2. Search: min height 56dp, leading search icon, current text and clear action only when populated.
3. Filter row: All, Favourites, Groups; 56dp min targets, text tabs with a selected underline/marker, independent keyboard focus ring.
4. Flexible RecyclerView, min zero height so it can share remaining space with fixed controls.
5. Fixed TV helper line: OK Watch · Hold OK Favourite · Back Close; localized and at least 16sp.

Keep the header compact: 28sp title, not the oversized portrait concept. At the reference height show as many complete rows as fit; do not squeeze seven rows into a fixed 486dp shell after header/search/filters. Scrolling is expected and must be obvious from a subtle scroll indicator/partial continuation space.

Search is asynchronous-display-safe: retain EditText instance, cursor, selection, composing span and focus while results change. Do not close/reopen the panel or dismiss the IME on each character. Preserve the existing matching/normalization semantics exactly.

Empty states:

- No playlist: offer the existing import/public flow.
- Search with no matches: No channels found plus Clear search.
- Empty favourites: No favourites yet; explain hold OK or use the existing favourite action.
- Empty selected group/search combination: explain the filter and offer All channels.
- Artwork missing/failing: neutral station vector; keep geometry unchanged.

Use existing filtering and source IDs. Counts represent the displayed filtered list, with one count source per view. Do not duplicate a decorative count three times.

Channel panel entry/exit: 180ms alpha and 24dp logical-start slide in; 140ms out. Use the same cancellable owner model as Info. Do not scale the live video for Channels.

### 6.4 Groups picker

Replace the AlertDialog.Builder(...).setItems implementation in setupChannelListOverlay() with GroupsPicker.

TV: width min(440dp, safeWidth), natural height with a maximum safeHeight; centered within safe bounds. Header 22sp Groups; secondary Filter channels by category. Rows 68dp minimum, quiet count at trailing edge, optional restrained group icon. When there are many groups, use RecyclerView; title/dismiss stay fixed.

Data contract:

- Build categories from existing channel group identities. Labels still use GroupLabels.display().
- Count the channels in each source group, constrained by the current search query; favourites state is cleared on group selection as it is today.
- Unrecognized categories use a generic group icon. Do not hardcode the four example groups or their counts.
- Group identifiers are original values; translated names are display labels only.
- If no group metadata exists, render the existing empty/ungrouped concept in a meaningful way without changing channel identities.
- Active group is checked; focused group is ringed independently. With no selectedGroup, no false saved check is shown.

Choosing a group commits once, dismisses the picker, updates the existing selectedGroup/favouritesOnly fields and restores a valid channel target through ChannelListNavigation. Cancel returns to Groups filter; it does not change filters. After selection, show the selected category as quiet filter context with a clear All route.

Style Cancel as a plain action row, not a bright separate Material button. The background Channels page dims with a 0.48 black scrim. No heavy blur is needed for this small decision.

### 6.5 Playback failure and buffering

Restyle layout_playback_error as a compact centered surface with clear state text, Retry and Next channel. Preserve its current actions and recovery. It never overlays a currently active Settings/Channels modal as the input owner.

Buffering is a stable inline status using the current callback, not a full-screen replacement. Do not animate a spinner if system animations are disabled. If a failure arrives while a panel is open, update underlying presentation and announce appropriately without moving focus.

Implementation acceptance for Phase 1: first/last wrap, held navigation, long-press favourite, matching confirm release, IME composition, delayed artwork, error arrival and expanded Info all retain correct focus and existing playback behavior.

## 7. Phase 2 — Settings architecture and every subpage

### 7.1 Shell and category composition

Settings remains the current logical overlay destination, opened by Menu or the gear action. Do not add an unrelated top navigation bar or invent a new Settings activity to satisfy the word “tab.”

TV shell:

- Fixed header with small original logo/brand, Settings title and 56dp close target.
- Master rail contains Playback, Playlist and App in that order. Exit app is a separate action anchored after a divider near the rail bottom.
- Category selection indicator stays visible when focus moves into detail. A category can be selected without being focused.
- Detail root shows only the selected category's settings. Use a fixed pane title and a scrollable body.
- Category rail and detail have separate scroll/focus memory. A child page uses the detail pane; master rail remains visible but does not receive accidental traversal through an input field.
- Footer uses restrained local hints: Left/Right Sections, OK Select, Back Return. Adapt the hint to current nesting/input state and locale.

Default category on first opening is Playback. On subsequent openings restore the last root category and last valid root row for the activity session, but reopen at that category's root, not a stale unfinished child page. Preserve any actual ongoing UpdateMenu operation in its existing owner.

### 7.2 D-pad graph and focus memory

In LTR:

- On opening Settings, focus the remembered category rail item, initially Playback. This makes the first frame's owner predictable.
- Up/Down in the rail moves between category items without wrapping. Focusing a category activates its root detail content; coalesce rapid changes to the next frame, without adding a long hover delay.
- Right or OK on a category enters its remembered visible enabled detail row, else the first row.
- Up/Down in a detail root traverses its rows, revealing them by scrolling.
- Left from a nonediting detail root returns to that category's rail item, remembering the exact row/scroll.
- At a nested page, Left returns to rail only from the nested page's explicit header/back boundary. A focused radio row or form does not silently discard a child page. Back/visible back pops one page.
- Selecting another category from the rail while a child page exists explicitly returns to category root and runs that child's existing leave callback once. Retain each category's root position.
- Back from nested page pops one level and restores triggering row and scroll.
- Back from root detail returns to rail; Back from rail closes the whole overlay.
- Close X closes the entire overlay from any depth, subject to the existing import-commit guard.

The separate Exit app rail action participates in Up/Down traversal but does not activate a detail category. OK invokes the existing exit action; Right has no hidden side effect.

RTL mirrors physical Left/Right, pane order, leading markers, arrows and shell dock. Up/Down semantics remain unchanged.

EditText Left/Right belongs to caret movement. Numeric/URL typing belongs to the field. Up/Down may leave a field only according to explicit nextFocus/submit paths. No global FocusFinder crossing from text input to the underlying video.

Touch single-column Settings:

- Root is the existing Playback / Playlist / App grouped settings list with all actions accessible by scrolling.
- Tap a value/navigation row to open its nested page; Back returns one level.
- Close dismisses Settings. A back gesture on root dismisses Settings.
- No keyboard ring appears merely because programmatic focus is retained.

### 7.3 Category/action inventory and existing bindings

| Category | Existing action ID / binding | Visible label | Existing behavior to invoke |
| --- | --- | --- | --- |
| Playback | btn_settings_aspect / btnSettingsAspect | Picture size, current value | Existing aspect choice and player setter |
| Playback | btn_settings_tracks / btnSettingsTracks | Audio & subtitles | Existing audio/subtitle choice hierarchy |
| Playback | btn_settings_sleep / btnSettingsSleep | Sleep timer, current value | Existing 0/15/30/60/90-minute effects |
| Playback | btn_settings_crt_row / btnSettingsCrtRow | Channel switch sound, On/Off | Existing CrtSwitchSound.setEnabled() |
| Playlist | btn_settings_reimport / btnSettingsReimport | Playlist sources | PlaylistMenus.show() |
| Playlist | btn_settings_guide / btnSettingsGuide | Programme guide | guideMenu() |
| App | btn_settings_mode / btnSettingsMode | Interface mode, current value | Auto / TV / Phone preference |
| App | btn_settings_language / btnSettingsLanguage | Language, current native name | languageMenu() |
| App | NEW btn_settings_effects | Visual effects, requested mode | New presentation-only effects choice |
| App | btn_settings_update / btnSettingsUpdate | Check for updates | Existing UpdateMenu.show() |
| App | btn_settings_logs / btnSettingsLogs | Share diagnostic log | Existing diagnostic preview/actions |
| App | btn_settings_about / btnSettingsAbout | About CouchZap | Existing About page and GitHub action |
| Rail/footer action | btn_settings_exit / btnSettingsExit | Exit app | Existing hardExit(), once |

Playlist source label and updated timestamp belong inside the Playlist sources row's supporting area. Retain tv_settings_source as a TextView where practical; do not strand metadata between unrelated rows. Format timestamps with current locale/system time conventions.

Existing action IDs remain unique on the actionable root where feasible. Converting a Button to a compound SettingRow changes ViewBinding types: update presentation-only label assignments in AppLanguage and related tests. Add explicit label TextView IDs. Do not leave hidden focusable proxy Buttons or duplicate IDs to satisfy old tests.

### 7.4 Picture size

Use ChoiceRows for Fit, Fill, 16:9 and 4:3, retaining the existing aspectRatios array and saved values. Selection commits through the existing callback once and returns to its parent row as it does today. No preview stream, decoder restart or arbitrary aspect conversion.

The current value is displayed in the root SettingRow; its checked choice is separate from keyboard focus.

### 7.5 Audio and subtitles

Keep the existing two-step root choices Audio and Subtitles. Populate track labels from playerController.choices(type). Audio supports Automatic; subtitles support Off, with the current existing selectTrack behavior.

When no choices exist, show the appropriate existing explanation rather than fake tracks. Use valid focused Back as the return target for a noninteractive information page. Track labels may wrap to two lines. Do not change stream/track selection logic merely to add a visible check.

If the current selected track cannot be determined through existing presentation data, do not invent a saved selection; show available choices and use neutral state until existing callbacks establish it.

### 7.6 Sleep timer

Choices are Off, 15, 30, 60 and 90 minutes, using localized strings/plurals. Keep existing sleepHandler/sleepRunnable, pause behavior and notice. Root value is current timer selection; it updates to Off after expiration without stealing focus.

This spec does not turn the timer into a background scheduling feature or change its persistence.

### 7.7 Channel switch sound

Replace the visible Switch in btn_settings_crt with a trailing tv_settings_crt_value TextView and chevron on btn_settings_crt_row.

Interaction: pressing OK/tapping the root immediately flips the existing saved boolean once, invokes CrtSwitchSound.setEnabled(newValue) once, updates On/Off and announces it. No nested modal is needed for a two-state action. The chevron indicates the actionable value treatment consistent with adjacent rows.

Retire the old btn_settings_crt Switch ID and update every presentation reference/test that used isChecked/toggle(). Do not retain a hidden Switch as the state store. Prefs.isCrtSoundEnabled() remains authoritative.

Preserve the current sound behavior: Off by default; preview when enabling; actual sound only for committed channel changes, never browsing/focus, retries or reconnects; respect media mute/volume.

### 7.8 Interface mode

ChoiceRows: Automatic, TV, Phone (retain existing localized label for Phone/touch mode if already different). Keep underlying saved values Auto, TV, Phone.

After selection, recalculate layout and restore the equivalent logical page/action under the new composition. Entering TV mode may request the existing landscape orientation. Do not relaunch the activity, clear the library or rebuild the player to change layout.

### 7.9 Language

Display exactly six choices, from existing AppLanguage.supported:

| Code | Native primary label | English secondary label |
| --- | --- | --- |
| en | English | English; omit redundant line or show Current language when selected |
| es | Español | Spanish |
| hi | हिन्दी | Hindi |
| th | ไทย | Thai |
| fa | فارسی | Persian |
| ar | العربية | Arabic |

Use ChoiceRow radio semantics, one persistent selected check, and independent focus chrome. All label groups share a coherent reading column. Native RTL words have correct intrinsic shaping and direction; do not manually reverse them. English secondary labels remain LTR and isolate mixed content.

Selection saves/applies immediately using existing languageMenu behavior and returns to its parent context. Changing to/from RTL also mirrors panels, focus directions, icons and category structure. Restore focus by action key after re-layout, not by stale pixel location.

The Settings host is a separate window: apply locale/layoutDirection and translate both the activity root and Settings host root. Existing SettingsPanel/UpdateMenu dynamic labels must use the newly wrapped context. Keep URLs/version/log blocks LTR. Re-render the current page without duplicating a job.

When opened from welcome, selection returns to the welcome Language control and does not leave an empty Settings root visible. First launch has no mandatory separate language gate; English remains default.

### 7.10 Visual effects — new presentation setting

Choices:

- Automatic — adapt to device capabilities.
- Full depth — use the full effect where supported.
- Safe — keep live video unchanged.
- Off — disable decorative motion/depth.

Store only effects_mode = auto/full/safe/off in Prefs. Existing preferences are untouched. Show requested mode as the row value; show effective tier and a short honest explanation in the choice page when it differs. Do not expose GPU jargon or developer performance charts in normal Settings.

Examples: Safe effects are active on this device; Full depth is unavailable while system blur is disabled; System animations are turned off. Localize them.

System reduced-animation settings always win. Full cannot bypass missing public API support, low-RAM hard guards, known surface failures or a runtime failure downgrade. Safe/Off are always available.

### 7.11 Playlist sources

Restyle PlaylistMenus.show() with source identity and timestamp as a compact informative header, then current actions:

- Import playlist file.
- Import playlist link.
- Start/explore public channels.
- Refresh current playlist.

Retain existing confirmation before replacing an existing list. Changing the label's wording must not change when confirmation occurs. Source identity, update timestamp and unsupported-count explanation are dynamic.

Do not automatically refresh on entering this page. Failed import keeps the existing playlist. Details of all child flows are in section 10.

### 7.12 Programme guide

Map guideMenu() and choosePlaylistGuide() to the same detail pane:

- Existing status/not-added/loaded count.
- Guide source from playlist when available.
- Import from link.
- Import from file.
- Refresh guide.
- Clear guide using current callback/guard.

Keep XMLTV/gzip support, matching IDs, size limits, current relevant-programme filtering and all existing validation in EpgRepository. Missing channel guide IDs yields the existing explanation. Multiple playlist guide URLs use a ChoiceRow list; their technical hosts stay LTR.

Loading/result/failure updates replace status in place. Do not rebuild a focused action on every progress update. Guide operations keep their existing job ownership and cancellation behavior.

### 7.13 Updates

Render the existing UpdateMenu states without rewriting its backend:

| Existing state | Presentation |
| --- | --- |
| IDLE | User Check action |
| CHECKING | Status and Cancel; stable focus |
| CURRENT | Installed version and Check again |
| AVAILABLE | Runtime version/size/notes plus Download |
| DOWNLOADING | Progress plus Cancel; do not recreate focused row every percentage |
| READY | Verified update ready, Install action |
| PERMISSION | Existing explanation/Open settings route |
| FAILED | Explanation and Retry |
| CANCELLED | Cancelled state and retry/check action |

Opening the user-invoked Check for updates action may start the existing one check, exactly as baseline UpdateMenu.show() does. Merely opening the App category or reopening a retained page must not create an extra check/download.

Keep activity-owned job/state and page-owned renderer; leaving/reopening must retain state with one request. Retain checksum, application ID, signing certificate and version checks, unknown-sources handling and Android installer confirmation. UpdateMenu's current Activity.currentFocus/findViewById checks must become host-aware presentation lookup.

### 7.14 Diagnostics

Keep the existing report preview and Copy, Save, Share actions. Show action group before a scrollable, selectable, LTR monospace report region. TV report text minimum 16sp, phone 13sp; it may scroll independently when expanded.

Keep DiagnosticLog privacy/redaction, app-only reports, existing file/clipboard fallbacks, FileProvider and explicit share intent. Nothing is sent automatically. No new logs containing channel names, URLs, source tokens or user content.

### 7.15 About and exit

About uses the original logo, concise existing about text, runtime package version and existing GitHub action. No invented contributor, license, city, capability or version claim. Do not add a commercial-looking badge wall.

Exit app remains a separate deliberate action. Use normal text/icon with a clearer focused edge, not an always-red card. Existing explicit action invokes hardExit() once; do not add an unsolicited confirmation. Preserve the existing double-Back exit behavior only for uncovered playback, with its current timing.

### 7.16 Retained page stack

Preserve SettingsPanel's logical page/leave contracts. Add stable category/root row keys and host-aware focus references.

- Keep one live View instance per cached root group/page, with exactly one parent.
- Do not instantiate a new window/activity for choices, source menus, language or diagnostics.
- Changing category at root saves scroll and last row; returning restores them.
- On close, logically detach page render observers according to their existing leave behavior, once. Preserve the rendered closing frame until transition completion, then clear child presentation.
- Do not recursively call close from reset/back/onDismiss. Introduce explicit beginClose()/finishClose() or equivalent silent-reset coordination.
- Existing page-owned catalog jobs cancel on leaving as they do now. Existing activity-owned update work stays owned by UpdateMenu; do not cancel it with a generic close-all-jobs operation.
- A page render callback may update a hidden page model, but cannot request focus outside the current host/pane.

## 8. Full and Safe depth: layer and capability architecture

### 8.1 Why the Settings host is an owned native window

RenderEffect on an ordinary parent View is not a reliable live-video blur for this SurfaceView player. Cross-window blur requires an actual separate window. Implement one activity-owned native Dialog host for Settings on all tiers; this is a presentation host for the existing logical route, not a new destination.

Construct the host once per activity lifetime. Move only layout_settings into its full-size FrameLayout container once during initialization. Never move/recreate SurfaceView or detach the player. Keep ActivityMainBinding references to the real retained settings views, or provide a clear Settings binding facade.

The Dialog window is transparent, match-parent, hardware accelerated and focusable. The content consists of:

1. Full-window black scrim View, with animated alpha.
2. Docked/full-screen Settings shell containing the existing header/actions/page host plus TV rail.

Set windowAnimations=0; the custom controller animates content. Disable native dim to avoid a second opaque scrim. Set canceledOnTouchOutside false; route scrim clicks explicitly through the close guard. Use a compatible nonfloating, translucent AppCompat Dialog theme; copy fullscreen/inset/native-highlight rules rather than assuming the Activity theme is inherited.

No SYSTEM_ALERT_WINDOW permission, TYPE_APPLICATION_OVERLAY, service, screenshot or cross-application overlay.

### 8.2 Activity scene layers

Refactor activity_main.xml to introduce stable wrappers at initial inflation:

| Layer | Contents | Full tier during Settings | Safe tier during Settings |
| --- | --- | --- | --- |
| cz_canvas_root | Background cz_canvas | Unchanged | Unchanged |
| cz_video_scene | Existing video_frame/SurfaceView, subtitle_view, shutter, corner-mask overlay | Scale 1→0.94, centered | Scale remains exactly 1; no transform/alpha/clip/layout/surface changes |
| cz_hud_scene | Ordinary Info/ghost/toast/welcome/quick-control visual layers | Scale 1→0.94; input blocked | Scale 1→0.97; input blocked |
| Channels layer | Channel panel and own scrim | Closed when Settings opens | Closed when Settings opens |
| Settings Dialog window | Scrim and shell | Full effects | Safe effects |

SubtitleView scales with video in Full, and remains with untouched video in Safe. Do not accidentally recede subtitles separately on Safe boxes. Low-tier promise includes video_frame, surface_view and subtitle_view.

All wrappers exist before playback starts; do not reparent any player view at runtime. Keep AspectRatioFrameLayout's fit/fill computations and dimensions intact. Scaling is a render property of cz_video_scene. Do not animate layout width/height or call SurfaceHolder.setFixedSize().

### 8.3 Visible rounded video without hidden APIs

Do not assume clipToOutline or View.setRenderEffect on an ancestor controls the separately composited SurfaceView.

Use VideoCornerMaskView as an ordinary overlay above the scene's video/subtitles:

- Draw only the four small corner regions between a rectangle and a rounded rectangle, filled opaque cz_canvas.
- Use cached Path/Paint objects; update their path when size/radius changes, not with a new allocation per frame.
- Draw using an even-odd path or four cached corner paths; no offscreen saveLayer.
- The layer is noninteractive and importantForAccessibility=no.
- It scales with the video wrapper; compensate local radius so visible target radius is 18dp TV / 16dp phone at scale 0.94.
- Radius interpolates from 0 to target during entry and reverses on exit.
- Full-tier root canvas behind the transformed rectangle is the same cz_canvas, so the corner masks visually join it.
- In Safe/Off, mask radius is zero and the mask is GONE; do not alter video clipping.

This is intentional visual masking, not true hardware clipping. Validate it with actual moving video. If a device's surface Z-order renders above app overlays, downgrade to Safe; do not alter the decoder/surface Z-order to force the effect.

### 8.4 Full-tier live backdrop blur

Use public API 31+ window blur-behind on the Settings Dialog:

- WindowManager.isCrossWindowBlurEnabled must be true.
- Add FLAG_BLUR_BEHIND.
- Set WindowManager.LayoutParams.setBlurBehindRadius() on the Dialog window.
- Target radius is min(24px, round(8dp × density)); the API uses physical pixels.
- Keep the window background transparent; ordinary shell surfaces use the specified controlled opacity.
- Use the black scrim View for dimming. Do not also enable a default window dim.
- Register addCrossWindowBlurEnabledListener and remove it on host destruction. Blur can be disabled at runtime by the device, power saving or multimedia modes.
- Avoid window attribute updates when the quantized radius has not changed. At most one radius update per display frame; radius is integer.
- Normal entry/exit is spring-driven, with radius mapped from clamped transition progress. If profiling finds attribute updates costly, use four bounded radius steps 0/8/16/24px inside the same transition and document that optimization.

Do not use setBackgroundBlurRadius as a substitute for whole-scene blur-behind without verifying its different bounds semantics. Do not apply RenderEffect to the shell's text or focus ring.

Reference API wrapper, instantiated only after an API 31 guard:

~~~kotlin
@androidx.annotation.RequiresApi(31)
class WindowBlurApi31(
    private val window: android.view.Window,
    private val manager: android.view.WindowManager,
    private val onAvailabilityChanged: (Boolean) -> Unit
) {
    private val listener = java.util.function.Consumer<Boolean> {
        onAvailabilityChanged(it)
    }
    private var attached = false
    private var appliedRadius = -1

    fun attach() {
        if (attached) return
        attached = true
        manager.addCrossWindowBlurEnabledListener(listener)
    }

    fun radiusPx(value: Int) {
        val radius = value.coerceAtLeast(0)
        if (radius == appliedRadius) return
        appliedRadius = radius
        if (radius > 0) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
        val attributes = window.attributes
        attributes.setBlurBehindRadius(radius)
        window.attributes = attributes
        if (radius == 0) {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
    }

    fun detach() {
        radiusPx(0)
        if (attached) manager.removeCrossWindowBlurEnabledListener(listener)
        attached = false
    }
}
~~~

In production, coordinate the listener's immediate callback with host initialization; guard window attachment/lifecycle and capability exceptions. The wrapper itself does not decide tier or mutate playback.

### 8.5 Capability policy and override

There is no reliable single “fast GPU” Android boolean. Use explicit conservative gates, a user fallback and observed rendering health. Treat the following as design policy, not a platform guarantee:

| Effective tier | Resolution |
| --- | --- |
| Off | Requested Off, system animation duration scale is zero, or animator enablement is false |
| Safe | API 24–30, low-RAM device, insufficient memory, software-rendered UI, power saver, missing/disabled window blur, known surface failure, session downgrade, or explicit Safe |
| Full candidate | API >= 31, hardware acceleration, not low-RAM, total RAM >= 3 GiB, >= 4 available processors, power saver off, live window blur enabled, no recorded surface/performance failure |

The CPU/RAM thresholds are coarse screening only. They do not certify decoder/compositor performance. User-requested Full may bypass only the soft CPU-count screening; it does not bypass public API support, low-RAM/memory guard, power saver, blur availability or a recorded surface failure.

Reference policy seam:

~~~kotlin
enum class EffectsMode { AUTO, FULL, SAFE, OFF }
enum class EffectsTier { FULL, SAFE, OFF }

data class EffectsCapabilities(
    val api: Int,
    val hardwareAccelerated: Boolean,
    val lowRam: Boolean,
    val totalRamBytes: Long,
    val processorCount: Int,
    val powerSave: Boolean,
    val windowBlurEnabled: Boolean,
    val surfaceFailure: Boolean,
    val sessionDowngraded: Boolean,
    val systemAnimationsEnabled: Boolean
)

fun resolveEffects(mode: EffectsMode, c: EffectsCapabilities): EffectsTier {
    if (mode == EffectsMode.OFF || !c.systemAnimationsEnabled) return EffectsTier.OFF
    if (mode == EffectsMode.SAFE) return EffectsTier.SAFE
    val threeGiB = 3L * 1024L * 1024L * 1024L
    val hardFailure = c.api < 31 || !c.hardwareAccelerated || c.lowRam ||
        c.totalRamBytes < threeGiB || c.powerSave || !c.windowBlurEnabled ||
        c.surfaceFailure || c.sessionDowngraded
    if (hardFailure) return EffectsTier.SAFE
    if (mode == EffectsMode.AUTO && c.processorCount < 4) return EffectsTier.SAFE
    return EffectsTier.FULL
}
~~~

Read ActivityManager.isLowRamDevice, ActivityManager.MemoryInfo.totalMem, Runtime.availableProcessors, PowerManager.isPowerSaveMode and the guarded WindowManager API. Re-evaluate when the Settings host opens or capability listeners change. Resolve hardware acceleration after attachment.

Auto initially uses Full only when all gates pass. Monitor the actual first user-requested transitions; do not run a hidden benchmark or manufacture video activity on welcome. Session fallback is sticky until the app restarts or user explicitly retries effects. Do not silently oscillate between tiers.

Runtime rendering guard:

- Collect current Activity and Settings window frame metrics using existing platform API 24+ support, or a narrowly scoped debug/measurement adapter; callbacks do no heavy work.
- At the current display refresh period T, after enough samples (at least 20 transition frames), mark a Full transition unhealthy if >10% of sampled frames exceed 1.5T or any render frame exceeds 100ms.
- Two unhealthy user-requested Full transitions downgrade the session. A surface exception, visible black-frame/surface failure detected by supported callbacks/test observation, or failed window-blur setup downgrades immediately.
- Do not infer decoder health or classify network buffering solely from UI frame metrics. Real playback/decoder validation remains a hardware test requirement.
- Ignore activity stop, display-mode switches and system permission/picker intervals when sampling.
- Log only fixed event enums/tier/frame counts via existing sanitized facilities. No user/channel/source identifiers.

When Full becomes unsupported mid-open, remove blur immediately, then restore video scale to 1 over at most 160ms if the device can safely finish that transform. For an actual surface failure, settle directly to safe untransformed values. Increase scrim to Safe target; retain focused row and shell position. Never upgrade to Full mid-open; wait for a subsequent opening.

On API 24/25, default remains Safe even on a box claiming large RAM. Preserve its known-good SurfaceView behavior.

## 9. Signature Settings transition — exact native motion contract

### 9.1 State machine

Use one SettingsMotionController and one desired visibility. Represent:

- Closed: host not showing, scene normalized, no Settings input ownership.
- Entering: host showing, Settings owns input, spring targets open.
- Open: host showing, exact final values.
- Dragging: touch/predictive gesture drives progress directly, Settings still owns input.
- Exiting: host still showing/owning input, spring targets closed.
- Disposed: callbacks/listeners removed; no View mutation allowed.

Do not infer desired visibility from View.VISIBLE alone. During Exiting the View remains visible until settled, but the next Menu command must reverse toward Open rather than issue another close.

| Event/current state | Result |
| --- | --- |
| Open from Closed | Attach/show host, capture origin, resolve tier, stage page, Entering |
| Open from Entering/Open | No duplicate host/job/reset; keep target Open |
| Close from Entering/Open | Respect guard, target Closed using current progress/velocity |
| Open during Exiting | Reverse spring in place; no alpha reset, re-inflate or old end callback |
| Menu during Entering/Exiting | Toggle desired visibility once for initial key-down |
| Back during Entering | Close once; matching release cannot close underlying page/app |
| Nested Back while Open | Page pop motion only; root scene remains recessed |
| Outside tap while importing commit | Existing finishing-import notice; remain open |
| System animations disabled | Settle desired state immediately with correct ownership/focus |
| Configuration/locale/mode change | Settle desired state, remeasure, restore logical target |
| Activity stopping/destroyed | Cancel, normalize both scenes, remove blur, dismiss owned host safely |

### 9.2 Geometry and property values

Let p be normalized openness, 0 Closed and 1 Open. Clamp p to [0,1] for video, scrim, blur and radius. The shell may use raw spring p in [-0.02,1.015] for a nearly imperceptible physical settle.

TV logical-start sign is -1 in LTR and +1 in RTL. Compact touch entry uses logical-end sign: +1 LTR and -1 RTL.

| Property | Closed start | Full Open | Safe Open | Off Open |
| --- | --- | --- | --- | --- |
| cz_video_scene scaleX/Y | 1 | 0.94 | 1, untouched | 1 |
| cz_hud_scene scaleX/Y | 1 | 0.94 | 0.97 | 1 |
| Video visible corner radius | 0 | TV 18dp / phone 16dp | 0 | 0 |
| Scrim alpha | 0 | 0.50 | 0.60 | 0.60, instant |
| Blur-behind radius | 0 | min(24px, 8dp in px) | 0 | 0 |
| TV shell translationX | sign × 32dp | 0 | 0 | 0 |
| Touch shell translationX | endSign × 24dp | 0 | 0 | 0 |
| Shell translationY | 6dp | 0 | 0 | 0 |
| Shell scaleX/Y | 0.985 | 1 | 1 | 1 |
| Shell alpha | 0 | 1 | 1 | 1 |

Mappings:

~~~kotlin
fun clamp01(value: Float) = value.coerceIn(0f, 1f)
fun smoothStep01(value: Float): Float {
    val x = clamp01(value)
    return x * x * (3f - 2f * x)
}

// p is the retained spring/gesture progress; targets are absolute, never additive.
val p01 = clamp01(p)
val shellProgress = p.coerceIn(-0.02f, 1.015f)
val fullVideoScale = 1f - 0.06f * p01
val safeHudScale = 1f - 0.03f * p01
val shellScale = 0.985f + 0.015f * shellProgress
val shellAlpha = smoothStep01(p01 / 0.32f)
val shellX = entrySign * entryDistancePx * (1f - shellProgress)
val shellY = sixDpPx * (1f - shellProgress)
val scrimAlpha = targetScrimAlpha * smoothStep01(p01)
val blurPx = (targetBlurRadiusPx * smoothStep01(p01)).toInt()
~~~

Apply both scales around the measured scene center. Calculate local mask radius from desired visible radius divided by video scene scale. Apply background transforms only to their designated wrappers; never alpha the SurfaceView itself.

In Safe, the rendering function must not write video scale/layout/clip/radius on each frame even if writing “1” would appear harmless. It is a separate branch that never touches the video scene. If downgrading from Full, normalize it once during downgrade and then use the Safe branch.

### 9.3 Spring constants and time budgets

Animate one FloatValueHolder progress with SpringAnimation. Derive layered properties from it so interruption cannot desynchronize scrim, video scale and panel.

| Operation | Stiffness | Damping ratio | Intended visible settle |
| --- | --- | --- | --- |
| Full Settings enter | 420f | 0.90f | Approximately 350–500ms |
| Full Settings exit | 520f | 0.95f | Approximately 280–420ms |
| Safe Settings enter | 650f | 1.00f | Approximately 220–340ms |
| Safe Settings exit | 800f | 1.00f | Approximately 180–280ms |
| Gesture cancel back open | 550f | 0.92f | Approximately 240–380ms |
| Gesture commit closed | 650f | 0.96f | Approximately 220–340ms |
| Nested page enter/return | 850f | 1.00f | Approximately 180–280ms |

MinimumVisibleChange for progress: 0.001f. Natural spring completion plus an end-value tolerance of 0.002 is sufficient; set exact final values on completion. Use a 700ms Full / 550ms Safe watchdog to settle the latest desired state if an abnormal run never ends; it is a recovery bound, not the normal animation duration.

Do not add a fixed delay before focus/interaction. Springs run on the main thread's animation loop. No sleeping, delayed focus gate or coroutine animation loop.

For simple alpha effects not driven by progress, use these exact cubic-bezier PathInterpolators:

- Standard enter: (0.20, 0.00, 0.00, 1.00), 180ms.
- Standard exit: (0.40, 0.00, 1.00, 1.00), 130ms.
- Focus chrome: (0.20, 0.00, 0.00, 1.00), 100ms in / 90ms out.

These curve durations do not override a SpringAnimation's physical timing. Never claim every spring lasts an exact fixed number of milliseconds.

### 9.4 Enter sequence

All logical steps before the first animated frame run on the main thread:

1. Finish welcome intro if needed; capture origin action/page, input modality and current focus snapshot.
2. Cancel held channel-confirm intent and pending channel-navigation callbacks. Close Channels/Info visually with coordinated outgoing motion; block their input immediately.
3. Resolve effective tier and stage the correct category/page. An openWelcomeFlow must build its language/import page before showing the host, avoiding a one-frame flash of root Settings.
4. Show the single retained Dialog at full-window size, with scrim alpha 0 and shell alpha 0. Shell already has final measured layout bounds.
5. Mark Settings as the input owner and block covered activity descendants/accessibility. Set logical root/page focus synchronously according to input modality.
6. Register/enable supported blur safely; start from radius 0. Set progress 0 only for a genuinely fresh Closed state.
7. Retarget progress to 1 using the tier's enter spring. Do not reset current progress if reversing Exiting.
8. On completion, clamp all visible properties to their exact Open targets and keep current focus.

Outgoing ordinary HUD child alpha can be owned by its own closing controller; scene scale belongs only to SettingsMotionController. Never let two animations own the same View property.

### 9.5 Exit and cleanup sequence

1. Respect existing importCommitting guard. Capture desired close and consume the triggering event sequence.
2. Stop page-owned rendering observers/callbacks according to existing leave semantics; keep closing Views drawable. Do not cancel unrelated activity-owned update work.
3. Keep host/focus ownership during Exiting. New content activation is blocked, but Back/Menu reversal commands are handled deterministically.
4. Retarget progress to 0 from its actual current value and velocity; do not play a different hardcoded reverse animation.
5. At confirmed Closed target: remove blur/flag, normalize scene transforms/masks, reset page presentation once, set layout_settings GONE, dismiss Dialog, then release input ownership.
6. Restore the triggering action if still visible/valid. From welcome return to its Import/Public/Language action. From active playback return to the normal playback/Info context; a legitimately uncovered error may receive its existing focus.
7. Verify the generation before any end callback changes visibility, focus or a window. A canceled close cannot dismiss a reopened host.

Do not set the shell GONE at the start of exit. Do not call the old immediate Anim.hideOverlay() on a Settings View still owned by this controller.

### 9.6 Nested page motion

The master rail and outer shell remain stationary; underlying video stays at its current recessed scale. Animate only the detail content:

- Forward: incoming detail alpha 0→1, translateX logical-end 18dp→0.
- Returning: incoming parent alpha 0→1, translateX logical-start 12dp→0.
- Outgoing detail alpha 1→0 with at most 8dp translation, 100ms.
- Use the page spring constants above; do not animate page measured width.
- Keep at most one outgoing and one incoming detail View in the transition container. Remove outgoing on completion/interrupt cleanup.
- Save parent focus/scroll before transition; restore the real triggering row immediately when returning, without scrolling to top first.
- If category changes rapidly while a page enters, settle/cancel the outgoing presentation and retain only the newest destination. Run each leave callback once.

Root category switching uses a restrained 100ms detail crossfade, not a spring slide on every Up/Down repeat. Focus moves at remote speed.

### 9.7 Touch and predictive-back progress

Provide a drag-to-dismiss route on compact touch root Settings and the single modal shell where appropriate. Preserve ordinary video swipes outside Settings.

- Gesture is scoped to a noneditable header/leading handle area inside the app; do not steal the operating system edge gesture or horizontal text/list interactions.
- Begin after ViewConfiguration.scaledTouchSlop and horizontal intent greater than 1.5 × vertical movement.
- Closing direction is toward the shell's entry edge (TV logical start, compact touch logical end), with drag magnitude mapped to openness.
- Use travelDistance = min(280dp, 0.65 × shellWidth). While dragging, p = clamp(1 - closingDistance/travelDistance).
- Release closes when p < 0.65 or closing velocity > 800dp/s. Otherwise spring back to Open.
- Convert VelocityTracker px/s into normalized p/s using the same travel distance. Retain that velocity in the release spring. Cancel the spring only when handing control to direct dragging.
- ACTION_CANCEL returns toward desired Open state unless logical close was already committed.
- Any drag is disabled during import commit. Never call player zapping from this gesture.

Committed system Back still uses one owner and one callback. On API 34+, a separately guarded predictive-back progress adapter may drive the same p for root dismissal; canceled gesture returns Open, committed gesture targets Closed. Nested pages preview only their page return, not the whole overlay. On lower APIs the committed Back path still animates normally.

Do not register Activity and Dialog back callbacks that both handle the same event. Preserve API 24 operation. Implement no new dependency or target-SDK change solely for predictive Back.

### 9.8 Dialog dispatch, lifecycle and UI lookup

Opening an owned Dialog does not require stopping the Activity/player. Retain existing onStart/onStop player lifecycle.

- The Dialog handles Settings D-pad, confirm, Back, Menu and IME input locally. Do not forward all Dialog events into MainActivity.dispatchKeyEvent(), which would recurse or invoke naked-playback zapping.
- Factor only the necessary existing UI commands into narrow callbacks. Preserve existing media-key behavior while a panel is open rather than inventing new shortcuts.
- Menu toggles desired visibility on initial down; matching up/repeats are consumed. The same rule applies to Back/confirm sequences opening a child page.
- Replace Activity.findViewById/currentFocus assumptions in SettingsPanel, UpdateMenu and presentation tests with an explicit Settings view registry/host root lookup.
- Never change Android's global activity findViewById behavior just to make detached-window tests pass.
- MainActivity.onWindowFocusChanged must not request focus in a hidden/closing Settings window; consult host ownership/state.
- Include host Views in locale translation, animation cleanup and accessibility ownership.
- On activity stop: synchronously settle Closed, zero blur, restore any Full video transform once, clear masks, dismiss without user callbacks, and then let existing lifecycle release player resources.
- Track whether this controller actually transformed video. If it never entered Full, lifecycle cleanup must not write video properties “just to reset them”; normalize only ordinary HUD/UI and the owned window.
- Remove frame/blur/power listeners and cancel gesture/animation callbacks on destroy. Keep no Activity/Dialog references in singleton objects.

### 9.9 Layout and routing preservation

Keep logical page keys such as language, aspect, sleep, mode, sources, own-playlist, update, diagnostics, about, guide and existing public-catalog routes. A category root is presentation grouping; it must not introduce a duplicate playlist/import router.

Where the old code checks layoutSettings.visibility, replace it with a well-defined host input/desired-state query if animation states matter. Keep a rendered-visible query separately for measurement/tests. Never let an Exiting Settings layer accidentally allow background Back/OK input.

The new host root is FrameLayout, so responsive layout code must use the actual parent LayoutParams type after moving layout_settings. Stage initial move once and measure against that host. Do not reuse an obsolete Activity-root width or layout gravity.

## 10. Phase 3 — Welcome and complete import/source flows

### 10.1 First-launch welcome

Preserve the existing direct welcome entry. Language selection is optional; the app does not stop at a mandatory locale screen.

TV layout:

- Header at safe top: original 40dp brand mark, 12dp gap and CouchZap wordmark 24sp/500; trailing language action with globe, current native language name and direction-aware expansion indicator.
- Below, two columns as section 3 specifies. Align the intro and action regions around a shared vertical center.
- Eyebrow: optional short localized line in secondary sage, not a large advertising slogan.
- Display copy: Take a seat. / Start watching. Use existing translated strings rather than hardcoded English.
- Body: Add your playlist, or start with public channels. Preserve existing meaning.
- Primary action: Import playlist, file/import icon, contained M3U · M3U8 · TXT supporting line.
- Secondary action: Start with public channels, globe/broadcast icon, contained iptv-org · Free-TV credit.
- Both actions have the same width and minimum 88dp height. Import receives initial D-pad focus; the other remains clearly actionable.
- TV footer is a small Navigate / OK Select hint inside safe bounds.

Phone: header at inset top, 20dp gutters, 24–32dp separation before message, 24dp before actions. Stack actions vertically with 12dp gap. Avoid a huge blank top margin. Scroll on short windows/large font. Remote hints are hidden.

Use native vectors and typography, no generated image as the actual screen. Avoid a couch illustration behind the headline or text colliding with artwork. Keep no account/marketing promises.

### 10.2 Welcome focus and motion

- Initial target is Import playlist. Down reaches Public channels; Up reaches Language through an explicit path. Left/Right movement follows the physical two-column structure without focusing noninteractive headings.
- Language control shows the saved native name or English default.
- A one-time intro may fade the intro/action groups in with 8dp vertical translation over 240ms, delays 0/40/80ms maximum. Finish by 320ms. A single existing bolt accent may peak at alpha 0.35 for 120ms, with no loop.
- On any initial key-down or touch-down, finish intro synchronously and process that same input. Do not eat the first selection.
- Respect welcome_intro_seen and system/effects Off. Safe tier may use a simple 120ms fade.

### 10.3 Import own playlist

Import action opens existing own-playlist page with:

1. Import file.
2. Import link.

Do not hide link import behind a small undocumented icon. Each action has a clear label and optional concise help line.

File path:

- Keep ACTION_OPEN_DOCUMENT with existing MIME types/URI permission handling.
- On Android 7–9 without a document picker, keep the existing SimpleFileBrowser fallback and storage permission flow.
- Android 10+ still requires a suitable system picker; show existing failure explanation. Do not invent broad filesystem access or request MANAGE_EXTERNAL_STORAGE.
- System picker UI is OS-owned and is not restyled.
- Cancel returns to the exact triggering action/page, with the existing playlist unchanged.

The file browser is already reparented into a SettingsPanel page by showFallbackBrowser(). Preserve that single-instance behavior under the new Settings window, including its leave callback returning it to the Activity root. Adapt LayoutParams to the actual parent. Do not show a hidden Activity file browser behind the Dialog.

### 10.4 Playlist/guide link page

Use a native URI EditText with:

- Actual current validation via HttpStreams.validUrl().
- LTR field/caret regardless of interface language.
- Minimum height 56dp, clear focus boundary and visible label outside placeholder.
- URI keyboard/input type, single-line horizontal editing and paste behavior.
- Localized Import action, enabled according to existing validation/submission guard.
- Inline error below the field; retain input when validation fails.
- IME Next/Done route to the same Import callback, not a second request.

No automatic submission on paste. Do not expose saved tokens/credentials in notices or logs. Back first dismisses the IME when appropriate, then returns a page; it does not exit the app.

### 10.5 Public provider and category selection

Reuse the existing provider routes and APIs. Present two informative provider actions:

- iptv-org with its existing description.
- Free-TV with its existing description/support note.

Next choices retain baseline capabilities:

| Provider | Choices |
| --- | --- |
| iptv-org | All, Countries, Languages, Categories |
| Free-TV | All, Countries, Categories |

Keep source provider identity in a quiet breadcrumb/header. Do not represent public channels as a bundled/guaranteed subscription.

Catalog page:

- Fixed search field and a virtualized, focus-safe list when results are long.
- Keep exact current query matching and original label/code pairs.
- Empty result, initial loading, load failure and Retry are explicit states.
- At least 240 countries/options must remain navigable one step per key press, including offscreen destinations, fast key repeats and filtered results.
- Selection is a separate OK action. Do not automatically import on focus or on loading the list.
- Leave cancels that page's existing catalog job.

The baseline creates catalog buttons inside a LinearLayout. It is acceptable to replace only that page's visual list with RecyclerView and a UI-only catalog focus controller. Keep loadCatalog/provider/source acceptance logic unchanged. Use stable provider-kind-code keys, explicit pending focus and retained search/caret. Do not reuse ChannelListNavigation by pretending catalog entries are Channels.

This new virtualized list must retain the v2.2.1 navigation fix: sequential traversal through all options, recovery when focus is missing, and no skipping caused by the host's generic focus search.

### 10.6 Existing replacement confirmation

When there is an existing playlist, preserve the current confirmation before replacement. Use a composed confirmation page inside Settings detail:

- Clear Replace playlist heading.
- Existing explanation that the selected source replaces the current list.
- Replace playlist and Cancel actions, with Cancel initially focused.
- No changes to library until the existing commit stage.

When no existing playlist requires confirmation, follow baseline direct acceptance. Do not add unnecessary extra confirmations.

### 10.7 Import progress, success and failure

Use existing importInProgress/importCommitting/generation/job fields:

| State | Presentation/interaction |
| --- | --- |
| Idle | Choices available |
| Loading/preparing | Stable progress/status; current existing cancellability preserved |
| Committing | Guard prevents conflicting actions/close; show finishing-import message |
| Success | Existing source saved; transition to real playback/list state |
| Failure with old list | Explain failure and that previous list is retained |
| Failure with no list | Offer Retry/Back and diagnostics through existing flow |
| Cancel/system picker dismissed | Restore trigger focus/scroll without error fabrication |

Do not animate fake percentages. Use determinate progress only when measured; otherwise use a quiet indeterminate indicator. Keep top-level language/import controls disabled during the same existing guarded interval and restore them on every terminal path.

The import-error page keeps existing TLS/network/timeout/other explanations and Diagnostic log shortcut. No raw stack trace in a normal error card.

### 10.8 Source sorting and refresh

Preserve any existing source-order/natural-name sorting options, source numbering and current public list order behavior. A visual list sort never renumbers channels.

Refresh remains user-invoked; cached channels remain usable until a successful replacement. Do not make the source timestamp advance on a failed operation or on opening Settings.

## 11. Phase 4 — Mobile and adaptive refinement

### 11.1 Mobile playback and gestures

Preserve existing behaviors and scope them to naked playback:

- Single tap shows/hides controls.
- Double tap toggles play/pause.
- Horizontal swipe switches channel through existing action/debounce paths.
- Swipe up opens Channels.
- Settings gear opens the same logical Settings route.

When a list, input, sheet or Dialog owns the pointer stream, underlying video gestures receive none of it. A swipe in a language/catalog list must scroll that list, never change channel. A button's double-tap does not bubble into video play/pause.

### 11.2 Compact phone Info

Use a bottom control dock with min 48dp targets, sufficient system-gesture inset and 12–16dp internal gaps:

- Header: channel logo/name, current stream status and clock if it fits.
- Main action row: Previous, Play/Pause, Next, Favourite.
- Secondary row: Channels, Picture size, Settings. Hide text labels only when the focused/touched action still has a clear accessible name.
- Expanded technical/EPG region scrolls separately; buttons remain reachable.
- Long source titles use up to two lines; essential controls are never reduced below target size to fit a title.

Keep pause/favourite state from current callbacks. No extra “Like” state independent of saved favourites.

### 11.3 Mobile Channels

Full-height compact screen/sheet with a pinned compact header and search/filter region; RecyclerView occupies remaining height. Close/back affordance is visible. Use 20dp horizontal gutters.

Rows keep logo/name/number/status relationships from ChannelRow, but prioritize the name. Do not carry all TV helper text, wide column gaps or decorative resolution chips into mobile.

Touch taps select the channel with existing behavior; long press toggles favourite where supported. Ensure long press does not trigger a later normal click.

On IME show, list resizes/insets and retains search selection. On orientation changes restore the same query and focused/logical row. Do not clear a filter when changing layout.

### 11.4 Mobile Settings and language

Compact Settings root is a grouped vertical list with Playback, Playlist and App section headings. Nested choices use the full-width integrated ChoiceRow design, not a TV category rail compressed into 360dp.

Use a clear back header for subpages; retain close only where it clearly dismisses the whole Settings context. Keyboard or remote navigation remains available when attached.

Language has six complete rows at normal font scale with scrolling if needed. No forced fit at large accessibility sizes. Maintain native names, translations and selected check semantics from section 7.

### 11.5 Small-height, tablet and multi-window behavior

- Landscape phone with height < 480dp uses scrollable side/full-height panels instead of oversized bottom cards.
- Tablet can use expanded master/detail when width/height permit; touch targets and font scale remain mobile metrics, not TV metrics.
- Folding/multi-window/resizing uses current window bounds. Never use device model or physical diagonal as the layout class.
- Reparent ordinary UI content only when composition changes require it; never reparent the player surface.
- Retain current origin, selected category, nested page, scroll, source/query state and logical focus through a mode/size change.

### 11.6 Touch polish

Use local pressed fill/ripple confined to the component outline, 90ms feedback; no forced spring scale for every touch scroll interaction. Keyboard focus spring activates only for relevant input modality.

Outside-tap dismissal has a full scrim target outside the shell. A down event inside a panel followed by up outside does not count as an outside tap. Respect gesture slop and do not dismiss on list fling.

No haptic feedback on TV. Phone haptics are limited to existing Android accessibility/platform defaults unless explicitly already present; this spec adds none.

## 12. Localization, accessibility and rendering quality

### 12.1 Resource discipline

All new user-visible strings go into base values/strings.xml and values-es, values-hi, values-th, values-fa and values-ar. Keep format placeholders, plural rules and escaping correct. Do not concatenate English fragments into a localized sentence.

Native language display names are stable self-names. “Current language,” empty states, motion-mode descriptions, counts, hints and error copy are localized.

Keep start/end padding, gravity and auto-mirrored directional vector resources. Preserve actual source/provider/log identities. Do not mechanically translate iptv-org, Free-TV, codec names, URLs or arbitrary custom group names.

### 12.2 Accessibility semantics

- Every actionable root has a concise accessible label, role and current value/state.
- Decorative icons/logo duplicates are not separate accessibility stops.
- Language/aspect choices expose single-selection semantics; sound/favourite expose checkable state.
- Heading TextViews are accessibility headings where supported.
- Use stateDescription through compatible APIs; include a content-description fallback on API 24.
- On panel open announce its title once. Progress percentage updates are not announced every frame.
- Covering a scene sets appropriate descendants to NO_HIDE_DESCENDANTS for accessibility and blocks keyboard focus; restore their previous values after dismissal.
- Set accessibility pane titles on the active host/page. Do not leave two windows announcing the same title.
- Keep TalkBack traversal within active content and expose Back/Close. A D-pad focus trap must not become an accessibility trap.
- Large fonts can grow rows and scroll. Never use auto-size to shrink below the typography minimum for essential controls.
- Verify all six scripts with real shaping; do not convert labels to bitmap images.

### 12.3 Drawing and performance

- Cache Paint, Path, typefaces, vector/tint state and blur capability values. No allocation/I/O/network calls in onDraw(), spring update listeners, focus listeners or RecyclerView binds beyond existing small data formatting.
- No permanent hardware layer on a root containing SurfaceView. Avoid layer promotion for the entire live scene; GPU transforms already support ordinary View properties.
- Keep focus chrome to a small local invalidation. Do not invalidate/rebind all rows on every focus step.
- Maintain existing stable RecyclerView IDs and valid NO_POSITION guards. If list diffing is introduced for presentation efficiency, preserve IDs, order, callbacks, focus and current search behavior.
- Cancel resettable motion in onViewRecycled/onDetachedFromWindow. Avoid additive transforms that compound after 50 focus cycles.
- Avoid per-frame window LayoutParams changes except the bounded supported blur radius. No relayout on each spring tick.
- Do not animate 4K bitmaps or repeatedly capture video to fake blur.

### 12.4 Native quality checks

Inspect actual Android-rendered views in landscape TV and portrait/landscape phone layouts. Image-generation mockups must not be used as screenshot evidence.

Check baseline alignment, glyph clipping, divider alignment, icon stroke consistency, safe bounds, high-contrast backgrounds and focused expansion. Every optional label must disappear without leaving a broken gap.

## 13. Native implementation conventions and dependency change

### 13.1 Add the correct AndroidX artifact

Approved dependency: androidx.dynamicanimation:dynamicanimation:1.1.0. “Spring animation” describes the API; do not add an imaginary artifact named spring-animation.

Add to the existing version catalog:

~~~toml
# Merge these entries into the existing sections; do not duplicate section headers.
[versions]
dynamicanimation = "1.1.0"

[libraries]
androidx-dynamicanimation = { group = "androidx.dynamicanimation", name = "dynamicanimation", version.ref = "dynamicanimation" }
~~~

Then in app/build.gradle.kts:

~~~kotlin
dependencies {
    implementation(libs.androidx.dynamicanimation)
}
~~~

Use the Java-compatible API from Kotlin; KTX is unnecessary. Preserve the existing built-in Kotlin/AGP setup. No new Compose plugin, Material dependency, blur library, adaptive dependency or network font dependency.

### 13.2 Token application in Views

Use semantic resources with a small runtime facade. TV versus phone font/geometry selection must use the effective mode, because manual TV/Phone overrides do not change Android's resource UI-mode qualifiers.

~~~kotlin
fun android.view.View.dp(value: Float): Float =
    value * resources.displayMetrics.density

fun android.widget.TextView.applyTextSizeResource(
    @androidx.annotation.DimenRes resourceId: Int
) {
    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(resourceId))
}
~~~

Define font sizes as sp resources, geometry as dp. Do not pass already-converted pixels into setTextSize(sp). Use actual window/host bounds for layout, not display pixels divided by an assumed constant.

Example resource pattern:

~~~xml
<resources>
    <dimen name="cz_tv_control_text">20sp</dimen>
    <dimen name="cz_phone_control_text">17sp</dimen>
    <dimen name="cz_tv_setting_min_height">64dp</dimen>
    <dimen name="cz_phone_setting_min_height">60dp</dimen>
    <dimen name="cz_focus_ring_width">2dp</dimen>
    <dimen name="cz_component_radius">10dp</dimen>
    <color name="cz_canvas">#FF0D100F</color>
    <color name="cz_surface_focus">#FF303D32</color>
    <color name="cz_text_primary">#FFF2F0E8</color>
    <color name="cz_sage_bright">#FFDDE9D5</color>
</resources>
~~~

This is an abbreviated pattern; implement the complete token tables, with no duplicate resource definitions.

### 13.3 Semantic components and binding changes

- Prefer the existing SettingRow and native Views over unrelated libraries.
- Expose stable component methods for label/value/selected/enabled/callbacks; keep strings and domain callbacks outside the drawing class.
- Maintain one listener dispatcher per component. Motion observers must not erase callbacks remembering navigation state.
- If action IDs migrate from Button to SettingRow, update AppLanguage's explicit text assignments to the new label views. The old root ID stays on the action; do not cast the new root to TextView.
- tv_settings_aspect_value, tv_settings_sleep_value, tv_settings_mode_value and tv_settings_source stay real TextViews.
- Add tv_settings_crt_value and appropriate row label IDs. Remove all code depending on the retired Switch.
- Programmatic rows receive stable action keys independent of View.generateViewId(); generated IDs alone are insufficient after a rebuild.
- Use existing SettingsPanel APIs as compatibility adapters while moving consumers to richer rows. Remove obsolete adapters only after all call sites and tests pass.

### 13.4 Core route and callback integration

Keep menu methods as the entry points used today. A caller should not know whether Settings is currently in a TV Dialog composition or a compact touch composition. Expose host methods along these lines:

~~~kotlin
interface SettingsPresentationHost {
    val ownsInput: Boolean
    val desiredOpen: Boolean
    val renderedVisible: Boolean
    val root: android.view.ViewGroup
    fun open(origin: UiFocusSnapshot?)
    fun close()
    fun toggle()
    fun settleForConfigurationChange()
    fun closeImmediatelyForLifecycle()
    fun destroy()
}
~~~

UiFocusSnapshot is the new presentation model from section 1, not domain state. The interface is a responsibility contract; implement a concrete native host, not an unused wrapper beside the old direct animations.

Internal category/page rendering can change. Existing source/import/update keys, intents, callbacks and Back return relationships remain authoritative.

### 13.5 Motion ownership checklist

Before integrating a component:

1. Identify the sole owner of each alpha, translation, scale, Z and visibility property.
2. Replace any old Anim.showOverlay/hideOverlay call for that same component.
3. Preserve logical visibility queries needed by error input guards.
4. Specify lifecycle settling and recycle reset.
5. Test reversal/rapid input before visual polish.

Keep shutter fades and player error handling separate from Settings depth. Do not change CrtSwitchSound or StreamInfoFormatter to make UI state easier to read.

## 14. Verification plan and acceptance criteria

This section tells the implementing agent what to verify. It does not claim those implementation tests have already been run when this specification is delivered.

### 14.1 Preserve and adapt current regression coverage

Keep the existing test suite and assertions covering actual behavior:

| Existing test file under app/src/test/java/com/couchzap/iptv/ | Contract to preserve |
| --- | --- |
| TvRemoteRegressionTest.kt | Remote mappings, focus containment, held keys, playing/current distinction |
| CatalogRemoteRegressionTest.kt | v2.2.1 long/filtered public-catalog traversal and missing-focus recovery |
| SettingsLanguageRegressionTest.kt | Optional welcome language, retained pages, nested Back, update job ownership, locale persistence |
| SettingsFocusHaloRegressionTest.kt | No native full-screen halo after parent focus restoration |
| SettingsVisualRegressionTest.kt | Settings visibility, bounds, last row and state appearance |
| SettingsLanguageRegressionTest.kt / PersianLocalizationRegressionTest.kt | Native scripts, RTL and technical LTR values |
| SearchTypingRegressionTest.kt | IME typing/composition, selection and filtering |
| OnboardingFlowRegressionTest.kt | Direct welcome/import/provider paths and no input swallowed by intro |
| NextVersionUiRegressionTest.kt / PolishRegressionTest.kt | Existing UI/player refinements and sound behavior |
| PlayerSessionRegressionTest.kt | Existing player lifecycle/session behavior |
| PublicSourcesRegressionTest.kt / LibraryFeaturesTest.kt | Source import, refresh, guide/favourite persistence |
| SupportFeaturesTest.kt | Updates/diagnostic safety contracts |
| PlaylistParserTest.kt / PlaylistRepositoryTest.kt | Data behavior unchanged |
| MainActivityTest.kt / ScreenRenderTest.kt | Startup and native-rendered UI |

Some old structural assertions are intentionally obsolete:

- Settings width equals Channels width.
- Settings always begins at activity-root x=0.
- All Settings views are found with Activity.findViewById after moving to a separate window.
- Every dynamic setting is a Button.
- Sound state is held by btn_settings_crt Switch.

Replace only these with assertions matching the new approved structure. Keep behavioral coverage, especially no focus stealing, correct selection, one request, script direction and preservation of data. Do not delete tests, broadly loosen bounds, skip SDKs or update every golden blindly to get a green run.

Introduce an explicit Settings host test accessor/injected registry. Render the active window plus its backdrop where appropriate; do not override platform findViewById or hide test failures behind a fake duplicate tree.

### 14.2 Meaningful new behavioral tests

Add tests that exercise observable outcomes:

1. Each language's TV Settings rail/detail graph, reverse horizontal directions in RTL, no wrapping, exact parent row/scroll restoration.
2. Enter → Back at 30% → Menu re-open: one host, no stale close callback, correct final scale/alpha and focus.
3. Repeated Menu/Back/confirm down/up: one action per physical sequence, no accidental row activation or app exit.
4. Fake capability resolution: API 24/25, API 28, API 31+ supported/unsupported blur, low RAM, power saver, requested Safe/Off/Full, disabled system animations.
5. Safe tier performs zero writes to video surface/container transforms/clip/layout while opening/closing Settings. A fake video-transform adapter can count calls.
6. Full tier reaches exactly 0.94 and restores exactly 1; corner mask, blur radius and scrim return to zero on close/stop.
7. Blur availability changes while open: downgrade without losing selected row; no mid-open upgrade.
8. Surface/setup failure: safe fallback, retained media state, no second player/surface.
9. Callback after a page is closed/replaced cannot request focus or update a detached host.
10. Sound On/Off commits once and updates accessible state; focus never previews/plays it.
11. Native language names, selected check and mixed LTR/RTL technical fields survive live language changes.
12. Expanded/compact mode switching retains the same page and row without resetting library/player state.
13. Search empty/results and a large catalog remain navigable under rapid held keys.
14. Existing update progress/Cancel, diagnostic copy/save/share fallbacks and file-picker cancel return correctly from the Dialog host.
15. Touch drag cancel/commit thresholds and gesture isolation: scrolling a list never zaps the underlying channel.
16. Default focus highlights and Z/scale reset after repeated reattachment/recycling remain dark/normal.

Use an injected clock/capability adapter and disabled/settled motion where appropriate for deterministic unit tests. Do not test implementation line order instead of behavior.

### 14.3 Device/API and layout matrix

| Environment | Required checks |
| --- | --- |
| API 24/25 Android TV/Box | Safe by default, startup with all fonts, SurfaceView stays unchanged, held remote navigation, Settings child Back |
| API 28 TV/phone | Safe rendering, script/font shaping, source-file fallback where applicable |
| API 31+ capable TV with blur support | Full scene scale/rounding, live blur, interrupted spring, blur disabled at runtime |
| Modern TV with blur unsupported/disabled | Honest Safe fallback; usable contrast without blur |
| API 35 phone | Insets, landscape/portrait, touch/keyboard, IME, Settings gesture |
| API 36 regression runtime where existing tests support it | Existing Android 16 focus-halo and return-state checks |
| Low-memory/power-save scenario | Safe/Off policy, no unexpected codec/surface work |

Do not change compileSdk/targetSdk merely because existing Robolectric coverage includes API 36.

Reference layouts:

- TV 960 × 540dp; verify real 720p, 1080p and 4K output where hardware is available.
- Phone 360 × 800dp and 412 × 915dp portrait.
- Short phone landscape around 800 × 360dp.
- Medium touch 600dp width; expanded touch 840dp+ width.
- Font scales 1.0, 1.3 and 2.0.
- All six languages; prioritize English/Persian/Arabic for every input/animation state and render every script in welcome/language/settings.

Resolution and dp are different. Use actual reported density and render bounds in tests.

### 14.4 Hardware motion validation

Robolectric screenshots cannot prove live SurfaceView animation, compositor blur, decoder smoothness or physical remote behavior. Validate a representative Android 7 box (including the user's Xcruiser XDSR685 HDR if available) and a capable modern device.

Measure at the current refresh rate:

- Full transition target: p95 UI frame time at or near one refresh period, with <5% transition frames over 1.5 periods in a controlled local-stream test.
- Safe transition target: no new visible stutter in video and no surface detach/black frames; smooth UI updates within device capability.
- Focus target: visual response begins by the next rendered frame after navigation; does not wait for data or spring completion.
- No surfaceDestroyed/surfaceCreated cycle caused by entering/exiting Settings.
- No player prepare/release, selected-track change or audio interruption attributable to panel motion.
- No focus ring/Z/scale accumulation after 100 moves and 30 open/close cycles.

These are acceptance targets, not claims guaranteed by CPU/RAM checks. Record tested devices, actual results and remaining gaps honestly. If Full fails, the shipping Auto policy must select Safe on that configuration.

### 14.5 Native screenshots and motion recordings

Produce native-rendered screenshots for:

- TV welcome, Channels with focused-unplayed and playing-unfocused rows, empty/search state.
- Groups with active selection distinct from focus.
- Info compact/expanded, favourite on/off.
- Settings Playback/Playlist/App category roots, root focus versus detail focus.
- Language selection, picture size, sound On/Off, source/link page, provider/catalog states.
- Update checking/downloading/error, diagnostic preview, About.
- Matching compact phone views and RTL versions.

Capture representative transition frames at p=0, 0.25, 0.5, 0.75 and 1 using a debug/test-only deterministic progress hook; production controls expose no slider.

Record a short real-device video of Full entry/exit/reversal and Safe entry/exit. Native render tests may use fixture stream metadata and illustrative backgrounds, labeled as fixtures.

ScreenRenderTest already uses Robolectric native graphics and writes PNGs. Adapt it to include the active Settings window. Preserve the distinction between a rendered layout test and actual video/hardware testing.

### 14.6 Build checks

Use the repository's existing supported JDK/SDK environment; do not alter tooling to fit the design.

~~~sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease :app:lintRelease
git diff --check
git diff --stat
~~~

Run appropriate device/instrumentation checks when the environment provides them. Do not claim physical-device or live-network tests that were not performed.

The only approved production dependency addition is DynamicAnimation 1.1.0. Confirm it does not change minSdk/toolchain unexpectedly. Keep baseline source fonts compatible with API 24.

Never publish, merge, create a release, upload APKs or modify signing material just because this design spec asks for builds. Report reviewable local results and follow the user's separate publication instruction if one is later provided.

## 15. Implementation checkpoints and completion definition

### 15.1 Phase 1 checkpoint — viewing and channel interaction

Complete tokens/components needed for Channels and Info, TV safe geometry, state separation, GroupsPicker, rendering states and native focus physics. Verify preserved playback/remote/search/favourite behavior. Keep the current welcome/settings operational during incremental work.

### 15.2 Phase 2 checkpoint — Settings and depth

Complete single window host, TV master/detail, every existing subpage, On/Off sound row, Visual effects setting, Full/Safe/Off policies, reversible motion, gesture/Back ownership and lifecycle settling. Verify retained updates/diagnostics/import callbacks and API 24 Safe path.

### 15.3 Phase 3 checkpoint — first use and sources

Complete native welcome compositions, optional language control, own-file/link imports, both public providers and all their catalog branches, long-list navigation, replacement confirmation, progress/error/diagnostic states and system-picker return paths.

### 15.4 Phase 4 checkpoint — mobile and final quality

Complete compact/expanded touch layouts, phone landscape, IME insets, gesture isolation, external keyboard behavior, all six scripts, large fonts, contrast and final native captures. Finish cross-device motion checks and regression suite.

### 15.5 Required implementation handoff

When implementation is finished, provide:

- The concrete behavior changed and the resulting TV/mobile interaction.
- A concise changed-file map, including any allowed presentation edits in MainActivity/support UI.
- Native screenshots and available motion recording paths.
- Build/lint/test results, including unrun hardware checks.
- Effective-tier behavior on tested devices and any explicit Safe fallback.
- A review of scope showing protected data/player/security/scripts were not changed.

Do not present the redesign as complete while TV remains a scaled phone layout, focus only works with touch, a child screen uses a generic unstyled alert, one locale crashes on API 24, or Settings exit still cuts immediately.

### 15.6 Definition of done

- [ ] Existing Views architecture retained; sole approved dependency added.
- [ ] Original identity preserved with one consistent token/component family.
- [ ] TV and phone use deliberately different compositions.
- [ ] D-pad graph is deterministic, scoped and reversible; root/nested focus memory works.
- [ ] Focus, selection, playing and favourite are visibly independent.
- [ ] Main UI operations retain source numbering, identities, state and existing side effects.
- [ ] Every discussed/existing screen and non-ideal state is covered.
- [ ] Full uses 94% video depth, visible rounded corners, scrim and supported live blur.
- [ ] Safe leaves video/subtitles/surface untouched; effects override and fallback work.
- [ ] Settings enter/exit/reversal/gesture/lifecycle use one coherent native motion owner.
- [ ] No old Settings immediate-hide path bypasses the new transition.
- [ ] No duplicate window, router, data state, player, hidden control proxy or focus listener.
- [ ] Existing Android 7 and Android 16 fixes are retained.
- [ ] All six languages and accessible font sizes render and navigate correctly.
- [ ] Existing behavioral checks pass; intentional structure tests updated meaningfully.
- [ ] Actual hardware motion claims are supported by recorded checks or clearly marked unverified.

## 16. Evidence and implementation references

### 16.1 Repository evidence, pinned baseline

The implementation map was derived from the following existing sources at the inspected commit. Repository paths elsewhere in this document are relative to the same baseline.

- [Build configuration](https://github.com/saeedt20/CouchZap/blob/30cf8de44ca78ae5ad120a9a59177ac20608af6f/app/build.gradle.kts): ViewBinding, minSdk 24, SDK 35, version 2.2.1.
- [MainActivity](https://github.com/saeedt20/CouchZap/blob/30cf8de44ca78ae5ad120a9a59177ac20608af6f/app/src/main/java/com/couchzap/iptv/MainActivity.kt): existing UI seams, callbacks, route and input guards.
- [Activity layout](https://github.com/saeedt20/CouchZap/blob/30cf8de44ca78ae5ad120a9a59177ac20608af6f/app/src/main/res/layout/activity_main.xml): SurfaceView, SubtitleView, retained Settings/OSD/import roots.
- [SettingsPanel](https://github.com/saeedt20/CouchZap/blob/30cf8de44ca78ae5ad120a9a59177ac20608af6f/app/src/main/java/com/couchzap/iptv/ui/SettingsPanel.kt): page stack, focus/scroll and dynamic row builders.
- [ChannelListNavigation](https://github.com/saeedt20/CouchZap/blob/30cf8de44ca78ae5ad120a9a59177ac20608af6f/app/src/main/java/com/couchzap/iptv/ui/ChannelListNavigation.kt): pending and remembered channel focus.
- [Anim](https://github.com/saeedt20/CouchZap/blob/30cf8de44ca78ae5ad120a9a59177ac20608af6f/app/src/main/java/com/couchzap/iptv/util/Anim.kt): current animation/settling behavior.
- [PlaylistMenus](https://github.com/saeedt20/CouchZap/blob/30cf8de44ca78ae5ad120a9a59177ac20608af6f/app/src/main/java/com/couchzap/iptv/ui/PlaylistMenus.kt): current source/provider/catalog routes.
- [AppLanguage](https://github.com/saeedt20/CouchZap/blob/30cf8de44ca78ae5ad120a9a59177ac20608af6f/app/src/main/java/com/couchzap/iptv/util/AppLanguage.kt): language order, live translation and direction.
- [Version catalog](https://github.com/saeedt20/CouchZap/blob/30cf8de44ca78ae5ad120a9a59177ac20608af6f/gradle/libs.versions.toml): existing dependency baseline.
- [Existing regression tests](https://github.com/saeedt20/CouchZap/tree/30cf8de44ca78ae5ad120a9a59177ac20608af6f/app/src/test/java/com/couchzap/iptv): retained behavioral coverage.

### 16.2 Primary Android references

These sources establish platform/API behavior. Colors, composition, spring constants, thresholds and input policies above are CouchZap design decisions, not values claimed to be mandated by Android or copied from tvOS.

- [Android TV layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts): landscape composition, dp and overscan.
- [Android TV focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system): focus and selection dimensions.
- [TV navigation](https://developer.android.com/training/tv/get-started/navigation): native focus traversal.
- [DynamicAnimation releases](https://developer.android.com/jetpack/androidx/releases/dynamicanimation): stable artifact 1.1.0.
- [Spring animation for Views](https://developer.android.com/develop/ui/views/animations/spring-animation): native physics animation and retargeting.
- [SpringAnimation API](https://developer.android.com/reference/androidx/dynamicanimation/animation/SpringAnimation): spring lifecycle and parameters.
- [Media3 surface types](https://developer.android.com/media/media3/ui/surface): SurfaceView rendering advantages, version and lifecycle considerations.
- [RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect): API 31+ RenderNode effects.
- [AOSP window blurs](https://source.android.com/docs/core/display/window-blurs): cross-window blur versus View effects and support limitations.
- [WindowManager](https://developer.android.com/reference/android/view/WindowManager): blur capability and availability listener.
- [WindowManager.LayoutParams.setBlurBehindRadius](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#setBlurBehindRadius(int)): radius in physical pixels and required flag.
- [Frame metrics listener](https://developer.android.com/reference/android/view/Window.OnFrameMetricsAvailableListener): API 24+ window measurement.

Recheck public API availability against compileSdk 35 when writing code. Do not use a method merely because it appears in a newer online SDK reference.

### 16.3 Font sources

Use upstream static font releases and retain the associated license, with the selected asset versions recorded:

- [Inter](https://github.com/rsms/inter).
- [Vazirmatn](https://github.com/rastikerdar/vazirmatn).
- [Noto Sans Devanagari](https://github.com/notofonts/devanagari).
- [Noto Sans Thai](https://github.com/notofonts/thai).

Do not import web CSS/WOFF font files into Android resources or synthesize source text as images.

---

**Implementation instruction:** Start with the pinned-code audit and Phase 1. Preserve the operational core, build the shared native presentation system, and complete all four phases with the checks above. This document is the implementation contract; generated visual concepts are supporting references only.
