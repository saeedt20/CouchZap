# Publishing the reviewed release APK

The Android release key stays outside GitHub. A clean Actions build produces an unsigned APK; the maintainer signs and verifies an APK privately with the existing CouchZap release key.

For the 2.1.x releases, publication preserved the exact APK tested by the user on Android 16. The independently checked CI build has the same application payload; the local and CI VCS metadata may differ. `verify-reviewed-release.py` compares every uncompressed entry, excluding only signing records and `META-INF/version-control-info.textproto`. Code, resources, manifest and assets must match.

The repository stores public reconstruction data and a recipe bound to the unsigned and signed SHA-256 checksums. The data contains the signature, ZIP structure and reviewed VCS metadata differences; it cannot sign a different APK and contains no private key or password. The release job retrieves that specific checked build, reconstructs the reviewed signed APK and verifies its checksum. It also compares the payload with the current main build before attaching the APK to the GitHub Release.

Publication requires a fresh Android TV 7 flow check of the same signed bytes. Normal developer builds still use the `COUCHZAP_*` environment variables documented in the README. Future releases need a fresh reviewed build and public reconstruction data generated with the same private key. Existing published assets are never overwritten by this workflow.

## Preparing a new version

1. Update `versionName` and increment `versionCode` in `app/build.gradle.kts`; update README, CHANGELOG and `docs/releases/v<version>.md`.
2. Run the Android checks. The unsigned CI artifact is named `couchzap-<version>-unsigned`; it is not an installable release asset.
3. Sign the reviewed APK privately with the existing release key. Verify the package, version, signer certificate and ZIP alignment, then compare the app payload with the successful independent CI build using `scripts/verify-reviewed-release.py`.
4. Prepare fresh public reconstruction data and `distribution/release-recipe.json` bound to the new APK checksums, successful build run and exact source commit. Add the signed APK checksum file. The recipe for an older version cannot sign or publish a new build.
5. Merge the verified recipe. On main, the workflow only runs the signed Android TV 7 publication check when the recipe version matches the app version. It reconstructs and checks the exact signed bytes, exercises welcome/language selection, public import, sound, diagnostics and update UI, then publishes the signed APK and checksum.

Source-only preparation can merge while signing is pending. When the app version differs from the existing recipe, normal tests, lint and unsigned assembly still run; signed publication waits for a matching reviewed recipe. The currently published signed release stays available until the new signed build is ready. Never substitute a debug key or a newly generated key for the existing release key.
