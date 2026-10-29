pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 카카오 로그인 SDK
        maven("https://devrepo.kakao.com/nexus/content/groups/public/")
    }
}

rootProject.name = "FlowerShoes"
include(":app")
// 공유 앱 셸(Compose Multiplatform). :app 과 iosApp 이 이것을 띄운다.
include(":composeApp")

// KMP 공용 코어. commonMain 에 순수 코틀린으로 두고 Android(jvm) · iOS 가 함께 쓴다.
include(":core:model")
include(":core:military")
include(":core:sync")
include(":core:drawing-engine")
include(":core:compression")
include(":core:crypto")
include(":core:database")
include(":core:data")
include(":core:designsystem")
include(":core:auth")
include(":core:drawing-ui")
include(":core:firebase")
include(":core:widget-bridge")
include(":core:notification")
include(":core:media")
// 비즈니스 이벤트 로깅(BizEvent → GA4) · 피처 플래그(Remote Config). 앱 전체를 가로지른다.
include(":core:logging")
include(":core:featureflag")
// 화면 진입 시간(TTI) · 프레임 드랍(jank) 계측. 측정 규칙은 commonMain, 시계 · 프레임 소스만 플랫폼별이다.
include(":core:tti")
include(":core:jank")

// 화면 단위 기능. core 만 의존하고 서로는 모른다.
include(":feature:onboarding")
include(":feature:home")
include(":feature:calendar")
include(":feature:letter")
include(":feature:drawing")
include(":feature:memory")
include(":feature:pairing")
include(":feature:settings")

// 공통 레이어. 모든 feature 가 참조한다.
include(":common:entity")
include(":common:datastore")
include(":common:di")

// 기기 · OS · 화면 · 리소스 · 네트워크 정보 수집. 계측 모듈들이 함께 쓴다.
// 안드로이드 모듈이다 — Build · PackageManager · ConnectivityManager 를 읽는 것이 일이라
// 순수 코틀린으로 둘 수 없다. 대신 DeviceInfoProvider 계약 하나로만 밖에 보인다.
include(":common:util")
