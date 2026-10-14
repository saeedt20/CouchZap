# CouchZap 2.1.2 focus-overlay candidate

The user confirmed that 2.1.1 installed on a Xiaomi M2101K7AG running Android
16, but the pale overlay after tapping a settings child page's Back button
remained. Removing button elevation addressed native button shadows; it did
not cover a separate framework focus highlight drawn over the whole view tree.

## Reproduction and change

The full-screen root is a legitimate keyboard focus fallback. Its solid
background has no focused drawable state, allowing Android's default focus
highlight to draw over every child. Native API 28 rendering reproduced the
reported wash: the panel pixel changed from `#242B26` to `#3F4540`.

On API 36, the test enables the framework's device-configurable focus highlight
and supplies non-touch attachment state explicitly. The 2.1.1 framework
attached a full-screen `RippleDrawable`. Its device rendering cannot be judged
solely from a software bitmap, so the regression checks both the unchanged
pixel colour and absence of that native drawable. Both SDK cases failed before
the fix and passed afterwards. This exercises a device-configured variant;
it does not read the Xiaomi device's internal configuration.

The API 26 theme now sets `android:defaultFocusHighlightEnabled` to false.
The shared base theme keeps the existing colours and custom focus selectors.
Focus routing and the retained settings page stack continue to work. API 24
uses the base theme without the newer attribute.

Settings Back also records local focus/touch state and root/page highlight
flags in the existing diagnostic log, without playlist URLs or channel names.
Version name is 2.1.2; version code is 11.

## Validation

The complete local build passed `testDebugUnitTest`, `lintDebug`, `lintRelease`
and `assembleRelease` with Java 21, SDK 35 and build tools 36.0.0. There were
98 tests, with no failures, errors or skipped cases. Debug lint had zero errors
and 126 warnings; release lint had zero errors and 125 warnings.

The native renders in `phase06/screenshots` were inspected. The before render
shows the API 28 wash; after renders cover English/Persian on API 28 and API 36.
These are regression fixtures. The final physical check is installation and
the same settings/Back path on the user's phone.

The installation candidate retains `com.couchzap.iptv` and the existing release
certificate `539f14a4b71cc830108cffefa8778ec06797be539c90ab80bc0c89b0c60eb187`.
Its local v1/v2 signatures and APK alignment are verified before delivery.

The submitted diagnostic report's `UnknownHostException` is a channel host
lookup failure. It does not identify a rendering failure. The published 2.1.0
release and its release recipe remain historical; this is a testing candidate.
