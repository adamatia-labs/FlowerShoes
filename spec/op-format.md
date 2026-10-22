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
| 커밋 | `BLAKE2b-256( P("kkotsin-commit-v1") ‖ P(boxPub) ‖ P(signPub) ‖ P(nonce) )` |
| SAS | `h = BLAKE2b-128( P("kkotsin-sas-v1") ‖ P(code) ‖ P(signPubA) ‖ P(boxPubA) ‖ P(signPubB) ‖ P(boxPubB) ‖ P(nonceB) )`, 앞 8바이트 big-endian → `>>> 1` → `% 1_000_000`, 6자리 0 채움 |
| 기기 교체 MAC | `BLAKE2b-256(key = 커플 키)( P("kkotsin-rotation-v1") ‖ P(요청 바이트) )` |
| 커플 키 전달 | `crypto_box_seal( i32be epoch ‖ key32, 받는 기기 boxPub )` |
| 백업 키 파생 | `BLAKE2b-256(key = BK)( P("kkotsin-bk-v1") ‖ P(용도) )`, 용도 = `data` · `blob-name` |
| 복구 코드 keywrap | Argon2id13(ops 3, mem 64MiB, salt 16바이트) → XChaCha20-Poly1305, AAD `kkotsin-keywrap-v1` |

## 인라인 푸시 페이로드

```json
{ "v": 1, "kind": "missyou", "opId": "…", "stream": "…", "seq": 12, "epoch": 2, "ct": "<base64 nonce‖ct‖tag>", "sig": "<base64>" }
```

- 암호화 전 원본 ≤ 2KB일 때만 실는다. 나머지는 `kind`만 실린 초인종이다.
- Swift 쪽은 `missyou` · `status`만 해석하고, 모르는 `kind` · `v`는 "새 소식이 있어요"로 표시한다.
