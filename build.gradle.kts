// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Plugin Google Services : lit app/google-services.json pour connecter l'app au projet Firebase
    alias(libs.plugins.google.services) apply false
}
