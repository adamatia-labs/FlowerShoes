package com.sadturtleman.flowershoes.common.presentation.helper

import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import com.sadturtleman.flowershoes.common.domain.helper.MessageHelper
import com.sadturtleman.flowershoes.common.domain.message.MessageEffect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * [Channel] 기반 구현. [NavigationHelperImpl] 과 같은 이유로 채널을 쓴다 —
 * 화면 회전 등으로 호스트가 잠시 없어도 BUFFERED 채널이 보관하고,
 * SharedFlow 와 달리 한 메시지가 정확히 한 번만 소비되어 스낵바가 두 번 뜨지 않는다.
 *
 * Hilt 로 @Singleton 제공되므로 ViewModel 어디서든 주입받아 쓰고,
 * Composable 트리에는 [LocalMessageHelper] 로 내려준다.
 */
class MessageHelperImpl : MessageHelper {
    private val _messageFlow = Channel<MessageEffect>(capacity = Channel.BUFFERED)
    override val messageFlow: Flow<MessageEffect> = _messageFlow.receiveAsFlow()

    override fun showToast(message: String) {
        emit(MessageEffect.ShowToast(message))
    }

    override fun showSnackbar(
        message: String,
        actionLabel: String?,
        onAction: (() -> Unit)?,
    ) {
        emit(
            MessageEffect.ShowSnackbar(
                message = message,
                actionLabel = actionLabel,
                onAction = onAction,
            )
        )
    }

    override fun showOneButtonDialog(
        description: String,
        title: String?,
        dismissible: Boolean,
        buttonText: String,
        onClickButton: (() -> Unit)?,
    ) {
        emit(
            MessageEffect.ShowOneButtonDialog(
                title = title,
                description = description,
                dismissible = dismissible,
                buttonText = buttonText,
                onClickButton = onClickButton,
            )
        )
    }

    override fun showTwoButtonDialog(
        description: String,
        title: String?,
        dismissible: Boolean,
        confirmText: String,
        onConfirm: (() -> Unit)?,
        cancelText: String,
        onCancel: (() -> Unit)?,
    ) {
        emit(
            MessageEffect.ShowTwoButtonDialog(
                title = title,
                description = description,
                dismissible = dismissible,
                confirmText = confirmText,
                onConfirm = onConfirm,
                cancelText = cancelText,
                onCancel = onCancel,
            )
        )
    }

    private fun emit(effect: MessageEffect) {
        val result = _messageFlow.trySend(effect)
        if (result.isFailure) Log.w(TAG, "dropped: $effect")
    }

    private companion object {
        const val TAG = "MessageHelper"
    }
}

val LocalMessageHelper = compositionLocalOf<MessageHelper> {
    error("LocalMessageHelper is not provided. Wrap with CompositionLocalProvider in MainActivity.")
}
