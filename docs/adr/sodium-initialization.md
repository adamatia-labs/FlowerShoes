# 동시 암호화 초기화

Android 에뮬레이터에서 Application 시작과 수신 테스트가 겹치자 `LibsodiumInitializer.initializeWithCallback`이 반환 코드 1을 오류로 처리하며 앱 프로세스가 종료됐다. 기존 `isInitialized()` 검사와 초기화 호출 사이에는 동기화가 없었고, `awaitInit()`은 매번 초기화를 호출했다.

## 결정

- JVM/Android는 초기화 여부 검사와 라이브러리 로드·초기화를 같은 모니터로 직렬화한다. 동기 `init()`과 suspend `awaitInit()` 모두 이 경계를 사용한다.
- iOS는 스레드 안전한 네이티브 `sodium_init()`의 완료를 직접 기다린다. Swift가 먼저 초기화한 경우를 포함하여 0과 1은 성공이며 음수는 실패다. 이 반환값의 의미는 [libsodium 공식 문서](https://doc.libsodium.org/usage)에 따른다.
- 공개 API와 키·암호문 형식은 바꾸지 않는다. 지원하는 JVM/Android/iOS 타깃의 초기화는 동기식이다.

## 검증

JVM 16개 스레드가 두 초기화 진입점과 실제 키 생성을 20회씩 실행한다. Android 수신 테스트는 예외 우회 없이 Application과 같은 초기화 진입점을 사용한다. iOS의 실제 컴파일·실행은 CI로 확인한다.
