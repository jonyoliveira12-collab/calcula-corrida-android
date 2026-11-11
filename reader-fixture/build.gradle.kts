// CI-only synthetic driver window. Never distributed with JHONDRIVER.
plugins { id("com.android.application") }
android {
    namespace = "br.com.calculacorrida.fixture"
    compileSdk = 35
    defaultConfig { applicationId = "com.ubercab.driver"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "fixture" }
    flavorDimensions += "driver"
    productFlavors {
        create("backdrop") { dimension = "driver"; applicationId = "br.com.calculacorrida.fixture.backdrop" }
        // This synthetic package is installed only on a disposable CI emulator.
        create("bank") { dimension = "driver"; applicationId = "com.nu.production" }
        create("uber") { dimension = "driver"; applicationId = "com.ubercab.driver" }
        create("ninetynine") { dimension = "driver"; applicationId = "com.taxis99" }
        create("indrive") { dimension = "driver"; applicationId = "sinet.startup.inDriver" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
