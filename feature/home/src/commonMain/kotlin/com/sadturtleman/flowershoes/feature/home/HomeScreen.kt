package com.sadturtleman.flowershoes.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role as A11y
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.designsystem.KkBanner
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonSize
import com.sadturtleman.flowershoes.core.designsystem.KkDdayCard
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkHeartIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkMissYouButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.model.Role
import org.koin.compose.viewmodel.koinViewModel

/**
 * 홈(Figma 2 · 홈). 연결 뒤(52:86) · 연결 전 곰신(70:629) · 연결 전 군인(137:1428) 세 모양.
 * 연결 전이어도 D-day 는 혼자 계산해 보여 주고, 입대일 전이면 연결을 강하게 권한다(홈 · 입대 전 350:972).
 * [onOpenPairing] 초대 링크 보내기 · 다시 보내기, [onWriteLetter] 편지 미리 쓰기, [onOpenCalendar] 일정,
 * [onOpenDayLine] 그날의 한 줄 쓰기(일정 id).
 */
@Composable
fun HomeRoute(
    onOpenPairing: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onWriteLetter: () -> Unit = {},
    onOpenLeave: () -> Unit = onOpenCalendar,
    onOpenDayLine: (String) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, viewModel::onIntent, onOpenPairing, onOpenCalendar, onOpenSettings, onWriteLetter, onOpenLeave, onOpenDayLine)
}

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onOpenPairing: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onWriteLetter: () -> Unit = {},
    onOpenLeave: () -> Unit = onOpenCalendar,
    onOpenDayLine: (String) -> Unit = {},
) {
    val c = KkTheme.colors
    val dayLine: @Composable () -> Unit = { state.dayLine?.let { DayLineCard(it, onOpenDayLine) { id -> onIntent(HomeIntent.DismissDayLine(id)) } } }
    Column(
        Modifier
            .fillMaxSize()
            .background(c.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(if (state.linked) KkSpacing.lg else 12.dp),
    ) {
        Header(state, onOpenSettings)
        when {
            state.loading -> Unit
            state.linked -> LinkedHome(state, onIntent, onOpenCalendar, dayLine)
            state.role == Role.SOLDIER -> {
                SoldierUnlinked(state, onOpenPairing, onOpenCalendar, onOpenLeave, onOpenSettings)
                dayLine()
            }
            else -> {
                dayLine()
                GomsinUnlinked(state, onOpenPairing, onWriteLetter, onOpenCalendar, onOpenSettings)
            }
        }
    }
}

// ── Header: "민준 ♥ 지수 · D+412 ⚙" / 연결 전 "홈 ⚙" ──

@Composable
private fun Header(state: HomeState, onOpenSettings: () -> Unit) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val couple = state.couple
        if (couple != null) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(couple.first, style = KkTheme.type.title, color = c.ink, maxLines = 1)
                KkHeartIcon(color = c.stamp, size = 26.dp)
                Text(couple.second, style = KkTheme.type.title, color = c.ink, maxLines = 1)
            }
        } else {
            Text("홈", style = KkTheme.type.title, color = c.ink, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = A11y.Button, onClick = onOpenSettings).semantics { contentDescription = "설정" },
                contentAlignment = Alignment.Center,
            ) { KkIcon(KkIcons.Settings, tint = c.ink) }
        }
    }
}

/** 입대일이 없으면(곰신이 "나중에 넣을게요") 누르면 설정으로 — 군 정보에서 넣는다. */
@Composable
private fun Dday(state: HomeState, onOpenCalendar: () -> Unit, onOpenSettings: () -> Unit = onOpenCalendar) {
    val d = state.dday
    if (d != null) {
        KkDdayCard(d.days, d.rank, d.percent, d.date, d.progress, caption = d.caption, onClick = onOpenCalendar)
    } else {
        KkDdayCard("D-?", "입대일 입력 전", "복무 0%", "설정에서 입대일을 넣어 주세요", 0f, onClick = onOpenSettings)
    }
}

/** 입대일 전 안내 띠(홈 · 입대 전 350:972). D-day 위에 놓는다. */
@Composable
private fun EnlistBanner(state: HomeState, onOpenPairing: () -> Unit) {
    if (state.enlistNudge) KkBanner("입대 전에 같이 연결해 두세요", "입대하면 연락이 어려워져요", action = "초대", onAction = onOpenPairing)
}

/**
 * 연결 권유. 입대일 전이면 큰 초대 버튼(350:972), 아니면 평소 카드. 초대를 이미 보냈으면 상태 줄([Invite]).
 */
@Composable
private fun Connect(state: HomeState, onOpenPairing: () -> Unit) {
    if (state.enlistNudge && !state.invitePending) KkPrimaryButton("연인 초대하기", onOpenPairing, Modifier.fillMaxWidth())
    else Invite(state.invitePending, onOpenPairing)
}

// ── 연결 뒤(52:86) ──

@Composable
private fun LinkedHome(state: HomeState, onIntent: (HomeIntent) -> Unit, onOpenCalendar: () -> Unit, dayLine: @Composable () -> Unit) {
    val c = KkTheme.colors
    Dday(state, onOpenCalendar)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
        state.partner?.let { StatusTile(it, Modifier.weight(1f)) }
        val shape = RoundedCornerShape(KkRadius.xl)
        Box(
            Modifier.weight(1f).fillMaxHeight().heightIn(min = 170.dp).kkElevation(KkElevation.Mid, shape).clip(shape).background(c.surface).padding(KkSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            KkMissYouButton(state.sentMissYouToday, onClick = { onIntent(HomeIntent.SendMissYou) })
        }
    }
    dayLine()
    if (state.scheduled.isNotEmpty()) {
        Text("오늘 도착할 메시지", style = KkTheme.type.labelBold, color = c.ink)
        state.scheduled.forEach { ScheduledMessage(it) }
    }
}

/** Widget/Status: 상대 이름 첫 글자 원 · 이름 · 몇 분 전 / 상태 점 + 상태 / ♥ 보고싶어 N. */
@Composable
private fun StatusTile(p: PartnerStatus, modifier: Modifier) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.xl)
    Column(
        modifier.fillMaxHeight().heightIn(min = 170.dp).kkElevation(KkElevation.Mid, shape).clip(shape).background(c.surface).padding(KkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.bar), contentAlignment = Alignment.Center) {
                Text(p.initial, style = KkTheme.type.label, color = c.accent)
            }
            Column {
                Text(p.name, style = KkTheme.type.label, color = c.ink, maxLines = 1)
                Text(p.ago ?: "아직 소식 없음", style = KkTheme.type.caption, color = c.sub)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(p.dot?.let { Color(it.toInt()) } ?: c.line))
            Text(p.label ?: "상태 없음", style = KkTheme.type.title, color = if (p.label != null) c.ink else c.sub, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            KkHeartIcon(color = c.stamp, size = 13.dp)
            Text("보고싶어 ${p.missYou}", style = KkTheme.type.label, color = c.accent)
        }
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
            Modifier.size(44.dp).clip(CircleShape).clickable(role = A11y.Button) { onDismiss(prompt.eventId) }.semantics { contentDescription = "닫기" },
            contentAlignment = Alignment.Center,
        ) { KkIcon(KkIcons.Close, tint = c.sub, size = 13.8.dp) }
    }
}

/** Scheduled Message(예약됨). */
@Composable
private fun ScheduledMessage(row: ScheduledRow) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface)
            .padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(KkIcons.Clock, tint = c.accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs)) {
            Text(row.time, style = KkTheme.type.label, color = c.accent)
            Text(row.message, style = KkTheme.type.body, color = c.ink, maxLines = 2)
        }
        Text(
            "예약됨", style = KkTheme.type.label, color = c.accent,
            modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(c.bar).padding(horizontal = KkSpacing.sm, vertical = KkSpacing.xxs),
        )
    }
}

// ── 연결 전 · 곰신(70:629) ──

@Composable
private fun GomsinUnlinked(state: HomeState, onOpenPairing: () -> Unit, onWriteLetter: () -> Unit, onOpenCalendar: () -> Unit, onOpenSettings: () -> Unit) {
    val c = KkTheme.colors
    EnlistBanner(state, onOpenPairing)
    Dday(state, onOpenCalendar, onOpenSettings)
    Connect(state, onOpenPairing)
    Text("연결 전에 할 수 있어요", style = KkTheme.type.title, color = c.ink)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CanDoTile(KkIcons.Mail, "편지 미리 쓰기", "연결되면 한 번에 도착해요", Modifier.weight(1f), onWriteLetter)
        CanDoTile(KkIcons.Calendar, "일정 적어 두기", "수료식 · 첫 면회 · 휴가", Modifier.weight(1f), onOpenCalendar)
    }
    Text("연결하면 함께 보여요", style = KkTheme.type.title, color = c.ink)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple(KkIcons.Home, "상대 상태", "?"), Triple(KkIcons.Heart, "보고싶어", "?")).forEach { (icon, label, value) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(KkRadius.lg)).background(c.line).padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KkIcon(icon, tint = c.sub)
                Text(label, style = KkTheme.type.body, color = c.sub)
                Text(value, style = KkTheme.type.labelBold, color = c.sub)
            }
        }
    }
}

/** 초대를 보냈으면 "초대 보냄 · 기다리는 중 · 링크 다시 보내기"(409:8664), 아니면 연결 권유 카드. */
@Composable
private fun Invite(pending: Boolean, onOpenPairing: () -> Unit) {
    if (!pending) {
        InviteCard { KkButton("초대 링크 보내기", onOpenPairing, Modifier.fillMaxWidth(), size = KkButtonSize.M) }
        return
    }
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(start = 12.dp, end = 4.dp, top = KkSpacing.sm, bottom = KkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(KkIcons.Send, tint = c.sub, size = 16.dp)
        Text("초대 보냄 · 기다리는 중", style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
        Text(
            "링크 다시 보내기",
            style = KkTheme.type.labelBold,
            color = c.accent,
            modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).clickable(role = A11y.Button, onClick = onOpenPairing)
                .heightIn(min = 48.dp).padding(horizontal = KkSpacing.lg, vertical = 12.dp),
        )
    }
}

@Composable
private fun InviteCard(buttons: @Composable () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.bar), contentAlignment = Alignment.Center) {
            KkIcon(KkIcons.Heart, tint = c.ink)
        }
        Text("연인과 연결할까요?", style = KkTheme.type.title, color = c.ink, textAlign = TextAlign.Center)
        buttons()
    }
}

@Composable
private fun CanDoTile(icon: KkIcons, title: String, body: String, modifier: Modifier, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        modifier.kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).clickable(role = A11y.Button, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KkIcon(icon, tint = c.ink)
        Text(title, style = KkTheme.type.labelBold, color = c.ink)
        Text(body, style = KkTheme.type.caption, color = c.sub)
    }
}

// ── 연결 전 · 군인(137:1428) ──

@Composable
private fun SoldierUnlinked(state: HomeState, onOpenPairing: () -> Unit, onOpenCalendar: () -> Unit, onOpenLeave: () -> Unit, onOpenSettings: () -> Unit) {
    val c = KkTheme.colors
    EnlistBanner(state, onOpenPairing)
    Dday(state, onOpenCalendar, onOpenSettings)
    state.soldier?.let { s ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("다음 진급", s.nextRank ?: "—", Modifier.weight(1f))
            if (s.leaveLeft != null) StatTile("남은 휴가", "${s.leaveLeft}일", Modifier.weight(1f), onClick = onOpenLeave)
            StatTile("복무", "${s.servedDay}일째", Modifier.weight(1f))
        }
    }
    Connect(state, onOpenPairing)
    state.nextLeave?.let { leave ->
        val shape = RoundedCornerShape(KkRadius.lg)
        Row(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface)
                .clickable(role = A11y.Button, onClick = onOpenCalendar).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KkIcon(KkIcons.Calendar, tint = c.ink)
            Column {
                Text(leave.title, style = KkTheme.type.labelBold, color = c.ink)
                Text(leave.detail, style = KkTheme.type.caption, color = c.sub)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        modifier.kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface)
            .then(if (onClick != null) Modifier.clickable(role = A11y.Button, onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = KkTheme.type.caption, color = c.sub)
        Text(value, style = KkTheme.type.labelBold, color = c.ink, maxLines = 1)
    }
}

