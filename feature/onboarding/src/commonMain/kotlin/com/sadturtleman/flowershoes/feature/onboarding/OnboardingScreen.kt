@file:OptIn(ExperimentalComposeUiApi::class)

package com.sadturtleman.flowershoes.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
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
import com.sadturtleman.flowershoes.core.military.ServiceRulesTable
import com.sadturtleman.flowershoes.core.military.koreanName
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.ThemeAccent
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
 * 온보딩(Figma 1 · 온보딩). [connect] 는 연결 단계 본문 — 연결 기능(:feature:pairing)을 앱 셸이 끼운다.
 * 인자는 "확인 코드 비교로 넘어가자" 콜백.
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    onOpenRestore: () -> Unit,
    onOpenPairing: () -> Unit,
    connect: @Composable ColumnScope.(onAdvance: () -> Unit) -> Unit = {},
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
    BackHandler(enabled = state.reading != null || state.step.ordinal > OnboardingStep.TERMS.ordinal) {
        viewModel.onIntent(OnboardingIntent.Back)
    }
    OnboardingScreen(state, viewModel::onIntent, connect)
}

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    connect: @Composable ColumnScope.(onAdvance: () -> Unit) -> Unit = {},
) {
    val c = KkTheme.colors
    state.reading?.let { doc ->
        TermsScreen(doc, onClose = { onIntent(OnboardingIntent.Read(null)) })
        return
    }
    var sheet by remember { mutableStateOf<ServiceSheet?>(null) }
    Box(Modifier.fillMaxSize().background(c.bgCanvas)) {
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
                        KkStepIndicator(step = 4, total = 4)
                        Text("연인과 연결해요", style = KkTheme.type.display, color = c.textPrimary)
                        Text("초대 코드를 보내면 둘의 D-day와 상태가 함께 보여요.", style = KkTheme.type.bodyL, color = c.textSecondary)
                        connect { onIntent(OnboardingIntent.StartPairing) }
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
                        state.error?.let { Text(it, style = KkTheme.type.caption, color = c.statusDanger) }
                    }
                    OnboardingStep.TERMS -> KkPrimaryButton("동의하고 시작하기", { onIntent(OnboardingIntent.Next) }, enabled = state.canProceed)
                    OnboardingStep.CONNECT -> KkButton("혼자 먼저 둘러볼게요", { onIntent(OnboardingIntent.FinishSolo) }, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost)
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
    Text("꽃신", style = KkTheme.type.display, color = c.textBrand)
    Text("곰신과 군인, 둘만의 전역 달력", style = KkTheme.type.titleM, color = c.textPrimary, textAlign = TextAlign.Center)
    Text("편지 · 그림 · D-day를 함께 나눠요", style = KkTheme.type.bodyM, color = c.textSecondary, textAlign = TextAlign.Center)
}

// ── 온보딩 0-1 · 약관 동의 ──

@Composable
private fun TermsContent(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    val c = KkTheme.colors
    Text("꽃신을 시작하려면\n동의가 필요해요", style = KkTheme.type.titleL, color = c.textPrimary)
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier
            .fillMaxWidth()
            .kkElevation(KkElevation.Low, shape)
            .clip(shape)
            .background(c.bgSurface)
            .clickable(role = A11y.Checkbox) { onIntent(OnboardingIntent.ToggleAll) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KkCheckbox(state.allAgreed)
        Text("전체 동의", style = KkTheme.type.titleS, color = c.textPrimary, modifier = Modifier.weight(1f))
    }
    Column(Modifier.fillMaxWidth()) {
        Agreement.entries.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable(role = A11y.Checkbox) { onIntent(OnboardingIntent.ToggleAgreement(item)) }
                    .padding(start = 16.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KkCheckbox(item in state.agreed)
                Text(item.label, style = KkTheme.type.bodyM, color = c.textPrimary, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).then(if (item.doc != null) Modifier.clickable(role = A11y.Button) { onIntent(OnboardingIntent.Read(item.doc)) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.doc != null) KkIcon(KkIcons.ChevronRight, tint = c.textSecondary)
                }
            }
        }
    }
}

// ── 온보딩 0-2 · 약관 보기 ──

@Composable
private fun TermsScreen(doc: TermsDoc, onClose: () -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar(doc.title, onLeading = onClose, leading = KkTopBarLeading.Close, modifier = Modifier.padding(horizontal = KkSpacing.xl))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            doc.sections.forEach { (title, lines) ->
                val shape = RoundedCornerShape(KkRadius.lg)
                Column(
                    Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, style = KkTheme.type.labelM, color = c.textSecondary)
                    lines.forEach { Text(it, style = KkTheme.type.bodyS, color = c.textPrimary) }
                }
            }
        }
        KkPrimaryButton("확인", onClose, Modifier.padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xxl, bottom = 14.dp))
    }
}

// ── 온보딩 1 · 역할 ──

@Composable
private fun RoleContent(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    val c = KkTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        KkLogoMark(size = 44.dp)
        Column {
            Text("꽃신", style = KkTheme.type.titleL, color = c.textBrand)
            Text("곰신이 꽃신 되는 날까지", style = KkTheme.type.caption, color = c.textSecondary)
        }
    }
    KkStepIndicator(step = 1, total = 4)
    Text("어느 쪽이신가요?", style = KkTheme.type.display, color = c.textPrimary)
    Text("역할에 맞춰 화면을 준비할게요.", style = KkTheme.type.bodyL, color = c.textSecondary)
    RoleCard("곰신이에요", "군대 간 연인을 기다리고 있어요", KkIcons.Heart, c.bgSurface, c.heart, state.role == Role.GOMSIN) {
        onIntent(OnboardingIntent.SelectRole(Role.GOMSIN))
    }
    RoleCard("군인이에요", "지금 복무 중이에요", KkIcons.Clock, c.bgMilitarySubtle, c.textMilitary, state.role == Role.SOLDIER) {
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
            .background(if (selected) c.bgBrandSubtle else c.bgSurface)
            .then(if (selected) Modifier.border(2.dp, c.borderBrand, shape) else Modifier)
            .clickable(role = A11y.RadioButton, onClick = onClick)
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.xxl),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
            KkIcon(icon, tint = iconTint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs)) {
            Text(title, style = KkTheme.type.titleM, color = c.textPrimary)
            Text(body, style = KkTheme.type.bodyS, color = c.textSecondary)
        }
    }
}

// ── 온보딩 2 · 테마 ──

@Composable
private fun ThemeContent(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    val c = KkTheme.colors
    KkStepIndicator(step = 2, total = 4)
    Text("앱 색을 골라 주세요", style = KkTheme.type.titleL, color = c.textPrimary)
    Text("내 폰에만 적용돼요. 연인의 화면은 그대로예요.", style = KkTheme.type.bodyM, color = c.textSecondary)
    // 미리보기: 지금 고른 테마로 그린 실제 컴포넌트(칩 · 분류 탭 · 버튼).
    val shape = RoundedCornerShape(KkRadius.xl)
    Column(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("미리보기", style = KkTheme.type.caption, color = c.textTertiary)
            val days = state.preview?.daysLeft(state.today)
            Text(
                if (days != null) "전역까지 D-$days" else "전역까지 D-547",
                style = KkTheme.type.labelS, color = c.textBrand,
                modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(c.bgBrandSubtle).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        var seg by remember { mutableStateOf(0) }
        KkSegmentedControl(listOf("전체", "편지", "그림"), seg, { seg = it })
        KkPrimaryButton("편지 쓰기", onClick = {})
    }
    val recommended = state.role?.let { ThemeAccent.defaultFor(it) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ThemeAccent.entries.chunked(2).forEach { row ->
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
internal fun ThemeCard(theme: ThemeAccent, selected: Boolean, recommended: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        modifier
            .then(if (selected) Modifier else Modifier.kkElevation(KkElevation.Low, shape))
            .clip(shape)
            .background(if (selected) c.bgBrandSubtle else c.bgSurface)
            .then(if (selected) Modifier.border(2.dp, c.borderBrand, shape) else Modifier)
            .clickable(role = A11y.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ThemeSwatch(theme)
            KkRadio(selected)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(theme.koreanName, style = KkTheme.type.titleS, color = c.textPrimary)
            if (recommended) {
                Text(
                    "추천", style = KkTheme.type.caption, color = c.textBrand,
                    modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(c.bgSurface).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(theme.mood, style = KkTheme.type.caption, color = c.textSecondary)
    }
}

/** 테마 견본: 배경색 원 + 강조색 원(27, 1px 테두리), 48×28. */
@Composable
internal fun ThemeSwatch(theme: ThemeAccent) {
    val light = kkColors(theme, dark = false)
    val ring = KkTheme.colors.borderDefault
    Canvas(Modifier.size(48.dp, 28.dp)) {
        val r = 13.5.dp.toPx()
        listOf(light.bgCanvas to Offset(14.dp.toPx(), 14.dp.toPx()), light.bgBrand to Offset(34.dp.toPx(), 14.dp.toPx())).forEach { (fill, center) ->
            drawCircle(fill, r, center)
            drawCircle(ring, r, center, style = Stroke(1.dp.toPx()))
        }
    }
}

// ── 온보딩 2 · 입대일 (+ 군종 · 입대일 시트) ──

private enum class ServiceSheet { BRANCH, DATE }

private fun Branch.months(today: LocalDate) = ServiceRulesTable.BUNDLED.ruleFor(this, today).serviceMonths

@Composable
private fun ServiceContent(state: OnboardingState, openSheet: (ServiceSheet) -> Unit) {
    val c = KkTheme.colors
    val mine = state.role == Role.SOLDIER
    KkStepIndicator(step = 3, total = 4)
    Text(if (mine) "언제 입대했나요?" else "연인은 언제 입대했나요?", style = KkTheme.type.display, color = c.textPrimary)
    Text("입대일만 알려 주면 전역일을 바로 계산해요.", style = KkTheme.type.bodyL, color = c.textSecondary)
    KkInput(
        value = "${state.branch.koreanName} · ${state.branch.months(state.today)}개월",
        onValueChange = {},
        label = "군종",
        helper = Branch.entries.filter { it != state.branch && it != Branch.SOCIAL_SERVICE }
            .joinToString(" · ") { "${it.koreanName.removeSuffix("대")} ${it.months(state.today)}개월" },
        onClick = { openSheet(ServiceSheet.BRANCH) },
    )
    KkInput(
        value = state.enlistDate?.dotted() ?: "",
        onValueChange = {},
        label = "입대일",
        placeholder = "입대일을 골라 주세요",
        helper = "훈련소 입소일 기준",
        onClick = { openSheet(ServiceSheet.DATE) },
    )
    Text("미리보기", style = KkTheme.type.labelM, color = c.textTertiary)
    val t = state.preview
    KkDdayCard(
        days = t?.let { "D-${it.daysLeft(state.today)}" } ?: "D-?",
        rank = "${t?.rankAt(state.today)?.rank?.koreanName ?: "이병"} · ${state.branch.koreanName}",
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
            .background(KkTheme.colors.bgInverse.copy(alpha = 0.45f))
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
            style = KkTheme.type.bodyS, color = c.textMilitary,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.md)).background(c.bgMilitarySubtle).padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
        )
        KkPrimaryButton("선택 완료", { onPick(pick) })
    }
}

internal fun LocalDate.dotted() = "$year.${month.number.toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"
