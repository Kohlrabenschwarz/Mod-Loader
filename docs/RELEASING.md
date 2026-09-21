# Releasing Mod Loader

This guide is for maintainers preparing an official GitHub release. The public APK must come from the exact source commit that receives the release tag, and every Android update must be signed by the same maintainer key.

## Protect the signing identity

The private PKCS12/JKS file and its passwords must remain outside this repository and outside GitHub. Keep an encrypted offline backup: losing the key means existing installations cannot receive a normal in-place update.

The build reads signing details only from the current process environment:

- `MODLOADER_KEYSTORE` — absolute path to the PKCS12/JKS file
- `MODLOADER_STORE_PASSWORD` — keystore password
- `MODLOADER_KEY_ALIAS` — signing alias
- `MODLOADER_KEY_PASSWORD` — key password

Do not put these values in Gradle files, scripts, release notes, GitHub Actions variables, or committed property files.

## Prepare the version

1. Set `versionName` and increase `versionCode` in `app/build.gradle.kts`.
2. Keep the Shizuku user-service version in step with implementation changes that require service recreation.
3. Update README version references, `VALIDATION.md`, credits, third-party notices, and release notes.
4. Confirm that localized UI resources have matching keys and localized agreements have the same structure as the English source.
5. Confirm that the Git worktree contains no secret, private payload, or machine-specific file.

## Build and verify

With the four signing variables set, run:

```sh
./gradlew test :app:lintDebug :app:assembleRelease :engine:assembleDebugAndroidTest
```

The signed APK is written to `app/build/outputs/apk/release/app-release.apk`. If signing variables are absent, Gradle may produce an unsigned release for inspection. Never publish that file as the official APK.

Before publishing, verify the APK with `apksigner`, check alignment with `zipalign`, inspect the packaged version and debuggable state, and calculate SHA-256 from the final signed bytes. When a test device is available, install the exact APK and exercise agreement display, language switching, import, activation, deactivation, SHA warnings, recovery, update discovery, and Shizuku disconnects. Record anything that could not be tested.

## Assemble the GitHub release

Use a stable tag in the form `vMAJOR.MINOR.PATCH`. The update checker expects the APK asset to be named exactly:

```text
Mod-Loader-vMAJOR.MINOR.PATCH-release.apk
```

Attach the signed APK, its `.sha256` file, `generic-mod-template.zip`, and `THIRD_PARTY_NOTICES.md`. Paste the matching note from `docs/releases/` into the GitHub Release description. GitHub automatically supplies source archives for the tag. Mark the release as latest and stable; drafts and prereleases are intentionally ignored by installed clients.

## Publication order

1. Finish and review the source.
2. Run validation.
3. Commit coherent changes with short explanatory messages.
4. Push the source commits.
5. Tag the exact release commit and push the tag.
6. Create the GitHub Release and upload the verified assets.
7. Compare the uploaded APK checksum with the local checksum.
8. Confirm that the in-app update checker sees the published release.

Version 1.0.0 uses application ID `dev.kohlrabenschwarz.ml`, version code 14, and the Kohlrabenschwarz signing certificate. Releases before 0.9.0 used a different application ID and cannot update in place.
