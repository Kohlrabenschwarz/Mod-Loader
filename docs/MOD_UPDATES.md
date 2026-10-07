# Mod updates

Mod updates are checked only when the user taps **Check mod update** on a card. The app fetches a small manifest from that mod's source. It does not contact every imported mod's server at startup. A check sends ordinary HTTP connection information, including the IP address, to the configured host. No game files, mod contents, identifiers, credentials, or telemetry are included in the request.

## Package format

Existing packages without update metadata continue to work. For an updatable package, add a fixed `modId` and increasing integer `versionCode` to the existing root `info.json`:

```json
{
  "schemaVersion": 1,
  "name": "Example Mod",
  "creator": "Author",
  "description": "Compatible game version and description.",
  "version": "1.0.0",
  "modId": "com.example.red-skin",
  "versionCode": 1,
  "affectedFiles": ["texture"]
}
```

Add a root `update.json`:

```json
{
  "schemaVersion": 1,
  "modId": "com.example.red-skin",
  "manifestUrl": "https://example.com/red-skin/latest.json"
}
```

`modId` must match `info.json`, use lowercase ASCII letters/numbers separated by `.`, `_`, or `-`, and contain 3–120 characters. `versionCode` is an integer from 1 to 2147483647. Each new release must increase it. Display versions may keep existing naming conventions. Name and creator remain self-declared labels. Metadata is not copied into game files. Older loader APKs that do not support this feature reject ZIPs containing `update.json`.

## Public manifest

Publish the latest release description at `manifestUrl`:

```json
{
  "schemaVersion": 1,
  "modId": "com.example.red-skin",
  "version": "1.1.0",
  "versionCode": 2,
  "zipUrl": "https://example.com/red-skin/red-skin-1.1.0.zip",
  "zipSha256": "REPLACE_WITH_64_LOWERCASE_HEX_CHARACTERS",
  "zipSize": 12345,
  "changelog": "New textures."
}
```

The hash and byte length cover the **entire completed ZIP**, including metadata and icons. The displayed version, versionCode and modId must exactly match its `info.json`. Its `update.json` must retain the same source; silent source changes are rejected. The archive's own hash is kept outside the ZIP to avoid self-reference. JSON documents are limited to 32 KiB; optional changelog text is limited to 2000 characters and 20 line breaks.

Use direct, publicly accessible HTTPS file URLs on port 443. Browser landing pages, login-required downloads, HTTP, embedded credentials, fragments, and private/local endpoints are unsupported. Up to five redirects are checked individually. Content-encoded responses are rejected. DNS results are screened for non-public addresses, but the platform connection performs its own resolution; this is not a DNS rebinding-resistant network sandbox.

Publish a uniquely named ZIP first, then update the stable manifest. Avoid replacing a ZIP at a published version URL. SHA-256 verifies that the download matches the source manifest; it does not authenticate the publisher or defend against an attacker replacing both files on the server. Signed manifests are not implemented in this test build.

## Build a release on Windows

The included helper preserves ZIP payload paths, updates `info.json`, adds `update.json`, and calculates the final ZIP hash/size. It creates new output files and refuses to overwrite existing files. Package validity is still checked by the loader.

```powershell
.\tools\New-ModUpdatePackage.ps1 `
  -InputZip .\my-mod.zip -OutputZip .\publish\my-mod-1.1.0.zip `
  -ModId com.example.red-skin -Version 1.1.0 -VersionCode 2 `
  -ManifestUrl https://example.com/red-skin/latest.json `
  -ZipUrl https://example.com/red-skin/my-mod-1.1.0.zip `
  -ManifestOutput .\publish\latest.json -Changelog "New textures."
```

Generate/import versionCode 1 first with the same modId and manifestUrl. Generate versionCode 2, upload its ZIP, and put its generated manifest at the stable URL. Check from the version 1 card, review the download confirmation, and update. The tool handles package preparation only; it does not publish files or grant hosting permissions.

## Replacement and recovery

The app streams the download to a temporary file with bounded buffers and checks exact byte length and SHA-256. The privileged service checks the hash again, validates the ZIP, parses its metadata and stages its payload **before** disabling the old mod. Integrity mismatches, stale archive previews, unrelated identities, downgrade attempts and source changes block replacement.

An active mod is restored through its existing transaction journals, including paths removed from the new release. Only after restoration does a durable directory exchange journal authorize publication of the new inactive archive under the same local UUID and folder. Restarting the service completes interrupted exchanges. Hash-aware library synchronization repairs an outdated app-side ZIP after a lost reply or interrupted cache copy; an unresolved mismatch blocks activation.

One prior archive and its restored backups are retained at `Bundles/mods/<folder>/previous/`. Later updates retain only the immediately preceding version. This directory is archival data, not an automatically reactivated rollback version; reactivation requires normal import and review. Deleting the mod also deletes this retained history. New activation creates backups from the current original game files, never from the previous mod's payload.

The exchange is recoverable, but not a whole-mod filesystem transaction or a guarantee against storage failure. If a crash happens while restoring the old mod before exchange authorization, that restoration can finish while the old package stays installed and inactive. A damaged update journal blocks operations and preserves both versions for recovery.

## Test cases

1. Import a version 1 ZIP with `update.json`. Its metadata must display normally. An old ZIP without `update.json` should still import.
2. Publish version 2 and check the old card: a newer version and changelog should appear. Cancel must leave it unchanged.
3. Update an inactive mod: same card/UUID, new metadata, inactive state.
4. Update an active compatible mod: originals restored, removed targets cleared, new version inactive. Activate it manually and check deactivation restores originals.
5. Publish an incorrect hash or size: update rejected, old archive preserved. Repeat with an incorrect modId, version, or source URL.
6. Disconnect during download: retry should be possible and the old package must remain installed.
7. Automated engine tests exercise crashes before and after directory moves, retained backups, lost success replies and repeated updates in test-owned game fixtures.

The generic template contains dummy UnityFS-header fixtures. It is suitable for **import and update preparation checks only**; it is not a playable mod and must not be activated in the real game.
