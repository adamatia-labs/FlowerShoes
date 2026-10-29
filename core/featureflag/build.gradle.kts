plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    // 플래그 값의 타입을 KSerializer 로 들고 다닌다. FlagKey 안의 @Serializable 참조에 필요하다.
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(17)

    // 피처 플래그 · AB 테스트. 앱 전체를 가로지른다.
    // 키 카탈로그(FlagKey) · 환경 폴백 · 두 칸 캐시는 commonMain 이고,
    // 값 출처(RemoteConfigSource)는 Firebase Remote Config(GitLive), Firebase 없는 빌드는 샘플이다.
    android {
        namespace = "com.sadturtleman.flowershoes.core.featureflag"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    // jvm 은 테스트용. Firebase 를 모른다.
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }

    // Firebase 를 아는 코드는 android · iOS 가 함께 쓰는 firebaseMain 에만 둔다.
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            // 키가 자기 타입을 KSerializer 로 들고 있어야 List<T> 와 JSON 객체까지 타입을 잃지 않는다.
            api(libs.kotlinx.serialization.json)
            // JSON 으로 내려오는 플래그 값(VO · 변형 enum).
            api(project(":common:entity"))
            // 디스패처 한정자. 리모트 왕복은 전부 IO 에서 돈다.
            implementation(project(":common:di"))
            // 플래그 · 실험 배정을 남기는 곳.
            implementation(project(":core:logging"))
        }
        val firebaseMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.gitlive.firebase.config)
            }
        }
        androidMain.get().dependsOn(firebaseMain)
        iosMain.get().dependsOn(firebaseMain)
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.firebase.bom))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
