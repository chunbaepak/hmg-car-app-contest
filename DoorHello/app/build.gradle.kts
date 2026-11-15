plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.doorhello"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.doorhello"
        // 차량 보안 요건(사이닝 키 로테이션·리니지)으로 API 28 이상 필수
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
}
