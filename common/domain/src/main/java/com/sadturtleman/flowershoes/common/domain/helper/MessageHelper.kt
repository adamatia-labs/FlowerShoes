package com.sadturtleman.flowershoes.common.domain.helper

import com.sadturtleman.flowershoes.common.domain.message.MessageEffect
import kotlinx.coroutines.flow.Flow

/**
 * 스낵바 · 다이얼로그 · 토스트를 띄우는 단일 통로. [NavigationHelper] 와 같은 모양이다.
 *
 * 호출부(ViewModel)는 이 인터페이스만 알고 Android 를 모른다. 실제로 그리는 것은
 * 호스트인 :app 의 RootComposable 한 곳이라, 화면마다 스낵바 호스트를 두지 않아도 된다.
 */
interface MessageHelper {
    val messageFlow: Flow<MessageEffect>

    fun showToast(message: String)

    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    )

    fun showOneButtonDialog(
        description: String,
        title: String? = null,
        dismissible: Boolean = true,
        buttonText: String = "확인",
        onClickButton: (() -> Unit)? = null,
    )

    fun showTwoButtonDialog(
        description: String,
        title: String? = null,
        dismissible: Boolean = true,
        confirmText: String = "확인",
        onConfirm: (() -> Unit)? = null,
        cancelText: String = "취소",
        onCancel: (() -> Unit)? = null,
    )
}
