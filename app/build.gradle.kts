plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.backmeupplz.meshtasticplus"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.backmeupplz.meshtasticplus"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.2"
    }
    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug") // personal sideload build; use a real key for Play
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
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0") // QR scanning, no camera permission
    testImplementation("junit:junit:4.13.2")
}
