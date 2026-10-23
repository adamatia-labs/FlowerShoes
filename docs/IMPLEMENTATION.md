# 꽃신 구현 노트

설계안 v6(+ 아래 v7 수정)을 코드로 옮기면서 정한 것과 진행 상황을 적는다.
설계 원문은 별도로 관리하고, 이 문서는 **설계와 코드가 어디서 만나는지**만 다룬다.

## v7 수정 (v6 리뷰 반영, 7-3-A 백업 복원)

1. **새 폰 토큰 전달**
   - 승인 문서가 생기면 트리거 `onRotationApproved`가 기기 상태만 바꾼다. 새 기기는 active, 옛 기기는 active=false.
   - 새 폰은 callable `finalizeDevice`를 직접 부른다. 이 함수는 **새 기기 키로 서명한 챌린지**를 확인하고, 커스텀 토큰을 발급한 뒤 `revokeRefreshTokens`를 부른다.
2. **빠진 부분 메우기**
   - 받는 쪽 커서가 상대 로그의 floor 아래면, 보내는 쪽은 재전송 대신 스냅샷을 보낸다.
   - 스냅샷에는 그 사람이 쓴 행 전체와, 그 사람 기기가 마지막으로 고친 일정이 들어간다.
3. **오래된 백업 키**
   - 상대 폰이 MAC을 검증할 수 없으면 자동 승인하지 않고 7-3-B(상대 넘겨주기)로 안내한다.
4. **연달아 오는 교체**
   - 24시간 안에 두 번째 교체 요청이 오면 자동 승인하지 않는다.
5. **탈퇴**
   - callable 함수가 Admin SDK로 처리한다.

## 구현하면서 정한 것

| 항목 | 설계 | 구현 | 이유 |
|---|---|---|---|
| 백업 키 파생 | HKDF | BLAKE2b 키 해시(용도 문자열) | libsodium에 HKDF가 없다. 같은 성질(용도별 독립 키)을 BLAKE2b로 얻는다 |
| 폐기 기기 Op 거부 | seq가 주, createdAt은 보조 | **seq로만** 판정 | createdAt은 시계가 어긋나면 정상 Op을 버린다 |
| 리플레이 길이 | 3~8초, 마지막 150ms 크로스페이드 | 그리기는 최대 7,850ms, 그 뒤 150ms 크로스페이드 | 마지막 획이 그려지는 도중에 덮이지 않게 |
| 리플레이 점 사이 | — | 남은 점 사이를 시간 비율로 이어 그림 | 간소화로 직선 위의 점이 빠지면 획이 끝에 한 번에 나타났다 |
| 그림 첨부 저장 | Storage | **Firestore `couples/{c}/blobs`** (base64, 1MB 한도) | GitLive Storage에 바이트 다운로드 API가 없다. 그림 문서는 5~15KB. 큰 사진은 Storage로 옮길 것 |
| 압축(zstd) | 그림 문서 · 백업 | **구현됨** `:core:compression` | 그림 문서: 저장 · 전송 시 zstd 프레임(작아질 때만), `decode` 는 프레임/원문 둘 다 읽는다. 백업 조각 · 첨부: 1바이트 표시(0 원문 · 1 zstd) 뒤 암호화. Android · jvm = zstd-jni(에뮬레이터에서 `28 b5 2f fd` 확인), iOS = Swift libzstd(SPM)를 시작 때 `Zstd.codec` 에 꽂음(macOS 빌드 미확인) |
| 전역일 계산 | — | 민법 기간 계산(같은 날의 전날, 그런 날이 없으면 그 달 말일) | 8월 31일 입대 같은 경우를 맞추기 위해 |
| 복무 규칙 기본값 | 시행일별 표 | 육군·해병 18, 해군 20, 공군 22, 사회복무 21개월, 진급 2/6/6 | **출시 전 병무청 공지와 대조 필요** |
| 테마 토큰 | Figma 변수 | Figma에서 받은 값. 시안에 쓰이지 않은 `bg/brand`와 다크 테두리 일부는 팔레트 단계로 추정 | `core/designsystem/.../Colors.kt`에 표시 |
| DI | Koin | 전부 Koin. 계측 모듈(TTI · jank · 로깅 · 플래그) · 기기 정보 · DataStore 도 옮겼다(`TelemetryModules`) | Hilt 는 KMP 미지원 |
| 화면 이동 | Navigation Compose | 탭과 덮어 띄우는 화면을 상태로 관리, 시스템 뒤로 가기, 딥링크 `kkotsin://letter|calendar|home|pairing` | 화면 수가 늘면 NavHost 로 옮긴다 |

## 모듈과 검증

| 모듈 | 내용 | 검증 |
|---|---|---|
| `:core:model` | 도메인 모델 | 컴파일 |
| `:core:military` | 규칙 표 · 전역일 · 진급일 · 호봉 · D-day | jvmTest 9 |
| `:core:sync` | 스트림 · 커서 · pending · 재전송/floor/스냅샷 판단 · LWW · cutoff | jvmTest 13 |
| `:core:drawing-engine` | 문서 · TLV(모르는 요소 건너뜀) · 간소화 · 지우개 구간 · 리플레이 | jvmTest 11 |
| `:core:crypto` | 기기 키 · 커플 키 · Op 봉투 · SAS 커밋 · 회전 MAC · 복구 코드 · 공유 벡터 | jvmTest 10 |
| `:core:database` | Room KMP 19개 테이블 · DAO · 플랫폼별 빌더 · 스키마 JSON | jvmTest 4 |
| `:core:data` | 쓰기+SyncOp 트랜잭션, 반영기, Repository, 동기화 클라이언트, 연결, 기기 교체 | jvmTest 20(두 폰 통합 11 포함) |
| `:core:designsystem` · `:core:drawing-ui` | Figma 토큰 4테마 × 라이트/다크, 컴포넌트, 렌더러 · 리플레이 플레이어 | 에뮬레이터 |
| `:core:auth` | 로그인 계약 + SDK 연결 전 로컬 구현 | 컴파일 |
| `:core:firebase` | Firestore 전송 어댑터(GitLive) · 푸시 토큰 | Android 컴파일(실제 프로젝트 없음) |
| `:core:widget-bridge` | 위젯 스냅샷 · 발행기 | 에뮬레이터 |
| `:core:notification` | 알림 계획(예산 50) · DB 변화 시 다시 계획 | jvmTest 5 + Android 알람 등록 확인 |
| `:core:backup` | 암호화 백업 엔진 · 복구 코드 keywrap | jvmTest 7 |
| `:feature:*` | 온보딩 · 홈 · 캘린더 · 편지 · 그림 · 추억 · 연결 · 설정 | 에뮬레이터에서 직접 확인 |
| `:composeApp` · `:app` | 셸 · DI · Glance 위젯 4종 · 로컬 알림 · FCM 수신 · 동기화 예약 | 에뮬레이터 |
| `firebase/` | 보안 규칙 · Functions(알림 정책 · 연결 · 교체 · 위젯 · 카카오 · 해제 · 탈퇴) | 규칙 에뮬레이터 13, 함수 단위 11 |
| `iosApp/` | XcodeGen · 앱 진입점 · NSE · 위젯 · 벡터 테스트 | **빌드 안 됨(Windows)** |

## 아직 안 된 것 (다음 순서)

1. **Firebase 프로젝트 연결**
   - `google-services.json` · `GoogleService-Info.plist`를 넣는다.
   - `firebase deploy`(규칙 · 함수 · TTL · Storage lifecycle 32일)를 실행한다.
   - 함수 파라미터 `KAKAO_APP_ID`와 `KAKAO_ADMIN_KEY`를 설정한다.
2. **소셜 로그인 키**
   - Android 는 붙었다: `AndroidAuthGateway`(카카오 SDK → 함수 `kakaoSignIn`, Google Credential Manager, Apple OAuth). `local.properties` 에 `KAKAO_NATIVE_APP_KEY` · `GOOGLE_WEB_CLIENT_ID` 를 넣고 Firebase 가 연결되면 동작한다. 키가 없으면 그 제공자는 "설정 전"으로 안내한다.
   - iOS 로그인 작성됨(macOS 빌드 미확인): Swift `SocialSignIn`(카카오 SDK · Sign in with Apple(nonce) · GoogleSignIn) → 공통 `FirebaseAuthGateway`(core:firebase, Android 컴파일 확인). 키는 `Config.xcconfig` 의 KAKAO_NATIVE_APP_KEY · GOOGLE_CLIENT_ID · GOOGLE_REVERSED_CLIENT_ID.
   - Apple 토큰 폐기 구현됨: iOS 가 authorization code 를 함수 `appleStoreToken` 에 넘기면 refresh token 을 `appleTokens/{uid}`(클라이언트 접근 불가, 규칙 테스트)에 보관하고 `deleteAccount` 가 `/auth/revoke` 한다. client secret(ES256 JWT)은 테스트로 확인. 함수 파라미터 APPLE_TEAM_ID · APPLE_KEY_ID · APPLE_CLIENT_ID · APPLE_PRIVATE_KEY 가 비면 건너뛴다.
   - Android 의 Apple 로그인(Firebase 웹 흐름)은 authorization code 를 주지 않아 폐기 대상이 아니다 — 심사 기준은 iOS 앱에 적용된다.
3. **iOS 키 보관함**
   - Keychain(access group 공유, ThisDeviceOnly)으로 `KeyVault`를 구현한다.
   - Android는 끝났다. `AndroidKeyVault`가 Keystore AES-GCM으로 감싼 파일을 쓰고, 재시작 뒤에도 키가 남는 것을 확인했다.
4. **백업 남은 것**
   - Android는 붙었다: `BackupManager`, `AndroidBackupSource`(VACUUM INTO), Firebase Storage 어댑터(서버 설정이 없으면 이 폰 안의 파일 저장소), Block Store 키 동기화, 설정의 지금 백업 · 복구 코드, 복원 화면, 백그라운드 자동 백업. 에뮬레이터에서 백업 → 일정 삭제 → 복원 → 재시작 → 일정이 돌아오는 것을 확인했다.
   - 복원 직후 자동 교체는 끝났다: `PostRestoreRotation` 이 동기화 때마다 요청 → 상대 확인 대기(배너) → finalize → 토큰 재로그인. 남은 것: iOS(iCloud 키체인 · Storage), Block Store 복원 시점 실기기 확인.
5. **나머지 흐름**
   - 7-3-B 상대 넘겨주기는 끝났다: `RelinkSession`(커밋 → 공개 → SAS → cutoff → epoch+1 → 공유 데이터 전체 스냅샷), 함수 `onRelinkStep`, 규칙, 연결 · 설정 화면. 남은 것은 넘겨주기 전 기기 생체 인증
   - 탈퇴 화면(서버 callable · 로컬 정리는 있다)
   - 기기 인증은 끝났다(Android): 연결 해제 · 복구 코드 보기 · 연인 폰 교체 받기 앞에 BiometricPrompt(생체 또는 PIN). 화면 잠금이 없는 기기는 경고 후 진행
   - 연결 해제는 끝났다: `UnlinkService`(키 · 동기화 상태 정리, 보관/모두 지우기), 설정 화면, callable `unlinkCouple`
   - 자동 승인하지 못한 교체 요청(`SyncScheduler.pendingManual`)을 안내하는 화면
   - 기기 교체 자동 승인은 동기화 루프에 연결돼 있다(`RotationApprover`, Firestore 어댑터 `FirestoreRotation`).
6. **PNG 렌더러** — Android는 끝났다(`:core:media` `DrawingPng`). 저장할 때 완성 PNG를 만들고, 동기화 · 위젯이 그 PNG를 쓴다. iOS 구현(Compose + Skia)은 macOS 빌드 확인이 남았다.
8. **iOS**
   - macOS에서 `xcodegen`으로 프로젝트를 만든다.
   - Kotlin/Native로 libsodium · Room · DataStore를 빌드한다. 설계 10장 0단계 PoC의 남은 항목이다.
   - iOS용 Firebase 어댑터를 붙인다.
   - NSE 메모리를 측정한다.
   - App Group으로 `link.json`과 받은 Op 대기열을 합치는 처리를 붙인다.

## 빌드 · 테스트

```
# Kotlin(Windows 에서는 jvmTest, iOS 타깃은 macOS 에서만)
./gradlew :core:military:jvmTest :core:sync:jvmTest :core:drawing-engine:jvmTest :core:crypto:jvmTest \
          :core:database:jvmTest :core:data:jvmTest :core:notification:jvmTest :core:backup:jvmTest
./gradlew :app:assembleDevDebug

# 서버
cd firebase/functions && npm install && npm test
cd firebase/rules-test && npm install && npm test   # Firestore 에뮬레이터
```

## iOS 어댑터(작성됨, macOS 빌드 미확인)

- `IosKeyVault`: Keychain(access group 공유, ThisDeviceOnly). NSE · 위젯용 개별 항목(coupleKey.{epoch}, device.signSecret, partner.signPub)도 함께 쓴다.
- `IosPlatformActions`: LAContext 기기 인증. `IosNotificationScheduler`: UNUserNotificationCenter.
- App Group: 위젯 스냅샷, `link.json`(WidgetSender), NSE 대기열 합치기(`mergeNseQueue` → `SyncClient.receiveQueued`).
- `KkotsinApp.swift`: GoogleService-Info.plist 가 있을 때만 Firebase configure, 딥링크, 앞으로 나올 때 `onForeground()`.
- 남은 것: macOS 에서 `xcodegen` → 빌드 → 실기기 확인, iCloud 키체인 백업 키 동기화.

## 실제 커플 동기화 확인(로컬 Firebase 에뮬레이터, 2026-10-22)

`google-services.json` 없이 Auth · Firestore · Functions · Storage 에뮬레이터(`demo-kkotsin`)와 Android 에뮬레이터 두 대로 끝까지 돌렸다.

1. `cd firebase && firebase emulators:start --project demo-kkotsin --only auth,firestore,functions,storage`
   - 함수 파라미터는 `functions/.env.demo-kkotsin`(데모 값)
2. `local.properties` 에 `FIREBASE_EMULATOR_HOST=10.0.2.2` → `./gradlew :app:assembleDevDebug`
   - 디버그 빌드만 10.0.2.2 평문 허용, 로그인은 Auth 에뮬레이터 이메일 계정(규칙이 익명을 막는다)
3. 확인한 것
   - 두 폰 로그인 → 초대 코드 → 커밋 · 공개 → 양쪽 같은 SAS(387 324) → 함수 `onPairingStep` 이 couples 문서 생성 → 연결
   - 보고싶어: 곰신 → 군인 우편함(inbox/{device}/ops 2건) → 군인 폰 반영
   - 앱이 열려 있으면 Firestore 초인종(`Mailbox.rings`)으로 5초 안에 반영(FCM 은 앱이 꺼져 있을 때)
   - 그림 편지: 군인 → 곰신, zstd 문서 + PNG 가 blob 으로 와서 그대로 보이고 리플레이 가능
4. 고친 버그: 홈의 `linked` 를 아무도 채우지 않아 연결 뒤에도 "연결 전"이었다 → 커플 테이블을 관찰, 연결되면 "연인의 마음"(오늘 받은 보고싶어) 카드

## 실제 Firebase 프로젝트(kkotsin-dev) 배포 · 확인(2026-10-22)

배포: Firestore(서울 asia-northeast3, 규칙 · 인덱스 · 우편 TTL) · Storage(서울 기본 버킷, 규칙) · Functions 13개(서울, 배포 이미지 1일 정리 정책) · Auth(일반 Firebase Auth, 이메일 로그인은 개발용).

- 설정 파일(`app/google-services.json`, `iosApp/App/GoogleService-Info.plist`)은 커밋하지 않는다. `firebase apps:sdkconfig` 로 받는다.
- 함수 파라미터: `firebase/functions/.env.kkotsin-dev`. KAKAO_APP_ID 는 아직 0(카카오 앱을 만들면 바꿔서 재배포), Apple 키는 비어 있어 토큰 보관 · 폐기를 건너뛴다. 비밀 값은 이 파일에 넣지 않는다.
- 개발 확인용 로그인: `local.properties` 의 `DEV_EMAIL_LOGIN=true`(디버그 빌드만).

Android 에뮬레이터 두 대로 확인한 것(실제 프로젝트):
1. 두 계정 생성 → 초대 코드 → 같은 SAS(469 459) → 함수 `onPairingStep` 이 couples 문서 생성 → 두 폰 연결
2. 보고싶어 양방향 2초 안 반영(앱이 열려 있을 때 Firestore 초인종)
3. 그림 편지: zstd 문서 + PNG 가 암호화 blob 으로 도착해 그대로 보이고 리플레이 가능
4. FCM: 받는 앱이 뒤에 있을 때 편지 → `onInboxCreated` → 화면 알림 2초. 보고싶어는 정책대로 하루 첫 번째만 화면 알림, 이후는 data 만
5. 백업: 실제 Storage 에 keywrap · blobs · db 조각 · manifest(모두 암호문)

확인하며 고친 것:
- FCM 토큰이 로그인 전에 발급돼 등록이 빠졌다 → 로그인 상태가 될 때마다 현재 토큰 등록
- 보낸 폰 시계가 조금 빠르면 일반 편지를 예약 편지로 보고 "예약 편지가 열렸어요" 알림을 걸었다 → 2분 이내 미래는 시계 차이로 본다(테스트 추가)
- FCM 화면 알림 아이콘 · 색(하트, rose)


## Figma 최종 화면(디자인 › ✅ 최종 화면, 8개 흐름) 구현 · 확인(2026-10-23)

두 에뮬레이터(곰신 · 군인)로 kkotsin-dev 에 붙여 흐름마다 Figma 프레임과 나란히 비교했다. iOS 는 CI(macOS)에서 빌드 · 실행 확인.

| 흐름 | 화면 | 상태 |
|---|---|---|
| 1 온보딩 | 로그인 · 약관 동의 · 약관 문서 3종(서비스 · 개인정보 · 광고성 정보) · 역할 · 테마 · 입대일(시트) · 연결 · 복원 5종 | 구현 · 확인 |
| 2 홈 | 연결 뒤 · 연결 전 곰신 · 연결 전 군인 · 탭 바 | 구현 · 확인 |
| 3 캘린더 | 접힘 · 펼침 · 빈 + 일정 시트 · 휴가 관리 · 상세 · 사용 등록 · 추가 | 구현 · 확인(두 폰 동기화) |
| 4 편지 | 편지함 · 만들기 시트 · 편지 쓰기 · 받은 편지 · 빈 편지함 | 구현 · 확인 |
| 4-1 그림 | 펜 · 그리는 중 · 밝기 · 사진 · 스티커 · 틀 · 빈 캔버스 · 나가기 · 보내기 · 보낸 뒤 알림 · 재생 중 · 다 봄 | 구현 · 확인 |
| 5 추억 | 타임라인 · 빈 추억 · 그림 상세 | 구현 · 확인 |
| 6 백업 · 재연결 · 해제 | 연결 해제(설정 · 상대 폰) · 복구 코드 · 기기 교체 상대 폰 알림 | 구현 · 확인(상대 쪽 두 화면은 동기화 결과로 뜬다) |
| 7 설정 | 설정 · 테마 · 알림 · 백업 · 군 정보 · 로그인 계정 · 문의하기 · 문의 내역 | 구현 · 확인(답변 알림 포함) |

Figma 와 다르게 한 것(이유):

- 편지함 분류: 채운 화면(전체 · 그림 있는 편지)과 빈 화면(전체 · 편지 · 그림)이 달라 빈 화면 쪽으로 맞췄다.
- 재연결 화면의 확인 코드(SAS)는 유지했다(Figma 는 생략) — 바꿔치기 방지가 설계의 핵심이다.
- 공군 복무 22개월(Figma 예시 21개월): 규칙표(ServiceRulesTable)를 따른다.
- 휴가 관리 입구: Figma 는 연결 전 군인 홈의 "남은 휴가" 칸뿐이라, 연결 뒤에도 들어갈 수 있게 설정 › 군 정보에 "휴가 관리" 줄을 더했다.
  휴가 수정 화면에 "휴가 지우기"(Figma 에 없음)를 두었다. 쓴 날은 캘린더 휴가 일정으로 남고, 일정을 지우면 남은 일수가 돌아온다.
- 문의 스크린샷: Storage 대신 문의 문서 아래 shots/{0..2} 에 base64 JPEG(한 장 700KB 이하) — 공통 코드로 두 플랫폼이 같다.
  문의 내역의 제목은 답변하면서 관리자가 쓰는 title, 없으면 본문 첫 줄. 답변은 콘솔 · 관리자 SDK 로 users/{uid}/inquiries/{id}.answer 를 쓰면
  onInquiryAnswered 가 answeredAt 을 채우고 알림을 보낸다.

처음엔 빠졌다가 채운 Figma 기능(2026-10-23):

- 편지 쓰기 › 사진(최대 3장, 무작위 키 blob 으로 전달) · 글꼴(기본 · 손글씨 · 명조 — 받는 폰도 같은 글꼴). DB v3(letter.font).
- 로그인 계정 › 다른 로그인 방법 연결(Apple · Google, Firebase linkWithCredential). 실제 연결은 Firebase 콘솔에서
  Apple · Google 제공자와 앱 SHA-1 을 등록해야 끝까지 된다(kkotsin-dev 는 미등록 — 에뮬레이터에서는 OAuth 화면까지 확인).
- 추억: 만남 카드 사진(일정에 photoIds, DB v4, blob 전달) · 하트 수(받은 편지 › 고마워 = HEART 영수증) ·
  그림 상세 "위젯에 n시간 표시됨"(그림 위젯이 홈 화면에 있을 때만, 받은 폰에서 센다).
- 약관: 회사명 Adamatia Labs · 시행일 2026.10.23(사용자 지정).

이번에 함께 고친 버그:

- 같은 두 사람이 다시 연결하면 초대한 폰이 옛 커플 id 에 묶이던 문제(linkedVia).
- 이름(닉네임)이 로컬에만 저장돼 상대 화면이 계속 "연인"이던 문제(UserPut Op).
- 상단 바 좌우 여백: Figma 는 설정 · 재연결 · 받은 편지 · 약관 화면에서 상단 바가 화면 끝까지(뒤로 버튼 중심 22), 받은 그림은 16 — 20 을 더 넣던 것을 맞췄다.
- 연결 전 군인 홈 통계 칸: 안쪽 여백 12 · 이름 tertiary(Figma).
- 받은 편지 · 받은 그림 · 추억 그림 상세에서 탭 바가 보이던 것: Figma 처럼 전체 화면(아래 버튼은 제스처 바 위).
- 복원 · 연인 폰 확인 대기의 조사("연인가") · 상대 이름 고정값.
- 연결 해제 뒤 다시 연결하면 내가 쓴 것이 상대에게 가지 않던 문제: seq 는 1부터 다시인데 "올려 보낸 위치"(meta uploaded:*)가
  남아 새 Op 을 이미 보낸 것으로 봤다. 해제할 때 함께 지운다(EndToEndTest 회귀 테스트).

실기기(에뮬레이터 두 대) · 실서버(kkotsin-dev)로 직접 일으켜 확인한 것(2026-10-23):

- 상대 연결 해제: 곰신 계정으로 unlinkCouple → 군인 폰 "연인이 연결을 해제했어요"(보관 · 지우기) → 곰신 폰도 "MJ가 연결을 해제했어요". 이후 다시 연결(코드 · SAS).
- 상대 기기 교체: 곰신 폰 백업 → 앱 데이터 지우기 → 복구 코드로 복원 → 군인 폰 자동 승인 + "연인이 새 폰으로 바꿨어요" → 새 폰에서 보낸 보고싶어가 군인 폰에 도착.
- 복구 코드 비밀번호 관리자 저장: Credential Manager 로 Google 비밀번호 관리자 저장 시트가 뜨고 저장됨.
- 방금 보낸 편지가 다음 시계 틱(최대 30초)까지 편지함에 안 보이던 문제.
- 스티커 글자 크기를 px 이 아닌 sp 로 넘겨 기기 밀도마다 그림이 달라지던 문제.
- Android 위젯 PNG 가 스티커를 글리프로만 그리던 문제 — 화면과 같은 Compose 렌더러로 통일.
- 상대가 연결을 해제해도 이 폰이 알아채지 못하던 문제(서버의 내 coupleId 확인).
