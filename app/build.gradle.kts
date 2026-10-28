val legacyAndroid = providers.gradleProperty("legacyAndroid").isPresent

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ru.okbit.ledclock"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "ru.okbit.ledclock"
        minSdk = 14
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.2"
    }
    flavorDimensions += "compatibility"
    productFlavors {
        create("legacy") {
            dimension = "compatibility"
            minSdk = 14
        }
        create("modern") {
            dimension = "compatibility"
            minSdk = 23
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            // Preserve update compatibility with the already published 1.0.0 APK.
            // The signing key stays outside the repository.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            if (legacyAndroid) it.filter.includeTestsMatching("ru.okbit.ledclock.ClockLegacyIntegrationTest")
            else it.filter.excludeTestsMatching("ru.okbit.ledclock.ClockLegacyIntegrationTest")
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            val testHome = layout.buildDirectory.dir("test-home").get().asFile
            it.systemProperty("user.home", testHome.absolutePath)
            providers.gradleProperty("artifactDir").orNull?.let { dir -> it.systemProperty("clock.artifactDir", dir) }
            it.doFirst { testHome.mkdirs() }
        }
    }
    bundle { language { enableSplit = false } }
    lint { abortOnError = true; checkReleaseBuilds = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:${if (legacyAndroid) "4.11.1" else "4.14.1"}")
}
