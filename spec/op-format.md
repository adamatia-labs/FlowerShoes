# Op 봉투 형식 (v1)

Kotlin(`:core:crypto` `OpEnvelope`)과 Swift(NSE · 위젯)가 같은 바이트를 만들고 읽어야 한다.
양쪽 테스트는 `spec/test-vectors/` 의 같은 파일을 읽어 검증한다(설계 6-8).

## 머리 (OpHeader) 바이트

```
u32be len(opId)   ‖ opId (UTF-8)
u32be len(stream) ‖ stream (UTF-8)    // "{deviceId}:app" | "{deviceId}:widget"
i64be seq
i32be keyEpoch
```

## 암호문

- 알고리즘: XChaCha20-Poly1305-IETF
- 키: 커플 키(32바이트, `keyEpoch` 번)
- AAD: 머리 바이트 전체
- 출력: `nonce(24) ‖ ciphertext ‖ tag(16)`

## 서명

- 알고리즘: Ed25519 detached, 보낸 기기의 서명 키
- 서명 대상: `BLAKE2b-512( P("kkotsin-op-v1") ‖ P(머리 바이트) ‖ P(암호문) )`
  - `P(x)` = `u32be len(x) ‖ x`

## 기타 해시 문맥

| 용도 | 계산 |
|---|---|
| 초대 토큰 | 16바이트 무작위 → base64url(패딩 없음) 22자. `pairings/{token}` 문서 id · 링크 `/i/{token}` · `kakaolink?invite={token}` |
| 공개키(`pubKey`) | `base64( signPub(32) ‖ boxPub(32) )` — 초대 · 수락 문서의 한 필드 |
| 첫 연결 커플 키(epoch 1) | `BLAKE2b-256( P("kkotsin-pair-v1") ‖ P(X25519(내 boxSec, 상대 boxPub)) ‖ P(token) ‖ P(signPub₁) ‖ P(boxPub₁) ‖ P(signPub₂) ‖ P(boxPub₂) )`, 기기 1 · 2 는 boxPub 바이트 오름차순. 벡터 `test-vectors/pairing-v1.json` |
| 넘겨주기 커밋 · SAS (#40 에서 제거) | 커밋 `BLAKE2b-256( P("kkotsin-commit-v1") ‖ P(boxPub) ‖ P(signPub) ‖ P(nonce) )`, SAS `h = BLAKE2b-128( P("kkotsin-sas-v1") ‖ P(code) ‖ P(signPubA) ‖ P(boxPubA) ‖ P(signPubB) ‖ P(boxPubB) ‖ P(nonceB) )`, 앞 8바이트 big-endian → `>>> 1` → `% 1_000_000`, 6자리 0 채움 |
| 기기 교체 MAC | `BLAKE2b-256(key = 커플 키)( P("kkotsin-rotation-v1") ‖ P(요청 바이트) )` |
| 커플 키 전달 | `crypto_box_seal( i32be epoch ‖ key32, 받는 기기 boxPub )` |

## 인라인 푸시 페이로드

```json
{ "v": 1, "kind": "missyou", "opId": "…", "stream": "…", "seq": 12, "epoch": 2, "ct": "<base64 nonce‖ct‖tag>", "sig": "<base64>" }
```

- 암호화 전 원본 ≤ 2KB일 때만 실는다. 나머지는 `kind`만 실린 초인종이다.
- Swift 쪽은 `missyou` · `status`만 해석하고, 모르는 `kind` · `v`는 "새 소식이 있어요"로 표시한다.

## 본문 (OpPayload, 암호화 전 평문)

`:core:data` `OpCodec` 이 kotlinx-serialization JSON 으로 만든다. 종류는 `"t"` 로 가리고, 받는 쪽은 모르는 `t` · 모르는 필드를 건너뛴다(커서는 전진).

| `t` | 내용 | 병합 |
|---|---|---|
| `missyou` | 보고싶어 한 번 | 추가 전용 |
| `status` | 지금 상태 | 작성자만, 최신 하나 |
| `event` | 일정 `Event` (+ 그날의 사진 `photos`) | 행 LWW(lamport, lastWriterDevice), 그날의 한 줄은 줄마다 늦은 쪽 |
| `leave` | 받은 휴가 `LeaveGrant` | 행 LWW · tombstone |
| `letter` | 편지 + 그림 메타 · 사진 참조 | 추가 전용 |
| `receipt` | 읽음 · 처음 봄 · 하트 | 먼저 온 값 유지 |
| `service` | 복무 정보 | lamport, 연결 직후엔 군인 입력 우선 |
| `user` | 역할 · 이름 · 폰 기종 · 산 것 | lamport |
| `bookcover` | 추억 책 표지 `{ "cover": "FLOWER" \| "MILITARY", "lamport", "writerDevice" }` | 행 LWW(lamport, writerDevice) |
| `delete` | `EVENT` · `LETTER` · `LEAVE_GRANT` 지우기 | 일정 · 휴가는 LWW tombstone |
| `snapshot` | 위 항목 묶음 + `coversThrough` | 항목마다 위 규칙 |

### 일정 (`event.event`)

- `type`: `VACATION` · `OVERNIGHT` · `VISIT` · `TRAINING` · `DUTY` · `ANNIVERSARY` · `OTHER`.
- `repeatYearly`(기본 `true`): `ANNIVERSARY` 일 때만 뜻이 있다. 원래 날 그대로 보내고, 받는 쪽이 해마다 옮겨 놓는다(2월 29일은 평년 2월 28일).
- 커플 기념일은 id `anniversary-couple` 인 `ANNIVERSARY` 일정 하나다. 두 폰이 같은 id 로 쓰므로 동시에 고쳐도 LWW 로 하나만 남는다.
- 휴가 사용 = `VACATION` 일정. 일수는 시작 · 끝 · 주말 포함, 출처(받은 휴가)를 가리키지 않는다.
- `dayLines`(그날의 한 줄): `{ "<사용자 id>": { "text": "…", "at": <쓴 시각 ms> } }`. 40자, 빈 `text` 는 지운 줄. 각자 자기 줄만 쓰고, 받는 쪽은 일정 LWW 와 상관없이 사용자마다 `at` 이 늦은 줄을 남긴다. `VISIT` · `OVERNIGHT` · `VACATION` 에만 쓴다.

### 휴가 하나의 풀

전체 = 정기휴가(군종 계산값 — 저장 · 전송하지 않는다) + `leave` 합, 사용 = `VACATION` 일정 일수 합. 초과 사용을 막지 않는다(남은 휴가가 음수).

Room DB 7 과 함께 없앤 것: `t = "anniversary"`(커플 기념일 필드), `event.leaveGrantId`, `LeaveKind.REGULAR`. 출시 전이라 호환을 두지 않는다.

Room DB 9: `event.meetNotes` → `event.dayLines`(같은 모양, 이름만). 옛 이름은 읽지 않는다.

### 추억 책 표지 (`bookcover`)

커플에 하나인 값. 산 사람이 고르고 두 사람이 같은 표지를 쓴다(구매 뒤에도 바꿀 수 있다). 조용한 종류(`profile`)로 보내고, 받는 쪽은 `book_cover` 행 하나를 lamport → writerDevice 순으로 비교해 늦은 쪽을 남긴다. 스냅샷은 내가 마지막으로 고친 값만(`snapshot`), 넘겨주기는 늘 싣는다. 받은 적이 없으면 `MILITARY`. Room DB 10 에서 `book_cover` 표를 더했다.
