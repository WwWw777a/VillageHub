// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    // САМОЕ ВАЖНОЕ: Эта строка подключает сервисы Google ко всему проекту
    id("com.google.gms.google-services") version "4.4.0" apply false
}