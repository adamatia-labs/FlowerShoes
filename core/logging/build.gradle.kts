plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvmToolchain(17)

    // 비즈니스 이벤트 로깅. 어느 feature 에도 속하지 않고 앱 전체를 가로지른다.
    // 남길 수 있는 이벤트 목록(BizEvent)과 기록기는 commonMain 이고, 쌓아 두지 않아 record 한 건이 그대로 한 번의 전송이 된다.
    // 전송(BizLogShooter)은 포트다 — 릴리스는 GA4(GitLive Analytics), 디버그와 Firebase 없는 빌드는 표준 출력.
    android {
        namespace = "com.sadturtleman.flowershoes.core.logging"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    // jvm 은 테스트용. Firebase 를 모른다.
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }

    // Firebase 를 아는 코드는 android · iOS 가 함께 쓰는 firebaseMain 에만 둔다.
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            // 디스패처 한정자. 쏘기는 전부 IO 에서 돈다.
            implementation(project(":common:di"))
        }
        val firebaseMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.gitlive.firebase.analytics)
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
