import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
}

// Firebase 설정(google-services.json, 커밋하지 않음)이 있을 때만. 없으면 혼자 모드로 빌드된다.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
// USB 연결 기기는 adb reverse 와 127.0.0.1, Android 에뮬레이터는 10.0.2.2를 쓴다.
val emulatorHost = providers.gradleProperty("firebaseEmulatorHost")
    .orElse(localProps.getProperty("FIREBASE_EMULATOR_HOST", "")).get().trim()
require(emulatorHost.isEmpty() || emulatorHost.matches(Regex("[a-zA-Z0-9.-]+"))) { "Firebase emulator host must be a hostname without a port" }

android {
    namespace = "com.sadturtleman.flowershoes"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.sadturtleman.flowershoes"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 소셜 로그인 키(local.properties). 비어 있으면 그 제공자는 "설정 전"으로 안내한다.
        val props = localProps
        val kakaoKey = props.getProperty("KAKAO_NATIVE_APP_KEY", "")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKey\"")
        // 디버그 빌드에서만 의미가 있다(Application 이 BuildConfig.DEBUG 와 함께 본다).
        buildConfigField("boolean", "DEV_EMAIL_LOGIN", props.getProperty("DEV_EMAIL_LOGIN", "false"))
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
        manifestPlaceholders["firebaseAutoInitEnabled"] = "true"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${props.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"")
        // 꽃신 평생 결제(RevenueCat 공개 SDK 키 — 앱에 실려 나가는 값이다). 기본은 테스트 스토어 키, 실제 키는 local.properties 로.
        buildConfigField("String", "REVENUECAT_API_KEY", "\"${props.getProperty("REVENUECAT_API_KEY", "test_chjLRvvSdzZezYKFbIWfRxJMRjg")}\"")
        manifestPlaceholders["kakaoScheme"] = if (kakaoKey.isBlank()) "kakaonotconfigured" else "kakao$kakaoKey"
        // 초대 링크(#39): Firebase Hosting 사이트(App Links 호스트 · 링크 모양)와 카카오 사용자 정의 피드 템플릿 ID.
        // 비어 있으면 자리표시 호스트 · OS 공유 시트로 동작한다.
        val inviteHost = props.getProperty("INVITE_HOST", "").ifBlank { "TODO_HOSTING_SITE.web.app" }
        require(inviteHost.matches(Regex("[a-zA-Z0-9._-]+"))) { "INVITE_HOST must be a hostname" }
        buildConfigField("String", "INVITE_HOST", "\"$inviteHost\"")
        manifestPlaceholders["inviteHost"] = inviteHost
        val templateId = props.getProperty("KAKAO_INVITE_TEMPLATE_ID", "").trim()
        require(templateId.isEmpty() || templateId.all(Char::isDigit)) { "KAKAO_INVITE_TEMPLATE_ID must be a number" }
        buildConfigField("long", "KAKAO_INVITE_TEMPLATE_ID", "${templateId.ifEmpty { "0" }}L")
    }

    buildTypes {
        // AdMob 배너 단위. 디버그는 Google 테스트 단위 — 자기 광고를 보거나 누르는 것은 정책 위반이다.
        debug {
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$emulatorHost\"")
            manifestPlaceholders["firebaseAutoInitEnabled"] = emulatorHost.isBlank().toString()
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
        }
        release {
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-9179621242550991/1874200235\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 피처 플래그와 AB 테스트는 환경마다 따로 있다. 어느 환경인지는 빌드가 정해 심어 주고,
    // :featureflag 는 그 값을 AppEnvironment 로 읽어 리모트 키 앞에 붙인다.
    // dev · qa 는 applicationId 가 달라 한 기기에 나란히 깔린다.
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "APP_ENV", "\"DEV\"")
        }
        create("qa") {
            dimension = "env"
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            buildConfigField("String", "APP_ENV", "\"QA\"")
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "APP_ENV", "\"PROD\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // 플레이버가 심는 APP_ENV 를 코드에서 읽는다.
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xexplicit-backing-fields")
    }
}

dependencies {
    // 꽃신 공유 앱 셸(Compose Multiplatform · Koin). 화면은 전부 여기서 온다.
    implementation(project(":composeApp"))
    implementation(libs.koin.android)
    // 홈 화면 위젯. 위젯은 :core:widget-bridge 가 쓴 스냅샷만 읽는다.
    implementation(project(":core:widget-bridge"))
    implementation(project(":core:notification"))
    implementation(project(":core:media"))
    // 푸시(초인종). google-services.json 이 있는 빌드에서만 실제로 동작한다.
    implementation(project(":core:firebase"))
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-auth")
    implementation(project(":core:drawing-engine"))
    implementation(project(":core:designsystem"))
    // 광고(Figma 9 · 광고 지면): AdMob 배너 + 동의(UMP).
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    // 꽃신 평생(한 번 결제)
    implementation(libs.revenuecat)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.biometric)
    // 시작 화면(Figma 9 · 공통 상태 · 앱 시작): 시스템 스플래시 → 공유 셸의 스플래시로 이어진다.
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.glance.material3)

    implementation(project(":common:di"))
    implementation(project(":common:datastore"))

    // 기기 정보. 빌드 변형만은 이 모듈이 BuildConfig 에서 읽어 꽂아 준다(TelemetryModules).
    implementation(project(":common:util"))

    // 화면 진입 시간 계측. Application 이 init · destroy 를 잡고, Activity 가 화면 트리에 기록기를 꽂는다.
    implementation(project(":core:tti"))

    // 비즈니스 이벤트 로깅(GA4). TTI 와 마찬가지로 Application 이 init · destroy 를 잡는다.
    implementation(project(":core:logging"))

    // 피처 플래그 · AB 테스트(Remote Config). Application 이 init 을 잡고, 환경은 이 모듈이 BuildConfig 에서 읽어 준다.
    implementation(project(":core:featureflag"))

    // 프레임 드랍 계측. Activity 가 JankStats 를 단다.
    implementation(project(":core:jank"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.credentials)
    // 앱이 앞/뒤로 오갈 때를 TTI 기록기의 init · destroy 신호로 쓴다.
    implementation(libs.androidx.lifecycle.process)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("com.google.firebase:firebase-firestore")
    androidTestImplementation("com.google.firebase:firebase-storage")
    androidTestImplementation(libs.kotlinx.coroutines.play.services)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
