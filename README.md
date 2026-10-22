# FlowerShoes
꽃신 앱

멀티 모듈 · MVI · Navigation 3 · Hilt 로 구성된 안드로이드 앱입니다.
기본 세팅은 [EveryMuseum](https://github.com/Sadturtleman/EveryMuseum) 의 구조를 그대로 옮겨 왔습니다.

## 모듈

| 모듈 | 성격 | 하는 일 |
|---|---|---|
| `:app` | Android | Navigation 3 호스트 · 라우팅 테이블(`AppRouteRegistry`) · 딥링크 · Application |
| `:common:presentation` | Compose | 디자인 시스템(`FlowerTheme` · `Flower*` 컴포넌트) · `MviViewModel` · Navigation/Message 헬퍼 |
| `:common:domain` · `:common:entity` | 순수 코틀린 | 여러 feature 가 함께 쓰는 계약과 모델 |
| `:common:data` | Android + Hilt | 여러 feature 가 함께 쓰는 저장소 구현 |
| `:common:network` | Android + Hilt | Retrofit · OkHttp · kotlinx-serialization JSON |
| `:common:datastore` | Android + Hilt | Preferences DataStore 래퍼(`DataStorage`) |
| `:common:di` | Android + Hilt | 코루틴 디스패처 한정자 |
| `:common:navigation` | 순수 코틀린 | `NavRoute` · `Page` · `NavigationHelper` 계약 |
| `:common:util` | Android + Hilt | 기기 정보(`DeviceInfoProvider`) |
| `:tti:*` | 계측 | 화면 진입 시간(TTI). Logcat `TTI:` |
| `:jank:*` | 계측 | 프레임 드랍. Logcat `JANK` |
| `:logging:*` | 계측 | 비즈니스 이벤트(`BizEvent`). Logcat `BIZLOG:` |
| `:featureflag:*` | 설정 | 환경별 피처 플래그 · AB 테스트(`FlagKey`) |
| `:home:*` | feature | 홈 화면 |

feature 하나는 다섯 모듈로 나뉩니다.

| 모듈 | 성격 | 아는 것 |
|---|---|---|
| `navigation` | 순수 코틀린 | 페이지 경로와 인자(`XxxPage`). 다른 feature 는 이것만 참조한다 |
| `presentation` | Compose + ViewModel | 입력은 `Intent` 하나, 출력은 `UiState` 하나 |
| `domain` | 순수 코틀린 | UseCase 와 Repository **계약** |
| `data` | Android + Hilt | 그 계약의 구현 |
| `entity` | 순수 코틀린 | feature 모델(VO) |

## 새 feature 추가

1. `settings.gradle.kts` 에 `:<feature>:{navigation,presentation,domain,data,entity}` 를 추가한다 (`:home:*` 를 복사해 시작).
2. `:app/build.gradle.kts` 에 다섯 모듈을 `implementation` 으로 넣는다.
3. `AppRouteRegistry.appRoutes` 에 `AppRoute` 한 줄을 더한다.
4. 탭이면 `FlowerTab` 에 항목을 더한다 — 탭 루트가 둘 이상이 되면 하단 탭 바가 나타난다.

## 빌드

- 환경은 `dev` · `qa` · `prod` 플레이버입니다. `dev` · `qa` 는 applicationId 꼬리가 달라 한 기기에 나란히 깔립니다.
- API 주소는 `local.properties` 에 둡니다. 없으면 자리표시 주소로 빌드됩니다.

```properties
FLOWERSHOES_BASE_URL=https://api.example.com/
```

```bash
./gradlew assembleDevDebug
./gradlew test
```

딥링크: `adb shell am start -a android.intent.action.VIEW -d "flowershoes://app/home"`

## 브랜치

`main` ← `develop` ← `feature/*`. 작업은 `feature/*` 에서 하고 `develop` 으로 PR 을 올립니다.
