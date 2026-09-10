plugins {
    // No version: the Android plugin is already on the classpath from :app, and asking for a
    // version again fails the build. The same applies to the asset-pack modules under packs/.
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

/**
 * Records which classes and methods the app actually runs while starting up and while the
 * first screens are used. The recording ships inside the app as a baseline profile, and
 * Android compiles those methods at install time instead of interpreting them on first run.
 *
 *   ./gradlew :app:generateBaselineProfile     record a new profile (needs a device or emulator)
 *   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest   measure startup
 *
 * The profile is committed at app/src/main/generated/baselineProfiles/baseline-prof.txt, so a
 * normal build never needs a device.
 */
android {
    namespace = "com.leaf.app.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The app ships in two flavours; the profile is recorded from the Play build,
        // which is the one on the store. Both flavours share the code it covers.
        missingDimensionStrategy("distribution", "play")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // Record on whatever device or emulator is attached, rather than a managed one.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
