plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.feiyu.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.feiyu.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 135
        versionName = "6.2.3"
    }

    buildFeatures { compose = true; buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 用本机自动生成的 debug 签名；正式发布请自行配置 release 签名
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.compose.runtime:runtime:1.9.4")
    implementation("androidx.compose.foundation:foundation:1.9.4")
    implementation("androidx.compose.ui:ui:1.9.4")
    implementation("io.github.kyant0:backdrop:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.3")
}
