// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()  // ✅ Required for Android Gradle Plugin
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")  // ✅ Gradle plugin
        classpath("com.google.gms:google-services:4.4.2") // ✅ Updated Firebase plugin
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22") // ✅ Correct Kotlin plugin
    }
}

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
