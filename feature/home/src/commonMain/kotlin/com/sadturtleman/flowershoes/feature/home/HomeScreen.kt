package com.sadturtleman.flowershoes.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonSize
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
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
 * [onOpenPairing] 연결(초대 코드), [onEnterCode] 받은 코드 입력, [onWriteLetter] 편지 미리 쓰기, [onOpenCalendar] 일정.
 */
@Composable
fun HomeRoute(
    onOpenPairing: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onWriteLetter: () -> Unit = {},
    onEnterCode: () -> Unit = onOpenPairing,
    onOpenLeave: () -> Unit = onOpenCalendar,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, viewModel::onIntent, onOpenPairing, onOpenCalendar, onOpenSettings, onWriteLetter, onEnterCode, onOpenLeave)
}

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onOpenPairing: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onWriteLetter: () -> Unit = {},
    onEnterCode: () -> Unit = onOpenPairing,
    onOpenLeave: () -> Unit = onOpenCalendar,
) {
    val c = KkTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(if (state.linked) KkSpacing.lg else 12.dp),
    ) {
        Header(state, onOpenSettings)
        when {
            state.loading -> Unit
            state.linked -> LinkedHome(state, onIntent, onOpenCalendar)
            state.role == Role.SOLDIER -> SoldierUnlinked(state, onOpenPairing, onEnterCode, onOpenCalendar, onOpenLeave)
            else -> GomsinUnlinked(onOpenPairing, onWriteLetter, onOpenCalendar)
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
                Text(couple.first, style = KkTheme.type.titleL, color = c.textPrimary, maxLines = 1)
                KkHeartIcon(color = c.heart, size = 26.dp)
                Text(couple.second, style = KkTheme.type.titleL, color = c.textPrimary, maxLines = 1)
            }
        } else {
            Text("홈", style = KkTheme.type.titleL, color = c.textPrimary, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (couple != null) state.daysTogether?.let { Text("D+$it", style = KkTheme.type.labelM, color = c.textBrand) }
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = A11y.Button, onClick = onOpenSettings).semantics { contentDescription = "설정" },
                contentAlignment = Alignment.Center,
            ) { KkIcon(KkIcons.Settings, tint = c.textPrimary) }
        }
    }
}

@Composable
private fun Dday(state: HomeState, onOpenCalendar: () -> Unit) {
    val d = state.dday
    if (d != null) {
        KkDdayCard(d.days, d.rank, d.percent, d.date, d.progress, onClick = onOpenCalendar)
    } else {
        KkDdayCard("D-?", "입대일 입력 전", "복무 0%", "설정에서 입대일을 넣어 주세요", 0f, onClick = onOpenCalendar)
    }
}

// ── 연결 뒤(52:86) ──

@Composable
private fun LinkedHome(state: HomeState, onIntent: (HomeIntent) -> Unit, onOpenCalendar: () -> Unit) {
    val c = KkTheme.colors
    Dday(state, onOpenCalendar)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
        state.partner?.let { StatusTile(it, Modifier.weight(1f)) }
        val shape = RoundedCornerShape(KkRadius.xl)
        Box(
            Modifier.weight(1f).height(170.dp).kkElevation(KkElevation.Mid, shape).clip(shape).background(c.bgSurface).padding(KkSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            KkMissYouButton(state.sentMissYouToday, onClick = { onIntent(HomeIntent.SendMissYou) })
        }
    }
    if (state.scheduled.isNotEmpty()) {
        Text("오늘 도착할 메시지", style = KkTheme.type.titleS, color = c.textPrimary)
        state.scheduled.forEach { ScheduledMessage(it) }
    }
}

/** Widget/Status: 상대 이름 첫 글자 원 · 이름 · 몇 분 전 / 상태 점 + 상태 / ♥ 보고싶어 N. */
@Composable
private fun StatusTile(p: PartnerStatus, modifier: Modifier) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.xl)
    Column(
        modifier.height(170.dp).kkElevation(KkElevation.Mid, shape).clip(shape).background(c.bgSurface).padding(KkSpacing.lg),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.bgMilitarySubtle), contentAlignment = Alignment.Center) {
                Text(p.initial, style = KkTheme.type.labelM, color = c.textMilitary)
            }
            Column {
                Text(p.name, style = KkTheme.type.labelM, color = c.textPrimary, maxLines = 1)
                Text(p.ago ?: "아직 소식 없음", style = KkTheme.type.caption, color = c.textTertiary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(p.dot?.let { Color(it.toInt()) } ?: c.borderStrong))
            Text(p.label ?: "상태 없음", style = KkTheme.type.titleM, color = if (p.label != null) c.textPrimary else c.textTertiary, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            KkHeartIcon(color = c.heart, size = 13.dp)
            Text("보고싶어 ${p.missYou}", style = KkTheme.type.labelS, color = c.textBrand)
        }
    }
}

/** Scheduled Message(예약됨). */
@Composable
private fun ScheduledMessage(row: ScheduledRow) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface)
            .padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(KkIcons.Clock, tint = c.textBrand)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs)) {
            Text(row.time, style = KkTheme.type.labelS, color = c.textBrand)
            Text(row.message, style = KkTheme.type.bodyM, color = c.textPrimary, maxLines = 2)
        }
        Text(
            "예약됨", style = KkTheme.type.labelS, color = c.textBrand,
            modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(c.bgBrandSubtle).padding(horizontal = KkSpacing.sm, vertical = KkSpacing.xxs),
        )
    }
}

// ── 연결 전 · 곰신(70:629) ──

@Composable
private fun GomsinUnlinked(onOpenPairing: () -> Unit, onWriteLetter: () -> Unit, onOpenCalendar: () -> Unit) {
    val c = KkTheme.colors
    InviteCard("자대에서 폰을 받으면 초대 코드로 연결해요.") {
        KkButton("초대 코드 보내기", onOpenPairing, Modifier.fillMaxWidth(), size = KkButtonSize.M)
    }
    Text("연결 전에 할 수 있어요", style = KkTheme.type.titleM, color = c.textPrimary)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CanDoTile(KkIcons.Mail, "편지 미리 쓰기", "연결되면 한 번에 도착해요", Modifier.weight(1f), onWriteLetter)
        CanDoTile(KkIcons.Calendar, "일정 적어 두기", "수료식 · 첫 면회 · 휴가", Modifier.weight(1f), onOpenCalendar)
    }
    Text("연결하면 함께 보여요", style = KkTheme.type.titleM, color = c.textPrimary)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple(KkIcons.Clock, "전역", "D-?"), Triple(KkIcons.Home, "상대 상태", "?"), Triple(KkIcons.Heart, "보고싶어", "?")).forEach { (icon, label, value) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(KkRadius.lg)).background(c.bgSubtle).padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KkIcon(icon, tint = c.textSecondary)
                Text(label, style = KkTheme.type.bodyS, color = c.textSecondary)
                Text(value, style = KkTheme.type.titleS, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun InviteCard(body: String, buttons: @Composable () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.heartSubtle), contentAlignment = Alignment.Center) {
            KkIcon(KkIcons.Heart, tint = c.textPrimary)
        }
        Text("연인과 연결할까요?", style = KkTheme.type.titleM, color = c.textPrimary, textAlign = TextAlign.Center)
        Text(body, style = KkTheme.type.bodyS, color = c.textSecondary, textAlign = TextAlign.Center)
        buttons()
    }
}

@Composable
private fun CanDoTile(icon: KkIcons, title: String, body: String, modifier: Modifier, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        modifier.kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).clickable(role = A11y.Button, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KkIcon(icon, tint = c.textPrimary)
        Text(title, style = KkTheme.type.titleS, color = c.textPrimary)
        Text(body, style = KkTheme.type.caption, color = c.textSecondary)
    }
}

// ── 연결 전 · 군인(137:1428) ──

@Composable
private fun SoldierUnlinked(state: HomeState, onOpenPairing: () -> Unit, onEnterCode: () -> Unit, onOpenCalendar: () -> Unit, onOpenLeave: () -> Unit) {
    val c = KkTheme.colors
    Dday(state, onOpenCalendar)
    state.soldier?.let { s ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("다음 진급", s.nextRank ?: "병장", Modifier.weight(1f))
            if (s.leaveLeft != null) StatTile("남은 휴가", "${s.leaveLeft}일", Modifier.weight(1f), onClick = onOpenLeave)
            StatTile("복무", "${s.servedDay}일째", Modifier.weight(1f))
        }
    }
    InviteCard("받은 초대 코드를 입력하거나 내 코드를 보내요.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            KkButton("내 코드 보내기", onOpenPairing, Modifier.weight(1f), style = KkButtonStyle.Secondary, size = KkButtonSize.M)
            KkButton("코드 입력", onEnterCode, Modifier.weight(1f), size = KkButtonSize.M)
        }
    }
    state.nextLeave?.let { leave ->
        val shape = RoundedCornerShape(KkRadius.lg)
        Row(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface)
                .clickable(role = A11y.Button, onClick = onOpenCalendar).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KkIcon(KkIcons.Calendar, tint = c.textPrimary)
            Column {
                Text(leave.title, style = KkTheme.type.titleS, color = c.textPrimary)
                Text(leave.detail, style = KkTheme.type.caption, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        modifier.kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface)
            .then(if (onClick != null) Modifier.clickable(role = A11y.Button, onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = KkTheme.type.caption, color = c.textTertiary)
        Text(value, style = KkTheme.type.titleS, color = c.textPrimary, maxLines = 1)
    }
}

