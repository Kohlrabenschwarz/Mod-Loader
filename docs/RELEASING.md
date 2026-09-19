# Building and publishing a release

The public APK is a minified, non-debuggable `release` build signed with a dedicated maintainer key. The private key is not committed or uploaded. Keep an encrypted offline backup of your own signing key and its passwords; Android updates require the same signing identity.

## Build locally

Set these environment variables for the Gradle process:

- `MODLOADER_KEYSTORE`: absolute path to your private PKCS12/JKS keystore.
- `MODLOADER_STORE_PASSWORD`: keystore password.
- `MODLOADER_KEY_ALIAS`: signing key alias.
- `MODLOADER_KEY_PASSWORD`: key password.

Then run:

```sh
./gradlew :domain:test :app:lintDebug :app:assembleRelease :engine:assembleDebugAndroidTest
```

The signed result is `app/build/outputs/apk/release/app-release.apk`. Without the signing variables, the release variant builds an unsigned APK for local inspection; do not upload that unsigned file as an installable release. Never place credentials in Gradle scripts or commit a properties file containing them.

Verify with the Android SDK's `apksigner verify --verbose --print-certs` and `zipalign -c -P 16 4`. Inspect the manifest version and verify `android:debuggable` is not enabled. Generate a SHA-256 checksum for the final signed APK, not its unsigned predecessor. Record the signing certificate fingerprint in the release notes.

## Publish

1. Update the app version code/name and Shizuku user-service version if its implementation changes.
2. Refresh credits, dependency notices, validation results, and release notes in English.
3. Run the checks above. Where available, install on an isolated device and test import, activation, deactivation, recovery, and Shizuku disconnects. State clearly when device testing was not performed.
4. Commit source with short messages explaining each coherent change. Tag the exact committed source.
5. Attach the signed release APK, SHA-256 checksum file, generic template, and `THIRD_PARTY_NOTICES.md` to the GitHub release. GitHub supplies source archives for the tag.
6. Publish without any signing keys, passwords, machine-specific files, caches, or user-supplied real mod archives.

Version 0.9.0 changes the application ID to `dev.kohlrabenschwarz.ml`. Earlier `dev.modloader.app` installations are separate applications, not in-place upgrade candidates. Preserve imports and complete/deactivate operations before switching loaders. The release is signed with the maintainer-provided Kohlrabenschwarz key; future releases must reuse that key and increase versionCode.

## Update discovery contract

Publish a stable GitHub Release with a canonical `vMAJOR.MINOR.PATCH` tag and a signed `Mod-Loader-vMAJOR.MINOR.PATCH-release.apk` asset. Mark that release latest. Drafts and prereleases are ignored. A git push alone does not notify installed clients; the published release is required. The app compares stable versions numerically, and Android checks the installed APK signature and version code during installation.
