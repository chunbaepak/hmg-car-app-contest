import java.util.Properties

plugins {
    id("com.android.application")
}

// release 서명 정보는 저장소에 올리지 않는 keystore.properties에서 읽는다.
// 파일이 없으면(팀원 PC 등) 디버그 키로 서명해서 assembleRelease가 그래도 동작하게 한다.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.changbak.story"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.changbak.story"
        // 차량 보안 요건(사이닝 키 로테이션·리니지)으로 API 28 이상 필수
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // Pleos는 서명 키 교체(key rotation)를 지원하는 v3 서명을 요구한다.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
}
