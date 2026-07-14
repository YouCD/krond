plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "online.youcd.krond"
    compileSdk = 35

    defaultConfig {
        applicationId = "online.youcd.krond"
        minSdk = 26
        targetSdk = 35
        versionCode = (findProperty("versionCode") as? String)?.toIntOrNull() ?: 5
        versionName = (findProperty("versionName") as? String) ?: "2.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val storeFileEnv = System.getenv("KEYSTORE_FILE")
            val storePasswordEnv = System.getenv("KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("KEY_ALIAS")
            val keyPasswordEnv = System.getenv("KEY_PASSWORD")
            if (!storeFileEnv.isNullOrBlank() && !storePasswordEnv.isNullOrBlank() &&
                !keyAliasEnv.isNullOrBlank() && !keyPasswordEnv.isNullOrBlank()
            ) {
                storeFile = file(storeFileEnv)
                storePassword = storePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            } else {
                val debugStore = File(System.getProperty("user.home"), ".android/debug.keystore")
                if (debugStore.exists()) {
                    storeFile = debugStore
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "**/libjunixsocket-native*.dylib",
                "**/junixsocket-native*.dll",
                "**/junixsocket-native*.a",
                "**/junixsocket-native*.srvpgm",
                "**/lib/aarch64-MacOSX*/**",
                "**/lib/aarch64-Windows*/**",
                "**/lib/amd64-*/**",
                "**/lib/x86_64-*/**",
                "**/lib/ppc64-*/**",
                "**/lib/arm-*/**",
                "**/lib/i686-*/**",
                "**/lib/loongarch64*/**",
                "**/lib/riscv64*/**",
                "**/lib/s390x*/**",
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
    }

    aaptOptions {
        noCompress += listOf("*.so")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.kohlschutter.junixsocket:junixsocket-common:2.10.1")
    implementation("com.kohlschutter.junixsocket:junixsocket-native-common:2.10.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
