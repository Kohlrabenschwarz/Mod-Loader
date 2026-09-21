# Credits

Mod Loader exists because it builds on years of open-source Android work. This page records the people and projects that made the application possible and explains which visual material is not covered by the project’s GPL license.

## Project

Maintained by [Kohlrabenschwarz](https://github.com/Kohlrabenschwarz).

Thank you to everyone who reports problems, tests recovery behavior, improves translations, or contributes code and documentation.

**Code changes and this README were prepared with OpenAI Codex (GPT-6 Astra).**

## Software and tools

- [Shizuku API](https://github.com/RikkaApps/Shizuku-API): RikkaApps and contributors. Privileged Binder API and provider; licensed under MIT. The separately installed Shizuku application is not bundled.
- [AndroidX / Jetpack](https://android.googlesource.com/platform/frameworks/support/): Google, the Android Open Source Project, and contributors. Compose, Material 3, lifecycle, activity integration, and supporting libraries; Apache-2.0 except separately identified upstream material.
- [Kotlin](https://github.com/JetBrains/kotlin) and [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines): JetBrains and contributors; Apache-2.0.
- [Gradle](https://github.com/gradle/gradle): Gradle contributors; Apache-2.0. Wrapper notices are preserved.
- Android build tools and the Android SDK: Google and the Android Open Source Project, under their respective terms; not redistributed in this repository.
- JUnit, AndroidX Test, and related test dependencies retain their own licenses. They are development/test tools, not part of the release application's runtime distribution.

The resolved release dependency inventory and notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and `licenses/third-party/`. Original notices extracted from resolved library artifacts are retained there and included in the APK's assets.

## Artwork and game identity

The launcher image was supplied by the maintainer. According to the maintainer, it is an edit of legacy Shadow Fight game artwork with an **ML** overlay. The original individual artist has not been identified. Credit for the underlying Shadow Fight game identity/artwork belongs to the respective game rights holders, including [Nekki](https://nekki.com/); this project does not claim authorship or ownership of that underlying artwork.

`app/src/main/res/drawable-nodpi/mod_loader_art.png` is excluded from the project's GPLv3 code license. Attribution is not a sublicense or proof of a redistribution grant. No independent permission to reuse the underlying game artwork or trademarks is granted by this repository. This is an unofficial community tool and is not affiliated with or endorsed by the game's creators.

The generic template contains project-generated `UnityFS`-header dummy fixtures and a simple template icon, not game assets. User-supplied real mod packages and proprietary game bundles are not included.
