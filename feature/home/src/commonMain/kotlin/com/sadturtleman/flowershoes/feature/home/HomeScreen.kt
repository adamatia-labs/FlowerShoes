package com.sadturtleman.flowershoes.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role as A11y
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.designsystem.KkBanner
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonSize
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkEnvelopeMark
import com.sadturtleman.flowershoes.core.designsystem.KkEnvelopeRow
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkMissYouFab
import com.sadturtleman.flowershoes.core.designsystem.KkPaperTag
import com.sadturtleman.flowershoes.core.designsystem.KkPaperTagStyle
import com.sadturtleman.flowershoes.core.designsystem.KkPostmark
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkServiceProgress
import com.sadturtleman.flowershoes.core.designsystem.KkServiceProgressStyle
import com.sadturtleman.flowershoes.core.designsystem.KkSkeleton
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.Role
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * 홈 v2(Figma 2 · 홈 350:686). 큰 손글씨 D-day · 복무 진행 · 종이 태그 두 장 · 오늘 온 편지 · 보고싶어 FAB.
 * 연결 전은 같은 머리 아래 초대(혼자 모드 350:834 · 350:892), 입대일 전은 안내 띠 + 초대 버튼(350:972), 전역 뒤는 보관 모드(352:1258).
 * 설정 탭이 없어 설정 톱니는 머리말 오른쪽에 둔다(#66 결정). 광고 없음.
 * [onOpenPairing] 초대 링크 보내기 · 다시 보내기, [onWriteLetter] 편지 쓰기, [onOpenLetter] 편지 열기(id),
 * [onOpenDayLine] 그날의 한 줄 쓰기(일정 id), [onOpenSettings] 설정(입대일 입력 포함).
 */
@Composable
fun HomeRoute(
    onOpenPairing: () -> Unit,
    onOpenSettings: () -> Unit,
    onWriteLetter: () -> Unit = {},
    onOpenLetter: (String) -> Unit = {},
    onOpenDayLine: (String) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, viewModel::onIntent, onOpenPairing, onOpenSettings, onWriteLetter, onOpenLetter, onOpenDayLine)
}

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onOpenPairing: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onWriteLetter: () -> Unit = {},
    onOpenLetter: (String) -> Unit = {},
    onOpenDayLine: (String) -> Unit = {},
) {
    val c = KkTheme.colors
    val fab = state.linked && !state.loading
    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = KkSpacing.xxl, end = KkSpacing.xxl, top = KkSpacing.md, bottom = if (fab) 88.dp else KkSpacing.xxl),
        ) {
            Header(state, onOpenSettings)
            if (state.loading) {
                Loading()
                return@Column
            }
            // 초대를 이미 보냈으면 띠의 "초대"는 빼고 아래 상태 줄의 "링크 다시 보내기"만 남긴다.
            if (state.enlistNudge) KkBanner(
                "입대 전에 같이 연결해 두세요", modifier = Modifier.padding(top = KkSpacing.md),
                action = "초대".takeUnless { state.invitePending }, onAction = onOpenPairing,
            )
            Hero(state.hero, onOpenSettings)
            Tags(state.tags)
            state.dayLine?.let { Spacer(Modifier.height(KkSpacing.lg)); DayLineCard(it, onOpenDayLine) { id -> onIntent(HomeIntent.DismissDayLine(id)) } }
            when {
                state.linked -> Letters(state, onWriteLetter, onOpenLetter)
                state.hero?.phase == HomePhase.BEFORE_ENLIST -> {
                    Spacer(Modifier.height(KkSpacing.xxl))
                    if (state.invitePending) InviteStatus(onOpenPairing) else KkPrimaryButton("${state.partnerName ?: "연인"} 초대하기", onOpenPairing)
                    if (state.role == Role.GOMSIN) PendingLetters(state, onOpenLetter)
                }
                state.role == Role.SOLDIER -> SoldierSolo(state, onOpenPairing)
                else -> GomsinSolo(state, onOpenPairing, onOpenLetter)
            }
        }
        if (fab) MissYouFab(onIntent, Modifier.align(Alignment.BottomEnd).padding(end = KkSpacing.xxl, bottom = KkSpacing.lg))
    }
}

// ── 머리말: "10월 28일 수요일" / "지수 & 민준" + 말린 꽃(곰신) · 군사우편 소인(군인) + 설정 ──

@Composable
private fun Header(state: HomeState, onOpenSettings: () -> Unit) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
            if (state.loading) {
                KkSkeleton(Modifier.width(80.dp))
                KkSkeleton(Modifier.width(120.dp).height(20.dp))
            } else {
                Text(state.dateLine, style = KkTheme.type.caption, color = c.sub, maxLines = 1)
                Text(state.title, style = KkTheme.type.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // 장식(보여 주기만 하는 물건): 곰신 테마는 말린 꽃 가지, 군인 테마는 오늘 날짜 군사우편 소인.
        if (KkTheme.appTheme == AppTheme.SOLDIER) KkPostmark("군사우편", state.stampDate, Modifier.rotate(12f))
        else KkIcon(KkIcons.Sprig, tint = c.plant, size = 60.dp)
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = A11y.Button, onClick = onOpenSettings).semantics { contentDescription = "설정" },
            contentAlignment = Alignment.Center,
        ) { KkIcon(KkIcons.Settings, tint = c.sub) }
    }
}

// ── 큰 D-day: 곰신 테마는 손글씨 라벨 + 점선 길, 군인 테마는 스텐실 라벨 + 개월 칸 ──

@Composable
private fun Hero(hero: HomeHero?, onOpenSettings: () -> Unit) {
    val c = KkTheme.colors
    val soldier = KkTheme.appTheme == AppTheme.SOLDIER
    Spacer(Modifier.height(KkSpacing.xxxl))
    // 입대일이 없으면(곰신이 "나중에 넣을게요") 자리표시 D-day 대신 입대일 넣기만 둔다(설정 › 군 정보).
    if (hero == null) {
        TextAction("입대일 넣기", onOpenSettings)
        return
    }
    Text(
        hero.caption,
        style = if (soldier) KkTheme.type.title else KkTheme.type.handL,
        color = if (soldier) c.accent else c.sub,
        maxLines = 1,
        modifier = Modifier.padding(start = KkSpacing.xxs),
    )
    // D-day/L 은 글자 앞 여백이 있어 Figma 처럼 4 왼쪽으로 당긴다. 글자를 키워 폭을 넘으면 한 줄에 들어가게 줄인다.
    BasicText(
        hero.value, style = KkTheme.type.ddayL.copy(color = c.ink), maxLines = 1, softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = KkTheme.type.ddayS.fontSize, maxFontSize = KkTheme.type.ddayL.fontSize),
        modifier = Modifier.offset(x = (-4).dp),
    )
    when {
        hero.phase == HomePhase.SERVING || hero.phase == HomePhase.DISCHARGE_DAY -> KkServiceProgress(
            progress = hero.progress, start = hero.start, end = hero.end, percent = hero.percent,
            modifier = Modifier.padding(top = KkSpacing.sm),
            style = if (soldier) KkServiceProgressStyle.Months else KkServiceProgressStyle.Path,
            months = hero.months,
        )
        else -> hero.detail?.let { Text(it, style = KkTheme.type.caption, color = c.sub, modifier = Modifier.padding(top = KkSpacing.sm)) }
    }
}

/** 종이 태그 두 장(보여 주기만 하는 물건이라 기울인다: +3° · -2.5°, Figma). */
@Composable
private fun Tags(tags: List<HomeTag>) {
    if (tags.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        tags.forEachIndexed { i, tag ->
            KkPaperTag(
                tag.label, tag.value,
                modifier = if (i == 0) Modifier else Modifier.offset(y = 2.dp),
                valueStyle = if (tag.hand) KkPaperTagStyle.Hand else KkPaperTagStyle.Number,
                tilt = if (i == 0) 3f else -2.5f,
            )
        }
    }
}

// ── 연결 뒤: 오늘 온 편지(보관 모드는 최근 편지) ──

@Composable
private fun ColumnScope.Letters(state: HomeState, onWriteLetter: () -> Unit, onOpenLetter: (String) -> Unit) {
    val c = KkTheme.colors
    Box(Modifier.padding(top = KkSpacing.xxxl).fillMaxWidth().height(1.dp).background(c.line))
    Text(
        if (state.hero?.phase == HomePhase.ARCHIVE) "최근 편지" else "오늘 온 편지",
        style = KkTheme.type.labelBold, color = c.ink,
        modifier = Modifier.padding(top = KkSpacing.md).heightIn(min = 24.dp),
    )
    val letter = state.letter
    if (letter == null) {
        Text(if (state.hero?.phase == HomePhase.ARCHIVE) "받은 편지가 없어요" else "오늘 온 편지가 없어요", style = KkTheme.type.caption, color = c.sub, modifier = Modifier.padding(top = KkSpacing.md))
        TextAction("편지 쓰기", onWriteLetter)
        return
    }
    Row(
        Modifier.padding(top = KkSpacing.xs).fillMaxWidth().clip(RoundedCornerShape(KkRadius.sm)).clickable(role = A11y.Button) { onOpenLetter(letter.id) }
            .padding(vertical = KkSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        KkIcon(KkIcons.Mail, tint = c.accent, modifier = Modifier.padding(top = KkSpacing.xs))
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text(letter.line, style = KkTheme.type.handLetter, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(letter.meta, style = KkTheme.type.caption, color = c.sub, maxLines = 1)
        }
    }
}

/** 보고싶어 FAB. 누르면 잠깐 "보냈어요"로 바뀌었다가 돌아온다(Figma 홈 · 보고싶어 보냄). 읽어 주는 말은 버튼 글자 그대로. */
@Composable
private fun MissYouFab(onIntent: (HomeIntent) -> Unit, modifier: Modifier) {
    var sent by remember { mutableStateOf(false) }
    LaunchedEffect(sent) {
        if (sent) {
            delay(SENT_MS)
            sent = false
        }
    }
    KkMissYouFab(sent, onClick = {
        if (!sent) onIntent(HomeIntent.SendMissYou)
        sent = true
    }, modifier)
}

private const val SENT_MS = 2_000L

// ── 혼자 모드 · 곰신(350:834): 초대 카드 + 연결되면 보낼 편지 ──

@Composable
private fun GomsinSolo(state: HomeState, onOpenPairing: () -> Unit, onOpenLetter: (String) -> Unit) {
    val c = KkTheme.colors
    Spacer(Modifier.height(KkSpacing.xxl))
    if (state.invitePending) InviteStatus(onOpenPairing)
    else {
        val shape = RoundedCornerShape(KkRadius.lg)
        Column(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).padding(KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
        ) {
            Text("연인을 초대해 보세요", style = KkTheme.type.labelBold, color = c.ink)
            KkButton("초대 링크 보내기", onOpenPairing, size = KkButtonSize.M)
        }
    }
    PendingLetters(state, onOpenLetter)
}

/**
 * "연결되면 보낼 편지 N통" + 최근 두 통. Figma 는 봉투를 기울여 겹쳤지만, 누르면 열리는 봉투라 design.md 대로 반듯하게 세로로 둔다.
 */
@Composable
private fun PendingLetters(state: HomeState, onOpenLetter: (String) -> Unit) {
    val letters = state.pendingLetters
    if (letters.isEmpty()) return
    val c = KkTheme.colors
    Text("연결되면 보낼 편지 ${letters.size}통", style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.padding(top = KkSpacing.xxxl))
    val stamp = state.hero?.value.orEmpty()
    Column(Modifier.padding(top = KkSpacing.lg), verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
        letters.take(2).forEach { l -> KkEnvelopeRow(l.line, l.meta, KkEnvelopeMark.Stamp(stamp), { onOpenLetter(l.id) }) }
    }
}

// ── 혼자 모드 · 군인(350:892): 진급 일정 + 작은 초대 카드 ──

@Composable
private fun SoldierSolo(state: HomeState, onOpenPairing: () -> Unit) {
    val c = KkTheme.colors
    if (state.ranks.isNotEmpty()) {
        Text("진급 일정", style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.padding(top = KkSpacing.md, bottom = KkSpacing.xs))
        state.ranks.forEach { row ->
            // List Row(Figma): 높이 56, 아래 선. 진급일은 "예정"이라 누르는 동작은 없다.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.name, style = KkTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                Text(row.detail, style = KkTheme.type.label, color = c.sub, maxLines = 1)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        }
    }
    Spacer(Modifier.height(KkSpacing.md))
    if (state.invitePending) InviteStatus(onOpenPairing)
    else {
        val shape = RoundedCornerShape(KkRadius.lg)
        Row(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface)
                .padding(start = KkSpacing.lg, end = KkSpacing.md, top = KkSpacing.md, bottom = KkSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(KkSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("연인 초대하기", style = KkTheme.type.caption, color = c.ink, modifier = Modifier.weight(1f))
            KkButton("초대", onOpenPairing, size = KkButtonSize.M)
        }
    }
}

/** 초대를 보낸 뒤: "초대 보냄 · 기다리는 중 · 링크 다시 보내기"(409:8664). */
@Composable
private fun InviteStatus(onOpenPairing: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(start = 12.dp, end = 4.dp, top = KkSpacing.sm, bottom = KkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(KkIcons.Send, tint = c.sub, size = 16.dp)
        Text("초대 보냄 · 기다리는 중", style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
        TextAction("링크 다시 보내기", onOpenPairing)
    }
}

/** 그날의 한 줄 카드(Figma 홈 · 그날의 한 줄 카드, 반듯하게): 문구 + [남기기] + 닫기. */
@Composable
private fun DayLineCard(prompt: DayLinePrompt, onWrite: (String) -> Unit, onDismiss: (String) -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape)
            .padding(start = KkSpacing.lg, end = KkSpacing.xxs, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prompt.text, style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
        KkButton("남기기", { onWrite(prompt.eventId) }, size = KkButtonSize.M)
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = A11y.Button) { onDismiss(prompt.eventId) }.semantics { contentDescription = "닫기" },
            contentAlignment = Alignment.Center,
        ) { KkIcon(KkIcons.Close, tint = c.sub, size = 13.8.dp) }
    }
}

/** 글자 버튼(accent Label/Bold, 터치 높이 48). */
@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text, style = KkTheme.type.labelBold, color = KkTheme.colors.accent, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).clickable(role = A11y.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = KkSpacing.lg, vertical = 15.dp),
    )
}

/** 홈 · 로딩(351:1230): 큰 D-day · 태그 · 편지 자리를 스켈레톤으로. */
@Composable
private fun Loading() {
    Spacer(Modifier.height(KkSpacing.xxxl))
    KkSkeleton(Modifier.width(116.dp).height(24.dp))
    KkSkeleton(Modifier.padding(top = KkSpacing.lg).width(240.dp).height(96.dp))
    KkSkeleton(Modifier.padding(top = KkSpacing.lg).fillMaxWidth())
    Row(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        KkSkeleton(Modifier.width(140.dp).height(66.dp))
        KkSkeleton(Modifier.width(140.dp).height(66.dp))
    }
    Row(Modifier.padding(top = 80.dp), horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
        KkSkeleton(Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            KkSkeleton(Modifier.width(240.dp).height(16.dp))
            KkSkeleton(Modifier.width(96.dp).height(10.dp))
        }
    }
}
