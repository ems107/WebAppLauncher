import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/** X.Y.Z, kept in gradle.properties; scripts/release.ps1 is what changes it. */
val appVersion = providers.gradleProperty("weblauncher.version").get()

/** Every published version must be greater than the one before, so X.Y.Z is folded into one number. */
fun versionCodeOf(version: String): Int {
    val parts = version.split(".").map { it.toInt() }
    require(parts.size == 3 && parts.all { it in 0..99 }) { "weblauncher.version must be X.Y.Z with parts 0-99: $version" }
    return parts[0] * 10000 + parts[1] * 100 + parts[2]
}

/**
 * The release key lives outside the repository. Without it a release build is
 * unsigned, and scripts/release.ps1 refuses to publish it.
 */
val releaseSigning = File(System.getProperty("user.home"), ".android/weblauncher-release.properties")
    .takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } to file.parentFile }

android {
    namespace = "es.edgarms.weblauncher"
    // Current AndroidX and OkHttp need to be compiled against 37; the app still targets 36.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "es.edgarms.weblauncher"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        buildConfigField("String", "UPDATE_REPO", "\"ems107/WebAppLauncher\"")
    }

    signingConfigs {
        releaseSigning?.let { (props, dir) ->
            create("release") {
                storeFile = File(dir, props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // A debug build is signed with another key: an update could never install over it.
            buildConfigField("boolean", "UPDATES_ENABLED", "false")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "UPDATES_ENABLED", "true")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.androidx.work.runtime)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
