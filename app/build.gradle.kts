import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.backmeupplz.meshtasticplus"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.borodutch.meshplus"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.1"
    }
    buildFeatures { compose = true }
    // play: Google's QR scanner (no camera permission). fdroid: open-source ZXing scanner, no Google code.
    flavorDimensions += "store"
    productFlavors {
        create("play") { dimension = "store" }
        create("fdroid") { dimension = "store" }
    }
    // Upload key for Play (and the APKs on meshplus.app); kept outside the repo. Without it (e.g. on F-Droid's build server), release builds are unsigned.
    val upload = Properties().apply {
        val f = file("${System.getProperty("user.home")}/.android/meshplus-upload.properties")
        if (f.exists()) f.inputStream().use(::load)
    }
    signingConfigs {
        if (upload.isNotEmpty()) create("upload") {
            storeFile = file(upload.getProperty("storeFile"))
            storePassword = upload.getProperty("storePassword")
            keyAlias = upload.getProperty("keyAlias")
            keyPassword = upload.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("upload")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended") // R8 strips unused icons
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("com.github.mik3y:usb-serial-for-android:3.11.0")
    implementation("no.nordicsemi.android:dfu:2.11.0") // firmware updates over Bluetooth (legacy nRF52 DFU)
    implementation("com.google.zxing:core:3.5.4") // QR codes for room invites
    "playImplementation"("com.google.android.gms:play-services-code-scanner:16.1.0") // QR scanning, no camera permission
    "fdroidImplementation"("com.journeyapps:zxing-android-embedded:4.3.0")
    testImplementation("junit:junit:4.13.2")
}
