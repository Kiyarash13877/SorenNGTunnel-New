plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
android {
    namespace   = "com.soreng.tunnel"
    compileSdk  = 34
    defaultConfig {
        applicationId = "com.soreng.tunnel"
        minSdk = 26; targetSdk = 34
        versionCode = 1; versionName = "1.0.0"
        ndk { abiFilters += listOf("arm64-v8a","armeabi-v7a","x86_64") }
        externalNativeBuild {
            cmake { cppFlags += "-std=c++17"; arguments("-DANDROID_STL=c++_shared") }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true; isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
        }
        debug { isDebuggable = true }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }; jniLibs { useLegacyPackaging = true } }
    externalNativeBuild { cmake { path = file("src/main/jni/CMakeLists.txt"); version = "3.22.1" } }
    splits { abi { isEnable = true; reset(); include("arm64-v8a","armeabi-v7a","x86_64"); isUniversalApk = true } }
    lint { abortOnError = false }
}
dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime); implementation(libs.lifecycle.vm.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui); implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview); implementation(libs.material3)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android); ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
    implementation(libs.datastore.prefs); implementation(libs.security.crypto)
    implementation(libs.coroutines.android)
    implementation(libs.gson); implementation(libs.okhttp)
    implementation(libs.zxing.core); implementation(libs.mlkit.barcode)
    implementation(libs.camerax.core); implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle); implementation(libs.camerax.view)
    implementation(libs.work.runtime)
    implementation(libs.accompanist.permissions)
    debugImplementation(libs.compose.ui.tooling)
}
