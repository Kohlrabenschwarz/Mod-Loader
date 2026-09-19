# Validation — v0.9.0

Validated on 2026-09-20. Application ID: `dev.kohlrabenschwarz.ml`. Version name: `0.9.0`; version code and Shizuku user-service version: `13`.

## Build and signing

- JDK 21, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.1.20, compile/target SDK 35, minimum API 30.
- `:domain:test`, `:app:testDebugUnitTest`, `:app:assembleRelease`, `:app:lintDebug`, and `:engine:connectedDebugAndroidTest` succeeded.
- Minified, non-debuggable release signed with the maintainer-provided PKCS12 key, alias `sign`. The private key and passwords are not included in the repository or release assets.
- APK signature verified and signing certificate compared with the supplied keystore certificate. Package name, version, ZIP alignment, and embedded GPL/third-party notices verified.
- Certificate SHA-256: `c458512cf92fc4fd9e10c71af75ada3d997894d55e5d88a9845810e3cd434055`.
- Released APK SHA-256: `d2b62e2470d8d6ae1f32e35fd7d20e7147780a43ece3d54bf728a9fd46c3cd0d`.
- Lint: 0 errors, 11 warnings. Remaining warnings concern pinned dependency/SDK versions, icon/resource suggestions, backup compatibility, and a storage API suggestion.

## Tests

- 24 JVM tests passed: 17 archive/path tests, 3 numeric release-version tests, and 4 GitHub release-parser tests.
- One optional external real-archive test skipped because its fixture was not supplied for this run.
- **31 Android instrumentation tests passed on Android 14/API 34 (`sdk_gphone64_x86_64`)**. Fixtures exercise archive activation/deactivation, backup hashes, interrupted transactions, explicit recovery, discard behavior, and missing game data without creating game directories.
- Six UI languages with 80 matching string keys; XML and format placeholders checked.

## Device smoke test

The signed release APK installed, updated in place using the same certificate, and launched on the Android 14 emulator. The app process remained alive without a fatal crash recorded for its PID. The UI and APK checksum display were inspected. Shizuku reached the connected state.

Shadow Fight Arena was not installed. Real game bundle injection, recovery against actual game updates, gameplay compatibility, and OEM-specific external storage access were **not** tested. Missing game data is reported through a dedicated localized message.

An earlier Android 9/API 28 device was below the supported minimum. Instrumentation was skipped there and is not counted as a test execution or pass.

## Update checks and remaining limits

The checker accepts published stable numeric tags and the exact official repository APK URL; unit tests cover drafts, prereleases, malformed versions, and external/lookalike download hosts. Requests use HTTPS, bounded response size/timeouts, and no access token. Download and installation are handled by the browser and Android installer with user confirmation. Installing a future higher version through that full flow has not been exercised in this release test.

Atomicity is per file, not across an entire mod. Durable journals resume interrupted work when the privileged service becomes available. Concurrent malicious directory replacement remains outside the current lstat-based threat model. Passing isolated engine tests does not establish real-game or OEM/FUSE compatibility.
