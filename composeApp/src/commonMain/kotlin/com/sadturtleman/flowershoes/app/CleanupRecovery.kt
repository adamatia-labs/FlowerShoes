package com.sadturtleman.flowershoes.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.sadturtleman.flowershoes.core.data.sync.UnlinkService
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject

/** 재실행에서 저장된 정리를 이어가며, 새 연결/편집 화면으로 들어가기 전에 결과를 보여 준다. */
@Composable
internal fun CleanupRecovery() {
    val service = koinInject<UnlinkService>()
    val pending by service.pendingCleanup.collectAsState(null)
    var retry by remember { mutableIntStateOf(0) }
    var failed by remember(pending?.token) { mutableStateOf(false) }
    LaunchedEffect(pending?.token, retry) {
        failed = false
        try {
            failed = withTimeoutOrNull(30_000) { service.resumePending(); true } != true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { failed = true }
    }
    val cleanup = pending ?: return
    AlertDialog(
        onDismissRequest = {},
        containerColor = KkTheme.colors.surface,
        title = { Text(if (cleanup.source == "ACCOUNT") "탈퇴를 마무리하고 있어요" else "연결 해제를 마무리하고 있어요", style = KkTheme.type.title, color = KkTheme.colors.ink) },
        text = {
            if (failed) Text("아직 정리를 마치지 못했어요. 선택한 내용은 그대로 남아 있어요.", style = KkTheme.type.body, color = KkTheme.colors.sub)
            else CircularProgressIndicator(color = KkTheme.colors.ink)
        },
        confirmButton = { if (failed) KkButton("다시 시도", { retry++ }) },
    )
}
