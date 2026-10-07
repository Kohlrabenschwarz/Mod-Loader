plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "dev.modloader.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.kohlrabenschwarz.ml"
        minSdk = 30
        targetSdk = 35
        versionCode = 17
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    testOptions {
        managedDevices {
            devices {
                create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel2Api30") {
                    device = "Pixel 2"; apiLevel = 30; systemImageSource = "aosp"
                }
                create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel2Api35") {
                    device = "Pixel 2"; apiLevel = 35; systemImageSource = "aosp"
                }
            }
        }
    }
    // Keep all in-app languages available offline in App Bundle installations.
    bundle { language { enableSplit = false } }
    val releaseStore = System.getenv("MODLOADER_KEYSTORE")
    signingConfigs {
        if (!releaseStore.isNullOrBlank()) create("maintainerRelease") {
            storeFile = file(releaseStore)
            storePassword = requireNotNull(System.getenv("MODLOADER_STORE_PASSWORD"))
            keyAlias = requireNotNull(System.getenv("MODLOADER_KEY_ALIAS"))
            keyPassword = requireNotNull(System.getenv("MODLOADER_KEY_PASSWORD"))
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            if (!releaseStore.isNullOrBlank()) signingConfig = signingConfigs.getByName("maintainerRelease")
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    implementation(project(":bridge"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
