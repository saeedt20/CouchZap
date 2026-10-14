# CouchZap 2.1.1 UI candidate

The physical TV box report identified excessive left padding in Channels,
joined Language and Check for updates rows, and a switch thumb covering the
Channel switch sound label. A phone running Android 16 also showed shadows
after opening a settings page and tapping the header Back button.

## Changes

- Channels uses 3% of screen width for its left content inset, with a 24 dp
  minimum, while retaining the existing top and bottom insets. At 960 dp
  wide, its content moves from 48 dp to 28 dp from the left edge.
- Language now has the same 8 dp bottom gap as neighbouring settings rows.
- The sound label and SwitchCompat occupy separate columns. The full row is
  the single remote focus target; tapping either the row or the thumb changes
  the saved state. Accessibility exposes that row as one checked switch.
- The custom button selectors retain focus and pressed feedback with native
  elevation, translationZ and state-list Z animation disabled. This covers
  XML buttons and the native buttons created for settings child pages.
- Version name is 2.1.1 and version code is 10. The CI test runtime uses Java
  21 for the new Robolectric Android 16 coverage.

## Validation

`testDebugUnitTest`, `lintDebug`, `lintRelease` and `assembleRelease` passed
locally using JDK 21, SDK 35 and build tools 36.0.0. There were 96 tests with
zero failures, errors or skipped cases. Each lint variant had zero errors and
125 warnings.

The six new regression cases cover API 24 and API 36: separate label/thumb
bounds in English and Persian, phone and TV layout, one remote toggle per OK
press, touch toggling, checked accessibility state, consistent row spacing,
and repeated header Back with preserved parent focus and scroll position.
Re-running the Back regression with the original theme failed on both SDKs:
the original buttons had 2 dp elevation rather than zero. The fixed theme
passed. This verifies the button-shadow source; physical GPU rendering on the
reported devices still needs the user's check.

Six screenshots in `phase05/screenshots` were inspected. TV renders use API
28 with demo channel/video fixtures. The two portrait renders use native
API 36 views after tapping the child-page Back button. These are test renders,
not screenshots from the user's devices.

The installation candidate uses `com.couchzap.iptv` and the existing release
certificate:
`539f14a4b71cc830108cffefa8778ec06797be539c90ab80bc0c89b0c60eb187`.
The final APK is signed locally with v1 and v2 for the existing device range.

The published 2.1.0 release recipe and its completed device probes remain
historical evidence for that APK. A future 2.1.1 publication needs its own
reviewed build, signature recipe and signed-device probe.
