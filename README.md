# Mod Loader

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/mod_loader_art.png" alt="Mod Loader artwork" width="180" />
</p>

Mod Loader is an Android mod library built specifically for **Shadow Fight 4: Arena**. It gives people a clear way to import, inspect, activate, deactivate, back up, and recover Unity bundle replacements without manually navigating Android’s restricted storage folders.

The app is written in Kotlin with Jetpack Compose and uses a Shizuku user service for file access on Android 11 and newer. It works with the game’s extensionless UnityFS bundle files and keeps each imported mod, its state, integrity records, and recovery data together.

[Download the latest release](https://github.com/Kohlrabenschwarz/Mod-Loader/releases/latest) · [Read the user agreement](TERMS.md) · [Create a mod package](examples/TEMPLATE.md) · [Open in Android Studio](ANDROID_STUDIO.md)

**Looking for more help?**

[![Discord](https://img.shields.io/badge/Discord-join-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/dUcABJtzpJ)

## Please read this before using the app

Mod Loader is an independent file-management tool. It is not affiliated with, approved by, sponsored by, or supported by Nekki Limited or Banzai Games.

Nekki’s rules prohibit modifications to Shadow Fight 4: Arena, including cosmetic skin and sound changes. Using any mod may result in a warning, suspension, loss of account access, loss of purchases or progress, or another penalty. Enforcement decisions belong entirely to Nekki. A low estimated risk is not permission and is not a guarantee that an account will remain unaffected.

Mod ZIPs are supplied by users and third parties. The app performs strict archive, path, size, checksum, and `UnityFS` header checks, but those checks cannot prove that a file is safe, lawful, authentic, compatible, or free of malicious content. You are responsible for the files you choose, the rights to use them, independent backups, and the consequences of modifying game data.

Read the complete [User Agreement and Risk Notice](TERMS.md). The same agreement is included in the app in English, Turkish, German, Hindi, Russian, and Simplified Chinese.

## What the app can do

- **Search your library.** Search names, creators, descriptions, versions, folders, and affected filenames. Search ignores letter case and accents, including Turkish I variants. Clear the search to return to the full library. Imported mod count has no fixed cap; available storage and device resources determine practical capacity. Large library responses use a file-descriptor stream rather than a single Binder string.
- **Keep a visual mod library.** Imported mods appear as cards with an icon, name, creator, version, description, affected-file count, and active state.
- **Import from a file or link.** Tap **+** to open two round options. Select a local ZIP or enter a direct public HTTPS ZIP URL. Both paths validate the package and ask for approval when a name or mod ID matches an existing card.
- **Replace an imported package with approval.** Choose which matching card to overwrite. A different package restores the old active mod first, preserves the card identity and previous stored archive, and leaves the replacement inactive.
- **Prepare releases in Dev Mode.** Settings accepts a publication root URL. Each stored mod gets `.dev/latest.json`, ZIP size/SHA-256 values, per-file checksums, and metadata templates. Separate mod-ID publication directories prevent manifests from colliding. Incomplete metadata produces a draft; see the [developer/test guide](docs/MOD_UPDATE_TEST_TR.md).
- **Import extensionless Unity bundles.** Payload filenames are preserved exactly. A `.bundle` extension is neither required nor added.
- **Show every target before activation.** The confirmation screen lists the complete set of files a mod will affect.
- **Activate and deactivate safely.** Original game files are backed up before replacement. Files introduced by a mod are removed when that mod is disabled.
- **Recover interrupted work.** Transaction journals allow the engine to continue recovery after a disconnect, crash, or process restart.
- **Inspect outside changes.** SHA-256 records track both the original and modified versions of every affected file. Warnings list changed paths, expected/current hashes, and the recorded backup date when available.
- **Offer deliberate warning actions.** From the warning panel you can recover saved base files, delete the stored mod and its backups while leaving current game files alone, or hide the warning without disabling integrity checks.
- **Explain active-mod conflicts.** Activation lists the conflicting mod names and shared files before making changes. Two active mods cannot manage the same destination file at the same time.
- **Launch the game.** The Play button stops the existing game process through the supported Shizuku service when available, then opens the game using Android’s normal launcher intent.
- **Check for app updates.** Stable GitHub releases are checked at startup. Downloads open in the browser and installation remains under the user’s control.
- **Update individual mods.** Packages with optional `update.json` metadata offer manual update checks. New ZIPs must match the manifest's SHA-256, size, identity and version. Active mods are restored first; replacements keep the same card and stay inactive, with the prior archive and backups retained. See [mod updates](docs/MOD_UPDATES.md).
- **Adapt to the user.** The interface supports English, Turkish, German, Hindi, Russian, and Simplified Chinese, plus light/dark mode and six accent colors.

## Requirements

- Android 11/API 30 or newer on the primary Android user.
- [Shizuku](https://shizuku.rikka.app/) running through ADB or wireless debugging. You can also use our [Modded Shizuku](https://github.com/Kohlrabenschwarz/Shizuku) when it provides the supported ADB shell backend; root/Sui mode remains unsupported.
- Shadow Fight 4: Arena installed and opened at least once so its game data can be downloaded.
- Enough free space for the imported ZIP, staging files, original backups, and recovery copies.

This release intentionally rejects root/Sui mode and work-profile users. Shizuku access can also vary between Android versions and device manufacturers.

The game target is fixed and cannot be changed from mod metadata:

```text
Package: com.nekki.shadowfightarena
/storage/emulated/0/Android/data/com.nekki.shadowfightarena/files/gamedata/Resources/Bundles/
```

Mod Loader does not patch the game APK, inject native code into the game process, execute commands supplied by mods, bypass anti-cheat, or certify a bundle as compatible with a particular game version.

## Installing and using Mod Loader

1. Download the latest  APK from GitHub Release.
2. Verify the SHA-256 checksum shown in that release if you want to confirm the download.
3. Install and start Shizuku, then open Mod Loader. The app attempts to connect up to three times and shows a green status card when it is ready. If Shizuku starts later, the app reconnects automatically; a failed connection also offers a retry action. Permission denials and unsupported backends have separate explanations.
4. Read the agreement. You can change its language before accepting it.
5. Tap the round **+** button and choose **Add from file** or **Add from link**. Links must download a public HTTPS ZIP directly.
6. Review the mod card and affected-file list. Enable the switch only when you trust the source and understand the targets.
7. Use the **Play** button to restart and open the game after changing mods.

Activation estimates the additional free space needed for staging, verified originals, and rollback. Progress names each processing phase and shows copied bytes where available. A damaged cached ZIP gets its own card and can be restored from a verified stored archive or imported again; other readable mods stay visible. Corrupt privileged state blocks changes until repaired, preserving its backups.

Tap a mod card to expand its information and mod update controls. Hold the card to open recovery and deletion actions. Settings contains language, appearance, app update, Dev Mode, and agreement options.

If the game is missing or has not created its data directory yet, install it, open it, finish its data download, and try again. A red Shizuku card means the privileged connection or permission is unavailable.


## How storage, backups, and recovery work

On the first successful privileged connection, the app creates a reserved `mods/` directory inside the game’s Bundles directory:

```text
Bundles/
├── <game files and active mod payloads>
└── mods/
    └── <mod-name>/
        ├── <mod-name>.zip
        ├── state.json
        ├── sha.json
        ├── .dev/                 # optional publishing files, outside the ZIP
        ├── previous/             # one prior stored version after replacement
        └── backup/
            └── <transaction-id>/
                ├── journal.json
                ├── old/
                ├── before-recovery/
                └── recovery-sha.json
```

Activation validates the archive again, stages the payload, saves and verifies existing destination files, and replaces each destination using an atomic rename. Deactivation restores overwritten originals and removes files that did not exist before the mod. Ordinary deletion first restores an active mod, then removes its archive and backups.

Replacement is atomic **per file**, not across an entire multi-file mod. Journals make interrupted operations recoverable, but no backup system can guarantee recovery from every storage failure or outside change. Backups are not encrypted and live in the game’s external data directory. Clearing game data, uninstall behavior, manual deletion, another shell-level tool, a game update, or storage failure may remove or invalidate them. Keep your own copies of important files.

### Understanding SHA warnings

Each `sha.json` stores `originalSha256` and `modifiedSha256` for every managed file. A null original value means the file did not exist before activation. Active destinations are checked against the mod version; inactive destinations are checked against the saved original.

When a mismatch is found, the app leaves the current files alone and shows a warning:

- **Recover base files** restores verified originals. Because those backups may predate a game update, the current files are preserved under `before-recovery/` first.
- **Delete** removes the archived mod and its backups while leaving the game’s current files unchanged.
- **Ignore this warning** hides only the currently observed set of changes. A different hash, missing file, or additional changed file produces a new warning even after choosing not to show the old one again. Integrity checking continues, and holding the card opens the warning again.

Editing `sha.json` does not grant permission to replace arbitrary files. The transaction journal and fixed target policy remain authoritative.

## Mod ZIP format

A minimal package looks like this:

```text
my-mod.zip
├── info.json
├── icon.png               # Optional
└── payload/
    ├── 5a9e13d8bc7844df970f612ea7d306c2
    └── b074ce9261ad4e8ab2597380cfed615a
```

Every payload file must begin with the seven ASCII bytes `UnityFS` (`55 6E 69 74 79 46 53`). `affectedFiles` in `info.json` must exactly match the payload. Metadata and icons are displayed in the library and are never copied into the game’s bundle directory.

The loader rejects traversal, absolute paths, symlinks, special files, hidden or reserved targets, Unicode control and bidirectional-formatting characters, normalization ambiguity, case collisions, encrypted entries, ZIP64 archives, compression bombs, and mismatched manifests. Limits are 1 GiB for the ZIP, 512 MiB per payload file, 2 GiB expanded total, 200 entries, a 200:1 compression ratio, a 4 MiB icon, and 2048 × 2048 icon dimensions.

These checks protect the loader’s file boundary. A valid `UnityFS` header does not prove that Unity can safely parse the bundle or that it matches the installed game version.

Start with the [generic test ZIP](examples/generic-mod-template.zip) and read the [complete package guide](examples/TEMPLATE.md). Its payload files are small test fixtures, not playable bundles.

## Updates and privacy

The app checks the public GitHub API for the latest stable release from `Kohlrabenschwarz/Mod-Loader`. It accepts only tags shaped like `vMAJOR.MINOR.PATCH` with an asset named `Mod-Loader-vMAJOR.MINOR.PATCH-release.apk`. Drafts, prereleases, malformed versions, and external asset links are ignored.

The request contains no mod content, game files, device identifiers, accounts, analytics, or telemetry. GitHub still receives ordinary connection information such as the requesting IP address. Update-check failures never block local mod management. The app does not request silent-install permission.

Individual mod updates contact the public HTTPS source specified in that mod's `update.json` only when requested from its card. That source receives ordinary connection information, including the IP address. SHA-256 checks the ZIP against its manifest; signed publisher authentication is not included. Read the [mod update guide](docs/MOD_UPDATES.md) for the format and replacement/recovery behavior.

The header shows the application name, installed version, and SHA-256 of the installed base APK. This checksum identifies the APK bytes; it is different from the signing-certificate fingerprint.

## Project layout

| Module | What lives there |
| --- | --- |
| `domain` | Fixed target definitions, ZIP preflight and parsing, models, repository contracts, and JVM tests |
| `engine` | Shizuku user service, safe file operations, archives, SHA state, backups, journals, and recovery |
| `bridge` | Shizuku lifecycle, permission state, Binder transport, and coroutine/Flow adapters |
| `app` | Compose UI, imports, preferences, localization, user agreement, update checks, and game launch |

The privileged service authenticates its Binder caller, requires the owning app UID, accepts only shell UID 2000, and restricts work to the fixed package and primary user. Existing path components are checked with `lstat`; final opens reject symbolic links and require regular files. See [SECURITY.md](SECURITY.md) for the trust model and remaining race limitation.

## Building the project

Use JDK 17 or 21 and Android SDK Platform 35. Open the repository root in Android Studio and use the checked-in Gradle wrapper.

```sh
./gradlew :domain:test :app:assembleDebug :app:lintDebug
./gradlew :engine:connectedDebugAndroidTest :app:connectedDebugAndroidTest
./gradlew :engine:pixel2Api30DebugAndroidTest :app:pixel2Api30DebugAndroidTest
./gradlew :engine:pixel2Api35DebugAndroidTest :app:pixel2Api35DebugAndroidTest
```

On Windows, use `gradlew.bat`. The CI workflow runs isolated engine, library, and UI instrumentation tests on Android 11/API 30 and Android 15/API 35. The fixtures operate in test-owned directories and do not require the game or a live Shizuku service. Real Shizuku and game integration still require separate device validation. The release signing key and passwords are deliberately kept outside the repository.

Helpful guides:

- [Android Studio and Compose previews](ANDROID_STUDIO.md)
- [Contributing](CONTRIBUTING.md)
- [Creating a release](docs/RELEASING.md)
- [Security policy](SECURITY.md)

## License and acknowledgements

Original project code and documentation are available under [GNU GPL version 3](LICENSE), `GPL-3.0-only`, Copyright © 2026 Kohlrabenschwarz and contributors. GPLv3 permits commercial use and requires covered distributions to follow its corresponding-source terms. Third-party software keeps its original license.

The launcher artwork has separate rights and is excluded from the GPL grant. It is a maintainer-supplied edit of legacy Shadow Fight artwork with an “ML” overlay. Attribution does not establish redistribution permission. Read [CREDITS.md](CREDITS.md) before redistributing the application.

Thanks to RikkaApps and Shizuku contributors, Google and the Android Open Source Project, JetBrains and Kotlin contributors, Gradle contributors, and the maintainers of the libraries listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

**Code changes and this README were prepared with OpenAI Codex (GPT-6 Astra, GPT-5.6 SOL).**
