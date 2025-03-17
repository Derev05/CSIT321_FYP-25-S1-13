plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services") // ✅ Required for Firebase
}

android {
    namespace = "com.example.kotlinbasics"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.kotlinbasics"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.sun.mail:android-mail:1.6.2")
    implementation("com.sun.mail:android-activation:1.6.2")

    // ✅ Use Firebase BOM (Handles Versioning Automatically)
    implementation(platform("com.google.firebase:firebase-bom:32.2.2")) // Downgrade BOM to support Kotlin 1.9.0

    // ✅ Firebase Dependencies (Remove Explicit Versions)
    implementation("com.google.firebase:firebase-auth") // ✅ Version managed by BOM
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-analytics")

    // ✅ Downgraded Firebase & Play Services for Kotlin 1.9.0
    implementation("com.google.firebase:firebase-auth:22.2.0") // Downgrade from 23.2.0
    implementation("com.google.android.gms:play-services-measurement-api:21.0.0") // Downgrade from 22.3.0

    implementation("org.tensorflow:tensorflow-lite:2.9.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation(project(":sdk"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
