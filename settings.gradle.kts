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
    }
}

rootProject.name = "FlowerShoes"
include(":app")

// KMP 공용 코어. commonMain 에 순수 코틀린으로 두고 Android(jvm) · iOS 가 함께 쓴다.
include(":core:model")
include(":core:military")
include(":core:sync")
include(":core:drawing-engine")
include(":core:crypto")

// 공통 레이어. 모든 feature 가 참조한다.
include(":common:presentation")
include(":common:domain")
include(":common:data")
include(":common:network")
include(":common:entity")
include(":common:datastore")
include(":common:di")
include(":common:navigation")

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

// 비즈니스 이벤트 로깅. TTI 와 마찬가지로 어느 feature 에도 속하지 않고 앱 전체를 가로지른다.
// domain 은 순수 코틀린이다 — 남길 수 있는 이벤트 목록(BizEvent)을 들고 있고, 쌓아 두지 않아
// record 한 건이 그대로 한 번의 전송이 된다.
// 시각(BizLogClock) 과 전송(BizLogShooter) 은 포트로 둔다.
include(":logging:domain")
include(":logging:data")

// 피처 플래그 · AB 테스트. 이것도 앱 전체를 가로지른다.
// domain 은 순수 코틀린이다 — presentation 과 feature:domain 이 함께 읽어야 하므로
// 안드로이드에 기대면 도메인 레이어에서 못 쓴다.
// 값 출처(RemoteConfigSource) 와 상태 기록(FlagStateRecorder) 은 포트로 둔다.
include(":featureflag:domain")
include(":featureflag:data")

// 프레임 드랍(jank) 계측. TTI 가 "언제 쓸 수 있게 되는가" 를 잰다면 이쪽은 "쓰는 동안 매끄러운가" 를 잰다.
// domain 은 순수 코틀린이다 — 버킷과 임계치 규칙이 플랫폼과 무관해야 테스트로 잡을 수 있다.
// presentation 이 JankStats 의 프레임을 도메인 모양으로 옮기고, data 가 내보낼 곳을 꽂는다.
include(":jank:domain")
include(":jank:presentation")
include(":jank:data")

// 네비게이션 호스트. 각 feature 의 화면을 라우팅 테이블 한 곳에 모은다.
include(":home:navigation")
include(":home:presentation")
include(":home:domain")
include(":home:data")
include(":home:entity")
