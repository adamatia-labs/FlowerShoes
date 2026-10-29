# 꽃신 구현 노트

설계안 v6(+ 아래 v7 수정)을 코드로 옮기면서 정한 것과 진행 상황을 적는다.
설계 원문은 별도로 관리하고, 이 문서는 **설계와 코드가 어디서 만나는지**만 다룬다.

## v7 수정 (v6 리뷰 반영, 7-3-A 백업 복원)

1. **새 폰 토큰 전달**
   - 상대의 승인 답이 생기면 트리거 `onDeviceChangeAnswered`가 서명을 확인하고 기기 상태만 바꾼다. 새 기기는 active, 옛 기기는 active=false.
   - 새 폰은 callable `finalizeDevice`를 직접 부른다. 이 함수는 **새 기기 키로 서명한 챌린지**를 확인하고, 커스텀 토큰을 발급한 뒤 `revokeRefreshTokens`를 부른다.
2. **빠진 부분 메우기**
   - 받는 쪽 커서가 상대 로그의 floor 아래면, 보내는 쪽은 재전송 대신 스냅샷을 보낸다.
   - 스냅샷에는 그 사람이 쓴 행 전체와, 그 사람 기기가 마지막으로 고친 일정이 들어간다.
3. **자동 승인 없음(#20)**
   - 기기 교체는 언제나 상대가 직접 승인한다. MAC · 자동 승인 · 넘겨주기(7-3-B)는 지웠다.
4. **연달아 오는 교체**
   - 24시간 안에 두 번째 교체 요청이면 상대 화면에 경고하고 한 번 더 확인한다.
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
   - Android는 붙었다: `BackupManager`, `RoomBackupSource`(일관된 SQLite 스냅샷), Firebase Storage 어댑터(서버 설정이 없으면 이 폰 안의 파일 저장소), Block Store 키 동기화, 설정의 지금 백업 · 복구 코드, 복원 화면, 백그라운드 자동 백업. 에뮬레이터에서 백업 → 일정 삭제 → 복원 → 재시작 → 일정이 돌아오는 것을 확인했다.
   - 복원 직후 자동 교체는 끝났다: `PostRestoreRotation` 이 동기화 때마다 요청 → 상대 확인 대기(배너) → finalize → 토큰 재로그인. 남은 것: iOS(iCloud 키체인 · Storage), Block Store 복원 시점 실기기 확인.
5. **나머지 흐름**
   - 기기 교체는 하나의 흐름이다(#20 · #40): 새 폰 로그인 → `DeviceChangeSession` 요청 → 상대 폰 `DeviceChangeApprover` 승인(cutoff → epoch+1 커플 키를 새 기기 공개키로 봉인 → 공유 데이터 전체 스냅샷) · 거절, 옛 폰 "내가 아니에요"(승인 전 막기). 함수 `onDeviceChangeCreated` · `onDeviceChangeAnswered` · `finalizeDevice`, 규칙 `couples/{c}/deviceChanges`. 넘겨주기(`RelinkSession` · SAS) · 자동 승인(`RotationApprover`)은 지웠다.
   - 탈퇴 화면(서버 callable · 로컬 정리는 있다)
   - 기기 인증은 끝났다(Android): 연결 해제 · 복구 코드 보기 · 연인 폰 교체 받기 앞에 BiometricPrompt(생체 또는 PIN). 화면 잠금이 없는 기기는 경고 후 진행
   - 연결 해제는 끝났다: `UnlinkService`(키 · 동기화 상태 정리, 보관/모두 지우기), 설정 화면, callable `unlinkCouple`
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
   - 디버그 빌드만 평문 허용, 로그인은 Auth 에뮬레이터 이메일 계정(규칙이 익명을 막는다). `-PfirebaseEmulatorHost=127.0.0.1`과 `adb reverse tcp:PORT tcp:PORT`(8080·9099·9199·5001)로 USB 실기기도 로컬 서버에 연결한다.
   - 로컬 QA는 Firebase 자동 초기화를 비활성화한 뒤 `demo-kkotsin`으로만 연결한다. 실제 프로젝트가 이미 초기화되어 있으면 중단한다. 실제 RevenueCat·AdMob·FCM·Block Store는 사용하지 않는다. Release의 에뮬레이터 주소는 항상 비어 있다.
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
- 스티커(Figma 🌸 스티커 · 그림 / 스티커): 탭을 칩으로(하트 · 꽃 · 표정 · 일상 · 글자 · D-day · 군 생활 · 평생 · 봄 소풍 · 1,100원),
  그림 스티커 72장(Fluent Emoji PNG) · 글자 12(Jua 알약, 문구마다 색) · D-day 8(전역 · 휴가 · 면회 · 외박 · 진급 · 우리 N일 · N년, 실제 남은 날).
  군 생활(꽃신 평생) · 봄 소풍(1,100원)은 💰 수익 모델 페이지의 결제 상품이라 잠김(흐린 미리보기 + 안내)으로 둔다 — 결제는 스토어 설정이 필요하다.
- 재연결(상대 폰 넘겨주기): 지수 · 확인에 새 기기 이름 · 로그인 방법(Figma), 민준 · 확인 대기에 확인 코드를 계속 표시,
  민준 · 넘겨받는 중 진행률(퍼센트 · 남은 시간 · 편지 · 그림 · 일정 줄), 끝나면 넘겨받은 역할로 온보딩 없이 홈.

이번에 함께 고친 버그:

- 같은 두 사람이 다시 연결하면 초대한 폰이 옛 커플 id 에 묶이던 문제(linkedVia).
- 이름(닉네임)이 로컬에만 저장돼 상대 화면이 계속 "연인"이던 문제(UserPut Op).
- 상단 바 좌우 여백: Figma 는 설정 · 재연결 · 받은 편지 · 약관 화면에서 상단 바가 화면 끝까지(뒤로 버튼 중심 22), 받은 그림은 16 — 20 을 더 넣던 것을 맞췄다.
- 연결 전 군인 홈 통계 칸: 안쪽 여백 12 · 이름 tertiary(Figma).
- 받은 편지 · 받은 그림 · 추억 그림 상세에서 탭 바가 보이던 것: Figma 처럼 전체 화면(아래 버튼은 제스처 바 위).
- 복원 · 연인 폰 확인 대기의 조사("연인가") · 상대 이름 고정값.
- 연결 해제 뒤 다시 연결하면 내가 쓴 것이 상대에게 가지 않던 문제: seq 는 1부터 다시인데 "올려 보낸 위치"(meta uploaded:*)가
  남아 새 Op 을 이미 보낸 것으로 봤다. 해제할 때 함께 지운다(EndToEndTest 회귀 테스트).
- 상대 폰 넘겨주기에서 새 폰이 "연인이 확인하길 기다리는 중"에 멈추던 문제: 함수가 활성화하자마자 relinks/{code} 를 지워
  새 폰이 커플 id 를 못 읽었다. 확인 코드 단계에서 미리 읽어 둔다(RelinkTest — 가짜 서버도 지우게 맞췄다).

실기기(에뮬레이터 두 대) · 실서버(kkotsin-dev)로 직접 일으켜 확인한 것(2026-10-23):

- 상대 연결 해제: 곰신 계정으로 unlinkCouple → 군인 폰 "연인이 연결을 해제했어요"(보관 · 지우기) → 곰신 폰도 "MJ가 연결을 해제했어요". 이후 다시 연결(코드 · SAS).
- 상대 기기 교체: 곰신 폰 백업 → 앱 데이터 지우기 → 복구 코드로 복원 → 군인 폰 자동 승인 + "연인이 새 폰으로 바꿨어요" → 새 폰에서 보낸 보고싶어가 군인 폰에 도착.
- 복구 코드 비밀번호 관리자 저장: Credential Manager 로 Google 비밀번호 관리자 저장 시트가 뜨고 저장됨.
- 방금 보낸 편지가 다음 시계 틱(최대 30초)까지 편지함에 안 보이던 문제.
- 스티커 글자 크기를 px 이 아닌 sp 로 넘겨 기기 밀도마다 그림이 달라지던 문제.
- Android 위젯 PNG 가 스티커를 글리프로만 그리던 문제 — 화면과 같은 Compose 렌더러로 통일.
- 상대가 연결을 해제해도 이 폰이 알아채지 못하던 문제(서버의 내 coupleId 확인).

## Figma 🗂 최종 정리(178:83)에 맞춤(2026-10-23)

52:82(✅ 최종 화면)와 글자 · 화면을 하나씩 비교해 달라진 것을 맞췄다.

- 설명 줄 정리: 온보딩(로그인 · 역할 · 테마 미리보기 카드 · 입대일 도움말 · 연결), 복원(백업 찾음 · 코드 입력 · 복원 중 · 확인 대기),
  재연결 · 연결 해제 · 기기 교체 알림 · 복구 코드, 연결 전 홈 초대 카드. 홈 · 추억 머리의 D+, 설정 프로필의 D+ 도 뺐다.
- 복원 · 백업 찾음 버튼: 복원하기 · 복구 코드로 복원하기 · 복원 안 하기. 복원 중 "약 N분 남음"(지금까지 걸린 시간으로 어림).
- 편지함: 머리는 늘 "+ 편지 쓰기"(만들기 시트 · 그림만 보내기 없음), 카드 종류 알약 없이 "10.21 · 지수". 받은 편지 "지수 · 10.21 (화)", 리플레이 칩 "▶".
- 편지 쓰기 "그림 · 사진 = 글자": 그림 · 사진이 커서 자리에 글자 한 칸(U+FFFC)으로 들어가 글과 함께 흐른다(InlineObjects.kt).
  지우면 같이 빠지고 글자 수에 한 글자로 센다. 보낼 때 글 속 순서대로 블록이 된다. 도구는 아이콘(그림 · 사진 · 스티커 · 글꼴) + 글자 수.
- 그림 편집: 제목 "그림 보내기", 손떨림 보정 칩 · 빈 캔버스 설명 없음. 스티커는 하트·꽃 · 군대 · 글자 · D-day 네 탭 +
  "봄 소풍 스티커 · 잠김 ▶ 광고 보고 쓰기" 띠(광고 SDK 전에는 안내만).
- 캘린더 · 일정 추가: 전체 화면, 종류 칩(면회 · 외박 · 휴가 · 훈련 · 당직 · 기타) · 하루 종일 · 시작/끝 시각 · 알림 · 메모.
  DB v5(event.memo · user.deviceName, 자동 마이그레이션). 일정을 고칠 때 휴가 연결 · 만남 사진이 사라지던 문제도 고쳤다.
- 설정: 꽃신 평생(결제는 스토어 연결 뒤) · 연결 정보(연인 폰 기종 — 내 정보 Op 에 실어 보낸다) · 기념일(100일 · 1년 단위 알림) ·
  화면 모드 · 위젯(3단계 + 미리보기, 누르면 고정 요청) · 로그인 방법 · 회원 탈퇴(지워지는 것) · 약관 · 정책(이벤트 · 혜택 알림 동의) ·
  오픈소스 라이선스(스티커 · 아이콘 / 라이브러리). 알림 상세 화면 대신 "알림 받기" 하나 + "이벤트 · 혜택 알림". 백업 사용량 "1.2GB / 2GB".
  문의하기는 유형 고르기 없이(서버에는 "기타").
- 광고 지면(캘린더 · 휴가 · 설정): KkAd 자리만 — LocalAds 공급자를 붙이기 전에는 그리지 않는다.
- AdMob 배너(Android): 앱 ID ca-app-pub-9179621242550991~5526504782, 배너 단위는 release 만 실제 ID, debug 는 Google 테스트 단위.
  UMP 동의(필요한 지역만) 뒤 MobileAds 초기화. 지면은 캘린더(이번 주 아래) · 휴가(3번째 카드 뒤) · 설정(데이터와 계정 사이)에
  고정 적응형 배너 1개씩(LocalAdBanner → AdmobBanner). 광고가 오기 전에는 높이 0. 꽃신 평생 숨김은 결제 연결과 함께.
- AdMob 배너(iOS): 앱 ID ca-app-pub-9179621242550991~5857677275(Info.plist GADApplicationIdentifier + SKAdNetworkItems),
  배너 단위 ca-app-pub-9179621242550991/4592697065(Release) · 테스트 단위(Debug). Swift AdBanners(GoogleMobileAds 12 · UMP 3, SPM)가
  동의 → SDK 시작 → BannerView 를 만들고, Kotlin IosAdBanner 가 UIKitView 로 지면에 놓는다(MainViewController(ads:)).
- 꽃신 평생 결제(Android, RevenueCat purchases 10.29.0): 지금 Offering 의 lifetime 패키지를 산다. 활성 entitlement 가 있으면 이용 중 →
  광고 전부 숨김 · 설정 "이용 중". RevenueCat 사용자 id = 꽃신 계정 id(logIn). 키는 local.properties REVENUECAT_API_KEY(기본 테스트 스토어 키).
  테스트 스토어로 구매 → 광고 숨김까지 에뮬레이터에서 확인. iOS 결제 · 연인에게 광고 없이(연결된 동안)는 아직.
- 꽃신 평생 결제(iOS, RevenueCat purchases-ios-spm 5.x): Swift LifetimePurchases(IosPurchases) → Kotlin IosLifetimeStore(LifetimeStore).
  Android 와 같은 규칙(lifetime 패키지 · 활성 entitlement · 계정 id logIn), 이용 중이면 광고 숨김. 키는 Config.xcconfig REVENUECAT_API_KEY(비면 테스트 키).

## 설계 · Figma 확정분 반영(2026-10-24)

설계안 v7 이후 확정한 것(수익 모델 · 추억 책 · 스티커 · 앱 아이콘 · 약관 1.1)을 코드에 맞췄다. 두 에뮬레이터(kkotsin-dev)로 확인.

| 항목 | 확정 내용 | 구현 | 확인 |
|---|---|---|---|
| 스티커 | 전부 무료(팔지 않음) | 계절 · 기념일 6팩(96장, Fluent 256→192px) 추가 → 그림 스티커 204 + 글자 12 + D-day. 잠금 · "광고 보고 쓰기" 띠 삭제, 서랍은 가로로 넘기는 칩(Figma 그림 / 스티커) | 에뮬레이터 |
| 앱 아이콘 | 시안 E · 꽃 군번줄 | 적응형(벡터 전경 · 그라데이션 바탕 · 단색), 예전 mipmap webp, iOS AppIcon 1024 | 에뮬레이터 스플래시 |
| 꽃신 평생 | 광고 제거 · 글꼴 · 편지지 · 펜 · 색 · 위젯 꾸미기 · 백업 5GB(10년) · 연결된 상대도 함께 | 혜택 다섯 줄, 백업 용량 무료 1GB / 평생 5GB. **연결된 상대와 함께**: 내 정보(UserPut)에 산 것(perks)을 싣고 `SharedPerks` 가 나 + 상대를 합친다 → 광고 숨김 · 평생 편지지 열림 · 설정 "연인과 함께 이용 중" | 두 폰(곰신이 산 평생 → 군인 폰 광고 없음 · 편지지 열림) |
| 편지 첨부 | 그림 · 사진 합쳐 최대 10장, 누구나 같음 · 예약 편지 개수 제한 없음 | `Letter.MAX_ATTACHMENTS = 10` | 컴파일 · 단위 |
| 편지지 | 무료 5 + 평생 5(원고지 · 크라프트 · 군사우편 · 줄노트 · 수채 하늘) | 가로로 넘기는 10장, 평생 표시. 이용권 없으면 안내 시트 → 설정 › 꽃신 평생(쓰던 편지는 임시저장). 받는 사람은 이용권 없이 그대로 보인다 | 에뮬레이터 |
| 추억 탭 | 우리의 책 표지 카드 + 월별 장 → 한 달(달력 · 편지 세 형태 · 만남) → 그날의 한 줄(보기 · 쓰기), 책으로 보기 | `feature:memory` 새로 씀. 그림 상세 화면 삭제(편지는 받은 편지로 연다). 안쪽 화면은 탭 바 없이 | 에뮬레이터 |
| 그날의 한 줄 | 면회 · 외박 · 휴가에 각자 한 줄(40자) + 사진. 끝난 다음 날 10시 로컬 알림(두 폰 각각, 쓴 사람 폰은 안 띄움) · 홈 카드(쓰거나 · 닫거나 · 사흘 지나면 사라짐) · 일정 상세 버튼 · 추억 위쪽 버튼. 한 명만 쓰면 쓴 사람 것만 | `Event.dayLines`(사용자 id → `DayLine(text, at)`), 줄마다 늦게 쓴 쪽이 남는다 — 일정 LWW 와 따로 합친다(`mergeDayLines`, InboxApplier). 홈 카드 `dayLineCard`, 알림 `NotificationPlanner(dayLineFor)`, 한 달 모아 보기 `EventRepository.observeDayLinesIn`. 닫은 카드는 기기에만(`DeviceSettings.dayLineCards`). DB v9(meetNotes → dayLines, 파괴적) | 단위(DayLineTest · DayLineRepositoryTest · DayLineNotificationTest) |
| 추억 책 PDF | 앱 안 책은 무료, 디자인된 PDF(화면용 + 인쇄용) 5,900원, 둘 중 한 명이 사면 둘 다 | 시안 B(군사우편) 쪽 렌더러(`BookPageView`: 표지 · 요약 · 한 달 왼쪽(달력 · 사진) · 오른쪽(편지 세 통)) — 앱 미리보기와 PDF 가 같은 그림. PDF 는 쪽마다 화면 밖에서 1800px 로 그려 Android `PdfDocument` 두 벌(8×8인치, 인쇄용은 재단 3mm). 결제는 RevenueCat Offering · entitlement `memory_book`(`BookStore`) | 미리보기 · 쪽 넘김 에뮬레이터. **PDF 생성은 결제 뒤라 미확인** |
| 내 기록 내보내기 | 무료 원본 ZIP | `RecordExporter`(편지.txt · 일정 · 만남.txt · 그림 PNG · 사진 JPG, UTF-8 이름 STORED ZIP) → 공유 시트. 설정 › 데이터 | 단위(ZipInputStream) + 에뮬레이터에서 만든 ZIP 확인 |
| 약관 1.1 | 유료 서비스 · 청약철회 · 백업 보관 기간 · 수집 항목 | `TermsDoc`(제5조 · 제8조의2 · 제8조의3 · 제9조 · 제10조 · 제10조의2, 개인정보 표 2줄) | 컴파일 |
| 백업 보관 기간 | 무료 1년 · 평생 10년, 30일 · 7일 전 예고 | 함수 `backupRetention`(매일 04:00, `retention.ts` 순수 함수 테스트 3), 앱은 토큰 등록 때 `users/{uid}.lastSeenAt` | 함수 단위 19 |

이어서 채운 것(같은 날):

| 항목 | 구현 | 확인 |
|---|---|---|
| 글꼴 11종 | 무료 4(기본 · 고운돋움 · 고운바탕 · 개구) + 평생 7(하이멜로디 · 감자꽃 · 싱글데이 · 연성 · 주아 · 동글 · 나눔손글씨 붓). Google Fonts(OFL) 원본을 한글 11,172자 · 영문 · 기호만 남기고 힌팅을 빼 앱에 묶었다(10개 38MB). 글자 크기 작게 · 보통 · 크게는 글꼴 id 에 "@S" · "@L" 로 싣는다(스키마 변경 없음, 옛 앱은 기본 글꼴). 예전 글꼴(나눔 펜 · 명조)은 받은 편지를 위해 남기고 목록에서 뺐다 | 에뮬레이터(글꼴 시트 · 주아 적용) |
| 크레용 · 수채 붓 · 파스텔 12 · 네온 6 | `Tool.CRAYON` · `WATERCOLOR`(TLV 뒤에 더함 — 옛 앱은 펜으로), 렌더러 두 붓, 펜 칸의 도구 칩 · 색 묶음, 평생 잠금 | 에뮬레이터(그려 봄) · drawing-engine 단위 |
| 위젯 프레임 | 받은 그림 위젯 기본 · 벚꽃 · 크라프트 · 폴라로이드 · 밤하늘(기본 말고 평생). 이 기기 설정 → 스냅샷 `widgetFrame` → Glance. "▶ 과정" → "▶"(설계: 과정 글자 삭제) | 설정 화면 에뮬레이터, 위젯은 컴파일 |
| 백업 용량 추가 | `BackupPlusStore`(Offering · entitlement `backup_plus`), 백업 화면 줄 · 용량(1GB 또는 5GB + 5GB) | 컴파일(결제는 누르지 않음) |
| RevenueCat 웹훅 | 함수 `revenuecatWebhook`(Authorization = REVENUECAT_WEBHOOK_AUTH, REST 로 다시 읽어 `entitlements/{uid}` lifetime · book · backupPlus) | 함수 단위(21) · 규칙(entitlements · mail 은 서버만, 15) |
| 삭제 예고 이메일 | `backupRetention` 이 예고 때 `mail` 문서를 쓴다(Firebase "Trigger Email" 확장이 보낸다) | 함수 단위 |
| 추억 책 PDF | 그래픽 레이어 비트맵이 HARDWARE 라 PdfDocument 가 못 그리던 문제를 고쳤다(복사). 디버그 빌드에만 "개발용 · 결제 없이 PDF 만들기" | **에뮬레이터에서 두 PDF 생성 · 쪽 이미지 확인**(4쪽, 576pt / 593pt) |
| iOS | 추억 책 PDF(UIKit PDF 컨텍스트) · 결제 다리(memory_book · backup_plus, Swift `Purchases.swift`) · 파일 공유 | macOS CI 통과(run 40073697956: iOS 테스트 · 프레임워크 링크 · 앱/NSE/위젯 빌드 · Swift 테스트 · 시뮬레이터 실행) |
| iOS 위젯 | 받은 그림 · 다음 만남 위젯을 더했다(전에는 D-day · 보고싶어 둘뿐). 그림 위젯은 프레임 · "▶" · 누르면 편지. **위젯 스냅샷 발행기가 iOS 에서 시작되지 않던 것**을 고쳤다(onForeground 에서 한 번), 그림 PNG 는 App Group(widget/drawing-*.png)로 복사, Swift 가 넣은 `setWidgetReloader` 로 WidgetCenter 새로고침 | macOS CI 통과(위젯 확장 빌드) |
| Figma 맞춤 | 코드에 먼저 들어간 것을 ✅ 최종 화면에 반영: 편지지 10장(평생 표시) · 그림 / 펜(크레용 · 수채 · 기본/파스텔/네온) · 설정 · 백업(640MB / 1GB · 백업 용량 추가 줄) · 설정 · 위젯 안내(그림 위젯 프레임). 글꼴 시트는 Figma 쪽에 맞춰 코드를 고쳤다(크기 칩 · "보고 싶은 밤" + 이름 · 평생 알약 · 라디오). 💰 "▶ 과정 · 6초" → "▶", 🗂 최종 정리 4 · 4-1 · 7 다시 복제 | Figma 스크린샷 · 에뮬레이터 |

남은 것(다음 순서):

1. **콘솔 설정**: 순서 · 값 · 확인 방법은 [SETUP_CONSOLE.md](SETUP_CONSOLE.md) (스토어 상품 → RevenueCat → 비밀 값(Secret Manager) → 웹훅 확인 → 메일 확장 → 백업 보관 함수).
2. **함수 배포 순서**: 웹훅이 entitlements 를 채운 뒤 `backupRetention` 을 켠다(전에는 평생 사용자도 1년 규칙).
3. **Trigger Email 확장** 설치(mail 컬렉션) — 없으면 이메일 예고는 쌓이기만 한다.
4. **iOS 실기기 확인** — CI(시뮬레이터)는 통과. 결제 · PDF · 위젯은 실기기에서 눌러 봐야 한다(결제는 샌드박스 계정).

## iOS 두 시뮬레이터 전체 경로 QA (2026-10-24~25)

기준 `develop@9c363a1`, 작업 브랜치 `feature/ios-full-test`. 이 절은 앞선 Android/CI 기록과 구별되는 **실제 macOS iOS 실행 기록**이다. Xcode 26.6(17F113), iOS 26.5 iPhone 17 두 대, JDK 21.0.7, xcodegen 2.46. 외부 로그인 설정이 없어 사용자 승인 아래 `demo-kkotsin` 로컬 가짜 계정을 썼다. 실제 OAuth 성공을 의미하지 않는다. 사용자가 이 QA 결과와 변경 내역의 커밋·푸시·develop 대상 PR 생성을 승인했다. 머지는 별도 승인 대상이다.

| 영역 | 이번에 실제 확인한 범위 | 남은 것 |
|---|---|---|
| 온보딩/연결 | 필수 약관·문서 3종/v1.1, 역할/테마, 군인 입대일, 곰신 단계 생략, CPace 양쪽 SAS 확인 | 실제 소셜 로그인, 수정 후 신규 온보딩부터 연결까지 전체 회귀 |
| 복원/백업 | iOS ‘이 빌드에서는 아직 백업을 쓸 수 없어요’ 안내 재현 | iOS BackupManager/iCloud 열쇠/백업·복원 전체 실행 |
| 홈 | 양방향 보고싶어, D-day/군 정보/상대 이름, 재실행 수신 | 상대 상태 값 변경 |
| 캘린더/휴가 | 일정 종류 6/시각/하루 종일/메모/수정/삭제, 휴가 생성/사용/수정/받은 휴가 삭제 | 예약 알림의 실제 시각, 사용 건 개별 취소, 광고 실제 로드 |
| 편지/그림 | 텍스트/그림 송수신, 고마워/답장, 유료 편지지·하이멜로디 큰 글자 수신, 임시저장, 예약 저장, 비율·붓·색·밝기·undo/redo·13 스티커·앨범 사진/틀·리플레이 | 예약 시각 도달 후 공개, 카메라 촬영, 모든 편집 조합 |
| 추억/내보내기 | 만남 메모 각자 40자·사진 양방향 합산 10장/11번째 거절/삭제, 책 미리보기, PDF 2종·ZIP 공유/파일 앱 | 추억책 상품 설정·구매/공유, 대표 콘텐츠 선택 UI |
| 설정/결제 | 평생 Test Store 승인 구매 1회 성공, A 이용 중/B 연인과 함께 이용 중, 프레임 5종, 프로필·군 정보·기념일·테마·문서·문의/내역 화면 | book/backup_plus 상품/복원, 실제 계정 연결, SDK 동의 순서 계측 |
| 기기 인증 | 폰 교체/복구 코드의 암호 화면까지 진입, 해제 옵션 선택/취소, 탈퇴 틀린 문구 비활성 | 사용자가 ‘미검증으로 남기기’ 지정. 암호 입력·실제 해제/탈퇴/교체하지 않음 |
| 위젯 | App Group 스냅샷·그림 PNG, 실제 홈 D-day 위젯 | 그림/다음 만남/App Intent/잠금 화면의 실제 실행 |
| 알림/NSE | 권한 허용, simctl 보고싶어·편지·backupExpiry 배너, 편지 탭 이동 | 실제 APNs, 종료 상태 NSE 복호화/큐 병합 증거, 실제 시간/조용한 시간 정책 |
| 딥링크 | home/calendar/pairing/letter, 없는 편지 처리, home 오버레이 닫기 | 실제 배포 서명·소셜 로그인 복귀와의 공존 |
| 접근성/기타 | AX 라벨·주요 탭 4테마/다크, 최대 글자 UI 수정 전후, 종료 후 유지, 오프라인 저장→재진입 뒤 상대 수신 | 실제 VoiceOver 초점/음성, 모든 하위 화면 조합, 재연결만으로 자동 즉시 도착 |

수정:

- 곰신 군 정보 생략/진행 단계, 연결 완료 전에 온보딩 완료 처리하던 문제.
- iOS 키보드 루트 이동을 끄고 IME 여백/초점을 처리. 일정 전체 화면 안전 영역/구분선.
- DI 생성 후 첫 foreground 동기화/알림/위젯 게시를 보장. 구매 상태 변화가 화면 composition을 교체하지 않도록 고정.
- 앱 URL을 카카오 SDK보다 먼저 분기, 미설정 SDK 호출 차단. home 오버레이 닫기·없는 편지 초기화·DB 첫 결과로 편지 열기.
- Firestore base64 문자열/문서 제한으로 1,019,798바이트 사진 전송이 멎던 문제: 큰 암호문은 Storage, 작은 것은 기존 Firestore 방식.
- Keychain 그룹의 팀 접두사 반영/쓰기 오류 확인, 연결/상대 서명키/그림 PNG 공유. 위젯 암호문 사본을 앱 로그에 반영하고 ACK까지 재전송.
- iOS 알림 delegate·토큰 등록·백그라운드 완료·탭 라우팅, backupExpiry 기본 문구 보존.
- 광고는 첫 지면 UMP→ATT 이후 시작, 구매/공유 상태 확인 전 생성 차단. 상품별 복원 entitlement 분리.
- 잠김 접근성 라벨, 큰 글자의 홈 카드·달력 날짜·추억 칩 배치. Swift 테스트 타깃 Info.plist 생성.

검증 결과: 앱/NSE/위젯 빌드 성공. core:data JVM 33 + 알림 JVM 6 + 온보딩 iOS 3 = 42개 통과, Functions 21개 통과, Swift Op 벡터 3개 통과. Firebase/designsystem 및 home/calendar/memory 공통 UI의 Android 컴파일 통과. **빌드 성공을 NSE/App Intent 런타임 성공으로 해석하지 않는다.** simctl 암호화 푸시에서는 배너만 확인했고 NSE 로그/대기열은 비어 있었다.

PDF: iOS UIKit 컨텍스트가 화면용 576×576pt, 인쇄용 593×593pt를 각각 4쪽 생성. 공유 시트 두 파일/파일 앱 저장, 렌더링 8쪽 확인. 무료 개발용 경로로 생성했다. 추억책 Test Store는 사용자 승인한 1회 시도에서 ‘상품 정보 없음’ 실패. 평생 Test Store 표시 $99.99와 Figma 7,900원 차이는 콘솔 설정 확인 대상이다.

Figma: 53:862/53:886/53:923 온보딩, 52:86 홈, 169:3744 일정, 69:302 편지, 91:781 스티커, 262:82 추억, 181:1679 만남, 163:3809 평생, 169:1641 위젯, 169:1707 화면 모드, 118:1509 해제, 169:1966 탈퇴 등을 비교했다. 53:194 편지함의 필터는 요구 기능이므로 코드 유지/Figma 갱신 대상. 263:255 책 대표 콘텐츠 선택은 실제 앱에 없어 미해결 차이로 남긴다. Figma 원본은 수정하지 않았다.

증거는 별도 로컬 QA 산출물 `FlowerShoes-iOS-QA.md/html`, `evidence/ui`(곰신), `evidence/ui/soldier`(군인), `evidence/pdf`, `swift-tests-final.log`, `final-gradle-tests.log`에 보관했다. 각 화면에는 PNG/AX tree/명령 JSON이 있다. 생성된 Xcode 프로젝트·검사 하니스·설정 파일·키는 커밋 대상이 아니다.

배포 전: `storage:v1` 큰 첨부는 구버전 수신 클라이언트와 호환되지 않으므로 양쪽 업데이트/버전 협상 방침이 필요하다. Storage 32일 수명주기 운영 설정은 별도 확인한다. 현재 전체 경로 통과나 출시 준비 완료 상태가 아니다.

추가 회귀: 동작 줄이기 ON에서 군 정보의 뒤로 이동이 멈추는 것을 재현했다. `KkPageFlip`의 이전 장/전환 대기 상태를 Compose 관찰 상태로 바꿔 즉시 끝나는 애니메이션도 이전 장을 치우도록 수정했다. v14에서 군 정보·테마·설정·홈 복귀를 실제 확인하고 OS 옵션을 OFF로 복원했다. 공통 designsystem Android 컴파일도 통과. 사진/그림이 들어간 데이터로 PDF 두 종류를 다시 생성해 8쪽 모두 시각 검토했다.

최종 추가 회귀(v16): 편지 사진 9장+그림 1장으로 합산 10장 제한·삭제 후 추가 활성화를 확인했다. 같은 줄의 큰 그림 때문에 사진이 세로로 늘어나고 겹치는 문제를 각 글리프의 4:3 영역으로 수정했다. 자리 확보용 투명 문자는 접근성 값에 노출하지 않는다. 최종판에서 사진 8장+그림 1장 사이에 본문을 입력하고 전송해 상대 기기의 8개 사진·본문·그림 표시를 확인했다. 종료 상태에서 기존 편지 딥링크로 바로 열기도 통과했다. 기존 66개에 편지 첨부 JVM 5개를 더해 **자동 테스트 71개 통과**. v16 앱/NSE/위젯 빌드와 최종 편지 Android 컴파일 통과. 대표 실제 화면은 [QA 증거](qa/ios-2026-10-25/README.md)에 포함했다.


## RevenueCat/Firebase 콘솔 연동 후속 검증 (2026-10-25)

사용자가 콘솔 설정과 서버 키 발급·Secret Manager 저장을 명시적으로 승인했다. RevenueCat의 기존 Test Store 프로젝트에 `memory_book`/`backup_plus` 비소모성 상품, 같은 ID의 entitlement와 offering(각 패키지 하나)을 생성했다. 기존 `default` offering과 `asm_pro`는 변경하지 않았다. Test Store 신규 상품은 테스트 전용 기본 가격 $0.99이며, 실제 판매용 5,900원/3,300원 설정 완료를 의미하지 않는다.

`kkotsin-dev` 서울 리전에 `revenuecatWebhook`만 배포하고 Production/Sandbox 모두 수신하도록 연결했다. 조회 오류에도 200을 보내던 문제를 수정해 누락 키·HTTP 오류·잘못된 응답·저장 실패에는 503으로 재전송을 요청한다. 현재 RevenueCat의 TEST 이벤트는 익명이 아닌 임의 UUID를 포함하므로 명시적으로 제외한다. Functions 테스트 23개 통과(기존 21+신규 2).

별도 승인한 추억책 Test Store 구매 1회가 성공했고 NON_RENEWING_PURCHASE 이벤트 응답 200, 해당 QA UID의 Firestore `lifetime=true`, `book=true`, `backupPlus=false`를 확인했다. 구매자와 비구매 상대 기기 모두 추가 구매 없이 공유 시트에 PDF 두 문서를 생성했다. [구매자](qa/ios-2026-10-25/book-purchase-pdf.png), [상대](qa/ios-2026-10-25/book-shared-pdf.png).

74개 경로 최신 판정은 통과 46·부분 16·미검증 10·차단 2·실패 0이며 전체 경로 통과는 아니다. 기존 71개와 추가 2개로 누적 자동 테스트는 73개다. 앱 바이너리는 기존 검증된 v16이며 서버만 추가 수정했다. 실제 스토어/App Store 복원, 실제 OAuth/APNs/NSE, 기기 인증, iOS BackupManager와 일부 위젯은 여전히 남아 있다.

Play Console 꽃신 앱을 생성했지만 상품 등록에는 판매자 계정 설정이 필요하다. Apple 개발자 계정은 사용자가 없다고 확인했다. SMTP는 사용자 요청으로 보류했다. 안내된 순서를 유지하기 위해 이메일 단계 이후의 `backupRetention`도 아직 배포하지 않았다. 비밀 값·설정 파일은 저장소에 넣지 않는다.


## 코드·로컬 QA 후속 완료 (2026-10-25 저녁)

앞 절의 백업 미구현·예약 미검증·온보딩 회귀·오프라인 재진입 한계는 이 절의 결과로 갱신한다. 실제 계정/기기가 필요한 경로와 미실행 UI는 계속 구별한다. 이번 변경은 로컬 검증 완료 상태이며 게시 전 검토 대상이다.

- iOS BackupManager/Storage/RoomBackupSource/RestoreApplier 연결. DB와 첨부 참조 동일 시점 스냅샷, schema6, 증분 총 용량, 실패 시 DB 트랜잭션 롤백·live Room 관찰 유지. Android 백업 schema도 실제 버전6으로 정정.
- 계정별 iCloud 동기화 열쇠 구현(가짜 계정 비활성), 백업/복원/키 재발급 직렬화, 원격 keywrap 저장 성공 뒤 로컬 캐시, 복원 성공 뒤 백업 키 변경. 백그라운드25초·30분 간격/네트워크 정책 자동 백업.
- 송신/ACK·리스너·복원 교체 승인 재시도, 30초 타임아웃 후 Firestore 재연결, 활성 중1분 보조 동기화. 승인 기록은 원격 승인 성공 후 처리한다.
- 백업 Switch/Selected 접근성, 확인하지 않은 iCloud 완료 안내 제거, 계정 변경 후 RevenueCat 소유 상태 재조회.
- A 앱 재설치 복원:7편지·26첨부, 26/26 SHA-256 동일. B:8편지·3그림·25첨부, 25/25 동일, 상대 앱 재진입 없이 자동 승인·key epoch3·이후 송수신. QA 계정 UUID만 유지했고 DB/첨부/설정을 수동 복사하지 않았다. 동일 기기에 남은 Keychain을 사용한 것으로 실제 iCloud 회수 증거는 아니다.
- Firestore38.6초 정지 후 foreground 재진입 없이1.0초에 상대 수신·8.1초 ACK·동일 ID1건. 이전 v3/v5의 자동 복구 실패를 수정하고 v7에서 통과했다.
- 신규 C/D 두 시뮬레이터의 필수 약관→역할/테마/군 정보→초대→SAS544520→홈→보고싶어 수신. 설정 재진입 없이 완료. 휴가 사용 건 개별 삭제 및 양쪽 반영도 확인했다.
- 21:00 KST 예약 편지:공개 전 숨김→OS 알림 센터 표시→본문 공개 확인. 이 알림 탭 직후 목적지 이동은 확인하지 못했다.

전체 Kotlin jvmTest·iosSimulatorArm64Test170회/고유125개, Functions23개, Swift3개 = 중복 제외151개 통과. v7 앱/NSE/위젯·검사 타깃 빌드와 최종 Android 앱 컴파일 통과. UI 탐색 명령은 자동 assertion 수에 포함하지 않는다. 대표 [추가 QA 화면](qa/ios-2026-10-25-local/README.md).

74개 경로는 통과50·부분15·미검증9·실패/차단0. 실제 OAuth·스토어 구매·APNs/NSE·다른 iPhone의 iCloud 키 회수·기기 인증, 복원 코드/건너뛰기 UI, 일부 위젯/광고 계측/VoiceOver·실제 시각 알림은 남았다. 구버전 백업 파일의 iOS UI 복원도 별도다. 자동 백업의 실제 iOS 실행/셀룰러 조건은 미검증이며 1분 보조 동기화는 서버 읽기를 늘릴 수 있다.

콘솔 후속: Play memory_book5900원/backup_plus3300원 국내 전용 설정·RevenueCat 비소모성 매핑·전용 서비스 계정·RTDN 수신·인증3개 검사 완료. 사용자 승인으로 Android1.0(1) 내부 테스트 게시, 테스터0명. 실제 Android 런타임/구매는 미검증. Apple 계정과 SMTP는 미준비이며 backupRetention은 계속 미배포. 웹훅→메일 실제 수신→보관 함수 순서를 유지한다.


## 자체 관리 SMTP 메일 발송 (2026-10-26)

Firebase Extensions 종료 안내에 따라 Trigger Email 설치 지침을 Cloud Functions 2세대 `onMailCreated`로 교체했다. 서버 전용 `mail/{mailId}` 생성 시 Nodemailer로 TLS SMTP 발송하며, SMTP_PASSWORD는 Secret Manager에서 이 함수에만 연결한다. 단일 수신자와 평문 제목/본문만 전달한다.

Firestore transaction lease로 동시 실행을 제어하고 성공 문서는 재발송하지 않는다. 일시적인 SMTP 장애는 최대 5회 재시도, 인증/영구 거부/잘못된 문서는 ERROR로 남긴다. 로그와 상태에는 SMTP 원문/비밀번호를 기록하지 않는다. SMTP 서버 수락 직후 Firestore 상태 저장 전에 프로세스가 멈추면 중복 수신될 수 있다. SUCCESS는 SMTP 수락이며 수신함 도착의 증거는 별도로 확인한다.

백업 예고 문서 ID는 사용자·마지막 사용 시각·예고 간격으로 고정해 스케줄 재실행의 중복 큐 생성을 막았다. `BACKUP_RETENTION_ENABLED`는 기본 false이며, 웹훅·실제 메일 수신·보관 정책 검증 전에 삭제 함수를 켜지 않는다. 실제 설정 파일 두 개는 로컬에 유지하고 Git 추적에서 제외하며 `.env.example`을 제공한다.

검증: Functions 단위 33개(메일 신규10), 실제 Firestore 에뮬레이터/로컬 TLS SMTP 통합5개, Firestore 규칙16개(메일 접근 차단 신규1), 총54개 통과. Node24.18/Android Studio Java25/Firebase CLI15.30에서 실행했다. CI는 운영 런타임과 같은 Node22 및 Java21로 동일 테스트를 실행하도록 추가했다. CI는 아직 실행 전이다. body-parser의 기존 높은 등급 취약점은 호환 패치1.20.9로 갱신했다.

운영 확인: kkotsin-dev 서울에 Node22 onMailCreated만 배포해 ACTIVE를 확인했다. 2026-10-26 00:16 KST에 승인된 테스트 메일1통이 첫 시도에 SMTP SUCCESS로 기록됐고, Gmail 연결 도구로 지정 수신함(INBOX)의 제목·본문·시각 일치를 확인했다. 기존 이메일 확장은0개, backupRetention은 계속 미배포다. `backupRetention` 활성화는 이번 메일 구현 완료에 포함하지 않는다. 로컬 검증을 실제 Gmail 전달이나 전체 보관 정책 통과로 계산하지 않는다.


## Android 실기기와 공통 백업 후속 검증 (2026-10-26)

- Android 복원이 DB 파일 교체 후 프로세스를 종료해 `BackupManager`의 백업 키 저장에 도달하지 못하던 문제를 수정했다. iOS 복원 구현을 `core:backup/RoomRestoreApplier`로 옮겨 두 OS 모두 마이그레이션·첨부 검증·동기화 일시 중지·트랜잭션 복원·설정 적용을 공유한다. Android 백업도 `RoomBackupSource`로 통합해 알림/초안/화면 모드/자동·셀룰러 백업 설정을 함께 보관한다.
- `google-services.json`이 있을 때 에뮬레이터 초기화를 건너뛰던 문제를 수정했다. QA의 FirebaseInitProvider를 끄고 demo 프로젝트를 명시하며, release에는 호스트를 넣지 않는다.
- libsodium 0.9.2의 arm64 라이브러리가 4KB 정렬이라 Android 호환성 경고가 발생했다. 사용자 승인으로 최소 Android를 8(API 26)로 올리고 바인딩 0.9.5를 사용한다. QA/출시 APK의 64비트 라이브러리 각각 12개가 16KB ELF 정렬 검사, QA APK가 zipalign -P 16 검사를 통과했다.
- Android 16 실기기(4KB)와 Android 17 에뮬레이터(16KB)에서 각각 2개 통합 테스트 통과: 실제 Room/DataStore·암호화 백업→복원→재백업, 키/사진/설정 보존과 로그인 신원 유지; demo Firebase Auth·Storage 왕복. 핵심 JVM 86개(crypto10/sync13/backup16/data39/database8) 통과. 변경이 없는 sync/database 태스크는 Gradle up-to-date 결과를 사용했다.
- Android QA/출시 빌드, iOS 시뮬레이터 Kotlin 프레임워크 링크, 앱·NSE·위젯·검사 러너의 Xcode build-for-testing 통과. 실제 Play/App Store 결제, APNs 전달, 새 기기의 iCloud/Block Store 키 복원 통과를 의미하지 않는다.
- Android 실기기 UI에서 로컬 가짜 로그인·필수 약관·곰신/네이비 온보딩·홈·217KB 백업 성공을 확인했다. iOS26.5에서도 최신 Xcode 원본 앱의 홈 실행을 확인했다. [대표 화면](qa/android-core-2026-10-26/README.md).
- Apple 멤버십·App ID3개·App Group 등록/연결 완료. 사용자 승인으로 꽃신 한 앱에 제한한 Sandbox APNs 키를 Firebase 개발 인증 키에 등록했다. 프로덕션 푸시, 실제 소셜 로그인·스토어 결제는 별도이며 콘솔 등록만으로 통과 처리하지 않는다.
- 이전 SMTP 커밋49167dc의 Functions 및 iOS PR CI는 모두 성공했다. 이번 미커밋 변경의 원격 CI 결과와 구분한다.
