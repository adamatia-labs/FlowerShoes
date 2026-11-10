# Android 위젯 회귀 검증 — 2026-11-11

대상: #158의 Android 갱신, 큰 위젯 하단 누락, 작은 다음 만남 잘림.

사용자 요청에 따라 물리 기기 대신 `Medium_Phone` AVD, Android 16에서 확인했다. QA 앱은 `firebaseEmulatorHost=127.0.0.1`로 빌드했다. 테스트는 앱 위젯 호스트에 실제 Glance RemoteViews를 바인딩하므로 모의 Compose 렌더러가 아니다. 삼성 런처와 잠금 화면의 동작을 검증한 결과는 아니다.

| 재현 조건 | 수정 전 | 수정 후 |
| --- | --- | --- |
| 같은 Glance 세션에서 스냅샷의 보낸 횟수를 2→7로 쓰고 갱신 | 파일은 7, 호스트 문구는 2에 머물러 실패 | 호스트 문구가 7로 갱신됨 |
| 전역·진행률·태그 3개·새 편지·보고싶어를 큰 위젯에 표시 | Glance 컨테이너의 자식 10개 제한 때문에 하단 행 자체가 없음 | 전역 영역을 묶어 하단 행 표시 |
| 큰 위젯 최소 250dp 크기 | 하단 행을 복구해도 편지 글자가 경계 밖으로 나감 | 간격·D-day 글자 크기 조정 후 편지/보고싶어 표시 영역과 말줄임 검사 통과 |
| 다음 만남 최소 110dp 크기에서 `11월 8일 면회` | 단일 21sp 줄이 잘림 | 두 줄 13sp 제목, 작은 크기 전용 여백으로 날짜·제목 표시 검사 통과 |

작은 다음 만남에서는 날짜·제목·D-day를 우선하고 요일/시각 보조 줄은 더 큰 높이에서 표시한다. 탭하면 기존 일정 화면이 열린다.

## 실행

PowerShell에서 Android SDK 경로와 대상 에뮬레이터를 지정한 뒤:

```powershell
$env:ANDROID_SERIAL='emulator-5584'
.\gradlew.bat :app:connectedQaDebugAndroidTest '-PfirebaseEmulatorHost=127.0.0.1' '-Pandroid.testInstrumentationRunnerArguments.class=com.sadturtleman.flowershoes.WidgetRefreshTest' --console=plain
```

`WidgetRefreshTest` 3개 통과. 테스트는 스냅샷을 복원하고 생성한 호스트 위젯을 삭제한다. 실제 Firebase를 사용하는 빌드에서는 실행하지 않는다.

## 남은 검증과 수정

- 상태를 설정하는 앱 UI 경로, 그림 프레임의 글자 대비, 그림 프레임 기준과 체크리스트 정합성.
- iOS 작은 가로 그림 위젯, 잠금 화면 보고싶어 동작.
- 실제 삼성 런처/기기 검증. 현재 Android provider XML은 모두 `home_screen`만 선언한다.
- #158의 일시적 첫 진입 오류와 알림 관찰은 아직 재현 원인이 확정되지 않았다.

이 결과만으로 #158 전체를 닫지 않는다.
