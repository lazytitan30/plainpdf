import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.leaf.app"
    // Latest AndroidX (Compose 1.12, core 1.19) requires API 37 to compile against.
    // targetSdk stays at 36, which is what Google Play requires since 31 August 2026.
    compileSdk = 37

    defaultConfig {
        // The store identity. The Kotlin package stays com.leaf.app from the earlier working name;
        // only this id is visible to users and to Play, and it cannot change after the first upload.
        applicationId = "com.plainpdf.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Local development only: ./gradlew -PdevAbi=x86_64 :app:installFossDebug builds a
        // single-ABI APK that fits a cramped emulator. Release builds ship every ABI.
        (project.findProperty("devAbi") as String?)?.let { abis ->
            ndk { abiFilters += abis.split(",") }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
        }
        create("foss") {
            dimension = "distribution"
        }
    }

    // Release signing comes from an untracked keystore.properties at the repo root:
    //   storeFile=/absolute/path/plainpdf-release.jks
    //   storePassword=...
    //   keyAlias=plainpdf
    //   keyPassword=...
    // Without it, release builds are produced unsigned (fine for F-Droid, which signs itself).
    val keystoreProps = rootProject.file("keystore.properties")
    if (keystoreProps.exists()) {
        val props = Properties().apply { keystoreProps.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a real keystore the release build is signed with the debug key so the
            // shrunk build can still be installed and tested locally. Never upload that one.
            signingConfig = if (keystoreProps.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    androidResources {
        // Only the languages the app actually ships, so Play lists them correctly.
        localeFilters += listOf("en", "b+sr+Latn", "de", "es", "fr", "it", "pt-rBR", "ru", "pl", "tr", "in", "vi", "ja", "ko", "zh-rCN", "hi", "ar")
        // Tesseract language files are copied out of the APK on first use; compressing them
        // would only make that copy slower.
        noCompress += "traineddata"
    }

    bundle {
        // Keep language resources in the base so the FOSS build works without Play delivery.
        language { enableSplit = false }
    }

    // Language packs for text recognition that are not bundled: Google Play downloads
    // one when the user taps Get in Settings. APK builds ignore them; only the bundle has them.
    assetPacks += setOf(":lang_por", ":lang_pol", ":lang_tur", ":lang_ind", ":lang_vie", ":lang_jpn", ":lang_kor", ":lang_chi_sim", ":lang_hin", ":lang_ara", ":lang_hrv", ":lang_slv", ":lang_bul", ":lang_mkd", ":lang_hun", ":lang_ron", ":lang_ell", ":lang_ukr", ":lang_nld", ":lang_ces")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

configurations.all {
    // androidx.pdf lists an OCR module that depends on ML Kit (Google Play services).
    // The app does its own OCR with Tesseract, and the FOSS build must not ship proprietary code.
    exclude(group = "androidx.pdf", module = "pdf-ocr-play-services")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)
    implementation(libs.fragment.ktx)
    implementation(libs.fragment.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.documentfile)
    implementation(libs.exifinterface)
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.android)
    implementation(libs.pdf.viewer.fragment)
    implementation(libs.pdf.ink)
    implementation(libs.pdfbox.android)
    implementation(libs.tesseract4android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.serialization.json)

    "playImplementation"(libs.billing.ktx)
    "playImplementation"(libs.play.asset.delivery)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
