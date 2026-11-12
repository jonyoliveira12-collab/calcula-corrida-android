plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.calculacorrida"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.calculacorrida"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes.getByName("debug") {
        // Evaluation installs alongside older builds signed with a different key.
        applicationIdSuffix = ".avaliacao050"
    }

    // Signing material is injected from private Actions secrets, never from source control.
    val signingPath = providers.environmentVariable("JHONDRIVER_KEYSTORE_PATH").orNull
    val signingPassword = providers.environmentVariable("JHONDRIVER_KEYSTORE_PASSWORD").orNull
    val signingAlias = providers.environmentVariable("JHONDRIVER_KEY_ALIAS").orNull
    val signingKeyPassword = providers.environmentVariable("JHONDRIVER_KEY_PASSWORD").orNull
    if (!signingPath.isNullOrBlank()) {
        require(!signingPassword.isNullOrBlank() && !signingAlias.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()) {
            "Missing private signing configuration"
        }
        signingConfigs.create("privateRelease") {
            storeFile = file(signingPath)
            storePassword = signingPassword
            keyAlias = signingAlias
            keyPassword = signingKeyPassword
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("privateRelease")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
