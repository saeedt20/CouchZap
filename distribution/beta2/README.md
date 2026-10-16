# Reviewed Beta 2 publication data

The signed APK is reconstructed from the successful unsigned build in Actions run
38942517488 and public APK ZIP/signature data. This uses the repository's existing
`scripts/assemble-reviewed-release.py` verification process. The binary data was
extracted from the signed, publicly distributable APK; it contains no private
signing key, signing password, or signing configuration.

The recipe pins the unsigned input hash and the exact signed output hash. The
publication job checks the source commit, immutable release tag, successful build,
identical application payload, and established signing certificate before upload.

Release source: `c8eb91d5fe581f1988516a68d59425d780302c0f` (`v3.0-beta.2`).
Signed APK SHA-256: `e931079d8f5f72cf1a98c78ef9d1eaa2345d1a923daa79090fd689e6385e5e93`.

This separate publication branch lets GitHub Actions upload the already signed
APK using its runtime token. It changes neither application source nor the stable
2.2.1 release recipe.
