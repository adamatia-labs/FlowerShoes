package com.sadturtleman.flowershoes.app.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.sadturtleman.flowershoes.core.auth.AuthGateway
import com.sadturtleman.flowershoes.core.auth.AuthProvider
import com.sadturtleman.flowershoes.core.auth.SignInResult
import kotlinx.coroutines.tasks.await

/**
 * 개발용: 로컬 Firebase 에뮬레이터(firebase emulators:start --project demo-kkotsin)에 붙는다.
 * local.properties 의 FIREBASE_EMULATOR_HOST(Android 에뮬레이터에서는 10.0.2.2)가 있을 때만 쓴다 — 출시 빌드와 무관.
 */
object EmulatorFirebase {
    const val PROJECT = "demo-kkotsin"

    fun init(context: Context, host: String, region: String = "asia-northeast3") {
        require(host.isNotBlank())
        val existing = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        check(existing == null || existing.options.projectId == PROJECT) {
            "Local QA requires demo-kkotsin. Disable FirebaseInitProvider before initializing the emulator."
        }
        if (existing == null) FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder().setProjectId(PROJECT).setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("demo-key").setStorageBucket("$PROJECT.appspot.com").build(),
        )
        FirebaseFirestore.getInstance().useEmulator(host, 8080)
        FirebaseAuth.getInstance().useEmulator(host, 9099)
        FirebaseFunctions.getInstance(region).useEmulator(host, 5001)
        FirebaseStorage.getInstance().useEmulator(host, 9199)
    }
}

/**
 * 개발용 로그인: 소셜 키 없이 기기마다 이메일 계정을 만든다(규칙이 익명 로그인을 막으므로).
 * 어느 버튼을 눌러도 같은 계정으로 들어간다. 에뮬레이터 또는 디버그 빌드의 DEV_EMAIL_LOGIN 에서만 쓴다.
 */
class DevEmailAuthGateway(
    private val deviceTag: () -> String,
    private val hasBackup: suspend (String) -> Boolean,
    /** 다른 로그인 방법 연결은 실제 제공자 화면이 필요해 Android 구현에 맡긴다. */
    private val linker: AuthGateway? = null,
) : AuthGateway {
    override val providers: List<AuthProvider> = AuthProvider.entries

    override suspend fun signIn(provider: AuthProvider): SignInResult = try {
        val auth = FirebaseAuth.getInstance()
        val email = "${deviceTag()}@kkotsin.test"
        val user = runCatching { auth.signInWithEmailAndPassword(email, PASSWORD).await().user }.getOrNull()
            ?: auth.createUserWithEmailAndPassword(email, PASSWORD).await().user
        val uid = checkNotNull(user).uid
        SignInResult.Success(uid, provider, hasBackup = runCatching { hasBackup(uid) }.getOrDefault(false))
    } catch (e: Exception) {
        SignInResult.Failed("개발용 로그인 실패: ${e.message}")
    }

    override suspend fun signOut() = FirebaseAuth.getInstance().signOut()

    override suspend fun linkedProviders() = linker?.linkedProviders().orEmpty()

    override suspend fun link(provider: AuthProvider) = linker?.link(provider) ?: super.link(provider)

    private companion object {
        const val PASSWORD = "kkotsin-dev"
    }
}
