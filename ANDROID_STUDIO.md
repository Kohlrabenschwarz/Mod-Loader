# Android Studio and UI editing

You do not need to learn the storage engine before changing spacing, colors, text, or card layout. This guide points to the Compose files that control the visible interface and shows how to preview changes without connecting Shizuku.

## Open the project

1. Clone the repository or extract its source archive. In Android Studio choose **Open** and select the directory containing `settings.gradle.kts` and `gradlew.bat`, not only the `app` directory.
2. Let Gradle Sync finish. The first sync needs network access to fetch dependencies.
3. Install **Android SDK Platform 35** in SDK Manager. Select **JDK 17 or 21** under **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK**. Use the included Gradle Wrapper.
4. Select the **debug** app build variant. Resolve build errors before refreshing previews.

## View the UI

Open `app/src/main/kotlin/dev/modloader/app/LoaderPreviews.kt` and select **Split** in the editor to show code and preview together, or **Design** for the preview only. These controls may appear as icons depending on Android Studio version. Click **Build & Refresh** if necessary.

Preview examples include light and dark screens, a narrow German layout with larger text, SHA warnings, and settings in all six languages. **Interactive Mode** can exercise preview UI interactions. Import and Play do not access Android storage or Shizuku in previews; mock data is used. Previews are not a substitute for testing the actual app.

This is a Jetpack Compose application: edit the Kotlin composables and inspect the result in Preview. It does not use the XML drag-and-drop layout editor. See the [official Preview guide](https://developer.android.com/develop/ui/compose/tooling/previews).

## Editing map

| File under `app/src/main/` | Purpose |
| --- | --- |
| `kotlin/dev/modloader/app/LoaderScreen.kt` | Header, mod cards, warning dialogs, buttons, settings |
| `kotlin/dev/modloader/app/LoaderTheme.kt` | Light/dark themes and six accent palettes |
| `kotlin/dev/modloader/app/LoaderPreviews.kt` | Mock preview data, dimensions, languages, font scales |
| `kotlin/dev/modloader/app/UiText.kt` | Language selection and localized resource lookup |
| `kotlin/dev/modloader/app/MainActivity.kt` | Activity, Android ZIP picker, launch intent, callbacks |
| `kotlin/dev/modloader/app/LoaderViewModel.kt` | State, operations, persistence and app checksum |
| `kotlin/dev/modloader/app/UserAgreement.kt` | Mobile agreement layout, language menu, acceptance controls |
| `kotlin/dev/modloader/app/AgreementDocument.kt` | Small Markdown parser used by the bundled agreement |
| `res/values/strings.xml` | Default English UI strings |
| `res/values-{tr,hi,zh,ru,de}/strings.xml` | Translated UI strings |
| `assets/legal/TERMS.md` | Complete English agreement shown inside the app |
| `assets/legal/terms/TERMS_*.md` | Complete localized agreements |
| `res/drawable-nodpi/mod_loader_art.png` | Maintainer-supplied launcher artwork |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive launcher icon composition |

For example, edit `Modifier.size(64.dp)` in `ModRow` to adjust card icon size, `padding(14.dp)` for internal spacing, or the theme color definitions to change accent colors. Keep format placeholders such as `%1$d` and `%1$s` consistent across translations. Imported mod descriptions are creator content and are not automatically translated.

After changing the agreement, increment its acceptance-policy version only when users truly need to review the new legal terms again. Keep the repository `TERMS.md` and embedded English asset byte-for-byte identical.

## Run and build

Select the **app** run configuration and a connected device, then press **Run**. The UI can open without Shizuku; actual mod operations require the supported Shizuku setup and installed game data. Use an isolated test device for privileged file tests.

```sh
./gradlew :domain:test :app:assembleDebug :app:lintDebug
./gradlew :engine:assembleDebugAndroidTest
```

Use `gradlew.bat` on Windows. See [release signing](docs/RELEASING.md) before distributing an APK. The official signing key is deliberately not part of this repository.
