# 카카오 피드 템플릿 · App Links · Universal Links 구성 조사 (#16)

조사일 2026-10-29. 기획서 §4(첫 연결 = 초대 링크 한 번, SAS 없음) · §8(카카오톡 피드 템플릿 공유 + OG 미리보기, 이름 외 개인 정보 없음, 만료 링크 화면)을 전제로 한다. 도메인은 Firebase Hosting `kkotsin-dev.web.app`(운영 프로젝트의 `*.web.app`)으로 시작하고, 경로를 고정해 나중에 커스텀 도메인으로 바꿀 수 있게 한다.

## 요약

- **공유는 카카오 피드 템플릿(사용자 정의 템플릿)**, 버튼은 앱 실행 링크(`execution_params`에 초대 코드)와 웹 링크(`https://<프로젝트>.web.app/i/{code}`) 두 가지. 앱 실행 링크는 `kakao{앱키}://kakaolink?…` 스킴으로 앱을 바로 연다 — 카카오톡 인앱 브라우저를 거치지 않으므로 App Links/Universal Links보다 확실하다.
- **App Links · Universal Links는 Hosting 정적 파일 두 개 + `headers` 설정**이면 된다. `*.web.app`도 공식 지원. 커스텀 도메인을 붙여도 `web.app`은 계속 살아 있으므로, 앱에 두 도메인을 모두 등록하면 예전 링크가 깨지지 않는다.
- **OG 미리보기는 Hosting `rewrites` → 2세대 함수(`asia-northeast3`)** 로 `/i/{code}`를 동적 렌더. 함수는 GET에서 아무것도 바꾸지 않는다(크롤러가 먼저 열기 때문).
- **deferred deep link는 무료 대안이 플랫폼별로 갈린다.** Android는 Play Install Referrer로 거의 공짜, iOS는 확실한 무료 방법이 없다. 권장: Android referrer + "설치 뒤 카톡의 초대 링크를 다시 눌러 주세요" 안내 + 코드 붙여넣기(iOS `UIPasteControl`). 유료 SDK(Branch 등)는 쓰지 않는다.
- **`kkotsin://`는 앱 내부용으로 유지**(알림 · 위젯). 카카오톡 대화 안 URL로는 커스텀 스킴을 링크할 수 없어 초대 링크로는 못 쓴다.

---

## 1. Kakao SDK로 피드 템플릿 공유

### 필요한 모듈

| 플랫폼 | 모듈 | 현재 저장소 |
|---|---|---|
| Android | `com.kakao.sdk:v2-share`(ShareClient) — 템플릿 클래스는 `v2-template`(share가 의존) | `kakao-user` 2.24.0만 있음 (`gradle/libs.versions.toml:57,140`) → `v2-share` 추가 필요 |
| iOS (SPM) | `KakaoSDKShare`(ShareApi) + `KakaoSDKTemplate` | `KakaoSDKUser`만 링크 (`iosApp/project.yml:87-88`) → 두 product 추가 |
| KMP | **공식 KMP SDK 없음.** 로그인처럼 `expect/actual` — Android는 Kotlin에서 직접, iOS는 Swift 브리지(`iosApp/App/SocialSignIn.swift` 패턴) | `Share.kt`의 `rememberPlatformShare()`는 지금 OS 공유 시트(ACTION_SEND / UIActivityViewController)에 텍스트만 넘긴다 |

출처: Android 모듈 표 https://developers.kakao.com/docs/ko/android/getting-started (`v2-share`는 2.11.0에서 `v2-link`에서 이름이 바뀜, 최소 API 23), iOS 모듈 표 https://developers.kakao.com/docs/ko/ios/getting-started (`KakaoSDKShare`, `KakaoSDKTemplate`; 2.23.0부터 CocoaPods 지원 종료, iOS 15+ · Xcode 26+).

### 콘솔 등록

- **앱 키**: 이미 로그인용 네이티브 앱 키가 있다(`KAKAO_NATIVE_APP_KEY`, Android `BuildConfig` · iOS `Info.plist` `KakaoNativeAppKey`). 공유도 같은 키.
- **Android 키 해시**: 로그인 때문에 이미 등록돼 있어야 함. 개발자별 debug 키, Play App Signing 키(SHA-1 → Base64) 모두. "키 해시가 등록되지 않은 앱에서는 카카오 API를 호출할 수 없습니다" (android/getting-started).
- **웹 도메인**: 템플릿 링크 · 스크랩에 쓰는 도메인은 `앱 > 제품 링크 관리 > 웹 도메인`에 등록해야 한다. 등록 안 된 도메인 · 스킴은 링크 구성요소에 쓸 수 없다. → `https://<프로젝트>.web.app` 등록, 커스텀 도메인 생기면 추가 등록(https://developers.kakao.com/docs/ko/message-template/common).
- **스토어 정보**: 앱 미설치 수신자가 앱 실행 버튼을 누르면 플랫폼별로 설정된 스토어 링크로 간다(우선순위: 앱 실행 → 스토어 → 모바일 웹 → PC 웹, message-template/common). Android 패키지 · iOS 번들 ID/앱스토어 ID를 플랫폼 정보에 넣는다.
- **사용자 정의 템플릿**: `[도구] > [메시지 템플릿]`에서 만든다 → 템플릿 ID.

### 앱 쪽 수신 설정

- 메시지 버튼은 `kakao${NATIVE_APP_KEY}://kakaolink?${execution_params}`로 앱을 연다(android-link, ios-link 문서).
- **Android**: `MainActivity`에 `VIEW`/`DEFAULT`/`BROWSABLE` + `<data android:scheme="${kakaoScheme}" android:host="kakaolink"/>` intent-filter 추가. 지금은 `oauth` 호스트만 `AuthCodeHandlerActivity`에 있다(`app/src/main/AndroidManifest.xml:78-84`). `kakaoScheme` 플레이스홀더는 이미 있음(`app/build.gradle.kts:47`). SDK 2.4.0 이상이면 `<queries>` 불필요.
- **iOS**: URL 스킴 `kakao$(KAKAO_NATIVE_APP_KEY)`와 `LSApplicationQueriesSchemes: [kakaokompassauth, kakaolink]`는 이미 있음(`iosApp/project.yml:125-128`). `onOpenURL`에서 `kakaolink` 호스트를 받아 `DeepLinks.open`으로 넘기면 된다.

### 기본 템플릿 vs 사용자 정의 템플릿

| | 기본(default) | 사용자 정의(custom) |
|---|---|---|
| 정의 | 요청마다 코드에서 JSON/객체로 | 콘솔 템플릿 빌더 + `templateId`, 동적 값은 `template_args` |
| 종류 | feed, list, location, commerce, text, calendar, scrap | feed, list, commerce |
| 이미지 | 1장, 최소 200×200 | 최대 3장, 최소 400×400 |
| 장점 | 앱만으로 완결 | **디자인 · 문구를 앱 업데이트 없이 콘솔에서 수정**, 미리보기 |

공통: 버튼 최대 2개(이름 8자 이하 권장), 제목 · 설명 각 2줄, 이미지 5MB 이하(https://developers.kakao.com/docs/ko/message-template/common).

**권장: 사용자 정의 피드 템플릿.** `template_args = { name: "<보낸 사람 이름>", code: "<초대 코드>" }` — 이름은 클라이언트가 공유 순간에 넣으므로 서버 조회가 필요 없다. 버튼 1 "꽃신에서 받기"(앱 실행: `execution_params: invite=${code}`), 버튼 2 또는 콘텐츠 링크 = 웹 `https://<도메인>/i/${code}`.

### 카카오톡 미설치(보내는 사람)

- `ShareClient.instance.isKakaoTalkSharingAvailable(context)` / iOS `ShareApi.isKakaoTalkSharingAvailable()`로 확인.
- 없으면 `WebSharerClient.makeCustomUrl(templateId, templateArgs)`(iOS `makeCustomUrl`) → Android `KakaoCustomTabsClient.openWithDefault`, iOS `SFSafariViewController`로 웹 공유(문서가 "웹 공유 사용 권장").
- 공유 성공 여부는 반환되지 않는다 — 웹훅으로만 확인(android-link). → 기획서의 "보낸 뒤 바로 홈 + 다시 보내기" 흐름과 맞음(성공을 기다리지 않는다).
- 카카오톡 최소 버전 Android/iOS 10.0.0 (https://developers.kakao.com/docs/ko/kakaotalk-share/common).
- 현실적 대안: 카카오톡이 없으면 OS 공유 시트로 `https://<도메인>/i/{code}` URL 텍스트를 보낸다(문자 · 다른 메신저). 이때 미리보기는 OG 페이지(3절)가 담당.

출처: https://developers.kakao.com/docs/ko/kakaotalk-share/android-link , https://developers.kakao.com/docs/ko/kakaotalk-share/ios-link

### 주의: 카카오톡 대화 안의 https 링크

- 카카오톡에서 URL을 누르면 **카카오톡 인앱 브라우저**가 열리고, App Links/Universal Links가 앱을 바로 열지 않는다는 보고가 반복된다(https://devtalk.kakao.com/t/ios-android/134879 , https://devtalk.kakao.com/t/applink/140357 , https://devtalk.kakao.com/t/branch/151358). 카카오 측 공식 답은 "인텐트 스킴, 커스텀 스킴은 톡내 URL로 링크 할 수 없으며", 웹 페이지를 거쳐 처리하라는 것.
- 그래서 **주 경로는 피드 템플릿의 앱 실행 버튼**, 웹 링크는 보조 경로로 두고 랜딩 페이지에 "앱에서 열기" 버튼(사용자 탭 → Universal Link/intent)과 스토어 버튼을 둔다. 실기기 확인 필요(Android/iOS × 카카오톡 최신).

## 2. Firebase Hosting `*.web.app`에서 App Links · Universal Links

### 서빙 방법

Firebase 공식 가이드(Dynamic Links 이전용, https://firebase.google.com/support/guides/app-links-universal-links): `*.web.app` 서브도메인은 "automatically provisioned at no cost", 파일 두 개를 `public/.well-known/`에 두고 `Content-Type: application/json` 헤더를 준다.

```json
"hosting": {
  "public": "hosting/public",
  "appAssociation": "NONE",
  "headers": [
    { "source": "/.well-known/assetlinks.json", "headers": [{ "key": "Content-Type", "value": "application/json" }] },
    { "source": "/.well-known/apple-app-site-association", "headers": [{ "key": "Content-Type", "value": "application/json" }] }
  ],
  "rewrites": [
    { "source": "/i/**", "function": { "functionId": "invitePage", "region": "asia-northeast3" } }
  ]
}
```

- `appAssociation` 기본값 `AUTO`는 Hosting이 Firebase 콘솔에 등록된 앱 정보로 두 파일을 **자동 생성**한다(Dynamic Links용). 직접 관리하려면 `NONE`으로 두고 정적 파일을 쓰는 게 명확하다(https://firebase.google.com/docs/hosting/full-config). 정적 파일은 rewrites보다 우선한다.
- 현재 `firebase/firebase.json`에는 `hosting` 블록이 없다 → 새로 추가.

**assetlinks.json** (https://developer.android.com/training/app-links/configure-assetlinks)
- HTTPS, 리다이렉트 없음, `application/json`.
- Play App Signing을 쓰면 로컬 keytool 지문이 아니라 Play Console `Release > Setup > App signing`의 SHA-256. dev/qa 빌드(`applicationIdSuffix .dev/.qa`, `app/build.gradle.kts:74,80`)는 패키지가 달라 statement를 따로 넣는다.
- 매니페스트: `<intent-filter android:autoVerify="true">` + `https` / host / `pathPrefix="/i/"`.
- Android 15+는 assetlinks.json에 dynamic rules(`dynamic_app_link_components`)를 둘 수 있지만 매니페스트 범위를 넓힐 수는 없다 — 지금은 불필요.

**apple-app-site-association** (https://developer.apple.com/documentation/xcode/supporting-associated-domains)
- 확장자 없음, `https://` + 유효한 인증서 + 리다이렉트 없음.
- `components` 형식: `{"applinks":{"details":[{"appIDs":["<TEAMID>.com.sadturtleman.kkotsin"],"components":[{"/":"/i/*"}]}]}}`
- 앱 entitlement `com.apple.developer.associated-domains: [applinks:<프로젝트>.web.app]` 추가(`iosApp/project.yml` entitlements, 지금은 없음).
- iOS 14+는 기기가 우리 서버가 아니라 **Apple CDN**에서 파일을 받는다. "Apple's content delivery network requests the … file for your domain within 24 hours. Devices check for updates approximately once per week." 개발 중엔 `?mode=developer`.

### 제약

- `web.app`은 Public Suffix List에 있어 서브도메인마다 독립 사이트 — 다른 프로젝트가 우리 `.well-known`을 건드릴 수 없다.
- 브랜드 노출: 링크에 `kkotsin-xxx.web.app`이 그대로 보인다(피드 템플릿에선 버튼 뒤에 숨음).
- Firebase 안내대로 App Links/Universal Links는 **스토어 이동과 설치 후 이어가기(deferred)를 제공하지 않는다**(같은 가이드).

### 나중에 커스텀 도메인으로 옮길 때

- Hosting에 커스텀 도메인을 연결해도 `*.web.app`은 같은 사이트를 계속 서빙한다. → 두 도메인 모두에 `.well-known` 파일이 존재.
- 앱은 **두 도메인을 함께** 등록해야 한다: Android intent-filter host 2개(각 도메인에서 assetlinks 검증), iOS entitlement에 `applinks:` 2개, 카카오 콘솔 웹 도메인 2개. 앱 업데이트 전 사용자에게는 새 도메인 링크가 웹으로 열린다 → 랜딩 페이지가 그 경우를 처리해야 함.
- 경로(`/i/{code}`)만 같으면 앱 파서는 호스트 목록만 늘리면 된다. 카톡 대화에 남은 예전 `web.app` 링크는 계속 동작(초대는 어차피 25시간 안에 만료 — `firestore.rules`의 `expireAt < request.time + 25h`).
- `web.app` → 커스텀 도메인으로 **리다이렉트를 걸면 안 된다**: `.well-known` 파일은 리다이렉트 금지. 리다이렉트를 쓰려면 `/.well-known/**`은 제외.

## 3. OG 미리보기 페이지(동적)

- **방법**: Hosting `rewrites`로 `/i/**` → 2세대 HTTPS 함수 `invitePage`(`region: asia-northeast3`, 프로젝트 기존 `REGION`, `firebase/functions/src/index.ts:35`). Hosting rewrite 지원 지역 목록에 `asia-northeast3` 포함, 다만 colocate 권장 지역(us-west1/us-central1/us-east1/europe-west1/asia-east1)은 아니라 약간의 지연. 요청 타임아웃 60초. `pinTag`로 Hosting과 함께 배포/롤백 가능(2세대만). 출처 https://firebase.google.com/docs/hosting/functions , https://firebase.google.com/docs/hosting/full-config
- **내용**: `og:title` = "{이름}님이 꽃신에 초대했어요", `og:description` 고정 문구, `og:image` 고정 이미지(정적 파일), `og:url`. 이름 외(사진 · 전화번호 · 이메일 · uid) 넣지 않는다. 만료 · 없는 코드면 같은 URL에서 "이 초대 링크는 만료됐어요" + "내가 초대 링크 보내기"(스토어/앱 열기) 화면, OG도 중립 문구.
- **이름 출처**: 지금 `pairings/{code}` 문서는 `uid`, `expireAt`만 허용(`firestore.rules:42-47`). 함수가 Admin SDK로 `users/{uid}`의 표시 이름을 읽거나, 초대 생성 시 `name` 필드를 함께 쓰게 규칙을 바꾼다(#15에서 결정). 피드 템플릿은 클라이언트가 `template_args`로 이름을 넣으므로 서버 이름 조회가 필요 없다 — OG 페이지(다른 메신저 · URL 직접 공유)에만 필요.
- **크롤러 주의**: 카카오 스크랩 서버 · 다른 메신저 미리보기 서버가 사람보다 먼저 URL을 연다. → **GET은 읽기 전용**, 초대를 소비/수락하지 않는다. 캐시는 `Cache-Control: private, no-store` 또는 짧은 `s-maxage`(만료 상태가 캐시에 남지 않게). 카카오 스크랩 캐시는 카카오 공유 디버거(https://developers.kakao.com/tool/debugger/sharing)로 초기화 가능 — 미검증. 방화벽이 카카오 스크랩 서버를 막으면 이미지가 안 뜬다(kakaotalk-share/common).
- 피드 템플릿 경로는 OG 크롤링을 쓰지 않는다(템플릿이 제목 · 이미지를 직접 가짐). OG 페이지는 OS 공유 시트 / 카톡 미설치 / 링크 복사 경로용.

## 4. 앱 미설치 수신자의 deferred deep link

Firebase Dynamic Links는 **2025-08-25 종료**, page.link · 커스텀 도메인 링크 모두 동작 안 함(https://firebase.google.com/support/dynamic-links-faq). Firebase 공식 대체 가이드도 deferred deep link는 제공하지 않는다고 명시.

| 방법 | 플랫폼 | 비용 | 신뢰도 · 비고 |
|---|---|---|---|
| **다시 누르기**(설치 후 카톡의 초대 링크를 다시 탭) | 둘 다 | 0 — 첫 실행 안내 문구 하나 | 링크가 카톡 대화에 남아 있어 자연스럽다. 로그인 뒤 연결이므로 어차피 흐름이 끊긴다. 만료 25h 안이면 OK |
| **Play Install Referrer** | Android | 낮음: `com.android.installreferrer:installreferrer:2.2`, 첫 실행 1회 조회 | 랜딩 페이지가 `https://play.google.com/store/apps/details?id=…&referrer=invite%3D{code}`로 보내야 함. 90일 유지(https://developer.android.com/google/play/installreferrer/library). 카카오 템플릿 버튼의 "스토어 이동"은 콘솔에 고정된 스토어 URL이라 referrer를 실을 수 없을 가능성 높음 — 미검증 |
| **클립보드** | iOS(Android도 가능) | 중간: 랜딩 페이지 "앱 설치" 버튼이 `navigator.clipboard.writeText(link)` 후 App Store로, 앱 첫 실행에 "초대 붙여넣기" | iOS 16+는 코드로 읽으면 "붙여넣기 허용" 알림, `UIPasteControl` 버튼을 누르게 하면 알림 없음(https://developer.apple.com/documentation/uikit/uipastecontrol). 자동 읽기는 UX · 심사 부담 → 버튼 방식만 |
| **코드 직접 입력** | 둘 다 | 낮음: `CrockfordCode` · 입력 UI가 이미 있음 | 최후 수단. 기획서는 "코드 입력 없음"을 지향하므로 숨은 대안으로만 |
| 직접 구현 fingerprint(IP · UA 매칭) | 둘 다 | 높음(서버 · 정확도 · 개인정보처리방침) | 권하지 않음 |
| 유료(Branch, AppsFlyer, Adjust) | 둘 다 | 월 비용 + SDK + 개인정보 고지 | 이 앱 규모 · 원칙(분석 최소화)과 안 맞음. 카톡 인앱 브라우저에서 Branch도 안 열린다는 보고 있음(devtalk 151358) |

**권장**: 다시 누르기 안내(기본) + Android Install Referrer(랜딩 페이지 경유 시) + iOS `UIPasteControl` 붙여넣기 버튼. 세 경로 모두 결국 같은 `DeepLink.Invite(code)`로 모인다.

## 5. 현재 커스텀 스킴 `kkotsin://pairing`과의 관계

- `DeepLink.parse`(`composeApp/.../app/DeepLinks.kt`)는 `kkotsin://`만 받고 `Pairing`은 **코드 없는** 객체다 — 지금은 페어링 화면을 열 뿐 초대 코드를 전달하지 않는다. Android는 `<data android:scheme="kkotsin"/>`(`AndroidManifest.xml:65`), iOS는 `CFBundleURLSchemes: [kkotsin]`.
- 지금 초대는 OS 공유 시트로 **코드 텍스트**를 보내고 상대가 입력하는 방식(`Share.kt` 주석) + SAS 확인(`firebase/functions/src/index.ts` `onPairingStep`).
- 변경 제안:
  - `DeepLink.Invite(code: String)` 추가. 입력 세 가지를 같은 값으로 정규화: `https://{web.app|커스텀 도메인}/i/{code}`(App/Universal Link), `kakao{KEY}://kakaolink?invite={code}`(피드 템플릿), referrer/클립보드 문자열.
  - `kkotsin://`는 알림 · 위젯 · 내부 이동용으로 그대로 둔다. 카톡 안 링크로 쓸 수 없고(카카오 답변), 다른 앱이 같은 스킴을 가로챌 수 있어 초대 코드를 실으면 안 된다.
  - 링크 형식은 `/i/{code}` 하나로 고정(커스텀 도메인 전환 대비).

## #15(페어링 프로토콜)에 영향 주는 점

1. **초대 코드 = 유일한 비밀(bearer capability).** SAS를 빼면 링크를 가진 사람이 곧 연결 상대다. 지금 코드는 8자 Crockford(40비트, `PairingSession.CODE_LENGTH = 8`)로 사람이 입력하는 길이다. 링크로만 오가면 길이 제약이 없으니 **128비트 이상(예: 26자 base32)** 으로 늘리는 게 싸다. Firestore 규칙은 "문서 id를 알면 get"이라 추측 공간이 곧 보안이다.
2. **링크는 사람보다 크롤러가 먼저 연다.** 카카오 스크랩 · 다른 메신저 미리보기 서버가 GET한다. 수락은 반드시 앱에서 로그인한 사용자의 명시적 동작(쓰기)이어야 하고, 웹 GET은 부작용이 없어야 한다. "1회용 링크" 소비 시점도 GET이 아니라 앱의 join 쓰기.
3. **카카오톡 인앱 브라우저가 Universal/App Links를 막을 수 있다.** 안정적 경로는 피드 템플릿 앱 실행 버튼(`kakao{KEY}://kakaolink`). 따라서 앱은 세 입력 경로(https, kakaolink, referrer/클립보드)를 모두 같은 join 흐름으로 받아야 한다.
4. **deferred가 iOS에서 불완전** → 수신자가 설치 · 로그인 사이에 코드를 잃을 수 있다. 만료(25h)가 "다시 누르기"를 감당할 만큼 길어야 하고, 만료 화면에서 "내가 초대 링크 보내기"로 역할을 바꿀 수 있어야 한다.
5. **OG 페이지에 이름을 띄우려면 서버가 초대 → 이름을 알아야 한다.** `pairings/{code}`에 `name` 필드를 허용할지, 함수가 `users/{uid}`를 읽을지 결정 필요. 피드 템플릿만 쓰면 서버 쪽 이름은 불필요.

## 검증 안 된 것 (실기기 · 콘솔에서 확인)

- 2026년 현재 카카오톡(Android/iOS) 인앱 브라우저에서 Universal Links/App Links 동작 여부.
- 앱 미설치 수신자가 피드 템플릿 앱 버튼을 눌렀을 때 정확한 이동(스토어 직행 vs 모바일 웹), 그리고 스토어 URL에 referrer를 실을 수 있는지.
- `appAssociation: "AUTO"` 상태에서 정적 `.well-known` 파일이 이기는지(문서상 정적 파일 우선이지만 `NONE` 권장).
- 카카오 스크랩 캐시 기간과 디버거 초기화 동작.
