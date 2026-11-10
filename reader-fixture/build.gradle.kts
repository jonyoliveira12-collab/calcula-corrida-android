// CI-only synthetic driver window. Never distributed with JHONDRIVER.
plugins { id("com.android.application") }
android {
    namespace = "br.com.calculacorrida.fixture"
    compileSdk = 35
    defaultConfig { applicationId = "com.ubercab.driver"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "fixture" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
