import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sadturtleman.flowershoes.home.presentation"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // MviViewModel 이 explicit backing field(`val uiState: StateFlow<S> field = ...`) 를 쓴다.
        // 선언 모듈과 소비 모듈 양쪽에 같은 플래그가 있어야 메타데이터를 읽을 수 있다.
        freeCompilerArgs.add("-Xexplicit-backing-fields")
    }
}

dependencies {
    implementation(project(":home:domain"))
    implementation(project(":home:navigation"))
    implementation(project(":home:entity"))
    implementation(project(":common:presentation"))

    // 이벤트를 남기게 되면 계약(BizLogger · BizEvent)을 여기에 더한다.
    // 구현 바인딩은 :logging:data 가 app 에서 설치한다.
    // implementation(project(":logging:domain"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
