package com.sadturtleman.flowershoes.core.firebase

import com.sadturtleman.flowershoes.core.auth.AuthGateway
import com.sadturtleman.flowershoes.core.auth.AuthProvider
import com.sadturtleman.flowershoes.core.auth.SignInResult
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.OAuthProvider
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.functions.functions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable

/** 제공자 화면이 돌려준 토큰. 취소면 [token] · [error] 가 모두 null. */
class ProviderToken(
    val token: String?,
    val rawNonce: String? = null,
    val error: String? = null,
    /** Apple 일회용 authorization code. 서버가 refresh token 으로 바꿔 두었다가 탈퇴 때 폐기한다. */
    val authCode: String? = null,
)

/**
 * 제공자 SDK 화면(카카오 SDK · Sign in with Apple · Google Sign-In). iOS 에서는 Swift 가 구현한다 —
 * Swift 는 Kotlin suspend 함수를 구현할 수 없어서 콜백 모양이다.
 */
interface SocialSignInUi {
    val kakaoConfigured: Boolean
    val googleConfigured: Boolean

    /** 카카오 액세스 토큰. */
    fun kakao(done: (ProviderToken) -> Unit)

    /** Apple id 토큰 + 요청에 쓴 원래 nonce(요청에는 SHA256 을 넣는다). */
    fun apple(done: (ProviderToken) -> Unit)

    /** Google id 토큰. */
    fun google(done: (ProviderToken) -> Unit)
}

/**
 * 공통 Firebase 로그인(설계 7-0). 화면은 [SocialSignInUi] 가, 계정 연결은 여기서:
 * 카카오 → 함수 kakaoSignIn(app_id 확인) → 커스텀 토큰, Apple · Google → Firebase 기본 제공자.
 * iOS 가 쓴다. Android 는 Credential Manager 때문에 앱 쪽 구현(AndroidAuthGateway)을 쓴다.
 */
class FirebaseAuthGateway(
    private val ui: SocialSignInUi,
    private val region: String = "asia-northeast3",
) : AuthGateway {

    override val providers: List<AuthProvider> = AuthProvider.entries

    @Serializable
    private data class KakaoResult(val token: String)

    override suspend fun signIn(provider: AuthProvider): SignInResult = try {
        val uid = when (provider) {
            AuthProvider.KAKAO -> {
                if (!ui.kakaoConfigured) return SignInResult.Failed("카카오 로그인 설정 전이에요")
                val t = await(ui::kakao).token ?: return SignInResult.Cancelled
                val custom = Firebase.functions(region).httpsCallable("kakaoSignIn")
                    .invoke(mapOf("accessToken" to t)).data(KakaoResult.serializer()).token
                Firebase.auth.signInWithCustomToken(custom).user?.uid
            }
            AuthProvider.APPLE -> {
                val t = await(ui::apple)
                val idToken = t.token ?: return SignInResult.Cancelled
                val uid = Firebase.auth.signInWithCredential(OAuthProvider.credential("apple.com", idToken = idToken, rawNonce = t.rawNonce)).user?.uid
                // 탈퇴 때 Apple 토큰을 폐기할 수 있게 code 를 맡긴다. 실패해도 로그인은 그대로.
                t.authCode?.let { code ->
                    runCatching { Firebase.functions(region).httpsCallable("appleStoreToken").invoke(mapOf("code" to code)) }
                }
                uid
            }
            AuthProvider.GOOGLE -> {
                if (!ui.googleConfigured) return SignInResult.Failed("Google 로그인 설정 전이에요")
                val idToken = await(ui::google).token ?: return SignInResult.Cancelled
                Firebase.auth.signInWithCredential(GoogleAuthProvider.credential(idToken, null)).user?.uid
            }
        } ?: return SignInResult.Failed("로그인하지 못했어요")
        SignInResult.Success(uid, provider)
    } catch (e: ProviderFailed) {
        SignInResult.Failed(e.message ?: "로그인하지 못했어요")
    } catch (e: Exception) {
        SignInResult.Failed("로그인하지 못했어요: ${e.message}")
    }

    override suspend fun signOut() = Firebase.auth.signOut()

    override suspend fun linkedProviders(): Set<AuthProvider> =
        Firebase.auth.currentUser?.providerData.orEmpty().mapNotNull { com.sadturtleman.flowershoes.core.auth.authProviderOf(it.providerId) }.toSet()

    override suspend fun link(provider: AuthProvider): SignInResult = try {
        val user = Firebase.auth.currentUser ?: return SignInResult.Failed("로그인 전이에요")
        val credential = when (provider) {
            AuthProvider.APPLE -> {
                val t = await(ui::apple)
                OAuthProvider.credential("apple.com", idToken = t.token ?: return SignInResult.Cancelled, rawNonce = t.rawNonce)
            }
            AuthProvider.GOOGLE -> {
                if (!ui.googleConfigured) return SignInResult.Failed("Google 로그인 설정 전이에요")
                GoogleAuthProvider.credential(await(ui::google).token ?: return SignInResult.Cancelled, null)
            }
            AuthProvider.KAKAO -> return SignInResult.Failed("카카오는 연결할 수 없어요")
        }
        user.linkWithCredential(credential)
        SignInResult.Success(user.uid, provider)
    } catch (e: ProviderFailed) {
        SignInResult.Failed(e.message ?: "연결하지 못했어요")
    } catch (e: Exception) {
        SignInResult.Failed(linkError(e))
    }

    private class ProviderFailed(message: String) : Exception(message)

    private suspend fun await(start: ((ProviderToken) -> Unit) -> Unit): ProviderToken {
        val t = suspendCancellableCoroutine { c -> start { if (c.isActive) c.resume(it) } }
        t.error?.let { throw ProviderFailed(it) }
        return t
    }
}

/** 연결 실패 문구: 다른 꽃신 계정이 이미 쓰는 Apple · Google 이면 그렇게 알린다. */
fun linkError(e: Exception): String =
    if (e.message.orEmpty().contains("already", ignoreCase = true)) "이미 다른 꽃신 계정에 연결된 계정이에요" else "연결하지 못했어요: ${e.message}"

/** Firebase 가 초기화됐는지(iOS: GoogleService-Info.plist 가 있어 Swift 가 configure 했는지). */
fun firebaseConfigured(): Boolean = runCatching { Firebase.app }.isSuccess

