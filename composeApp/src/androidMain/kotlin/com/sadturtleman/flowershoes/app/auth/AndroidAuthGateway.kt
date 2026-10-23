package com.sadturtleman.flowershoes.app.auth

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.sadturtleman.flowershoes.core.auth.AuthGateway
import com.sadturtleman.flowershoes.core.auth.AuthProvider
import com.sadturtleman.flowershoes.core.auth.SignInResult
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await

/** 로그인 화면(Credential Manager · 카카오톡 · Apple 웹)은 지금 앞에 있는 Activity 가 필요하다. */
object CurrentActivity : Application.ActivityLifecycleCallbacks {
    private var ref = WeakReference<Activity>(null)
    val activity: Activity? get() = ref.get()

    override fun onActivityResumed(activity: Activity) { ref = WeakReference(activity) }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { ref = WeakReference(activity) }
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/** 빌드에 들어간 로그인 키(local.properties → BuildConfig). 비어 있으면 그 제공자는 "설정 전". */
data class AuthKeys(val kakaoNativeAppKey: String, val googleWebClientId: String)

/**
 * Android 로그인(설계 7-0).
 *
 * - 카카오: SDK 로그인(카카오톡 → 없으면 카카오계정) → 함수 kakaoSignIn(app_id 확인) → 커스텀 토큰
 * - Google: Credential Manager → Firebase GoogleAuthProvider
 * - Apple: Firebase OAuth(apple.com) 웹 흐름
 * 로그인 정보로는 데이터를 풀 수 없다 — 신원 확인과 백업 위치 찾기에만 쓴다.
 */
class AndroidAuthGateway(
    private val context: Context,
    private val keys: AuthKeys,
    private val hasBackup: suspend (uid: String) -> Boolean,
    private val region: String = "asia-northeast3",
) : AuthGateway {

    private val auth get() = FirebaseAuth.getInstance()

    init {
        if (keys.kakaoNativeAppKey.isNotBlank()) KakaoSdk.init(context, keys.kakaoNativeAppKey)
    }

    override val providers: List<AuthProvider> = AuthProvider.entries

    override suspend fun signIn(provider: AuthProvider): SignInResult {
        val activity = CurrentActivity.activity ?: return SignInResult.Failed("화면을 찾지 못했어요")
        return try {
            val uid = when (provider) {
                AuthProvider.KAKAO -> kakao(activity) ?: return SignInResult.Cancelled
                AuthProvider.GOOGLE -> googleToken(activity)?.let { auth.signInWithCredential(GoogleAuthProvider.getCredential(it, null)).await().user?.uid }
                    ?: return SignInResult.Cancelled
                AuthProvider.APPLE -> apple(activity)
            }
            SignInResult.Success(uid, provider, hasBackup = runCatching { hasBackup(uid) }.getOrDefault(false))
        } catch (e: NotConfigured) {
            SignInResult.Failed(e.message ?: "로그인 설정 전이에요")
        } catch (e: Exception) {
            SignInResult.Failed("로그인하지 못했어요: ${e.message}")
        }
    }

    override suspend fun signOut() {
        auth.signOut()
        if (keys.kakaoNativeAppKey.isNotBlank()) runCatching { suspendCancellableCoroutine<Unit> { c -> UserApiClient.instance.logout { c.resume(Unit) } } }
    }

    private class NotConfigured(message: String) : Exception(message)

    private suspend fun kakao(activity: Activity): String? {
        if (keys.kakaoNativeAppKey.isBlank()) throw NotConfigured("카카오 로그인 설정 전이에요")
        val token = kakaoToken(activity) ?: return null
        @Suppress("UNCHECKED_CAST")
        val result = FirebaseFunctions.getInstance(region).getHttpsCallable("kakaoSignIn")
            .call(mapOf("accessToken" to token.accessToken)).await().getData() as Map<String, Any?>
        val custom = result["token"] as? String ?: error("커스텀 토큰이 없다")
        return auth.signInWithCustomToken(custom).await().user?.uid
    }

    private suspend fun kakaoToken(activity: Activity): OAuthToken? = suspendCancellableCoroutine { c ->
        val client = UserApiClient.instance
        val onAccount: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            when {
                error is ClientError && error.reason == ClientErrorCause.Cancelled -> c.resume(null)
                error != null -> c.resumeWithException(error)
                else -> c.resume(token)
            }
        }
        if (client.isKakaoTalkLoginAvailable(activity)) {
            client.loginWithKakaoTalk(activity) { token, error ->
                // 카카오톡에서 취소한 경우는 그대로 취소, 그 밖의 실패는 카카오계정 로그인으로 넘어간다.
                if (error is ClientError && error.reason == ClientErrorCause.Cancelled) c.resume(null)
                else if (error != null) client.loginWithKakaoAccount(activity, callback = onAccount)
                else c.resume(token)
            }
        } else {
            client.loginWithKakaoAccount(activity, callback = onAccount)
        }
    }

    override suspend fun linkedProviders(): Set<AuthProvider> =
        auth.currentUser?.providerData.orEmpty().mapNotNull { com.sadturtleman.flowershoes.core.auth.authProviderOf(it.providerId) }.toSet()

    /** 다른 로그인 방법 연결: Google 은 Credential Manager id 토큰, Apple 은 Firebase OAuth 웹 흐름으로 지금 계정에 더한다. */
    override suspend fun link(provider: AuthProvider): SignInResult {
        val activity = CurrentActivity.activity ?: return SignInResult.Failed("화면을 찾지 못했어요")
        val user = auth.currentUser ?: return SignInResult.Failed("로그인 전이에요")
        return try {
            when (provider) {
                AuthProvider.GOOGLE -> {
                    val idToken = googleToken(activity) ?: return SignInResult.Cancelled
                    user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                }
                AuthProvider.APPLE -> {
                    val p = OAuthProvider.newBuilder("apple.com").setScopes(listOf("email", "name")).build()
                    user.startActivityForLinkWithProvider(activity, p).await()
                }
                AuthProvider.KAKAO -> return SignInResult.Failed("카카오는 연결할 수 없어요")
            }
            SignInResult.Success(user.uid, provider, hasBackup = false)
        } catch (e: NotConfigured) {
            SignInResult.Failed(e.message ?: "로그인 설정 전이에요")
        } catch (e: Exception) {
            SignInResult.Failed(com.sadturtleman.flowershoes.core.firebase.linkError(e))
        }
    }

    /** Google id 토큰(Credential Manager). 취소면 null. */
    private suspend fun googleToken(activity: Activity): String? {
        if (keys.googleWebClientId.isBlank()) throw NotConfigured("Google 로그인 설정 전이에요")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetGoogleIdOption.Builder().setServerClientId(keys.googleWebClientId).setFilterByAuthorizedAccounts(false).build())
            .build()
        val response = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (_: GetCredentialCancellationException) {
            return null
        }
        return GoogleIdTokenCredential.createFrom(response.credential.data).idToken
    }

    private suspend fun apple(activity: Activity): String {
        val provider = OAuthProvider.newBuilder("apple.com").setScopes(listOf("email", "name")).build()
        val result = auth.pendingAuthResult?.await() ?: auth.startActivityForSignInWithProvider(activity, provider).await()
        return checkNotNull(result.user?.uid)
    }
}
