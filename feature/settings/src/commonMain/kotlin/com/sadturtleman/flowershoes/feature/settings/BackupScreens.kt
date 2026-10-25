package com.sadturtleman.flowershoes.feature.settings

import androidx.compose.foundation.background
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.flow.first
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkSpinner
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.backup.RestoreStage
import com.sadturtleman.flowershoes.core.backup.KeySource
import com.sadturtleman.flowershoes.core.backup.BackupPreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
fun BackupSection(platform: PlatformActions = NoPlatformActions, viewModel: BackupViewModel = koinViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = KkTheme.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
            modifier = Modifier.clickable {
                if (ui.showCode) viewModel.hideCode()
                // 복구 코드는 기기 인증 뒤에만 보여 준다.
                else scope.launch { if (platform.authenticate("복구 코드를 보려면 본인 확인이 필요해요") != DeviceAuth.Failed) viewModel.revealCode() }
            }.padding(vertical = KkSpacing.sm),
        )
        if (ui.showCode) {
            Text(ui.recoveryCode?.let(CrockfordCode::format) ?: "아직 백업하지 않았어요", style = KkTheme.type.titleS, color = c.textBrand)
            Text("다른 기종으로 바꿀 때 필요해요. 비밀번호 관리자에 저장해 두세요.", style = KkTheme.type.caption, color = c.textTertiary)
        }
        ui.message?.let { Text(it, style = KkTheme.type.bodyS, color = c.textSecondary) }
    }
}

/** 복원 화면 상태(Figma 1 · 온보딩의 복원 다섯 화면). */
sealed interface RestoreUi {
    data object Loading : RestoreUi
    data class Found(val preview: BackupPreview) : RestoreUi
    data class Code(val input: String = "", val error: String? = null, val busy: Boolean = false) : RestoreUi
    data class Restoring(val fraction: Float, val stage: RestoreStage) : RestoreUi
    data class NoBackup(val accountLabel: String) : RestoreUi
    data class Failed(val message: String) : RestoreUi
    data object Unavailable : RestoreUi
    data object Complete : RestoreUi
}

/** 복원 흐름. 성공하면 앱이 DB 를 바꿔 끼우고 다시 시작한다(그 뒤 "거의 다 됐어요"는 셸이 보여 준다). */
class RestoreViewModel(
    private val manager: BackupManager?,
    private val identity: Identity,
    private val settings: com.sadturtleman.flowershoes.core.data.DeviceSettings,
) : ViewModel() {
    private val _ui = MutableStateFlow<RestoreUi>(if (manager == null) RestoreUi.Unavailable else RestoreUi.Loading)
    val ui: StateFlow<RestoreUi> = _ui.asStateFlow()

    init {
        manager?.let { m ->
            viewModelScope.launch {
                val preview = try { m.preview(identity.userId) } catch (_: Exception) {
                    _ui.value = RestoreUi.Failed("백업을 확인하지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요")
                    return@launch
                }
                _ui.value = if (preview != null) RestoreUi.Found(preview) else RestoreUi.NoBackup(accountLabel())
            }
        }
    }

    private suspend fun accountLabel(): String {
        val provider = when (settings.accountProvider.first()) {
            "KAKAO" -> "카카오"
            "APPLE" -> "Apple"
            "GOOGLE" -> "Google"
            else -> "이 기기"
        }
        return "$provider 계정"
    }

    fun useRecoveryCode() { _ui.value = RestoreUi.Code() }

    fun editCode(text: String) = _ui.update { (it as? RestoreUi.Code)?.copy(input = text.uppercase(), error = null) ?: it }

    fun restore() {
        val m = manager ?: return
        val code = (_ui.value as? RestoreUi.Code)?.input?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            _ui.update { if (it is RestoreUi.Code) it.copy(busy = true) else RestoreUi.Restoring(0f, RestoreStage.DATA) }
            val result = runCatching {
                m.restore(identity.userId, code) { f, stage -> _ui.value = RestoreUi.Restoring(f, stage) }
            }.getOrElse { e -> _ui.value = RestoreUi.Failed("복원하지 못했어요: ${e.message}"); return@launch }
            _ui.value = when (result) {
                RestoreResult.NoBackup -> RestoreUi.NoBackup(accountLabel())
                RestoreResult.NeedsRecoveryCode -> RestoreUi.Code()
                RestoreResult.WrongRecoveryCode -> RestoreUi.Code(code.orEmpty(), "복구 코드가 맞지 않아요. 다시 확인해 주세요")
                is RestoreResult.TooNew -> RestoreUi.Failed("앱을 업데이트한 뒤 다시 복원해 주세요")
                is RestoreResult.Restored -> RestoreUi.Complete
            }
        }
    }
}

/**
 * 새 폰 · 재설치: 백업에서 복원.
 * [onStartFresh] 처음 시작하기 · [onRelink] 복구 코드가 없어요(연인 폰에서 넘겨받기) · [onOtherAccount] 다른 방법으로 로그인했었나요?
 */
@Composable
fun RestoreRoute(
    onBack: () -> Unit,
    onStartFresh: () -> Unit = onBack,
    onRelink: () -> Unit = onBack,
    onOtherAccount: () -> Unit = onBack,
    platform: PlatformActions = NoPlatformActions,
    viewModel: RestoreViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(ui) { if (ui == RestoreUi.Complete) onBack() }
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            when (val s = ui) {
                RestoreUi.Loading -> Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) { KkSpinner(c.textBrand, 28.dp) }
                RestoreUi.Complete -> Header("복원했어요")
                RestoreUi.Unavailable -> Header("백업에서 복원하기", "이 플랫폼에서는 아직 백업을 쓸 수 없어요.")
                is RestoreUi.Found -> FoundContent(s.preview, platform.keySyncName)
                is RestoreUi.Code -> {
                    // Figma 최종 정리: 제목 + 칸만(설명 · 도움말 없음).
                    Header("복구 코드를 입력해 주세요")
                    KkInput(
                        value = s.input,
                        onValueChange = viewModel::editCode,
                        label = "복구 코드",
                        placeholder = "XXXX-XXXX-XXXX-XXXX-XXXX-XXXX",
                        error = s.error,
                    )
                }
                is RestoreUi.Restoring -> RestoringContent(s)
                is RestoreUi.NoBackup -> {
                    Header("이 계정에는 백업이 없어요", "처음 쓰는 거라면 바로 시작하면 돼요. 예전에 다른 방법으로 로그인했다면 그 계정에 기록이 있어요.")
                    InfoCard {
                        Text("지금 로그인한 계정", style = KkTheme.type.caption, color = c.textTertiary)
                        Text(s.accountLabel, style = KkTheme.type.titleS, color = c.textPrimary)
                        Text("백업 없음 · 처음 로그인", style = KkTheme.type.bodyS, color = c.textSecondary)
                    }
                }
                is RestoreUi.Failed -> Header("복원하지 못했어요", s.message)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xxl, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
        ) {
            when (val s = ui) {
                is RestoreUi.Found -> {
                    // Figma: 복원하기 · 복구 코드로 복원하기(보조) · 복원 안 하기.
                    KkPrimaryButton("복원하기", viewModel::restore, enabled = s.preview.keySource != KeySource.NONE)
                    KkSecondaryButton("복구 코드로 복원하기", viewModel::useRecoveryCode)
                    KkButton("복원 안 하기", onStartFresh, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost)
                }
                is RestoreUi.Code -> {
                    KkPrimaryButton("복원하기", viewModel::restore, enabled = s.input.isNotBlank(), loading = s.busy)
                    KkButton("복구 코드가 없어요", onRelink, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost)
                }
                is RestoreUi.NoBackup -> {
                    KkPrimaryButton("처음 시작하기", onStartFresh)
                    KkSecondaryButton("다른 방법으로 로그인했었나요?", onOtherAccount)
                }
                is RestoreUi.Failed, RestoreUi.Unavailable -> KkButton("돌아가기", onBack, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost)
                // 복원 중에는 버튼이 없다(Figma: 빈 Footer).
                else -> Spacer(Modifier.height(52.dp))
            }
        }
    }
}

@Composable
private fun Header(title: String, body: String? = null) {
    Text(title, style = KkTheme.type.titleL, color = KkTheme.colors.textPrimary)
    body?.let { Text(it, style = KkTheme.type.bodyL, color = KkTheme.colors.textSecondary) }
}

/** 흰 카드(반경 12, Elevation/Low, 여백 20). */
@Composable
private fun InfoCard(spacing: androidx.compose.ui.unit.Dp = 10.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(KkTheme.colors.bgSurface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
private fun FoundContent(preview: BackupPreview, keySyncName: String) {
    val c = KkTheme.colors
    val sum = preview.summary
    Header(if (!sum?.myName.isNullOrBlank()) "다시 오셨네요, ${sum?.myName}님" else "다시 오셨네요")
    InfoCard {
        Text("마지막 백업", style = KkTheme.type.caption, color = c.textTertiary)
        Text(backupTime(preview.createdAtMs), style = KkTheme.type.titleS, color = c.textPrimary)
        Text(
            sum?.let { "편지 ${it.letters} · 그림 ${it.drawings} · 사진 ${it.photos} · ${bytesLabel(it.bytes)}" } ?: "열쇠를 찾으면 담긴 내용을 볼 수 있어요",
            style = KkTheme.type.bodyS, color = c.textSecondary,
        )
    }
}

@Composable
private fun RestoringContent(s: RestoreUi.Restoring) {
    val c = KkTheme.colors
    Header("백업을 복원하는 중이에요")
    val started = remember { kotlin.time.Clock.System.now().toEpochMilliseconds() }
    InfoCard(spacing = 14.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${(s.fraction * 100).toInt()}%", style = KkTheme.type.titleM, color = c.textBrand)
            // Figma: "약 2분 남음" — 지금까지 걸린 시간으로 어림한다.
            val pct = (s.fraction * 100).toInt()
            val elapsed = kotlin.time.Clock.System.now().toEpochMilliseconds() - started
            val remain = if (pct in 1..99) elapsed * (100 - pct) / pct / 60_000 else null
            Text(
                when { s.stage == RestoreStage.RELINK -> "곧 다시 시작해요"; remain == null -> "받는 중"; else -> "약 ${maxOf(1, remain)}분 남음" },
                style = KkTheme.type.caption, color = c.textTertiary,
            )
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.bgSubtle)) {
            Box(Modifier.fillMaxWidth(s.fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.bgBrand))
        }
        fun state(stage: RestoreStage) = when {
            s.stage.ordinal > stage.ordinal -> "완료" to c.textSecondary
            s.stage == stage && stage != RestoreStage.RELINK -> "받는 중" to c.textBrand
            s.stage == stage -> "이어 가는 중" to c.textBrand
            else -> "대기" to c.textTertiary
        }
        listOf("편지 · 일정 · 설정" to RestoreStage.DATA, "그림 · 사진" to RestoreStage.MEDIA, "연인과 다시 연결" to RestoreStage.RELINK).forEach { (label, stage) ->
            val (text, color) = state(stage)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = KkTheme.type.bodyM, color = c.textPrimary)
                Text(text, style = KkTheme.type.labelS, color = color)
            }
        }
    }
}

/** "2026.10.21 (화) 21:40" (서울 시간). */
internal fun backupTime(ms: Long): String {
    val t = kotlin.time.Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.of("Asia/Seoul"))
    val day = listOf("월", "화", "수", "목", "금", "토", "일")[t.dayOfWeek.ordinal]
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${t.year}.${two(t.month.number)}.${two(t.day)} ($day) ${two(t.hour)}:${two(t.minute)}"
}

internal fun bytesLabel(b: Long): String = when {
    b == 0L -> "0KB"
    b >= 1_000_000_000 -> "${(b / 100_000_000) / 10.0}GB"
    b >= 1_000_000 -> "${b / 1_000_000}MB"
    else -> "${maxOf(1, b / 1_000)}KB"
}

/**
 * 복원 · 지수 폰 확인 대기("거의 다 됐어요"). 복원 뒤 다시 시작했을 때 상대 폰의 자동 확인을 기다리는 동안 셸이 한 번 보여 준다.
 */
@Composable
fun RestoreWaitingScreen(partnerName: String, onBrowse: () -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg), verticalArrangement = Arrangement.spacedBy(KkSpacing.lg)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(c.bgBrandSubtle), contentAlignment = Alignment.Center) {
                KkIcon(KkIcons.Clock, tint = c.textPrimary)
            }
            Header("거의 다 됐어요")
            InfoCard(spacing = 14.dp) {
                listOf(
                    Triple("백업 복원", "완료", c.statusSuccess to c.textSecondary),
                    Triple("${partnerName} 폰 확인", "기다리는 중", c.bgBrand to c.textBrand),
                    Triple("편지 · 보고싶어 보내기", "확인 후", c.borderStrong to c.textTertiary),
                ).forEach { (label, state, colors) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(colors.first))
                        Text(label, style = KkTheme.type.bodyM, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text(state, style = KkTheme.type.labelS, color = colors.second)
                    }
                }
            }
        }
        KkPrimaryButton("기록 둘러보기", onBrowse, Modifier.padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xxl, bottom = 14.dp))
    }
}
