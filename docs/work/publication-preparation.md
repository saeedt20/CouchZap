# CouchZap 2.1.0 — publication preparation

Prepared on 2026-10-14 following the request to publish the four-stage implementation.

## Reviewable contents

- Source changes from stages 1–4: remote focus/playback status, unified retained settings and Persian/English localization, welcome/public source selection and ordering, panel polish/interruptible intro/optional CRT click.
- Application version 2.1.0, versionCode 9, package com.couchzap.iptv, minimum API 24 and target API 35.
- README updated with English/Persian native-view screenshots and current features/limitations.
- Release notes in `docs/releases/v2.1.0.md`.
- Locally optimized and signed `CouchZap-2.1.0.apk`, 2,723,392 bytes.
- APK SHA-256: `f7c89ede0bd6b5efffb6c459378b6ac8dfe4175dad04e2c17bc3a6c5849330e9`.
- Certificate SHA-256 matches the existing release and updater pin: `539f14a4b71cc830108cffefa8778ec06797be539c90ab80bc0c89b0c60eb187`.
- `apksigner verify` succeeds with v2 signing; `zipalign` and APK package/version/min/target checks succeed.
- Private keys and passwords remain outside the repository.

## Status

The intended destination is the existing public repository https://github.com/saeedt20/CouchZap, its main branch and a new v2.1.0 GitHub Release with APK and SHA-256 assets. Existing releases would be retained.

Publication is pending. GitHub's main branch still points to `1f70cb990261cbd97ab8878802ce3439c704084e`; no new branch/tag/release was published by this request. GitHub CI and a signed Android TV 7 emulator test for 2.1.0 have not run yet. The versioned APK linked for review was built locally; it is not a GitHub CI artifact.

Debug/release lint and optimized release build succeed with no errors. The final test results are appended below. The update-download regression fixture uses a deliberately future version so it continues to test an available update after the application version bump.

After publication authorization, upload the source to the existing repository, run checks, obtain and privately sign the exact CI-produced APK, bind public reconstruction data to its checksums, run the signed Android TV 7 check, update the release workflow to v2.1.0, publish, then download and verify the exact public APK. The final CI-produced APK may have a different file hash from the local review APK; record its exact hash with its published asset.

Final local validation: **89 tests, 0 failures, 0 errors, 0 skips**. Debug/release lint have 0 errors and 124 warnings each.

## GitHub publication validation

The complete source snapshot was pushed as `3ce326237a1f42bdfe2c2d1ff9b1baeb6dbb84fc`. [GitHub checks and optimized assembly](https://github.com/saeedt20/CouchZap/actions/runs/38592458257) succeeded. Its exact unsigned APK was downloaded, its artifact digest verified, and it was signed privately with the original release key.

The release APK is 2,775,731 bytes. Its SHA-256 is `7ecd4779bcd6a3547a675de33ac894ba363b7631a8cea86bc4ac1600d936514f`. Both v1 and v2 signatures verify, and the release certificate matches the updater pin. The public recipe reconstructs the exact signed bytes from the checked GitHub build. The earlier local APK hash above describes the review artifact, not this final CI APK.

The [signed Android TV 7 flow check](https://github.com/saeedt20/CouchZap/actions/runs/38610290571) and [Android 7 network integration check](https://github.com/saeedt20/CouchZap/actions/runs/38610290636) were started. The signed-APK flow check succeeded. Publication requires both checks to succeed and leaves older release assets unchanged.
