# 관계 보관 기록 격리 검증 — 2026-11-11

대상: #101. #123의 보관 경계 수정과 #165의 전체 스냅샷 복구를 함께 검증했다. 사용자 요청에 따라 물리 기기 대신 `Medium_Phone` AVD, Android 16을 사용했다.

## Android 검증

`RelationshipArchiveTest` 2개 통과. 실제 Android SQLite 파일에 A/B 기록을 저장하고 `UnlinkService.onPartnerUnlinked(KEEP_AS_MEMORIES)`를 실행한다. 발신 DB를 닫았다 다시 연 뒤 혼자 모드 기록을 추가하고 A/C 관계를 설정한다. 실제 `SyncClient`가 만든 일반 복구·기기 교체 스냅샷을 각각 새 수신 DB의 `InboxApplier`에 적용한다.

| 기록 | 확인한 결과 |
| --- | --- |
| 편지·그림·사진 | 이전 편지와 그림 메타·첨부는 발신 기기에 남고 수신 DB에는 없음. 해제 후 쓴 편지는 전달됨 |
| 일정·기념일·휴가 | 이전 일반 일정·기념일·휴가는 남고 전달되지 않음. 새 기념일만 전달됨 |
| 읽음 | 이전 편지 읽음과 원본이 사라진 읽음 모두 전달되지 않음 |
| 사용자·복무 | 이전 상대와 보관된 대리 입력 복무 정보는 전달되지 않음. 새 대리 입력 값은 새 상대의 복무 정보로 전달됨 |
| 보고싶어·상태 | 이전 기록은 남고 전달되지 않음. 새 보고싶어는 전달됨. 새 상태는 일반 복구에 포함되고 기기 교체에서는 기획 §5에 따라 제외됨 |
| 추억 책 표지·전송 건수 | 새 표지만 전달됨. 기기 이전 건수는 새 편지·기념일만 셈 |

이 Android 테스트는 관계 행 설정과 스냅샷 전달을 직접 수행한다. 로그인 UI, 실제 서버, 암호화 전송, OS 프로세스 강제 종료, 물리 기기 또는 iOS를 검증한 결과는 아니다. 검증 대상 앱은 독립 QA 패키지이며 테스트 DB와 파일은 실행 후 정리한다.

## JVM 검증

`:core:data:jvmTest` 159개 통과. 기존 `core.data.sync.RelationshipArchiveTest` 8개는 세 기기의 실제 연결·암호화·동기화 경로와 메모리 전송 서버를 사용한다. 이번에는 이전 상대 복무 정보, 보관된 대리 입력 정보, 새 혼자 모드 복무 정보까지 검증을 확장했다. 기존 테스트는 보관 첨부의 전송 차단, 오래된 로그·중첩 스냅샷, 보관 ID 재사용, 구버전 출처 불명 기록도 검사한다.

## 재실행

```powershell
$env:ANDROID_SERIAL='emulator-5584'
.\gradlew.bat :core:data:jvmTest :app:connectedQaDebugAndroidTest '-PfirebaseEmulatorHost=127.0.0.1' '-Pandroid.testInstrumentationRunnerArguments.class=com.sadturtleman.flowershoes.RelationshipArchiveTest' --console=plain
```

SDK 경로는 실행 환경의 `ANDROID_HOME` 또는 `local.properties`를 사용한다. 운영 Firebase 배포는 이 검증에 포함하지 않는다.
