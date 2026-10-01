plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "dev.modloader.engine"
    compileSdk = 35
    defaultConfig { minSdk = 30; consumerProguardFiles("consumer-rules.pro"); testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    buildFeatures { aidl = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(project(":domain"))
    implementation("androidx.annotation:annotation:1.11.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
