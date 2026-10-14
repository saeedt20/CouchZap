plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.couchzap.iptv"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.couchzap.iptv"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "2.1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    val releaseKey = System.getenv("COUCHZAP_KEYSTORE")
    if (!releaseKey.isNullOrBlank()) {
        val releaseSigning = signingConfigs.create("couchzapRelease") {
            storeFile = file(releaseKey)
            storePassword = System.getenv("COUCHZAP_STORE_PASSWORD")
            keyAlias = System.getenv("COUCHZAP_KEY_ALIAS") ?: "couchzap"
            keyPassword = System.getenv("COUCHZAP_KEY_PASSWORD") ?: storePassword
        }
        buildTypes.getByName("release").signingConfig = releaseSigning
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures { viewBinding = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation("androidx.media3:media3-exoplayer-rtsp:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation("com.caverock:androidsvg-aar:1.4")
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
