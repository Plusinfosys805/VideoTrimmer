plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.plusinfosys.videotrimmerkotlin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.plusinfosys.videotrimmerkotlin"
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
    buildFeatures {
        dataBinding = true
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

 /*   implementation(files("libs/ffmpeg-kit-full-gpl-6.0-2.LTS.aar"))
    implementation(files("libs/smart-exception-java-0.2.1.jar"))
    implementation(files("libs/smart-exception-common-0.2.1.jar"))*/

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.ui.graphics.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.glide)
    implementation(libs.sdp.android)
    implementation(libs.ssp.android)

}