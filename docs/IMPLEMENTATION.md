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
| 압축(zstd) | 그림 문서 · 백업 | **넣지 않음**(형식은 압축 없이 확정) | 그림 문서는 Android ↔ iOS 가 주고받으므로 양쪽이 같은 zstd 여야 한다. iOS 는 libzstd 를 cinterop 으로 넣어야 해 macOS 없이 만들 수 없다. 델타 · varint 로 이미 5~15KB, 백업은 암호문이라 이득이 작다. 넣을 때는 문서 헤더에 압축 플래그 필드를 추가한다(TLV 라 옛 앱과 호환) |
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
   - 남은 것: iOS 로그인(카카오 iOS SDK · Sign in with Apple · Google), Apple 탈퇴 시 `/auth/revoke`(Apple 키 필요).
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
- 남은 것: macOS 에서 `xcodegen` → 빌드 → 실기기 확인, iOS 로그인 SDK, iCloud 키체인 백업 키 동기화.

