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
val releaseSigningPropertiesFile = rootProject.file("signing/release-signing.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.exists()) {
        releaseSigningPropertiesFile.inputStream().use(::load)
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
        versionCode = 10
        versionName = "0.4.1"
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
        if (releaseSigningPropertiesFile.exists()) {
            create("releaseUpload") {
                // Google Play 上传签名配置来自未提交的本地文件，避免泄露 keystore 密码。
                storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (debugSigningPropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("fixedDebug")
            }
        }
        release {
            if (releaseSigningPropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("releaseUpload")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // 根据 values 目录自动生成 Android 13+ 的应用语言配置。
        generateLocaleConfig = true
    }

    bundle {
        language {
            // 应用支持运行时切换语言，AAB 不应按语言拆分资源。
            enableSplit = false
        }
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
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
