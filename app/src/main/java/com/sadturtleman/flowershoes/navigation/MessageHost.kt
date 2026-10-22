package com.sadturtleman.flowershoes.navigation

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import com.sadturtleman.flowershoes.common.domain.message.MessageEffect
import com.sadturtleman.flowershoes.common.presentation.helper.LocalMessageHelper
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerTheme
import kotlinx.coroutines.launch

/**
 * [com.sadturtleman.flowershoes.common.domain.helper.MessageHelper] 로 올라온 메시지를
 * 실제 UI 로 그리는 유일한 지점.
 *
 * 다이얼로그는 이벤트를 그대로 흘려보내지 않고 [remember] 상태로 붙잡는다.
 * 그래야 재구성(recomposition)이나 회전 중에도 떠 있는 다이얼로그가 사라지지 않는다.
 * (프로세스가 죽으면 사라진다 — 되살아나야 하는 선택지는 화면의 UiState 로 다뤄야 한다)
 *
 * 스낵바 호스트는 호출부가 [androidx.compose.material3.Scaffold] 에 연결한다.
 */
@Composable
fun MessageHost(snackbarHostState: SnackbarHostState) {
    val messageHelper = LocalMessageHelper.current
    val context = LocalContext.current

    var dialog by remember { mutableStateOf<MessageEffect?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(messageHelper) {
        messageHelper.messageFlow.collect { effect ->
            when (effect) {
                is MessageEffect.ShowToast ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()

                is MessageEffect.ShowSnackbar -> {
                    // 앞의 스낵바가 떠 있으면 밀어내고 새 것을 띄운다(대기열이 쌓이지 않게).
                    snackbarHostState.currentSnackbarData?.dismiss()
                    // showSnackbar 는 닫힐 때까지 suspend 한다. 수집 코루틴에서 직접 부르면
                    // 그 동안 다음 메시지를 받지 못해, 새 메시지가 앞의 것을 밀어내지 못하고 뒤에 쌓인다.
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = effect.message,
                            actionLabel = effect.actionLabel,
                            withDismissAction = effect.actionLabel == null,
                            // 기본값이 "actionLabel 이 있으면 Indefinite" 라 되돌리기 스낵바가
                            // 화면을 옮겨도 계속 떠 있는다. 알림성 메시지이므로 항상 짧게 둔다.
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) effect.onAction?.invoke()
                    }
                }

                is MessageEffect.ShowOneButtonDialog -> dialog = effect
                is MessageEffect.ShowTwoButtonDialog -> dialog = effect
            }
        }
    }

    when (val current = dialog) {
        is MessageEffect.ShowOneButtonDialog -> FlowerAlertDialog(
            title = current.title,
            description = current.description,
            dismissible = current.dismissible,
            onDismiss = { dialog = null },
            confirmText = current.buttonText,
            onConfirm = {
                dialog = null
                current.onClickButton?.invoke()
            },
        )

        is MessageEffect.ShowTwoButtonDialog -> FlowerAlertDialog(
            title = current.title,
            description = current.description,
            dismissible = current.dismissible,
            onDismiss = { dialog = null },
            confirmText = current.confirmText,
            onConfirm = {
                dialog = null
                current.onConfirm?.invoke()
            },
            cancelText = current.cancelText,
            onCancel = {
                dialog = null
                current.onCancel?.invoke()
            },
        )

        else -> Unit
    }
}

/** 취소 버튼은 [cancelText] 가 있을 때만 그린다 — 한 개짜리와 두 개짜리가 같은 모양을 쓰도록. */
@Composable
private fun FlowerAlertDialog(
    title: String?,
    description: String,
    dismissible: Boolean,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    cancelText: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    AlertDialog(
        // dismissible = false 면 바깥 탭 · 뒤로가기로 닫히지 않는다. 버튼을 눌러야 한다.
        onDismissRequest = { if (dismissible) onDismiss() },
        title = title?.let { { Text(text = it, style = FlowerTheme.typography.titleM) } },
        text = { Text(text = description, style = FlowerTheme.typography.bodyM) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmText, color = FlowerTheme.colors.textBrand)
            }
        },
        dismissButton = cancelText?.let {
            {
                TextButton(onClick = { onCancel?.invoke() }) {
                    Text(text = it, color = FlowerTheme.colors.textSecondary)
                }
            }
        },
        containerColor = FlowerTheme.colors.bgSurface,
        titleContentColor = FlowerTheme.colors.textPrimary,
        textContentColor = FlowerTheme.colors.textSecondary,
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
        ),
    )
}
