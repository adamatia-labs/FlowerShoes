plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "com.sadturtleman.flowershoes.feature.drawing"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            // feature 는 core 만 본다. 다른 feature 로의 이동은 :composeApp 이 콜백으로 잇는다.
            implementation(project(":core:designsystem"))
            implementation(project(":core:data"))
            implementation(project(":core:auth"))
            implementation(project(":core:drawing-engine"))
            implementation(project(":core:drawing-ui"))
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation("org.jetbrains.compose.ui:ui-backhandler:${libs.versions.composeMultiplatform.get()}")
        }
    }
}
