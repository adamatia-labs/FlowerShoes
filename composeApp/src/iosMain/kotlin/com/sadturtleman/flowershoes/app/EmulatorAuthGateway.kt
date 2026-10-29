package com.sadturtleman.flowershoes.app

import com.sadturtleman.flowershoes.core.auth.AuthGateway
import com.sadturtleman.flowershoes.core.auth.AuthProvider
import com.sadturtleman.flowershoes.core.auth.SignInResult
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID

/** Debug + localhost demo-kkotsin 전용. 실제 소셜 로그인 성공으로 간주하지 않는다. */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
internal class EmulatorAuthGateway : AuthGateway {
    override val providers = AuthProvider.entries

    override suspend fun signIn(provider: AuthProvider): SignInResult = try {
        check(kotlin.native.Platform.isDebugBinary)
        val defaults = NSUserDefaults.standardUserDefaults
        val tag = defaults.stringForKey("kkotsin.qa.device") ?: NSUUID().UUIDString.also {
            defaults.setObject(it, forKey = "kkotsin.qa.device")
        }
        val email = "ios-$tag@kkotsin.test"
        val auth = Firebase.auth
        val user = auth.currentUser ?: runCatching {
            auth.signInWithEmailAndPassword(email, "kkotsin-local-test").user
        }.getOrNull() ?: auth.createUserWithEmailAndPassword(email, "kkotsin-local-test").user
        val uid = checkNotNull(user).uid
        SignInResult.Success(uid, provider)
    } catch (e: Exception) {
        SignInResult.Failed("로컬 에뮬레이터 로그인 실패: ${e.message}")
    }

    override suspend fun signOut() = Firebase.auth.signOut()
}
