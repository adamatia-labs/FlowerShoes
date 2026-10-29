package com.sadturtleman.flowershoes.core.auth

/** 첫 화면의 로그인 버튼 순서(카카오 → Apple → Google). iOS 는 다른 소셜 로그인을 넣으므로 Apple 이 필수다. */
enum class AuthProvider { KAKAO, APPLE, GOOGLE }

sealed interface SignInResult {
    /**
     * 로그인됨. [uid] 는 Firebase uid(카카오는 "kakao:{회원번호}").
     * [displayName] 은 제공자가 알려 준 이름(카카오 닉네임 · Google 이름 · Apple 첫 로그인 이름) — 없을 수 있다.
     */
    data class Success(val uid: String, val provider: AuthProvider, val displayName: String? = null) : SignInResult

    data object Cancelled : SignInResult

    data class Failed(val message: String) : SignInResult
}

/**
 * 로그인. 로그인은 신원 확인에만 쓴다 — 로그인 정보로는 데이터를 풀 수 없다(열쇠는 기기 · 커플 키).
 *
 * 카카오: SDK 로그인 → 액세스 토큰을 Cloud Function `kakaoSignIn` 에 보낸다 → 함수가 app_id 를 확인하고
 * 커스텀 토큰을 돌려준다 → signInWithCustomToken. Apple · Google 은 Firebase Auth 기본 제공자다.
 */
interface AuthGateway {
    /** 지금 플랫폼에서 쓸 수 있는 제공자. */
    val providers: List<AuthProvider>

    suspend fun signIn(provider: AuthProvider): SignInResult

    suspend fun signOut()

    /** 이 계정에 연결된 Apple · Google(로그인 계정 › 다른 로그인 방법 연결). 카카오는 커스텀 토큰이라 연결 대상이 아니다. */
    suspend fun linkedProviders(): Set<AuthProvider> = emptySet()

    /** 지금 계정에 Apple · Google 로그인을 더한다. 성공하면 그 방법으로도 같은 계정에 들어온다. */
    suspend fun link(provider: AuthProvider): SignInResult = SignInResult.Failed("이 빌드에서는 연결할 수 없어요")
}

/** 다른 로그인 방법으로 연결할 수 있는 제공자(Figma: Apple · Google). */
val LINKABLE_PROVIDERS = listOf(AuthProvider.APPLE, AuthProvider.GOOGLE)

/** Firebase 제공자 id → [AuthProvider]. */
fun authProviderOf(firebaseProviderId: String): AuthProvider? = when (firebaseProviderId) {
    "apple.com" -> AuthProvider.APPLE
    "google.com" -> AuthProvider.GOOGLE
    else -> null
}

/**
 * Firebase 프로젝트와 각 SDK 키가 연결되기 전에 쓰는 구현. 로그인 없이 이 기기 안에서만 동작한다.
 * uid 는 로컬 id 그대로 둔다.
 */
class LocalOnlyAuthGateway(override val providers: List<AuthProvider> = AuthProvider.entries) : AuthGateway {
    override suspend fun signIn(provider: AuthProvider): SignInResult =
        SignInResult.Success(uid = "", provider = provider)

    override suspend fun signOut() = Unit
}
