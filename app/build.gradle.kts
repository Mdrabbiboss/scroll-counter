plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rabbi.scrollcounter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rabbi.scrollcounter"
        minSdk = 26
        targetSdk = 35
        versionCode = 75
        versionName = "7.8"
        // Phones only (ARM). Emulator-only x86 builds of the pose-detection library are left out.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Store native libraries compressed inside the APK (much smaller download).
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    // Release signing comes from environment variables (set by GitHub Actions secrets or your PC).
    val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")
    signingConfigs {
        // Fixed key for test builds, so every new test APK installs as an update over the previous one.
        // (Not for Play Store: use your own upload key via the SIGNING_* variables for that.)
        create("testing") {
            storeFile = file("test-signing.jks")
            storePassword = "scrolltest"
            keyAlias = "scrolltest"
            keyPassword = "scrolltest"
        }
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("testing")
        }
        release {
            signingConfig = signingConfigs.getByName(if (keystorePath != null) "release" else "testing")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Installs the Compose baseline profiles so the app starts faster
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // AdMob + Google consent (GDPR / UMP)
    implementation("com.google.android.gms:play-services-ads:23.3.0")
    // Block Store: keeps the reel history on the phone even if the app is uninstalled
    implementation("com.google.android.gms:play-services-auth-blockstore:16.4.0")
    implementation("com.google.android.ump:user-messaging-platform:3.0.0")

    // "10 squats to unlock": camera + on-device pose detection (no internet, nothing stored)
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
}
