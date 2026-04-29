import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Read optional Last.fm API key from local.properties (preferred) or from
// the LASTFM_API_KEY environment variable. Never commit the key — leaving
// it empty disables scrobbling at runtime.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val lastfmApiKey: String =
    localProps.getProperty("lastfm.apiKey") ?: System.getenv("LASTFM_API_KEY") ?: ""
val lastfmApiSecret: String =
    localProps.getProperty("lastfm.apiSecret") ?: System.getenv("LASTFM_API_SECRET") ?: ""

android {
    namespace = "com.gsmtrick.musicplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gsmtrick.musicplayer"
        minSdk = 23
        targetSdk = 34
        versionCode = 8
        versionName = "3.2"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "LASTFM_API_KEY", "\"${lastfmApiKey}\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"${lastfmApiSecret}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Media3 / ExoPlayer
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Coil for album art
    implementation("io.coil-kt:coil-compose:2.6.0")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Palette for dynamic colors from album art
    implementation("androidx.palette:palette-ktx:1.0.0")

    // NewPipeExtractor: search/stream/download YouTube and other services.
    // Used by NewPipe / ViMusic-style open-source clients. Note this lives
    // in a legal grey area w.r.t. YouTube ToS; fine for personal/sideload,
    // not Play-Store safe.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
