import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// AdMob ad unit IDs, keyed by product flavor -- see admob/admob.properties.example for the
// expected keys. The real file is gitignored; falls back to Google's public test IDs if it's
// missing so a fresh checkout still builds (just shows test ads instead of configured ones).
val adMobProperties = Properties().apply {
    val file = rootProject.file("admob/admob.properties")
    if (file.exists()) load(FileInputStream(file))
}
fun adMobProperty(key: String): String = (adMobProperties[key] as? String)?.trim().orEmpty()

android {
    namespace = "com.softyorch.stroopoverload"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.softyorch.stroopoverload"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            manifestPlaceholders["admobAppId"] =
                adMobProperty("PROD_KEY_ID_ADMOB_APP").ifBlank { "ca-app-pub-3940256099942544~3347511713" }
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "AD_UNIT_NATIVE_DASHBOARD", "\"${adMobProperty("PROD_KEY_ID_NATIVE_DASHBOARD")}\"")
            buildConfigField("String", "AD_UNIT_NATIVE_GAME", "\"${adMobProperty("PROD_KEY_ID_NATIVE_GAME")}\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL_ONLINE", "\"${adMobProperty("PROD_KEY_ID_INTERSTITIAL_ONLINE")}\"")
        }
    }
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["admobAppId"] = adMobProperty("DEV_KEY_ID_ADMOB_APP")
                .ifBlank { "ca-app-pub-3940256099942544~3347511713" } // Google's public test app ID
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "AD_UNIT_NATIVE_DASHBOARD", "\"${adMobProperty("DEV_KEY_ID_NATIVE_DASHBOARD")}\"")
            buildConfigField("String", "AD_UNIT_NATIVE_GAME", "\"${adMobProperty("DEV_KEY_ID_NATIVE_GAME")}\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL_ONLINE", "\"${adMobProperty("DEV_KEY_ID_INTERSTITIAL_ONLINE")}\"")
        }
        create("demo") {
            dimension = "environment"
            versionNameSuffix = "-demo"
            // Demo builds are the App Store review/showcase flavor -- never show ads there.
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("boolean", "ADS_ENABLED", "false")
            buildConfigField("String", "AD_UNIT_NATIVE_DASHBOARD", "\"\"")
            buildConfigField("String", "AD_UNIT_NATIVE_GAME", "\"\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL_ONLINE", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.firebase.functions)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.ads)
    implementation(libs.google.ump)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
