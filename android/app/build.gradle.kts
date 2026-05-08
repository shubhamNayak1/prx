plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.baseras.fieldpharma"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.baseras.fieldpharma"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // For sideloaded demo APKs only — NOT for the Play Store.
        //
        // CI builds (GitHub Actions): keystore + passwords come from env vars
        //   (workflow generates a fresh keystore each run).
        // Local builds: falls back to Android Studio's auto-generated debug keystore
        //   at ~/.android/debug.keystore.
        create("demo") {
            val envKeystore = System.getenv("KEYSTORE_PATH")
            if (envKeystore != null && file(envKeystore).exists()) {
                storeFile = file(envKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "demo123"
                keyAlias = System.getenv("KEY_ALIAS") ?: "demo"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "demo123"
            } else {
                storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // Local backend via emulator-loopback. For LAN device dev, override with
            // your machine IP (e.g. http://192.168.1.42:4000)
            buildConfigField("String", "API_URL", "\"http://10.0.2.2:4000\"")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("demo")
            // Render-deployed backend (HTTPS required for release builds).
            buildConfigField("String", "API_URL", "\"https://fieldpharma-api.onrender.com\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.play.services.location)
    implementation(libs.coil.compose)
}
