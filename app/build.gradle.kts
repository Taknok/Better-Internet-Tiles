plugins {
    id("com.android.application")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {

    enableKotlin = true
    namespace = "be.casperverswijvelt.unifiedinternetqs"
    compileSdk = 37

    defaultConfig {
        applicationId = "be.casperverswijvelt.unifiedinternetqs"
        minSdk = 31
        targetSdk = 37
        versionCode = 3010200
        versionName = project.findProperty("versionName")?.toString() ?: "3.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            // This matches the previous ext.enableCrashlytics = false
            extra.set("enableCrashlytics", false)
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
    
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    val kotlin_version: String by rootProject.extra

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Compose UI
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.animation:animation:1.12.0")
    implementation("androidx.compose.ui:ui-tooling:1.12.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.navigation:navigation-compose:2.10.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // SU
    implementation("com.github.topjohnwu.libsu:core:5.0.3")

    // Shizuku
    val shizuku_version = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizuku_version")
    implementation("dev.rikka.shizuku:provider:$shizuku_version")

    // FreeDroidWarn
    implementation("com.github.woheller69:FreeDroidWarn:V1.+")
}

configurations.configureEach {
    exclude(group = "androidx.lifecycle", module = "lifecycle-viewmodel-ktx")
}
