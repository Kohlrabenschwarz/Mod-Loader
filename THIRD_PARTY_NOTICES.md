# Third-party notices

The project's GPLv3 license does not change the licenses of any third-party components. Those components can be used independently under their own terms. This inventory records the resolved **releaseRuntimeClasspath** for version 1.0.0, including platform/BOM metadata and transitive dependencies; not every coordinate contributes runtime bytecode.

## Attribution

- Shizuku API, AIDL, provider, and shared components: Copyright (c) Rikka and contributors; **MIT License**. The original MIT copyright and permission notice is preserved in `licenses/third-party/Shizuku-API-MIT.txt`.
- AndroidX, Compose, Material components, and Android supporting libraries: Google, Android Open Source Project, and contributors; upstream Apache-2.0 notices are preserved.
- Kotlin standard library, coroutines, serialization, and annotations: JetBrains and contributors; Apache-2.0 and retained upstream notices.
- Guava ListenableFuture: Google and contributors; Apache-2.0.
- JSpecify: JSpecify contributors; Apache-2.0.
- Gradle Wrapper: Gradle contributors; Apache-2.0, with upstream LICENSE included. The Gradle distribution and Android SDK are obtained separately by build tools.

The `upstream-*.txt` files preserve license/notice bytes extracted from the resolved JAR/AAR artifacts (including nested classes.jar). Identical notices are deduplicated by hash; `dependency-inventory.json` maps each dependency to its notice files and POM-declared license. Full texts and notices are included in the APK under `assets/legal/` so binary distributions retain them.

Development-only test tools include JUnit 4 (EPL-1.0), Hamcrest (BSD-3-Clause), and AndroidX Test (Apache-2.0). They are fetched by Gradle for testing and are not bundled in the release APK. Debug-only Compose tooling is not included in the release runtime inventory.

Game artwork and trademarks are separate from software dependency licenses; see [CREDITS.md](CREDITS.md). No game bundles or user mod archives are distributed.

## Resolved release dependencies

| Component | Declared license | Upstream |
| --- | --- | --- |
| `androidx.activity:activity-compose:1.10.1` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/activity#1.10.1) |
| `androidx.activity:activity-ktx:1.10.1` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/activity#1.10.1) |
| `androidx.activity:activity:1.10.1` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/activity#1.10.1) |
| `androidx.annotation:annotation-experimental:1.4.1` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/annotation#1.4.1) |
| `androidx.annotation:annotation-jvm:1.9.1` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/annotation#1.9.1) |
| `androidx.annotation:annotation:1.9.1` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/annotation#1.9.1) |
| `androidx.arch.core:core-common:2.2.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/arch-core#2.2.0) |
| `androidx.arch.core:core-runtime:2.2.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/arch-core#2.2.0) |
| `androidx.autofill:autofill:1.0.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx) |
| `androidx.collection:collection-jvm:1.5.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/collection#1.5.0) |
| `androidx.collection:collection-ktx:1.5.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/collection#1.5.0) |
| `androidx.collection:collection:1.5.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/collection#1.5.0) |
| `androidx.compose.animation:animation-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-animation#1.8.0) |
| `androidx.compose.animation:animation-core-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-animation#1.8.0) |
| `androidx.compose.animation:animation-core:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-animation#1.8.0) |
| `androidx.compose.animation:animation:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-animation#1.8.0) |
| `androidx.compose.foundation:foundation-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-foundation#1.8.0) |
| `androidx.compose.foundation:foundation-layout-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-foundation#1.8.0) |
| `androidx.compose.foundation:foundation-layout:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-foundation#1.8.0) |
| `androidx.compose.foundation:foundation:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-foundation#1.8.0) |
| `androidx.compose.material3:material3-android:1.3.2` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.3.2) |
| `androidx.compose.material3:material3:1.3.2` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.3.2) |
| `androidx.compose.material:material-icons-core-android:1.7.8` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-material#1.7.8) |
| `androidx.compose.material:material-icons-core:1.7.8` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-material#1.7.8) |
| `androidx.compose.material:material-ripple-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-material#1.8.0) |
| `androidx.compose.material:material-ripple:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-material#1.8.0) |
| `androidx.compose.runtime:runtime-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.8.0) |
| `androidx.compose.runtime:runtime-saveable-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.8.0) |
| `androidx.compose.runtime:runtime-saveable:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.8.0) |
| `androidx.compose.runtime:runtime:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.8.0) |
| `androidx.compose.ui:ui-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui-geometry-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui-geometry:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui-graphics-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui-graphics:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui-text-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui-text:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui-tooling-preview-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui-tooling-preview:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui-unit-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui-unit:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui-util-android:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui-util:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose.ui:ui:1.8.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.8.0) |
| `androidx.compose:compose-bom:2025.04.01` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack) |
| `androidx.concurrent:concurrent-futures:1.1.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/topic/libraries/architecture/index.html) |
| `androidx.core:core-ktx:1.13.1` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/core#1.13.1) |
| `androidx.core:core-viewtree:1.0.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/core#1.0.0) |
| `androidx.core:core:1.13.1` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/core#1.13.1) |
| `androidx.customview:customview-poolingcontainer:1.0.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/customview#1.0.0) |
| `androidx.emoji2:emoji2:1.4.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/emoji2#1.4.0) |
| `androidx.graphics:graphics-path:1.0.1` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/graphics#1.0.1) |
| `androidx.interpolator:interpolator:1.0.0` | The Apache Software License, Version 2.0 | [Project](http://developer.android.com/tools/extras/support-library.html) |
| `androidx.lifecycle:lifecycle-common-java8:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-common-jvm:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-common:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-livedata-core:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-process:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-runtime-android:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-runtime-compose:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-runtime-ktx:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-runtime:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.lifecycle:lifecycle-viewmodel:2.9.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0) |
| `androidx.profileinstaller:profileinstaller:1.4.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/profileinstaller#1.4.0) |
| `androidx.savedstate:savedstate-android:1.3.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/savedstate#1.3.0) |
| `androidx.savedstate:savedstate-ktx:1.3.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/savedstate#1.3.0) |
| `androidx.savedstate:savedstate:1.3.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/savedstate#1.3.0) |
| `androidx.startup:startup-runtime:1.1.1` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/startup#1.1.1) |
| `androidx.tracing:tracing:1.0.0` | The Apache Software License, Version 2.0 | [Project](https://developer.android.com/jetpack/androidx/releases/tracing#1.0.0) |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | The Apache Software License, Version 2.0 | [Project](http://developer.android.com/tools/extras/support-library.html) |
| `com.google.guava:listenablefuture:1.0` | Apache-2.0 (inherited from Guava parent) | See inventory |
| `dev.rikka.shizuku:aidl:13.1.5` | MIT License | [Project](https://github.com/RikkaApps/Shizuku-API) |
| `dev.rikka.shizuku:api:13.1.5` | MIT License | [Project](https://github.com/RikkaApps/Shizuku-API) |
| `dev.rikka.shizuku:provider:13.1.5` | MIT License | [Project](https://github.com/RikkaApps/Shizuku-API) |
| `dev.rikka.shizuku:shared:13.1.5` | MIT License | [Project](https://github.com/RikkaApps/Shizuku-API) |
| `org.jetbrains.kotlin:kotlin-stdlib-common:2.4.20` | The Apache License, Version 2.0 | [Project](https://kotlinlang.org/) |
| `org.jetbrains.kotlin:kotlin-stdlib:2.4.20` | The Apache License, Version 2.0 | [Project](https://kotlinlang.org/) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` | Apache-2.0 | [Project](https://github.com/Kotlin/kotlinx.coroutines) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.2` | Apache-2.0 | [Project](https://github.com/Kotlin/kotlinx.coroutines) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | Apache-2.0 | [Project](https://github.com/Kotlin/kotlinx.coroutines) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2` | Apache-2.0 | [Project](https://github.com/Kotlin/kotlinx.coroutines) |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom:1.7.3` | The Apache Software License, Version 2.0 | [Project](https://github.com/Kotlin/kotlinx.serialization) |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3` | The Apache Software License, Version 2.0 | [Project](https://github.com/Kotlin/kotlinx.serialization) |
| `org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3` | The Apache Software License, Version 2.0 | [Project](https://github.com/Kotlin/kotlinx.serialization) |
| `org.jetbrains:annotations:23.0.0` | The Apache Software License, Version 2.0 | [Project](https://github.com/JetBrains/java-annotations) |
| `org.jspecify:jspecify:1.0.0` | The Apache License, Version 2.0 | [Project](http://jspecify.org/) |
