# 콘솔 설정 안내 (RevenueCat · Firebase)

코드로 할 수 없는 계정 설정이다. 위에서부터 순서대로 한다. 순서가 중요하다 — **4번(웹훅 확인) 전에 6번(백업 삭제 함수)을 켜지 않는다.**

프로젝트: Firebase `kkotsin-dev`(서울 asia-northeast3), RevenueCat 앱(Android · iOS 한 프로젝트).

---

## 1. 스토어에 상품 만들기

RevenueCat 은 스토어 상품을 묶을 뿐이라, 먼저 각 스토어에 상품을 만든다. 둘 다 **한 번 결제(비소모성)**.

| 상품 | 가격 | Google Play(인앱 상품) | App Store(비소모성) |
|---|---|---|---|
| 추억 책 PDF | 5,900원 | 제품 ID `memory_book` | 제품 ID `memory_book` |
| 백업 용량 추가 | 3,300원 | 제품 ID `backup_plus` | 제품 ID `backup_plus` |

- Google Play Console › 앱 › 수익 창출 › 인앱 상품 › 상품 만들기 → ID · 이름 · 설명 · 가격 → 활성화.
- App Store Connect › 앱 › 수익화 › 앱 내 구입 › + → 비소모성 → 참조 이름 · 제품 ID · 가격 · 심사용 스크린샷.

## 2. RevenueCat 에 묶기

RevenueCat 대시보드 › 프로젝트 › Product catalog.

1. **Products** › + New → 1번의 두 스토어 상품을 각각 가져온다(Android · iOS 각 2개).
2. **Entitlements** › + New
   - `memory_book` → 추억 책 상품(Android · iOS) 붙이기
   - `backup_plus` → 용량 추가 상품(Android · iOS) 붙이기
   - 꽃신 평생 entitlement 는 지금 것 그대로(이름 상관없음 — 앱은 "위 두 개가 아닌 활성 entitlement"를 평생으로 본다)
3. **Offerings** › + New
   - Identifier `memory_book` → Package 하나에 추억 책 상품
   - Identifier `backup_plus` → Package 하나에 용량 추가 상품
   - **Current** Offering 은 꽃신 평생 그대로 둔다(앱이 평생 결제에 current 를 쓴다).

확인: 디버그 빌드 › 추억 › 책으로 보기에 스토어 가격이 뜨면 연결된 것(못 읽으면 "5,900원" 고정 글자).

## 3. 함수 비밀 값 넣기(Secret Manager)

PC 에서(`firebase login` 된 상태, `firebase/` 폴더):

```bash
firebase functions:secrets:set REVENUECAT_WEBHOOK_AUTH --project kkotsin-dev
```
- 아무도 추측할 수 없는 긴 임의 문자열(예: 비밀번호 관리자로 생성한 40자). **4번 웹훅 Authorization 칸에 같은 값을 넣는다.**

```bash
firebase functions:secrets:set REVENUECAT_SECRET_KEY --project kkotsin-dev
```
- RevenueCat › Project settings › API keys › **Secret API key**(`sk_` 로 시작, v1). 앱에 들어가는 공개 키(`goog_` · `appl_` · `test_`)가 아니다.

이 두 값은 저장소(.env 파일 포함)에 넣지 않는다.

## 4. 웹훅 함수 배포 · 연결 · 확인

```bash
firebase deploy --only functions:revenuecatWebhook --project kkotsin-dev
```

1. 배포 출력(또는 Firebase 콘솔 › Functions)의 `revenuecatWebhook` 주소를 복사한다.
2. RevenueCat › Integrations › Webhooks › + New
   - Webhook URL: 1의 주소
   - Authorization header value: 3번의 `REVENUECAT_WEBHOOK_AUTH` 값과 **똑같이**
   - Environment: 처음엔 Sandbox 와 Production 둘 다
3. **Send test event** → 응답 200 이면 인증 통과(테스트 이벤트의 익명 사용자는 기록하지 않는다).
4. 실제 확인: 디버그 빌드에서 RevenueCat 테스트 스토어(또는 샌드박스 계정)로 꽃신 평생을 산다 → Firestore `entitlements/{그 계정 uid}` 문서에 `lifetime: true` 가 생기면 끝.

## 5. 삭제 예고 이메일(Trigger Email 확장)

Firebase 콘솔 › Extensions › **Trigger Email from Firestore** 설치.
- Email documents collection: `mail`
- SMTP connection URI: 보내는 메일 서비스(예: SendGrid · Gmail 앱 비밀번호) 주소
- Default FROM: 예 `꽃신 <no-reply@도메인>`

확인: Firestore 에 `mail` 문서를 하나 직접 만들어(`to`, `message.subject`, `message.text`) 메일이 오면 끝. 확장이 없으면 예고 메일 문서는 쌓이기만 한다(푸시 예고는 그대로 간다).

## 6. 백업 보관 함수 켜기(마지막)

4번에서 `entitlements` 가 채워지는 것을 확인한 **뒤에**:

```bash
firebase deploy --only functions --project kkotsin-dev
```

- `backupRetention` 이 매일 04:00(서울) 돈다: 마지막 사용 후 무료 1년 · 꽃신 평생 10년, 30일 · 7일 전 푸시(+메일), 기한이 지나면 `backups/{uid}/` 삭제.
- 웹훅 전에 켜면 평생 사용자도 1년 규칙을 받는다. 이미 켰다면 4번을 마친 뒤 지난 구매를 RevenueCat 에서 한 번씩 다시 보내거나(대시보드 › 고객 › Send webhook), 해당 사용자 `entitlements/{uid}` 를 확인한다.

## 7. iOS 실기기 확인

CI 는 시뮬레이터에서 빌드 · 실행까지만 본다. 실기기(TestFlight)에서:
- 꽃신 평생 · 추억 책 · 용량 추가 결제(샌드박스 계정)와 구매 복원
- 추억 › 책으로 보기 › PDF 받기 → 공유 시트에 PDF 두 개
- 홈 화면 위젯: 받은 그림(프레임 · ▶ · 누르면 편지) · 다음 만남 · D-day · 보고싶어
