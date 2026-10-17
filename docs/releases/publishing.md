# Publishing the reviewed release APK

The Android release key stays outside GitHub. A clean Actions build produces an unsigned APK; the maintainer signs and verifies an APK privately with the existing CouchZap release key.

For the 2.1.x releases, publication preserved the exact APK tested by the user on Android 16. The independently checked CI build has the same application payload; the local and CI VCS metadata may differ. `verify-reviewed-release.py` compares every uncompressed entry, excluding only signing records and `META-INF/version-control-info.textproto`. Code, resources, manifest and assets must match.

The repository stores public reconstruction data and a recipe bound to the unsigned and signed SHA-256 checksums. The data contains the signature, ZIP structure and reviewed VCS metadata differences; it cannot sign a different APK and contains no private key or password. The release job retrieves that specific checked build, reconstructs the reviewed signed APK and verifies its checksum. It also compares the payload with the current main build before attaching the APK to the GitHub Release.

The legacy 2.x automatic publication workflow requires a fresh Android TV 7 emulator flow check of the same signed bytes. Normal developer builds still use the `COUCHZAP_*` environment variables documented in the README. Future releases need a fresh reviewed build and public reconstruction data generated with the same private key. Existing published assets are never overwritten by this workflow.

## Stable 3.1.0 publication

3.1.0 is accepted from corrected application commit `443aecdbb1f817993f6aa866f678916e245ea4c2`. Claude builds/signs privately; Codex independently verifies the returned binary, updates documentation and publishes. See the [acceptance record](v3.1.0-verification.md) for the exact APK/certificate hashes, 213-test results, 682-entry payload comparisons and physical-device checks marked not run.

The fresh recipe and public reconstruction data are versioned in `distribution/3.1.0/`. They are bound to successful [CI run 38980113816](https://github.com/saeedt20/CouchZap/actions/runs/38980113816) and the accepted corrected signed APK. The root `distribution/release-recipe.json` remains a historical 2.2.1 recipe; it intentionally does not match the current app version and cannot publish 3.1.0 through the old main auto-release path.

After final main Android checks pass, tag that main commit `v3.1.0` and manually dispatch `publish-3.1.0.yml` on main with its successful checks-run ID. The workflow checks source ancestry and the documentation/publication-only diff, verifies both build-run identities, reconstructs the exact signed bytes, compares them against the final main build, checks the certificate/version/alignment, and uploads to a draft. It verifies both uploaded asset digests before publishing explicitly as **non-prerelease / Latest**. It refuses to overwrite an existing public release. No private key is accessed by this workflow.

After publication, re-download the public APK and checksum, verify the exact bytes and certificate, and check `releases/latest` plus the updater's expected asset URLs. Keep old tags/releases/assets unchanged. This process does not claim a new Android TV emulator or physical-device flow run for 3.1.0.

## Preparing a new version with the legacy automatic flow

1. Update `versionName` and increment `versionCode` in `app/build.gradle.kts`; update README, CHANGELOG and `docs/releases/v<version>.md`.
2. Run the Android checks. The unsigned CI artifact is named `couchzap-<version>-unsigned`; it is not an installable release asset.
3. Sign the reviewed APK privately with the existing release key. Verify the package, version, signer certificate and ZIP alignment, then compare the app payload with the successful independent CI build using `scripts/verify-reviewed-release.py`.
4. Prepare fresh public reconstruction data and `distribution/release-recipe.json` bound to the new APK checksums, successful build run and exact source commit. Add the signed APK checksum file. The recipe for an older version cannot sign or publish a new build.
5. Merge the verified recipe. On main, the workflow only runs the signed Android TV 7 publication check when the recipe version matches the app version. It reconstructs and checks the exact signed bytes, exercises welcome/language selection, adjacent country-row remote navigation, public import, sound, diagnostics and update UI, then publishes the signed APK and checksum.

Source-only preparation can merge while signing is pending. When the app version differs from the existing recipe, normal tests, lint and unsigned assembly still run; signed publication waits for a matching reviewed recipe. The currently published signed release stays available until the new signed build is ready. Never substitute a debug key or a newly generated key for the existing release key.
