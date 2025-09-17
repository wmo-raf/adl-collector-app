plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.climtech.adlcollector"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.climtech.adlcollector"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["appAuthRedirectScheme"] = "com.climtech.adlcollector"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    // Align Kotlin toolchain with Java 17 to avoid mismatch errors
    kotlin { jvmToolchain(17) }

    buildFeatures { compose = true }
}

dependencies {
    // ── Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ── Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)

    // ── Navigation
    implementation(libs.androidx.navigation.compose)

    // ── Hilt (KSP)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Prefer maintained compose integration:
    implementation(libs.androidx.hilt.navigation.compose)
    // Hilt + WorkManager
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // ── WorkManager & App Startup
    implementation(libs.androidx.work.ktx)
    implementation(libs.androidx.startup.runtime)

    // ── Room (KSP)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ── Networking
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.core)
    ksp(libs.moshi.kotlin.codegen)

    // ── Firebase (BOM-managed)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)

    // ── Other
    implementation(libs.openid.appauth)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // ── Desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // ── Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Compose test artifacts should also use the BOM
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug-only tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

hilt {
    // Keeps incremental builds fast with KSP
    enableAggregatingTask = false
}
