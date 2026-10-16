# Continuation status

The original handoff was completed: the native redesign was committed and published on `codex/native-design`, then released with Claude as **3.0 Beta**. The stable release remains **2.2.1**.

The interrupted suite was rerun independently on 16 October 2026: **156 tests passed, zero failures/errors/skips**. The [portable result](verification/final-tests-summary.json) records the tested source commit. The beta correction aligns versionName/versionCode with the existing APK and enables branch CI, including app-payload comparison.

See the [implementation report](IMPLEMENTATION.md), [beta release notes](../releases/v3.0-beta.md), [design contract](SPEC.md) and [native previews](captures/).

Historical absolute workspace paths are not required after cloning. Use the pinned Gradle 9.3.1 wrapper, JDK 21 and Android SDK 35/build-tools 36.0.0.

Physical remote, live SurfaceView/blur, HDR/decoder and frame-time checks remain unverified. Private signing material stays outside the repository. This correction does not replace the signed APK, merge to main or promote the beta to stable.
