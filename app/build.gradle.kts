import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun gitCommitCount(): Int {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    check(process.waitFor() == 0) { "Cannot determine version from Git; pass -Phneo.versionCode=<positive integer> for source archives." }
    val count = output.toIntOrNull() ?: error("Invalid Git commit count: $output")
    return count
}

val buildNumber = providers.gradleProperty("hneo.versionCode").orNull?.let {
    it.toIntOrNull()?.takeIf { number -> number > 0 }
        ?: error("hneo.versionCode must be a positive integer")
} ?: gitCommitCount()

fun endpointProperty(name: String, default: String): String {
    val value = providers.gradleProperty(name).getOrElse(default)
    val uri = URI(value)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        uri.fragment == null && uri.query == null) { "$name must be an HTTPS URL without credentials, query, or fragment" }
    return value.trimEnd('/')
}

val hnBaseUrl = endpointProperty("hneo.hnBaseUrl", "https://api.hackerwebapp.com")
val releasesUrl = endpointProperty("hneo.releasesUrl", "https://api.github.com/repos/rocrp/hneo-android/releases/latest")
val signingValues = listOf("KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    .associateWith { System.getenv(it)?.takeIf(String::isNotBlank) }
val hasReleaseSigning = signingValues.values.any { it != null }
require(!hasReleaseSigning || signingValues.values.all { it != null }) {
    "Release signing requires KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD together"
}

android {
    namespace = "dev.rocry.hneo"
    compileSdk = 35

    defaultConfig {
        applicationId = providers.gradleProperty("hneo.applicationId").getOrElse("dev.rocry.hneo")
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        buildConfigField("String", "HN_BASE_URL", "\"$hnBaseUrl\"")
        buildConfigField("String", "RELEASES_URL", "\"$releasesUrl\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingValues.getValue("KEYSTORE_FILE")!!)
                require(storeFile!!.isFile) { "KEYSTORE_FILE does not exist: $storeFile" }
                storePassword = signingValues.getValue("KEYSTORE_PASSWORD")
                keyAlias = signingValues.getValue("KEY_ALIAS")
                keyPassword = signingValues.getValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.markdown.renderer.m3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
