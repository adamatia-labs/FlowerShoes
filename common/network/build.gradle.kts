import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val localProperties: Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/**
 * API 주소. 저장소에 올리지 않도록 local.properties 의 `FLOWERSHOES_BASE_URL` 에서 읽는다.
 * 끝의 '/' 가 빠지면 Retrofit 이 예외를 던지므로 형식을 지켜 적어야 한다.
 *
 * 서버가 정해지기 전에도 빌드 · 실행은 되어야 하므로 값이 없으면 자리표시 주소를 쓴다.
 * (Retrofit 은 주소 형식만 검사하므로 이 주소로는 실제 요청이 나가지 않는다)
 */
val flowerShoesBaseUrl: String = localProperties
    .getProperty("FLOWERSHOES_BASE_URL").orEmpty().trim()
    .ifEmpty { "https://localhost/" }

android {
    namespace = "com.sadturtleman.flowershoes.common.network"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("String", "BASE_URL", "\"$flowerShoesBaseUrl\"")
    }

    buildFeatures {
        buildConfig = true
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
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 통신 스택을 쓰는 data 모듈들이 Retrofit 타입을 그대로 받도록 api 로 노출한다.
    api(libs.retrofit)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)

    // 응답 본문은 JSON 으로 받는다. DTO 의 @Serializable 은 각 data 모듈이 단다.
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
