# Security Policy

Mod Loader handles untrusted ZIP files and performs privileged writes to a fixed game-data directory. Security reports are taken seriously, especially when a problem could escape that directory, replace the wrong file, bypass user confirmation, expose signing material, or prevent reliable recovery.

## Supported version

Security fixes are made on the current `main` branch and shipped in the newest GitHub Release. Older APKs are not supported after a fixed release is available.

## Reporting a vulnerability

Do not publish exploit details, malicious mod archives, signing material, device identifiers, or private logs in a public issue. Contact the maintainer through the private contact method listed on the [Kohlrabenschwarz GitHub profile](https://github.com/Kohlrabenschwarz). Include the affected version, Android version, Shizuku backend, reproduction steps, and the smallest safe test case you can provide.

The maintainer should acknowledge a report before requesting sensitive artifacts. Never send the release keystore or its passwords.

If a public issue is sufficient, ordinary crashes, translation mistakes, and compatibility problems may be reported there without private device data or copyrighted game files.

## Trust boundaries

- Mod ZIPs and every field in `info.json` are untrusted. The creator field is a label, not an identity proof or signature.
- The loader accepts payloads only for the fixed Shadow Fight Arena `Bundles/` tree. Traversal, absolute paths, symlinks, special files, hidden targets, reserved `mods/` targets, Unicode control/format characters, case collisions, unsupported compression, and configured size or ratio violations are rejected.
- Every payload file must begin with the `UnityFS` magic bytes. Filename extensions are not trusted or required.
- Imported data may still be malformed for Unity or the game. The loader cannot establish that arbitrary game data is safe to parse. Users must review the target list and explicitly confirm activation.
- The Shizuku user service accepts calls only from the owning application UID, only on Android's primary user, and only when running as ADB shell UID 2000. Root/Sui mode is intentionally rejected.
- The application does not execute commands from mod metadata or filenames. The only privileged process command has fixed arguments and stops the fixed game package.
- Updates are discovered through a fixed HTTPS GitHub API endpoint and open only the canonical release asset URL. Android's package installer must verify that an update has the same application ID and signing certificate.

## Remaining limitations

File replacement is durable and atomic per file, with verified backups and recovery journals. It is not atomic across a whole multi-file mod. A process with independent shell-level access can still attempt a parent-directory replacement race during the small gap between `lstat` validation and path-based file operations. Final file opens reject symlinks, but fully addressing parent-directory races requires a directory-file-descriptor backend using native `openat` operations.

Backups are stored in the game's external data directory and are not encrypted. Clearing the game's data, uninstall behavior on some devices, another shell-level tool, or manual deletion can remove them. Keep original mod archives separately.

## Release hygiene

- Keep the PKCS#12 release key and all passwords outside the repository.
- Increase `versionCode` for every APK update and sign it with the established release certificate.
- Verify `gradle/wrapper/gradle-wrapper.jar` and the pinned distribution checksum before release.
- Review Dependabot pull requests monthly and update only after the complete verification suite passes.
- Run JVM tests, Android lint, release assembly, and isolated Android instrumentation tests on a disposable device.
- Publish the exact tested signed APK and record its SHA-256 in the release notes.
