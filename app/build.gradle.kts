plugins {
    id("com.android.application")
}
android {
    namespace = "com.cyber90s.dual"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.cyber90s.dual"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "Beta 4"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
}
