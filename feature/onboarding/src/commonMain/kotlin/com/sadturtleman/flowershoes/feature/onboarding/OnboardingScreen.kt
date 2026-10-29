@file:OptIn(ExperimentalComposeUiApi::class)

package com.sadturtleman.flowershoes.feature.onboarding

import com.sadturtleman.flowershoes.core.designsystem.TermsDoc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role as A11y
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.auth.AuthProvider
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkChoice
import com.sadturtleman.flowershoes.core.designsystem.KkCheckbox
import com.sadturtleman.flowershoes.core.designsystem.KkDdayCard
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkLogoMark
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadio
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSegmentedControl
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSheetOption
import com.sadturtleman.flowershoes.core.designsystem.KkSheetSurface
import com.sadturtleman.flowershoes.core.designsystem.KkSocial
import com.sadturtleman.flowershoes.core.designsystem.KkSocialButton
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkStepIndicator
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.kkColors
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.military.Rank
import com.sadturtleman.flowershoes.core.military.ServiceRulesTable
import com.sadturtleman.flowershoes.core.military.expectedName
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.koreanName
import com.sadturtleman.flowershoes.core.model.mood
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import org.koin.compose.viewmodel.koinViewModel

/**
 * 온보딩(Figma 1 · 온보딩). 마지막 시작 방법에서 "연인 초대하기"면 [onOpenPairing] — 연결 화면(:feature:pairing)은 앱 셸이 띄운다.
 * [invited] 면 받은 초대 링크가 있다: 시작 방법을 묻지 않고 바로 수락 화면으로 간다.
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    onOpenPairing: () -> Unit,
    invited: Boolean = false,
    /** 이미 연결된 계정의 새 폰 → 기기 교체 화면. */
    onDeviceChange: () -> Unit = {},
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            when (it) {
                OnboardingEvent.Finished -> onFinished()
                OnboardingEvent.OpenPairing -> onOpenPairing()
                OnboardingEvent.DeviceChange -> onDeviceChange()
            }
        }
    }
    BackHandler(enabled = state.reading != null || state.step.ordinal > OnboardingStep.TERMS.ordinal) {
        viewModel.onIntent(OnboardingIntent.Back)
    }
    LaunchedEffect(state.step, invited) {
        if (state.step == OnboardingStep.CONNECT && invited) viewModel.onIntent(OnboardingIntent.StartPairing)
    }
    OnboardingScreen(state, viewModel::onIntent)
}

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
) {
    val c = KkTheme.colors
    state.reading?.let { doc ->
        TermsScreen(doc, onClose = { onIntent(OnboardingIntent.Read(null)) })
        return
    }
    var sheet by remember { mutableStateOf<ServiceSheet?>(null) }
    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Content: 좌우 20 · 위아래 16, 사이 16. 로그인만 가운데 정렬.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (state.step == OnboardingStep.LOGIN) Modifier else Modifier.verticalScroll(rememberScrollState()))
                    .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
                verticalArrangement = if (state.step == OnboardingStep.LOGIN) {
                    Arrangement.spacedBy(KkSpacing.lg, Alignment.CenterVertically)
                } else {
                    Arrangement.spacedBy(KkSpacing.lg)
                },
                horizontalAlignment = if (state.step == OnboardingStep.LOGIN) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                when (state.step) {
                    OnboardingStep.LOGIN -> LoginContent()
                    OnboardingStep.TERMS -> TermsContent(state, onIntent)
                    OnboardingStep.ROLE -> RoleContent(state, onIntent)
                    OnboardingStep.THEME -> ThemeContent(state, onIntent)
                    OnboardingStep.SERVICE -> ServiceContent(state) { sheet = it }
                    OnboardingStep.CONNECT -> {
                        // 시작 방법(346:457): 연인 초대하기 · 혼자 먼저 시작하기.
                        KkStepIndicator(step = state.totalSteps, total = state.totalSteps)
                        Text("어떻게 시작할까요?", style = KkTheme.type.title, color = c.ink)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            KkChoice("연인 초대하기", state.startWithInvite, { onIntent(OnboardingIntent.SelectStart(true)) })
                            KkChoice("혼자 먼저 시작하기", !state.startWithInvite, { onIntent(OnboardingIntent.SelectStart(false)) })
                        }
                    }
                }
            }
            // Footer: 위 24 · 좌우 20 · 아래 34(홈 인디케이터는 navigationBarsPadding 이 더한다).
            Column(
                Modifier.fillMaxWidth().padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xxl, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
            ) {
                when (state.step) {
                    OnboardingStep.LOGIN -> {
                        state.providers.forEach { p ->
                            KkSocialButton(p.social(), onClick = { onIntent(OnboardingIntent.SignIn(p)) }, enabled = !state.signingIn)
                        }
                        state.error?.let { Text(it, style = KkTheme.type.caption, color = c.danger) }
                    }
                    OnboardingStep.TERMS -> KkPrimaryButton("동의하고 시작하기", { onIntent(OnboardingIntent.Next) }, enabled = state.canProceed)
                    else -> KkPrimaryButton("다음", { onIntent(OnboardingIntent.Next) }, enabled = state.canProceed)
                }
            }
        }
        when (sheet) {
            ServiceSheet.BRANCH -> BranchSheet(state, onDismiss = { sheet = null }) { onIntent(OnboardingIntent.SelectBranch(it)); sheet = null }
            ServiceSheet.DATE -> DateSheet(state, onDismiss = { sheet = null }) { onIntent(OnboardingIntent.SetEnlistDate(it)); sheet = null }
            null -> Unit
        }
    }
}

private fun AuthProvider.social() = when (this) {
    AuthProvider.KAKAO -> KkSocial.Kakao
    AuthProvider.APPLE -> KkSocial.Apple
    AuthProvider.GOOGLE -> KkSocial.Google
}

// ── 온보딩 0 · 로그인 ──

@Composable
private fun LoginContent() {
    val c = KkTheme.colors
    KkLogoMark()
    Text("꽃신", style = KkTheme.type.title, color = c.accent)
    Text("곰신과 군인, 둘만의 전역 달력", style = KkTheme.type.title, color = c.ink, textAlign = TextAlign.Center)
}

// ── 온보딩 0-1 · 약관 동의 ──

@Composable
private fun TermsContent(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    val c = KkTheme.colors
    Text("꽃신을 시작하려면\n동의가 필요해요", style = KkTheme.type.title, color = c.ink)
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier
            .fillMaxWidth()
            .kkElevation(KkElevation.Low, shape)
            .clip(shape)
            .background(c.surface)
            .toggleable(value = state.allAgreed, role = A11y.Checkbox) { onIntent(OnboardingIntent.ToggleAll) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KkCheckbox(state.allAgreed)
        Text("전체 동의", style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
    }
    Column(Modifier.fillMaxWidth()) {
        Agreement.entries.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .toggleable(value = item in state.agreed, role = A11y.Checkbox) { onIntent(OnboardingIntent.ToggleAgreement(item)) }
                    .padding(start = 16.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KkCheckbox(item in state.agreed)
                Text(item.label, style = KkTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).then(if (item.doc != null) Modifier.clickable(role = A11y.Button) { onIntent(OnboardingIntent.Read(item.doc)) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.doc != null) KkIcon(KkIcons.ChevronRight, tint = c.sub, contentDescription = "${item.doc.title} 보기")
                }
            }
        }
    }
}

// ── 온보딩 0-2 · 약관 보기 ──

@Composable
private fun TermsScreen(doc: TermsDoc, onClose: () -> Unit) = com.sadturtleman.flowershoes.core.designsystem.KkTermsScreen(doc, onClose)

// ── 온보딩 1 · 역할 ──

@Composable
private fun RoleContent(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    val c = KkTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        KkLogoMark(size = 44.dp)
        Column {
            Text("꽃신", style = KkTheme.type.title, color = c.accent)
            Text("곰신이 꽃신 되는 날까지", style = KkTheme.type.caption, color = c.sub)
        }
    }
    KkStepIndicator(step = 1, total = state.totalSteps)
    Text("어느 쪽이신가요?", style = KkTheme.type.title, color = c.ink)
    RoleCard("곰신이에요", "군대 간 연인을 기다리고 있어요", KkIcons.Heart, c.surface, c.stamp, state.role == Role.GOMSIN) {
        onIntent(OnboardingIntent.SelectRole(Role.GOMSIN))
    }
    RoleCard("군인이에요", "지금 복무 중이에요", KkIcons.Clock, c.bar, c.accent, state.role == Role.SOLDIER) {
        onIntent(OnboardingIntent.SelectRole(Role.SOLDIER))
    }
}

@Composable
private fun RoleCard(title: String, body: String, icon: KkIcons, iconBg: Color, iconTint: Color, selected: Boolean, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.xl)
    Row(
        Modifier
            .fillMaxWidth()
            .kkElevation(KkElevation.Low, shape)
            .clip(shape)
            .background(if (selected) c.bar else c.surface)
            .then(if (selected) Modifier.border(2.dp, c.accent, shape) else Modifier)
            .clickable(role = A11y.RadioButton, onClick = onClick)
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.xxl),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
            KkIcon(icon, tint = iconTint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs)) {
            Text(title, style = KkTheme.type.title, color = c.ink)
            Text(body, style = KkTheme.type.body, color = c.sub)
        }
    }
}

// ── 온보딩 2 · 테마 ──

@Composable
private fun ThemeContent(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    val c = KkTheme.colors
    KkStepIndicator(step = 2, total = state.totalSteps)
    Text("앱 색을 골라 주세요", style = KkTheme.type.title, color = c.ink)
    val recommended = state.role?.let { AppTheme.defaultFor(it) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTheme.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { theme ->
                    ThemeCard(theme, selected = state.theme == theme, recommended = theme == recommended, modifier = Modifier.weight(1f)) {
                        onIntent(OnboardingIntent.SelectTheme(theme))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ThemeCard(theme: AppTheme, selected: Boolean, recommended: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    com.sadturtleman.flowershoes.core.designsystem.KkThemeCard(theme, selected, recommended, modifier, onClick)

// ── 온보딩 2 · 입대일 (+ 군종 · 입대일 시트) ──

private enum class ServiceSheet { BRANCH, DATE }

private fun Branch.months(today: LocalDate) = ServiceRulesTable.BUNDLED.ruleFor(this, today).serviceMonths

@Composable
private fun ServiceContent(state: OnboardingState, openSheet: (ServiceSheet) -> Unit) {
    val c = KkTheme.colors
    val mine = state.role == Role.SOLDIER
    KkStepIndicator(step = 3, total = 4)
    Text(if (mine) "언제 입대했나요?" else "연인은 언제 입대했나요?", style = KkTheme.type.title, color = c.ink)
    KkInput(
        value = "${state.branch.koreanName} · ${state.branch.months(state.today)}개월",
        onValueChange = {},
        label = "군종",
        onClick = { openSheet(ServiceSheet.BRANCH) },
    )
    KkInput(
        value = state.enlistDate?.dotted() ?: "",
        onValueChange = {},
        label = "입대일",
        placeholder = "입대일을 골라 주세요",
        onClick = { openSheet(ServiceSheet.DATE) },
    )
    Text("미리보기", style = KkTheme.type.label, color = c.sub)
    val t = state.preview
    KkDdayCard(
        days = t?.let { "D-${it.daysLeft(state.today)}" } ?: "D-?",
        rank = "${(t?.rankAt(state.today)?.rank ?: Rank.PRIVATE).expectedName} · ${state.branch.koreanName}",
        percent = "복무 ${t?.let { ((it.progress(state.today).coerceIn(0.0, 1.0) * 1000).toInt() / 10.0) } ?: 0.0}%",
        date = t?.let { "${it.dischargeDate.dotted()} 전역" } ?: "입대일을 고르면 보여요",
        progress = t?.progress(state.today)?.toFloat() ?: 0f,
    )
}

/** 하단 시트 + 45% 딤. */
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(KkTheme.colors.ink.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        KkSheetSurface(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, content = content)
    }
}

@Composable
private fun BranchSheet(state: OnboardingState, onDismiss: () -> Unit, onPick: (Branch) -> Unit) {
    var pick by remember { mutableStateOf(state.branch) }
    Sheet(onDismiss) {
        KkSheetHeader("군종 선택")
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
            Branch.entries.forEach { b ->
                KkSheetOption(b.koreanName, selected = pick == b, onClick = { pick = b }, meta = "${b.months(state.today)}개월")
            }
        }
        KkPrimaryButton("선택 완료", { onPick(pick) })
    }
}

@Composable
private fun DateSheet(state: OnboardingState, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val c = KkTheme.colors
    var pick by remember { mutableStateOf(state.enlistDate ?: state.today) }
    Sheet(onDismiss) {
        KkSheetHeader("입대일 선택", "훈련소 입소일을 골라 주세요")
        com.sadturtleman.flowershoes.core.designsystem.KkDatePicker(pick, { pick = it })
        val t = remember(pick, state.branch) {
            com.sadturtleman.flowershoes.core.military.ServiceCalculator()
                .timeline(com.sadturtleman.flowershoes.core.model.Service("preview", state.branch, pick, ServiceRulesTable.BUNDLED.version))
        }
        Text(
            "${pick.month.number}월 ${pick.day}일 입대 → ${t.dischargeDate.year}년 ${t.dischargeDate.month.number}월 ${t.dischargeDate.day}일 전역 (${state.branch.koreanName} ${state.branch.months(pick)}개월)",
            style = KkTheme.type.body, color = c.accent,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.md)).background(c.bar).padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
        )
        KkPrimaryButton("선택 완료", { onPick(pick) })
    }
}

internal fun LocalDate.dotted() = "$year.${month.number.toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"
