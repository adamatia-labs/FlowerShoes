package com.sadturtleman.flowershoes.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sadturtleman.flowershoes.core.data.DeviceState
import com.sadturtleman.flowershoes.core.database.CoupleEntity

/** 계정/연결이 바뀌면 이전 화면의 편집 상태와 작업을 함께 정리한다. */
@Composable
fun RelationshipScreenScope(account: DeviceState?, couple: CoupleEntity?, content: @Composable () -> Unit) {
    // 부모 ViewModel은 화면 회전에도 남아 같은 세션의 편집 상태를 유지한다.
    val stores = viewModel { RelationshipViewModels() }
    val session = when {
        account == null -> null
        !account.onboarded -> ScreenSession.Onboarding
        else -> ScreenSession.Main(account.userId, account.deviceId, account.accountSession,
            couple?.id, couple?.myUserId, couple?.partnerUserId, couple?.linkedAtMs)
    }
    val owner = stores.select(session)
    key(session) {
        if (owner == null) content()
        else CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
    }
}

private sealed interface ScreenSession {
    // 로그인 성공으로 uid가 바뀌어도 진행 중인 역할/테마 입력을 유지한다. 홈 진입 때 폐기한다.
    data object Onboarding : ScreenSession
    data class Main(val uid: String, val device: String, val generation: String,
        val couple: String?, val me: String?, val partner: String?, val linkedAt: Long?) : ScreenSession
}

private class RelationshipViewModels : ViewModel() {
    private var session: ScreenSession? = null
    private var owner: ViewModelStoreOwner? = null

    fun select(next: ScreenSession?): ViewModelStoreOwner? {
        if (session != next) {
            owner?.viewModelStore?.clear()
            session = next
            owner = next?.let { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
        }
        return owner
    }

    override fun onCleared() { owner?.viewModelStore?.clear() }
}
