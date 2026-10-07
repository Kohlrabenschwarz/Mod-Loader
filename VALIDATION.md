# Mod Loader 1.1.0 validation

Verified on 2026-10-07. Version name is **1.1.0**, version code **17**, production application ID **dev.kohlrabenschwarz.ml**, minimum Android API **30**, target/compile API **35**.

## Automated checks

| Suite | Passed | Skipped | Failed |
| --- | ---: | ---: | ---: |
| Domain JVM tests | 37 | 1 | 0 |
| App JVM tests | 21 | 0 | 0 |
| Engine instrumentation, Android 15/API 35 | 55 | 0 | 0 |
| App library/UI/network instrumentation, Android 15/API 35 | 17 | 0 | 0 |
| Total | 130 | 1 | 0 |

The optional external-archive JVM test was skipped because no private fixture was supplied. Live public manifest, verified ZIP download, and direct ZIP-link import probes ran successfully against a maintainer-controlled Cloudflare test host.

Coverage includes active-mod restoration, removed targets, hash/metadata/source mismatches, stale overwrite previews, same-version approved imports, idempotent retries, cancellation/staging cleanup, bounded HTTP responses, every durable directory-exchange boundary, Dev Mode hashes/templates, unsafe sidecar symlinks, more than 100 stored mods, search/conflict behavior, and import-menu/link-confirmation UI.

All six UI language files have the same **149 string keys**. Popup menu, link dialog, developer settings, and overwrite-dialog screenshots were reviewed. Android release lint reports **0 errors and 8 existing warnings**. Signed minified release, debug, and instrumentation APK builds succeeded with JDK 21 and the repository Gradle wrapper.

## Official APK checks

- Release is not debuggable.
- APK Signature Scheme v2 verification passed.
- ZIP/16 KiB page alignment verification passed.
- Signing certificate SHA-256 matches the actual published 1.0.0 APK: `c458512cf92fc4fd9e10c71af75ada3d997894d55e5d88a9845810e3cd434055`.
- The exact signed 1.0.0 APK was installed on the isolated emulator, then updated in place with the signed 1.1.0 APK. Android accepted the update and the production Activity launched successfully.
- Final APK SHA-256: `9ba0dd4db79a76330941eb4b6bb902cf11a7c6623f46908a2c2e1573370eb732`.

## Limits of this verification

Privileged engine tests use isolated synthetic storage, not a real game installation. Full minified-release Shizuku/game activation and recovery were not exercised against actual game data in the final validation. Android 11/API 30 and Android 15/API 35 device jobs are configured in GitHub Actions; their hosted results are separate from the local checks above.

The repository was private during release preparation. Its anonymous GitHub latest-release endpoint returned 404, so the in-app public GitHub update checker cannot discover a private release. Publishing visibility was not changed.
