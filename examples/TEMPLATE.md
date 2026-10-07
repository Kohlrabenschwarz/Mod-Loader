# Generic mod ZIP template

This template is the quickest way to learn the package format without using real game content. Import it as-is first: if its card appears with the example metadata, your Shizuku-independent import path and ZIP parser are working.

Import `generic-mod-template.zip` with the **+** button. The ZIP root contains `info.json`, `icon.png`, and a `payload/` directory with two randomly named **extensionless** files.

The payload contains minimal `UnityFS`-header fixtures, not functional Unity AssetBundles. Use it to check import, icons, descriptions, manifest parsing, and header validation. Do not activate the fixtures against a real game; replace them with compatible files you are allowed to use.

## Create a mod

1. Replace `payload/` files with your compatible mod files, preserving their names. Do not add a `.bundle` extension unless the real target filename has one.
2. List every payload path relative to `payload/` in `affectedFiles`. It must exactly match the actual file set.
3. Edit `name`, `creator`, `description`, `version`, and the optional `icon` field.
4. ZIP `info.json`, the icon, and `payload/` directly at the archive root, without an extra enclosing directory.

```json
{
  "schemaVersion": 1,
  "name": "Example Mod",
  "version": "1.0.0",
  "creator": "Your name",
  "description": "Describe the mod and compatible game version.",
  "icon": "icon.png",
  "affectedFiles": ["5a9e13d8bc7844df970f612ea7d306c2"]
}
```

`payload/<name>` maps to `files/gamedata/Resources/Bundles/<name>` inside the fixed game's external data directory. Metadata and icons are not copied there.

Every payload file must begin with the seven ASCII bytes `UnityFS` (`55 6E 69 74 79 46 53`). This check is based on file content, so extensionless randomized filenames remain supported. The header check rejects obviously unrelated files; it does not prove that a bundle is safe, complete, or compatible with the installed game version.

The manifest is required and limited to 32 KiB. Name and creator are limited to 100 characters each, description to 2,000, and version to 40. Schema version 1 is supported. Optional root icons may be `icon.png`, `icon.jpg`, or `icon.webp`, at most 4 MiB and 2048 × 2048 pixels.

Alternative payload prefixes are `Resources/Bundles/`, `files/gamedata/Resources/Bundles/`, `com.nekki.shadowfightarena/files/gamedata/Resources/Bundles/`, or `Android/data/com.nekki.shadowfightarena/files/gamedata/Resources/Bundles/`. Metadata still belongs at the ZIP root. Paths outside Bundles, into its reserved `mods/` subdirectory, traversal, symlinks, and collisions are rejected.

Successful import confirms archive/path integrity, not Unity or game-version compatibility. Keep your own original copies and share only material you have the rights to distribute.

If the loader rejects a package, start by comparing its archive root and `affectedFiles` list with this template. Do not remove validation checks to accommodate a malformed ZIP; correct the package instead.

## Optional online updates

Add a root `update.json`, a stable `modId`, and an increasing `versionCode` to support manual update checks. See [Mod updates](../docs/MOD_UPDATES.md) for the package/remote manifest format, SHA-256 validation, publication helper, and inactive replacement behavior. Packages without update metadata remain supported. Loader versions predating this feature reject `update.json`.

## Large texture bundle limits

Payload files may be up to **512 MiB each**, with a **1 GiB ZIP** and **2 GiB expanded payload** limit. These limits accommodate large uncompressed textures such as RGBA32 without loading the entire bundle into memory. ZIP64 and the 200:1 compression-ratio limit remain unchanged. Activation shows an estimate of additional free space; original backups and rollback may require substantially more space than the ZIP alone.
