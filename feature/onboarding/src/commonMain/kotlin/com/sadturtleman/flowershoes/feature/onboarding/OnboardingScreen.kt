package com.sadturtleman.flowershoes.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.auth.AuthProvider
import com.sadturtleman.flowershoes.core.designsystem.KkChoice
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkScreenHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkStepDots
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.kkColors
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.ThemeAccent
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    onOpenRestore: () -> Unit,
    onOpenPairing: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            when (it) {
                OnboardingEvent.Finished -> onFinished()
                OnboardingEvent.OpenRestore -> onOpenRestore()
                OnboardingEvent.OpenPairing -> onOpenPairing()
            }
        }
    }
    OnboardingScreen(state, viewModel::onIntent)
}

@Composable
fun OnboardingScreen(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    val c = KkTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .safeDrawingPadding()
            .padding(horizontal = KkSpacing.screen, vertical = KkSpacing.lg),
    ) {
        if (state.step != OnboardingStep.LOGIN) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onIntent(OnboardingIntent.Back) }, enabled = state.step != OnboardingStep.ROLE) {
                    Text("이전", style = KkTheme.type.labelM, color = c.textSecondary)
                }
                Spacer(Modifier.weight(1f))
                KkStepDots(current = state.step.ordinal - 1, total = OnboardingStep.entries.size - 1)
            }
            Spacer(Modifier.height(KkSpacing.xxl))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            when (state.step) {
                OnboardingStep.LOGIN -> LoginStep(state, onIntent)
                OnboardingStep.ROLE -> RoleStep(state, onIntent)
                OnboardingStep.THEME -> ThemeStep(state, onIntent)
                OnboardingStep.SERVICE -> ServiceStep(state, onIntent)
                OnboardingStep.CONNECT -> ConnectStep()
            }
        }
        when (state.step) {
            OnboardingStep.LOGIN -> Unit
            OnboardingStep.CONNECT -> Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                KkPrimaryButton("초대 코드로 연결하기", onClick = { onIntent(OnboardingIntent.StartPairing) })
                KkSecondaryButton("혼자 먼저 둘러볼게요", onClick = { onIntent(OnboardingIntent.FinishSolo) })
            }
            else -> KkPrimaryButton("다음", onClick = { onIntent(OnboardingIntent.Next) }, enabled = state.canProceed)
        }
    }
}

@Composable
private fun LoginStep(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    val c = KkTheme.colors
    Spacer(Modifier.height(80.dp))
    Text("꽃신", style = KkTheme.type.display, color = c.textBrand)
    Text("기다림을 함께 세는 곰신 커플 앱", style = KkTheme.type.bodyL, color = c.textSecondary)
    Spacer(Modifier.height(120.dp))
    state.providers.forEach { provider ->
        KkSecondaryButton(
            text = when (provider) {
                AuthProvider.KAKAO -> "카카오로 시작하기"
                AuthProvider.APPLE -> "Apple로 시작하기"
                AuthProvider.GOOGLE -> "Google로 시작하기"
            },
            onClick = { if (!state.signingIn) onIntent(OnboardingIntent.SignIn(provider)) },
        )
    }
    state.error?.let { Text(it, style = KkTheme.type.bodyS, color = c.statusDanger) }
    Text(
        "폰을 바꿨나요? 백업에서 복원하기",
        style = KkTheme.type.labelM, color = c.textBrand,
        modifier = Modifier.clickable { onIntent(OnboardingIntent.OpenRestore) }.padding(vertical = KkSpacing.sm),
    )
    Text("시작하면 이용약관과 개인정보처리방침에 동의하게 돼요.", style = KkTheme.type.caption, color = c.textTertiary)
}

@Composable
private fun RoleStep(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    KkScreenHeader("누구신가요?", "역할에 따라 기본 테마와 화면이 조금 달라져요.")
    Spacer(Modifier.height(KkSpacing.sm))
    KkChoice("곰신이에요", state.role == Role.GOMSIN, { onIntent(OnboardingIntent.SelectRole(Role.GOMSIN)) }, description = "군대 간 연인을 기다리고 있어요")
    KkChoice("군인이에요", state.role == Role.SOLDIER, { onIntent(OnboardingIntent.SelectRole(Role.SOLDIER)) }, description = "지금 복무 중이에요")
}

@Composable
private fun ThemeStep(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    KkScreenHeader("테마를 골라 주세요", "내 폰에만 적용돼요. 설정에서 언제든 바꿀 수 있어요.")
    Spacer(Modifier.height(KkSpacing.sm))
    ThemeAccent.entries.forEach { theme ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(kkColors(theme, KkTheme.colors.isDark).bgBrand))
            KkChoice(
                label = theme.label(),
                selected = state.theme == theme,
                onClick = { onIntent(OnboardingIntent.SelectTheme(theme)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceStep(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    val c = KkTheme.colors
    val mine = state.role == Role.SOLDIER
    KkScreenHeader(
        if (mine) "언제 입대했나요?" else "연인은 언제 입대했나요?",
        "군종과 입대일로 전역일을 계산해요. 조기 전역 · 연장은 나중에 직접 고칠 수 있어요.",
    )
    Spacer(Modifier.height(KkSpacing.sm))
    Branch.entries.forEach { b ->
        KkChoice(b.label(), state.branch == b, { onIntent(OnboardingIntent.SelectBranch(b)) })
    }
    var picking by remember { mutableStateOf(false) }
    Spacer(Modifier.height(KkSpacing.sm))
    KkSecondaryButton(state.enlistDate?.let { "입대일 ${it.korean()}" } ?: "입대일 고르기", onClick = { picking = true })
    state.dischargePreview?.let {
        Text("전역 예정일 ${it.korean()}", style = KkTheme.type.titleS, color = c.textMilitary)
    }
    if (picking) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.enlistDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onIntent(OnboardingIntent.SetEnlistDate(Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date))
                    }
                    picking = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("취소") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun ConnectStep() {
    KkScreenHeader(
        "연인과 연결해요",
        "한 사람이 초대 코드를 만들고, 다른 사람이 코드를 입력해요. 두 폰에 뜨는 6자리 확인 코드가 같은지 꼭 확인해 주세요.",
    )
    Spacer(Modifier.height(KkSpacing.sm))
    Text(
        "연결하기 전에도 D-day와 일정은 혼자 쓸 수 있어요. 연결하면 입력한 정보가 상대와 합쳐져요.",
        style = KkTheme.type.bodyM, color = KkTheme.colors.textSecondary,
    )
}

internal fun ThemeAccent.label() = when (this) {
    ThemeAccent.ROSE -> "로즈"
    ThemeAccent.OLIVE -> "올리브"
    ThemeAccent.NAVY -> "네이비"
    ThemeAccent.MONO -> "모노"
}

internal fun Branch.label() = when (this) {
    Branch.ARMY -> "육군"
    Branch.NAVY -> "해군"
    Branch.AIR_FORCE -> "공군"
    Branch.MARINE -> "해병대"
    Branch.SOCIAL_SERVICE -> "사회복무요원"
}

internal fun LocalDate.korean() = "$year.${month.ordinal + 1}.$day"
