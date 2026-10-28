# 분석 백엔드 선택 조사 (#24)

조사일: 2026-10-29. 질문: KMP(Android · iOS) 꽃신 앱이 `BizEvent`를 어디로 보낼 것인가.
후보: Firebase Analytics(GitLive KMP 바인딩 또는 expect/actual), PostHog, Amplitude, 자체 Cloud Functions 수집.

제약 (docs/product/planning.md):
- §4: 정해진 이벤트만(`BizEvent` sealed). 사용자가 입력한 글자는 넣지 않는다(화면 이름 · 선택지 · 숫자만).
- §7: 성과 기준은 "연결까지 완료한 커플당 획득 비용". 출시 뒤 지표는 "설치 후 7일 안 연결 전 이탈 비율"과 그중 상대가 훈련소 기간이던 비율.

## 결론 (요약)

**Firebase Analytics (GA4)를 GitLive `dev.gitlive:firebase-analytics`로 붙인다.** 이유:

1. 이미 GitLive 2.1.0 + Firebase BoM 33.16.0 + iOS SPM `firebase-ios-sdk`를 쓰고 있어서, 같은 버전의 analytics 모듈 하나와 SPM product 하나만 더하면 된다. 새 벤더 · 새 계약 · 새 국외 이전 항목이 생기지 않는다(이미 Google이 처리자).
2. 비용 0, 이벤트 수 한도 사실상 없음. 퍼널 탐색에 "Within(시간 제약)"이 있어 "first_open → 연결 완료, 7일 이내"를 그대로 만들 수 있다.
3. ATT: 분석 자체는 ATT 대상이 아니다. 다만 앱이 이미 AdMob + ATT를 쓰므로 IDFA가 링크된 상태라는 점은 아래 "주의"대로 막는다.

"연결 완료 커플 수" 자체는 클라이언트 분석이 아니라 **서버(Firestore/Functions)가 진실**이다. 분석은 퍼널 · 이탈을 보고, 커플 수는 서버 집계로 교차 확인한다.

PostHog는 2순위(공식 KMP SDK가 있으나 0.x 프리릴리스이고 Kotlin 2.4.0+ 요구, 새 처리자 추가). Amplitude · 자체 수집은 권하지 않는다.

## 현재 코드 상태

- `gradle/libs.versions.toml`: `gitliveFirebase = "2.1.0"`, `firebaseBom = "33.16.0"`. 쓰는 모듈은 firestore · storage · auth · functions. analytics는 없다.
- `iosApp/project.yml`: SPM `firebase-ios-sdk` `from: 11.0.0`, product는 FirebaseCore/Firestore/Auth/Storage/Functions/Messaging. analytics 없음. 같은 파일에 `GoogleMobileAds` + `GoogleUMP`, `NSUserTrackingUsageDescription`이 있고 `App/AdBanners.swift`가 UMP → ATT 순서로 동의를 받는다. 즉 **ATT 팝업과 AdSupport(IDFA)는 광고 때문에 이미 앱에 있다.**
- `logging/domain/.../BizEvent.kt`: Android 전용 모듈. `name: String`, `parameters: Map<String, Any?>`. 이벤트는 `view_enter`, `flags_resolved`, `ab_exposed`, `layout_toggle`. 연결 퍼널 이벤트(설치 · 초대 링크 공유 · 연결 완료)는 아직 없다.

## 기준별 비교

| 기준 | Firebase Analytics (GitLive) | PostHog | Amplitude | 자체 Functions 수집 |
|---|---|---|---|---|
| KMP 지원 | GitLive `firebase-analytics`가 `commonMain` API + `androidMain` · `appleMain` 구현. 지금 쓰는 2.1.0 버전도 Maven Central에 있음 [1][2] | 공식 `com.posthog:posthog-kmp`, 0.x 프리릴리스(최신 0.10.0). iOS 링크는 0.5.0+ · Kotlin 2.4.0+ 필요(현재 프로젝트 Kotlin 2.3.21) [3][4] | 공식 KMP SDK 없음. 서드파티 `nathanfallet/amplitude-kmp`뿐 [5] | Ktor/GitLive Functions 호출로 직접 구현. SDK 없음, 배치 · 재시도 · 오프라인 큐를 직접 만든다 |
| 비용(무료 한도) | 무료. 앱 이벤트 이름 500개/사용자, 이벤트당 파라미터 25개, 사용자 속성 25개, 사용자당 하루 10만 이벤트 [6][7] | 월 100만 이벤트 무료, 이후 $0.00005/이벤트부터, 결제 한도 설정 가능. 익명 이벤트가 더 쌈 [8][3] | 무료 플랜 월 200만 이벤트, 데이터 접근 1년, 저장 차트 10개 [9] | Functions 월 200만 호출 무료(Blaze), Firestore 쓰기 하루 2만 무료 [7]. 이벤트마다 쓰기 1회면 DAU 수천에서 바로 넘는다. 분석 화면은 직접 만들어야 함 |
| 개인정보처리방침 · 스토어 라벨 | 처리자 Google(이미 Firebase로 등재). iOS 라벨: 사용 데이터(제품 상호작용) · 식별자(기기 ID) 추가. 앱 인스턴스 ID · 광고 ID · 기기 모델 · 지역을 자동 수집 [10] | 새 처리자(PostHog Inc., US 또는 EU 호스트) 추가 → 처리방침 · 국외 이전 항목 추가. 라벨: 사용 데이터 + 식별자 | 새 처리자(Amplitude, US) 추가 | 처리자 추가 없음. 라벨은 사용 데이터만 추가 가능 |
| 퍼널(설치 → 연결 완료, 7일 내 이탈) | 퍼널 탐색: 개방/폐쇄 퍼널, 단계 사이 "Within" 시간 제약, 이탈자 세그먼트 [11]. 단 탐색 · 퍼널 보고서는 데이터 보존 2/14개월 설정에 묶임 [12]. 원시 데이터는 BigQuery 내보내기(하루 100만 이벤트 한도, Spark는 샌드박스 60일 만료) [13][14] | 퍼널 · 리텐션 기본 제공, 시간 창 지정 가능 | 퍼널 · 리텐션 기본 제공 | 직접 SQL/BigQuery로 만든다 |
| 광고 식별자 · ATT | 분석은 ATT 불필요(Apple 정의상 다른 회사 데이터와 광고 목적으로 연결할 때만 "추적") [15]. 단 기본값은 Android 광고 ID 수집, iOS는 AdSupport가 링크돼 있으면 IDFA 수집 [10]. 끄는 설정 있음(아래) | IDFA 사용 언급 없음 [3] | 기본 IDFA 미수집, 플러그인으로만 [16] | 식별자 직접 통제 |
| 광고 성과(커플당 획득 비용) | Google Ads · AdMob과 같은 계열이라 연결 쉬움. 메타 등 다른 채널은 별도 | 광고 연동 약함 | 연동 다수(유료 기능 다수) | 없음 |

## 권장 구성 (Firebase Analytics)

1. **의존성**: `dev.gitlive:firebase-analytics:2.1.0`(지금 GitLive 버전과 맞춤) + Android `com.google.firebase:firebase-analytics`(BoM 33.16.0). iOS SPM은 `FirebaseAnalytics`가 아니라 **`FirebaseAnalyticsCore`**(AdSupport 없는 변형) [17]. GitLive appleMain cinterop이 Core 변형으로 링크되는지는 구현 티켓에서 확인 필요.
2. **광고 ID 차단**: 
   - Android `AndroidManifest.xml` `<meta-data android:name="google_analytics_adid_collection_enabled" android:value="false"/>` [18]
   - iOS `Info.plist` `GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS = NO` [17]. AdMob이 AdSupport를 링크하므로 "Core 변형만으로 IDFA가 안 잡힌다"고 단정할 수 없다 — 이 플래그와 GA4 ↔ AdMob/Google Ads "광고 개인 최적화" 연결을 끄는 것으로 막는다.
   - 결과적으로 ATT 팝업은 지금처럼 광고 화면 진입 시에만 뜨고, 분석 때문에 추가로 띄우지 않는다.
3. **`BizEvent` 이동**: `logging/domain`의 `BizEvent`/`BizLogger`를 KMP `commonMain`으로 옮기고, 구현체 하나가 `Firebase.analytics.logEvent(name, parameters)`를 호출 [1]. 매핑 시 주의:
   - GA4 파라미터 값은 문자열 · 숫자만 → `Any?`에서 null은 버리고 Boolean은 문자열/숫자로.
   - `flags_resolved`는 플래그마다 `flag_*` 파라미터가 생긴다 → 이벤트당 25개 한도 [6]. 플래그가 많아지면 사용자 속성(25개, 값 36자)으로 옮기거나 쪼갠다.
   - 이름 40자, 값 100자 한도 [6].
   - `setUserId`에는 Firebase Auth uid(불투명 값)만. 전화번호 · 이메일 · 닉네임 금지(§4).
4. **연결 퍼널 이벤트 추가**(별도 티켓): 예) `invite_shared`, `invite_opened`, `pairing_completed`. 7일 이탈은 자동 이벤트 `first_open` → `pairing_completed`, Within 7일 퍼널 [11]. "상대가 훈련소 기간"은 입대일 기준 계산값(예: `partner_in_training = 0/1`)을 이벤트 파라미터로 — 날짜 원문 대신 계산된 숫자만.
5. **커플 수는 서버로**: 연결 완료는 Functions가 처리하는 사건이므로 커플당 비용의 분모는 Firestore/Functions 집계를 원본으로 삼고 GA4 수치는 교차 확인용.
6. **데이터 보존**: GA4 속성 설정에서 이벤트 데이터 보존을 14개월로 올린다(기본 2개월이면 퍼널 탐색이 2개월만 본다) [12].
7. **처리방침**: "Google Analytics for Firebase — 앱 사용 기록(화면 · 선택 · 숫자), 앱 인스턴스 ID, 기기 정보. 입력한 글자는 보내지 않음"을 추가. 스토어 라벨에 사용 데이터 · 진단/식별자 항목 반영(추적 목적 아님).

## 다른 후보를 고르지 않은 이유

- **PostHog**: 기능(퍼널 · 리텐션 · 피처 플래그)은 가장 좋고 무료 한도도 넉넉하다. 그러나 공식 KMP SDK가 0.x이고 iOS 링크에 Kotlin 2.4.0+가 필요해 지금 툴체인(2.3.21)과 맞지 않으며, 새 처리자 · 국외 이전 고지가 늘어난다. Firebase로 시작한 뒤 퍼널 분석이 부족하면 다시 볼 후보.
- **Amplitude**: 공식 KMP SDK 없음 → expect/actual로 Android Kotlin SDK + iOS Swift SDK를 각각 감싸야 한다. 무료 한도는 크지만 얻는 것 대비 통합 비용이 크다.
- **자체 Functions 수집**: 처리자가 늘지 않는 장점뿐이다. 이벤트당 Firestore 쓰기 비용이 §5의 "지금 오가는 양에만 비례하는 변동비" 원칙에 새 항목을 더하고, 큐 · 재시도 · 대시보드를 직접 만들어야 한다. 단, 위 5번처럼 "연결 완료" 같은 서버 사건 집계는 이미 서버에 있으므로 그것만 쓴다.

## 열린 질문

- GitLive `firebase-analytics` appleMain이 SPM `FirebaseAnalyticsCore`만으로 링크되는지(구현 티켓에서 빌드로 확인).
- GitLive 2.1.0 → 2.7.0 업그레이드는 BoM 34 계열로 넘어가므로 별도 결정(`libs.versions.toml` 주석 참고).
- 광고 채널이 Google Ads 외(메타 · 커뮤니티 배너)면 채널별 획득 비용은 GA4만으로 못 낸다 → 초대 링크/스토어 링크의 UTM · 캠페인 파라미터 설계가 필요.

## 출처

1. GitLive firebase-analytics commonMain API — https://github.com/GitLiveApp/firebase-kotlin-sdk/blob/master/firebase-analytics/src/commonMain/kotlin/dev/gitlive/firebase/analytics/analytics.kt (source sets: androidMain, appleMain, jsMain, jvmMain)
2. Maven Central `dev.gitlive:firebase-analytics` 버전 목록(1.13.0 … 2.1.0 … 2.7.0, 3.0.0-alpha02) — https://repo1.maven.org/maven2/dev/gitlive/firebase-analytics/maven-metadata.xml ; 릴리스 — https://github.com/GitLiveApp/firebase-kotlin-sdk/releases
3. PostHog Kotlin Multiplatform 문서 — https://posthog.com/docs/libraries/kmp
4. Maven Central `com.posthog:posthog-kmp` (latest 0.10.0, 2026-10-27) — https://repo1.maven.org/maven2/com/posthog/posthog-kmp/maven-metadata.xml
5. 서드파티 Amplitude KMP — https://klibs.io/project/nathanfallet/amplitude-kmp ; 공식 Kotlin SDK — https://github.com/amplitude/Amplitude-Kotlin
6. GA4 수집 · 구성 한도 — https://support.google.com/analytics/answer/9267744
7. Firebase 요금(Analytics 무료, Firestore · Functions 무료 할당) — https://firebase.google.com/pricing
8. PostHog Product Analytics 요금 — https://posthog.com/docs/product-analytics/pricing , https://posthog.com/pricing
9. Amplitude 요금 — https://amplitude.com/pricing
10. Firebase/GA 자동 수집 데이터(광고 ID · IDFA/IDFV · 앱 인스턴스 ID) — https://support.google.com/firebase/answer/6318039
11. GA4 퍼널 탐색(Within 시간 제약, 개방/폐쇄 퍼널) — https://support.google.com/analytics/answer/9327974
12. GA4 데이터 보존(탐색 · 퍼널 보고서에만 적용) — https://support.google.com/analytics/answer/7667196
13. Firebase BigQuery 내보내기(Analytics 하루 100만 이벤트 한도, 샌드박스) — https://firebase.google.com/docs/projects/bigquery-export
14. BigQuery 샌드박스 한도(10 GiB, 월 1 TiB 쿼리, 60일 만료) — https://docs.cloud.google.com/bigquery/docs/sandbox
15. Apple 사용자 개인정보 및 데이터 사용(추적 정의, ATT) — https://developer.apple.com/app-store/user-privacy-and-data-use/
16. Amplitude iOS Swift SDK(IDFA는 플러그인, COPPA 제어) — https://amplitude.com/docs/sdks/analytics/ios/ios-swift-sdk
17. Firebase Analytics 데이터 수집 설정(iOS: FirebaseAnalyticsCore, IDFV · 광고 개인화 플래그) — https://firebase.google.com/docs/analytics/configure-data-collection
18. Firebase Analytics 데이터 수집 설정(Android: `google_analytics_adid_collection_enabled`) — https://firebase.google.com/docs/analytics/android/configure-data-collection
