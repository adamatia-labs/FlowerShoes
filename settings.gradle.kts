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

// 화면 진입 시간(TTI) 계측. 어느 feature 에도 속하지 않고 앱 전체를 가로지른다.
// domain 은 순수 코틀린이다 — 무엇을 언제 재는지는 플랫폼과 무관해야 하므로
// 시각(TtiClock) 과 저장(TtiRecordStore) 을 포트로 두고 data 가 안드로이드 구현을 꽂는다.
include(":tti:domain")
include(":tti:data")
include(":tti:presentation")

// 프레임 드랍(jank) 계측. TTI 가 "언제 쓸 수 있게 되는가" 를 잰다면 이쪽은 "쓰는 동안 매끄러운가" 를 잰다.
// domain 은 순수 코틀린이다 — 버킷과 임계치 규칙이 플랫폼과 무관해야 테스트로 잡을 수 있다.
// presentation 이 JankStats 의 프레임을 도메인 모양으로 옮기고, data 가 내보낼 곳을 꽂는다.
include(":jank:domain")
include(":jank:presentation")
include(":jank:data")

