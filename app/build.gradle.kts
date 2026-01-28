plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // 1. ПЛАГИН GOOGLE
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.villagehub"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.villagehub"
        minSdk = 24
        targetSdk = 34
        versionCode = 4       // Версия 4
        versionName = "1.3"   // Версия 1.3

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- 2. FIREBASE (База данных) ---
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-database")

    // --- 3. GPS ГЕОЛОКАЦИЯ ---
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // --- 4. YANDEX ADS (Реклама - обновленная версия) ---
    implementation("com.yandex.android:mobileads:7.18.1")

    // --- 5. FIREBASE CLOUD MESSAGING (ПУШИ - ПРИЕМ) ---
    implementation("com.google.firebase:firebase-messaging")

    // --- 6. OKHTTP (ДЛЯ ОТПРАВКИ ЗАПРОСОВ В ИНТЕРНЕТ/ПУШЕЙ) ---
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}