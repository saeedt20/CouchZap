# Publishing the reviewed release APK

The Android release key stays outside GitHub. A clean Actions build produces an unsigned APK; the maintainer signs those exact bytes privately with the existing CouchZap release key and verifies them with Android's `apksigner`.

The repository stores public signature data and a recipe bound to the unsigned and signed SHA-256 checksums. The release job retrieves that specific checked build, reconstructs the reviewed signed APK and verifies its checksum before attaching it to the GitHub Release. The public data cannot sign a different APK and contains no private key or password.

Normal developer release builds still use the `COUCHZAP_*` environment variables documented in the README. Future releases need a fresh reviewed build and signature data generated with the same private key. Existing published assets are never overwritten by this workflow.
