import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val debugSigningPropertiesFile = rootProject.file("signing/debug-signing.properties")
val debugSigningProperties = Properties().apply {
    if (debugSigningPropertiesFile.exists()) {
        debugSigningPropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.zhaoyuchen.androidforward"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.zhaoyuchen.androidforward"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (debugSigningPropertiesFile.exists()) {
            create("fixedDebug") {
                // 本地固定调试签名配置来自未提交的 signing/debug-signing.properties。
                storeFile = rootProject.file(debugSigningProperties.getProperty("storeFile"))
                storePassword = debugSigningProperties.getProperty("storePassword")
                keyAlias = debugSigningProperties.getProperty("keyAlias")
                keyPassword = debugSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (debugSigningPropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("fixedDebug")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
