# Validation — v1.0.0

Validated on 21 September 2026. Application ID: `dev.kohlrabenschwarz.ml`. Version name: `1.0.0`; version code and Shizuku user-service version: `14`.

## Build and signing

- JDK 21, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.4.20, R8 9.1.29, compile/target SDK 35, minimum API 30.
- The minified, non-debuggable release was signed with the maintainer-provided PKCS12 key, alias `sign`. The key and passwords remain outside the repository and APK.
- APK Signature Scheme v2 verification succeeded with one signer. ZIP alignment verification succeeded.
- Signing certificate SHA-256: `c458512cf92fc4fd9e10c71af75ada3d997894d55e5d88a9845810e3cd434055`.
- Release APK SHA-256: `f844dd87765085ae1e1eede83e09a07e3bf34f2ec1b0f782814396c26b233a79`.
- Release APK size: 4,630,517 bytes.
- The packaged manifest reports application ID `dev.kohlrabenschwarz.ml`, version `1.0.0` / code `14`, minimum API 30, target API 35, cleartext disabled, backup disabled, and no debuggable flag.
- The APK contains GPLv3, NOTICE, credits, user terms, and third-party notices under `assets/legal/`.

## Automated verification

- 32 distinct JVM tests passed.
- 36 Android instrumentation tests passed on Android 14/API 34.
- Android lint completed with 0 errors and 9 non-blocking warnings.
- Debug assembly and the minified signed release assembly succeeded.
- All six locale files contain the same 100 string-resource keys.
- The 94 release runtime Maven coordinates in the dependency inventory returned no known vulnerabilities in the OSV batch query during the security review.
- Repository and history scans found no committed private key, signing password, or access token.

Tests cover ZIP traversal and ambiguity, entry collisions, compression bombs, CRC corruption, encrypted/ZIP64/symlink entries, Unicode path and display spoofing, `UnityFS` payload headers, metadata matching, fixed target boundaries, no-follow file operations, backup integrity, SHA mismatch handling, crash recovery, activation conflicts, update URL validation, and agreement-version acceptance.

## Android 14 release smoke test

The signed v1.0.0 release candidate installed and launched on the Android 14 emulator. The versioned agreement blocked the application before Shizuku initialization, required explicit acknowledgement, and then allowed the app to reach a connected Shizuku state. The application correctly reported that game data was missing.

The release rejected `run-as`, confirming that the package is not debuggable. A signed v0.9.0 APK with version code 13 was installed and then updated in place to the signed v1.0.0 APK with version code 14. Android accepted the update, and the new agreement appeared after the upgrade.

The final release defaults new installations to English and presents the complete bundled agreement in a mobile layout with a language menu. The English document is byte-for-byte identical to the repository `TERMS.md`; German, Hindi, Russian, Turkish, and Simplified Chinese documents preserve the same eight sections, eleven list items, and two Nekki links. Agreement version 2 requires existing users to review the complete text again. Document parsing, language mapping, the English default, and preference normalization are covered by JVM tests. The final minified APK compiled and passed signature verification. The command-line Android 14 emulator did not restart for a second UI smoke run.

Shadow Fight Arena was not installed. Real game injection, recovery against actual game updates, gameplay compatibility, and OEM-specific external-storage behavior remain unverified.

## Security and legal review

The application confines operations to the fixed Shadow Fight Arena Bundles tree, validates Binder callers, accepts only the Shizuku shell backend, uses bounded streaming I/O, rejects unsafe archive paths and entries, requires `UnityFS` payload magic, preserves verified originals, and uses recovery journals. Users must review the complete affected-file list before activation.

The versioned user agreement explains Nekki's modification rules, account-penalty risk, untrusted third-party content, prohibited misuse, backup limitations, lack of affiliation, GPLv3 boundaries, and liability limits. Acceptance is stored locally and can be reviewed in Settings.

The launcher artwork remains the main legal distribution risk. It was supplied by the maintainer and described as an edit of legacy Shadow Fight artwork. Credits identify the underlying rights holders, exclude the artwork from the GPL grant, and state that attribution is not redistribution permission. Legal review or replacement with original artwork is recommended before broad distribution.

## Remaining technical limits

File replacement is atomic per file, not across an entire multi-file mod. Journals resume interrupted work. Final file opens reject symbolic links and verify regular files, but a separate shell-level attacker may still attempt a parent-directory replacement race; fully closing that gap requires a directory-FD/native `openat` backend.

Backups are unencrypted in the game's external data directory and may be removed by game-data clearing, uninstall behavior, storage failure, manual deletion, or another shell-level tool. A `UnityFS` header confirms only the magic bytes and does not establish bundle safety, authenticity, or compatibility.
