package com.sadturtleman.flowershoes.feature.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.designsystem.*
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number

/** 입력 화면이 없던 상대 상태 위젯의 작성 경로. 기존 v2 입력·시트·버튼을 조합한다. */
@Composable
fun CurrentStatusSheet(state: CurrentStatusEditState, editor: CurrentStatusEditor) {
    val c = KkTheme.colors
    KkModalSheet(editor::close, dismissEnabled = !state.saving) {
        CurrentStatusSheetContent(state, editor::label, editor::duration,
            { editor.save(c.eventDuty.toArgb().toLong() and 0xffffffffL) }, editor::clear, editor::close)
    }
}

@Composable
fun ColumnScope.CurrentStatusSheetContent(
    state: CurrentStatusEditState,
    onLabel: (String) -> Unit,
    onDuration: (StatusDuration) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val c = KkTheme.colors
    KkSheetHeader("내 상태", "연결된 상대의 상태 위젯에 보여요")
    KkInput(
        value = state.label, onValueChange = onLabel, placeholder = "예: 당직 중, 공부 중",
        label = "지금 상태", helper = "${state.label.length}/40", enabled = state.ready && !state.saving,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.ready) {
        Text("언제까지 보여 줄까요?", style = KkTheme.type.label, color = c.ink)
        StatusDuration.entries.filter { it != StatusDuration.KEEP || state.previousExpiry != null }.forEach { duration ->
            KkSheetOption(
                label = duration.label, selected = state.duration == duration,
                onClick = { onDuration(duration) }, enabled = !state.saving,
                meta = if (duration == StatusDuration.KEEP) state.previousExpiry?.toLocalDateTime(SEOUL)?.let {
                    "${it.month.number}/${it.day} ${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
                } else null,
            )
        }
    }
    state.error?.let { Text(it, style = KkTheme.type.body, color = c.danger) }
    KkButton(
        "상태 저장", onSave, Modifier.fillMaxWidth(),
        enabled = state.canSave, loading = state.saving, loadingText = "저장 중…",
    )
    if (state.hadStatus) KkButton(
        "상태 지우기", onClear, Modifier.fillMaxWidth(), style = KkButtonStyle.Secondary,
        enabled = state.ready && !state.saving,
    )
    KkButton("닫기", onClose, Modifier.fillMaxWidth(), style = KkButtonStyle.Text, enabled = !state.saving)
}
