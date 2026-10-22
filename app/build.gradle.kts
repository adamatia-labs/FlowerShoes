import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
}

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
    implementation(project(":core:backup"))
    implementation(project(":core:media"))
    // 푸시(초인종). google-services.json 이 있는 빌드에서만 실제로 동작한다.
    implementation(project(":core:firebase"))
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-messaging")
    implementation(project(":core:drawing-engine"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(project(":common:di"))
    implementation(project(":common:datastore"))

    // 기기 정보. 빌드 변형만은 이 모듈이 BuildConfig 에서 읽어 꽂아 준다(TelemetryModules).
    implementation(project(":common:util"))

    // 화면 진입 시간 계측. Application 이 init · destroy 를 잡는다.
    // domain 은 계약(TtiRecorder · PrintTtiShooter), data 는 Koin 조립,
    // presentation 은 화면을 감싸는 TtiPage 때문에 필요하다.
    implementation(project(":tti:domain"))
    implementation(project(":tti:data"))
    implementation(project(":tti:presentation"))

    // 비즈니스 이벤트 로깅. TTI 와 마찬가지로 Application 이 init · destroy 를 잡는다.
    // domain 은 계약(BizLogger · BizEvent), data 는 Koin 조립과 전송 구현이다.
    implementation(project(":logging:domain"))
    implementation(project(":logging:data"))

    // 피처 플래그 · AB 테스트. Application 이 init 을 잡고, 환경은 이 모듈이 BuildConfig 에서 읽어 준다.
    implementation(project(":featureflag:domain"))
    implementation(project(":featureflag:data"))

    // 프레임 드랍 계측. Activity 가 JankStats 를 단다.
    implementation(project(":jank:domain"))
    implementation(project(":jank:presentation"))
    implementation(project(":jank:data"))

    implementation(libs.androidx.activity.compose)
    // 앱이 앞/뒤로 오갈 때를 TTI 기록기의 init · destroy 신호로 쓴다.
    implementation(libs.androidx.lifecycle.process)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
