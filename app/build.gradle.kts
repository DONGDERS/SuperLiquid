plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.liuran001.mmliquidglass"
    compileSdk = 37
    compileSdkMinor = 2
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dongder.super.liquid"
        minSdk = 33
        targetSdk = 37
        versionCode = 21
        versionName = "0.4.4"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    // Release stays unsigned here: scripts/package.sh injects META-INF/xposed
    // into the APK root first, zipaligns, then signs with apksigner. Signing
    // before the injection would invalidate the v2 signature.
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/**"
            excludes += "META-INF/services/**"
            excludes += "**.properties"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // libxposed API: provided by the LSPosed loader at hook time.
    compileOnly(files("libs/xposed-api-102.jar"))
    // Module-process bridge to LSPosed service (RemotePreferences for the UI).
    implementation("io.github.libxposed:service:102.0.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3:1.5.0-alpha28")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.compose.material:material-icons-core")

    // HyperOS-style skin (the other half of the Mat/Miuix dual theme).
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    implementation("com.materialkolor:material-kolor:5.0.1")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
}
