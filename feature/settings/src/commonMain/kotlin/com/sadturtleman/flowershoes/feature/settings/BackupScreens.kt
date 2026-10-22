package com.sadturtleman.flowershoes.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.backup.BackupManager
import com.sadturtleman.flowershoes.core.backup.RestoreResult
import com.sadturtleman.flowershoes.core.crypto.CrockfordCode
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.designsystem.KkCard
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkScreenHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTextField
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class BackupUi(
    val available: Boolean,
    val busy: Boolean = false,
    val lastBackupAtMs: Long? = null,
    val recoveryCode: String? = null,
    val showCode: Boolean = false,
    val message: String? = null,
    val codeInput: String = "",
    val needsCode: Boolean = false,
)

/**
 * 백업 · 복원(설계 7-6). [manager] 가 null 이면 이 플랫폼에는 아직 백업이 붙지 않았다.
 * 복원이 성공하면 앱이 DB 를 바꿔 끼우고 다시 시작한다.
 */
class BackupViewModel(private val manager: BackupManager?, private val identity: Identity) : ViewModel() {
    private val _ui = MutableStateFlow(BackupUi(available = manager != null))
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()

    fun backupNow() = work {
        val m = it.backupNow(identity.userId)
        _ui.update { s -> s.copy(lastBackupAtMs = m.createdAtMs, message = "백업했어요") }
    }

    /** 복구 코드는 기기 인증 뒤에 보여 준다(화면이 먼저 받는다). */
    fun revealCode() = work { m -> _ui.update { it.copy(recoveryCode = m.recoveryCode(), showCode = true) } }

    fun hideCode() = _ui.update { it.copy(showCode = false, recoveryCode = null) }

    fun editCode(text: String) = _ui.update { it.copy(codeInput = text) }

    fun restore() = work { m ->
        val code = _ui.value.codeInput.takeIf { _ui.value.needsCode && it.isNotBlank() }
        val message = when (val r = m.restore(identity.userId, code)) {
            RestoreResult.NoBackup -> "이 계정의 백업이 없어요"
            RestoreResult.NeedsRecoveryCode -> {
                _ui.update { it.copy(needsCode = true) }
                "이 폰에는 백업 열쇠가 없어요. 이전 폰의 설정 › 백업 › 복구 코드를 입력해 주세요"
            }
            RestoreResult.WrongRecoveryCode -> "복구 코드가 맞지 않아요"
            is RestoreResult.TooNew -> "앱을 업데이트한 뒤 다시 시도해 주세요"
            is RestoreResult.Restored -> "복원했어요. 앱을 다시 시작해요"
        }
        _ui.update { it.copy(message = message) }
    }

    private fun work(block: suspend (BackupManager) -> Unit) {
        val m = manager ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            runCatching { block(m) }.onFailure { e -> _ui.update { it.copy(message = "실패했어요: ${e.message}") } }
            _ui.update { it.copy(busy = false) }
        }
    }
}

/** 설정 안의 백업 섹션. */
@Composable
fun BackupSection(viewModel: BackupViewModel = koinViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = KkTheme.colors
    if (!ui.available) {
        Text("이 플랫폼에서는 아직 백업을 쓸 수 없어요.", style = KkTheme.type.bodyS, color = c.textTertiary)
        return
    }
    KkCard {
        Text("암호화 백업", style = KkTheme.type.titleS, color = c.textPrimary)
        Text("백업은 이 폰만 아는 열쇠로 잠가서 올려요. 서버도 열어 볼 수 없어요.", style = KkTheme.type.bodyS, color = c.textSecondary)
        ui.lastBackupAtMs?.let { Text("마지막 백업 ${kotlin.time.Instant.fromEpochMilliseconds(it)}", style = KkTheme.type.caption, color = c.textTertiary) }
        KkSecondaryButton(if (ui.busy) "백업 중…" else "지금 백업", onClick = viewModel::backupNow)
        Text(
            if (ui.showCode) "숨기기" else "복구 코드 보기",
            style = KkTheme.type.labelM, color = c.textBrand,
            modifier = Modifier.clickable { if (ui.showCode) viewModel.hideCode() else viewModel.revealCode() }.padding(vertical = KkSpacing.sm),
        )
        if (ui.showCode) {
            Text(ui.recoveryCode?.let(CrockfordCode::format) ?: "아직 백업하지 않았어요", style = KkTheme.type.titleS, color = c.textBrand)
            Text("다른 기종으로 바꿀 때 필요해요. 비밀번호 관리자에 저장해 두세요.", style = KkTheme.type.caption, color = c.textTertiary)
        }
        ui.message?.let { Text(it, style = KkTheme.type.bodyS, color = c.textSecondary) }
    }
}

/** 새 폰 · 재설치: 백업에서 복원. */
@Composable
fun RestoreRoute(onBack: () -> Unit, viewModel: BackupViewModel = koinViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().padding(KkSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        Text("← 돌아가기", style = KkTheme.type.labelM, color = c.textSecondary, modifier = Modifier.clickable(onClick = onBack))
        KkScreenHeader("백업에서 복원하기", "같은 계정으로 로그인했다면 편지 · 그림 · 일정을 그대로 되살릴 수 있어요.")
        if (!ui.available) {
            Text("이 플랫폼에서는 아직 백업을 쓸 수 없어요.", style = KkTheme.type.bodyM, color = c.textTertiary)
            return@Column
        }
        if (ui.needsCode) {
            KkTextField(ui.codeInput, viewModel::editCode, placeholder = "복구 코드 24자리")
        }
        KkPrimaryButton(if (ui.busy) "복원 중…" else "복원하기", onClick = viewModel::restore, enabled = !ui.busy)
        ui.message?.let { Text(it, style = KkTheme.type.bodyM, color = c.textSecondary) }
        Text("백업도 복구 코드도 없다면, 연인 폰의 설정 › '연인 폰 교체 받기'로 데이터를 넘겨받을 수 있어요.", style = KkTheme.type.bodyS, color = c.textTertiary)
    }
}
