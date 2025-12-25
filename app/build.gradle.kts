@file:Suppress("UnstableApiUsage")

import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

fun parseLocalesConfig(file: File): List<String> {
    check(file.exists()) {
        "Missing locales_config.xml at: ${file.path}"
    }
    return Regex("""android:name="([^"]+)"""")
        .findAll(file.readText())
        .map { it.groupValues[1] }
        .toList()
}

android {
    namespace = "hu.reelee81.pdflabelprinting"
    compileSdk = 36

    defaultConfig {
        applicationId = "hu.reelee81.pdflabelprinting"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "12.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        viewBinding = true
    }

    androidResources {
        val locales = parseLocalesConfig(file("src/main/res/xml/locales_config.xml"))
        localeFilters += locales
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.itextpdf.kernel)
    implementation(libs.itextpdf.layout)
    implementation(libs.itextpdf.bc.adapter)
    implementation(libs.itextpdf.bc.connector)
    implementation(libs.androidx.pdf.viewer)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
}
