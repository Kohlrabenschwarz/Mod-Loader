# Generic mod ZIP template

Import `generic-mod-template.zip` with the **+** button. The ZIP root contains `info.json`, `icon.png`, and a `payload/` directory with two randomly named **extensionless** files.

The payload is dummy test data, not Unity AssetBundles. Use it to check import, icons, descriptions, and manifest parsing. Replace the dummy payload before testing in a real game.

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

The manifest is required and limited to 32 KiB. Name and creator are limited to 100 characters each, description to 2,000, and version to 40. Schema version 1 is supported. Optional root icons may be `icon.png`, `icon.jpg`, or `icon.webp`, at most 4 MiB and 2048 × 2048 pixels.

Alternative payload prefixes are `Resources/Bundles/`, `files/gamedata/Resources/Bundles/`, `com.nekki.shadowfightarena/files/gamedata/Resources/Bundles/`, or `Android/data/com.nekki.shadowfightarena/files/gamedata/Resources/Bundles/`. Metadata still belongs at the ZIP root. Paths outside Bundles, into its reserved `mods/` subdirectory, traversal, symlinks, and collisions are rejected.

Successful import confirms archive/path integrity, not Unity or game-version compatibility. Keep your own original copies and share only material you have the rights to distribute.
