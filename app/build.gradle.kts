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
        minSdk = 26
        targetSdk = 37
        versionCode = 18
        versionName = "0.4.1"
    }

    // Release stays unsigned here: scripts/package.sh injects META-INF/xposed
    // into the APK root first, zipaligns, then signs with apksigner. Signing
    // before the injection would invalidate the v2 signature.
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            vcsInfo.include = false
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
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.core:core:1.13.1")

    // HyperOS-style skin (the other half of the Mat/Miuix dual theme).
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
}
