# 콘솔 설정 안내 (RevenueCat · Firebase)

계정 설정과 함수 배포 안내다. 위에서부터 순서대로 한다. **4번(웹훅 확인)과 5번(메일 실제 수신)을 모두 마치기 전에 6번(백업 삭제 함수)을 켜지 않는다.**

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

## 5. 삭제 예고 이메일(자체 관리 Cloud Functions 2세대)

Firebase Extensions는 2027-03-31 관리 서비스가 종료된다. **Trigger Email 확장을 새로 설치하지 않는다.** `onMailCreated`가 서버 전용 `mail/{mailId}` 문서를 읽어 SMTP로 발송한다. [종료 안내](https://firebase.google.com/docs/extensions/faq-and-troubleshooting), [이전 안내](https://firebase.google.com/docs/extensions/users/migrate).

1. Gmail 계정에 2단계 인증을 켜고 [앱 비밀번호](https://myaccount.google.com/apppasswords)를 발급한다. 일반 로그인 비밀번호를 사용하지 않는다.
2. `firebase/functions/.env.example`을 `.env.kkotsin-dev`로 복사하되, 기존 파일이 있으면 보존하고 아래 항목만 추가한다. 실제 설정 파일은 Git에서 제외한다.

   ```dotenv
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=465
   SMTP_USER=발신_Gmail_주소
   SMTP_FROM=발신_Gmail_주소
   BACKUP_RETENTION_ENABLED=false
   ```

3. 사용자가 자기 터미널에서 다음 명령을 **그대로** 실행한다. `SMTP_PASSWORD`는 비밀 값의 이름이므로 실제 비밀번호로 바꾸지 않는다. 실행 후 나타나는 입력창에 앱 비밀번호를 공백 없이 직접 넣는다. 비밀번호는 채팅·명령 인수·dotenv 파일에 적지 않는다.

   ```bash
   firebase functions:secrets:set SMTP_PASSWORD --project kkotsin-dev
   ```

4. `firebase/`에서 메일 함수만 배포한다. `SMTP_PASSWORD`는 이 함수에만 연결된다.

   ```bash
   firebase deploy --only functions:onMailCreated --project kkotsin-dev
   ```

5. 지정한 테스트 수신자에게 관리자 콘솔에서 새 `mail` 문서를 생성한다. 제목은 `[꽃신] SMTP 연결 테스트`, 본문은 `메일 연결 확인용 테스트입니다. 백업은 삭제되지 않습니다.`로 한다. 문서 예:

   ```json
   { "to": "테스트_수신_주소", "message": { "subject": "[꽃신] SMTP 연결 테스트", "text": "메일 연결 확인용 테스트입니다. 백업은 삭제되지 않습니다." } }
   ```

6. `delivery.state=SUCCESS`(SMTP 서버 수락)와 수신함/스팸함의 **실제 수신을 별도로** 확인한다. 자동 재시도는 최대 5회이며 `ERROR`에는 고정 오류 코드만 남는다. 인증 오류·수신 거절은 재시도하지 않는다. 원인을 수정한 후 관리자가 새 문서로 재전송한다. 기존 문서 수정만으로는 발송하지 않는다.

운영 사항:
- 클라이언트는 `mail` 읽기/쓰기/목록 조회가 모두 금지된다. 제목·본문·수신자 한 명만 허용하며 첨부·HTML·임의 헤더는 보내지 않는다.
- 성공 문서는 중복 이벤트에서 건너뛰고, 동시 실행은 Firestore transaction으로 잠근다. SMTP 수락 직후 상태 저장 전에 프로세스가 중단되면 중복 수신될 수 있다. SMTP는 정확히 한 번 전달을 보장하지 않는다.
- SMTP 응답 원문, 비밀번호, 메일 본문·주소를 함수 로그에 기록하지 않는다. `mail` 문서에는 수신자·본문·발송 상태가 있으므로 관리자 접근과 보존 기간을 관리한다.
- 최초 배포 전 쌓인 문서는 자동 전송되지 않는다. 지난 삭제 예고를 일괄 재전송하지 말고 필요성을 확인한다. 기존 Trigger Email 확장이 있다면 같은 `mail` 컬렉션을 동시에 처리하게 두지 않는다.
- Gmail은 소규모 검증용으로 사용한다. 대량 발송 전 서비스의 발송 한도와 전용 SMTP 제공자를 검토한다.

로컬 검증(Java 21 이상, Node 22, Firebase CLI 필요):

```bash
npm ci --prefix firebase/functions
npm ci --prefix firebase/rules-test
cd firebase/functions
npm test
npm run test:mail:emulator
```

통합 테스트는 `demo-kkotsin-mail` 에뮬레이터와 로컬 TLS SMTP만 사용한다. 실제 계정·메일을 사용하지 않는다.

## 6. 백업 보관 함수 켜기(마지막)

4번의 `entitlements` 갱신과 5번의 실제 메일 수신을 모두 확인하고, 테스트 데이터로 보관 정책을 검증한 **뒤에** 운영 활성화를 별도로 승인한다. 기본값은 `BACKUP_RETENTION_ENABLED=false`이며 실수로 함수를 배포해도 삭제를 실행하지 않는다.

활성화할 때만 `.env.kkotsin-dev`에서 `BACKUP_RETENTION_ENABLED=true`로 바꾸고:

```bash
firebase deploy --only functions:backupRetention --project kkotsin-dev
```

- `backupRetention` 이 매일 04:00(서울) 돈다: 마지막 사용 후 무료 1년 · 꽃신 평생 10년, 30일 · 7일 전 푸시(+메일), 기한이 지나면 `backups/{uid}/` 삭제.
- 웹훅 전에 켜면 평생 사용자도 1년 규칙을 받는다. 이미 켰다면 4번을 마친 뒤 지난 구매를 RevenueCat 에서 한 번씩 다시 보내거나(대시보드 › 고객 › Send webhook), 해당 사용자 `entitlements/{uid}` 를 확인한다.

## 7. iOS 실기기 확인

CI 는 시뮬레이터에서 빌드 · 실행까지만 본다. 실기기(TestFlight)에서:
- 꽃신 평생 · 추억 책 · 용량 추가 결제(샌드박스 계정)와 구매 복원
- 추억 › 책으로 보기 › PDF 받기 → 공유 시트에 PDF 두 개
- 홈 화면 위젯: 받은 그림(프레임 · ▶ · 누르면 편지) · 다음 만남 · D-day · 보고싶어
