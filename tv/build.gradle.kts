plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.gestotv.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gestotv.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = runNumber
        versionName = "0.1.$runNumber"
    }

    // Llave fija: así cada versión nueva se instala encima de la anterior sin desinstalar.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("debug") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("com.google.zxing:core:3.5.3")
}
