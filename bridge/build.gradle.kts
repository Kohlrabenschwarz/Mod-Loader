plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "dev.modloader.bridge"
    compileSdk = 35
    defaultConfig { minSdk = 30 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    api(project(":engine"))
    implementation("dev.rikka.shizuku:api:13.1.5")
    api("dev.rikka.shizuku:provider:13.1.5") // app manifest'i provider sınıfını doğrudan kullanır.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
