plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.compose) // For Jetpack Compose compiler
    alias(libs.plugins.hilt)                     // Hilt for DI
    alias(libs.plugins.kotlin.serialization)     // Kotlinx Serialization for JSON
    alias(libs.plugins.ksp)                      // KSP for annotation processing
}

android {
    namespace = "com.jamie.blinkchat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jamie.blinkchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.jamie.blinkchat.HiltTestRunner" // Will create this custom runner for Hilt
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Set to true for actual release builds
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            // To easily distinguish debug builds if installed alongside release builds
            applicationIdSuffix = ".debug"
            // MamespaceSuffix = ".debug" // for newer AGP
        }
    }
    compileOptions {
        // Your Java 11 settings are fine. For broader compatibility, some projects stick to 1.8.
        // As long as Kotlin's jvmTarget matches, it's generally okay.
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11" // Matches your compileOptions
    }
    buildFeatures {
        compose = true
    }

    packaging { // Renamed from packagingOptions to packaging in newer AGP
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    // Kotlin & Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // AndroidX Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Compose (using BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.multidex)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Hilt (Dependency Injection)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler) // Use ksp for Hilt's annotation processor
    implementation(libs.hilt.navigation.compose)

    // Networking (Retrofit & OkHttp)
    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor) // For debugging network calls

    // JSON Parsing (Kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization.converter)

    // Local Database (Room)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler) // Use ksp for Room's annotation processor
    implementation(libs.androidx.room.ktx) // For coroutine support

    // Preferences (Jetpack DataStore & EncryptedSharedPreferences)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Image Loading (Coil)
    implementation(libs.coil.compose)

    // Logging (Timber)
    implementation(libs.timber)

    implementation(libs.androidx.work.runtime.ktx) // androidx.work:work-runtime-ktx
    // For Hilt integration with WorkManager (optional but recommended for injecting dependencies into Workers)
    implementation(libs.androidx.hilt.work)
    // Annotation processor for Hilt-WorkManager integration
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.compose.material)
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.turbine) // For testing Kotlin Flows
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.hilt.android.testing) // For Hilt in unit tests
    kspTest(libs.hilt.compiler) // For Hilt in unit tests if using KSP
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.hilt.android.testing) // For Hilt in instrumented tests
    kspAndroidTest(libs.hilt.compiler) // For Hilt in instrumented tests if using KSP
}