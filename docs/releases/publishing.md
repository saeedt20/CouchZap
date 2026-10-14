# Publishing the reviewed release APK

The Android release key stays outside GitHub. A clean Actions build produces an unsigned APK; the maintainer signs and verifies an APK privately with the existing CouchZap release key.

For 2.1.2, publication preserves the exact APK already tested by the user on Android 16. The independently checked CI build has the same application payload; the local and CI VCS metadata may differ. `verify-reviewed-release.py` compares every uncompressed entry, excluding only signing records and `META-INF/version-control-info.textproto`. Code, resources, manifest and assets must match.

The repository stores public reconstruction data and a recipe bound to the unsigned and signed SHA-256 checksums. The data contains the signature, ZIP structure and reviewed VCS metadata differences; it cannot sign a different APK and contains no private key or password. The release job retrieves that specific checked build, reconstructs the reviewed signed APK and verifies its checksum. It also compares the payload with the current main build before attaching the APK to the GitHub Release.

Publication requires a fresh Android TV 7 flow check of the same signed bytes. Normal developer builds still use the `COUCHZAP_*` environment variables documented in the README. Future releases need a fresh reviewed build and public reconstruction data generated with the same private key. Existing published assets are never overwritten by this workflow.
