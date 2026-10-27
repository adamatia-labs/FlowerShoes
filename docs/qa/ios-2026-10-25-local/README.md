# 추가 로컬 iOS QA · 2026-10-25

실제 iPhone17/iOS26.5 시뮬레이터와 demo-kkotsin 가짜 계정에서 실행했다. 실제 OAuth·iCloud·APNs·스토어 결제 통과 증거가 아니다.

| 화면 | 확인 |
|---|---|
| [재설치 후 백업](backup-found.png) | 8편지·3그림·19사진·19MB 백업 발견 |
| [상대 자동 승인](automatic-rotation.png) | 상대 앱 재진입 없이 새 기기 자동 승인 안내 |
| [예약 알림](scheduled-notification.png) | 2026-10-25 21:00 예약 공개의 실제 OS 알림 |
| [신규 연결 후 수신](fresh-onboarding-received.png) | 신규 온보딩 CPace 완료 후 보고싶어1. 최초 연결은 설정 재진입 없이 완료했고 이 최종 PNG는 시스템 권한창을 닫은 후 캡처 |

B 복원 전후 편지8·그림3·첨부25 유지, 첨부25/25 SHA-256 동일. 새 기기 승인으로 device3→4, key epoch3. Keychain이 남아 있는 같은 시뮬레이터의 재설치이며 새 iPhone의 iCloud 키 회수는 별도 미검증이다. DB/첨부를 수동으로 복사하지 않았다.

Firestore38.6초 정지 후 앱 재진입 없이1.0초 수신·8.1초 ACK, 상대 동일 ID1건. 자동 테스트 고유151개(Kotlin125, Functions23, Swift3)와 앱/NSE/위젯 빌드·Android 컴파일 통과. 로컬 전체 경로표74개는 통과50·부분15·미검증9다.
