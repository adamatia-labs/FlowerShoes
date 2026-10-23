package com.sadturtleman.flowershoes.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.data.dayCount
import com.sadturtleman.flowershoes.core.data.subtitle
import com.sadturtleman.flowershoes.core.data.title
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkDatePicker
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSegmentedControl
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkStatusChip
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.LeaveKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import org.koin.compose.viewmodel.koinViewModel

private sealed interface LeavePage {
    data object List : LeavePage
    data class Detail(val id: String) : LeavePage
    data class Use(val id: String) : LeavePage
    data class Edit(val draft: LeaveDraft) : LeavePage
}

/** 휴가 관리 흐름: 목록 → 상세 → 사용 등록 · 수정, 목록 + → 추가. 뒤로 가기는 한 단계씩. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LeaveRoute(onBack: () -> Unit, viewModel: LeaveViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var stack by remember { mutableStateOf(listOf<LeavePage>(LeavePage.List)) }
    val push = { p: LeavePage -> stack = stack + p }
    val pop = { if (stack.size > 1) stack = stack.dropLast(1) else onBack() }
    BackHandler(onBack = pop)
    when (val page = stack.last()) {
        LeavePage.List -> LeaveListScreen(
            state, onBack = onBack, onSort = viewModel::setSort,
            onOpen = { push(LeavePage.Detail(it)) }, onAdd = { push(LeavePage.Edit(viewModel.newDraft())) },
        )
        is LeavePage.Detail -> {
            val card = state.card(page.id)
            if (card == null) {
                // 지웠거나(상대 폰 포함) 아직 불러오는 중.
                LeaveListScreen(state, onBack = onBack, onSort = viewModel::setSort, onOpen = { push(LeavePage.Detail(it)) }, onAdd = { push(LeavePage.Edit(viewModel.newDraft())) })
            } else {
                LeaveDetailScreen(card, onBack = pop, onEdit = { push(LeavePage.Edit(viewModel.editDraft(card.grant))) }, onUse = { push(LeavePage.Use(card.grant.id)) })
            }
        }
        is LeavePage.Use -> {
            val card = state.card(page.id)
            if (card == null) {
                LaunchedEffect(page) { pop() }
            } else {
                LeaveUseScreen(card, state.partnerName, viewModel.defaultRange(card), onClose = pop) { from, to, notify ->
                    viewModel.use(card, from, to, notify)
                    pop()
                }
            }
        }
        is LeavePage.Edit -> LeaveEditScreen(
            page.draft,
            regularCount = state.cards.count { it.grant.kind == LeaveKind.REGULAR },
            onClose = pop,
            onSave = { viewModel.save(it); pop() },
            onDelete = page.draft.id?.let { id -> { viewModel.delete(id); stack = listOf(LeavePage.List) } },
        )
    }
}

// ── 휴가 관리(154:1524) ──

@Composable
private fun LeaveListScreen(state: LeaveState, onBack: () -> Unit, onSort: (LeaveSort) -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val c = KkTheme.colors
    Box(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            KkTopBar("", onLeading = onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.lg, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
            ) {
                Text("휴가 관리", style = KkTheme.type.titleL, color = c.textPrimary)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("전체 휴가일", state.total, c.textPrimary, Modifier.weight(1f))
                        Stat("사용 예정일", state.planned, c.textMilitary, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("사용한 일수", state.usedDays, c.textPrimary, Modifier.weight(1f))
                        Stat("남은 휴가일", state.remaining, c.textBrand, Modifier.weight(1f))
                    }
                }
                KkSegmentedControl(LeaveSort.entries.map { it.label }, state.sort.ordinal, { onSort(LeaveSort.entries[it]) })
                if (state.cards.isEmpty()) {
                    Text(
                        "받은 휴가를 + 로 추가하면 남은 일수를 세어 드려요.\n정기휴가 · 포상휴가 · 위로휴가를 받은 단위로 적어요.",
                        style = KkTheme.type.bodyS, color = c.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = KkSpacing.xl),
                    )
                }
                state.sorted.forEach { card -> GrantCard(card) { onOpen(card.grant.id) } }
            }
        }
        val shape = CircleShape
        Box(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = KkSpacing.xl, bottom = 34.dp)
                .size(56.dp).kkElevation(KkElevation.High, shape).clip(shape).background(c.bgBrand)
                .clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = "휴가 추가" },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = KkTheme.type.titleL, color = c.textOnBrand)
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, color: Color, modifier: Modifier) {
    LowCard(modifier, spacing = 6.dp) {
        Text(label, style = KkTheme.type.bodyS, color = KkTheme.colors.textSecondary)
        Text("$value", style = KkTheme.type.dday, color = color)
    }
}

@Composable
private fun GrantCard(card: LeaveCard, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface)
            .clickable(role = Role.Button, onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(card.grant.title, style = KkTheme.type.titleS, color = c.textPrimary)
            Text(card.grant.subtitle, style = KkTheme.type.caption, color = c.textSecondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Text("${card.remaining}일", style = KkTheme.type.titleS, color = if (card.remaining == 0) c.textTertiary else c.textPrimary)
            Text(" / ${card.grant.days}일", style = KkTheme.type.bodyM, color = c.textTertiary)
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { KkIcon(KkIcons.ChevronRight, tint = c.textSecondary) }
        }
    }
}

// ── 휴가 상세(154:1638) ──

@Composable
private fun LeaveDetailScreen(card: LeaveCard, onBack: () -> Unit, onEdit: () -> Unit, onUse: () -> Unit) {
    val c = KkTheme.colors
    val g = card.grant
    Screen(g.title, KkTopBarLeading.Back, onBack, footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KkButton("수정", onEdit, Modifier.weight(1f), style = KkButtonStyle.Secondary)
            KkButton("사용 등록", onUse, Modifier.weight(1f), enabled = card.remaining > 0)
        }
    }) {
        LowCard(spacing = 12.dp) {
            Kv("종류", g.kind.label)
            Kv("받은 날", g.grantedOn.dotted())
            Kv("일수", "${g.days}일")
            Kv("남은 일수", "${card.remaining}일", c.textBrand)
        }
        Text("사용 내역", style = KkTheme.type.titleM, color = c.textPrimary)
        LowCard(spacing = 14.dp) {
            if (card.uses.isEmpty()) {
                Text("아직 쓴 날이 없어요. 사용 등록을 하면 캘린더에 휴가 일정으로 들어가요.", style = KkTheme.type.bodyS, color = c.textSecondary)
            }
            card.uses.forEach { e -> UseRow(e, card.stateOf(e)) }
        }
    }
}

@Composable
private fun UseRow(e: Event, state: LeaveUseState) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${e.start.date.md()} – ${e.end.date.md()}", style = KkTheme.type.bodyM, color = c.textPrimary)
            Text("${e.dayCount}일 · 캘린더에 등록됨", style = KkTheme.type.caption, color = c.textSecondary)
        }
        val (bg, fg) = when (state) {
            LeaveUseState.PLANNED -> c.bgMilitarySubtle to c.textMilitary
            LeaveUseState.ONGOING -> c.bgBrandSubtle to c.textBrand
            LeaveUseState.DONE -> c.bgSubtle to c.textSecondary
        }
        Text(
            state.label, style = KkTheme.type.labelS, color = fg,
            modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ── 사용 등록(154:1703) ──

@Composable
private fun LeaveUseScreen(
    card: LeaveCard,
    partnerName: String?,
    initial: Pair<LocalDate, LocalDate>,
    onClose: () -> Unit,
    onRegister: (LocalDate, LocalDate, Boolean) -> Unit,
) {
    val c = KkTheme.colors
    var from by rememberSaveable { mutableStateOf(initial.first.toString()) }
    var to by rememberSaveable { mutableStateOf(initial.second.toString()) }
    var notify by rememberSaveable { mutableStateOf(true) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = 시작, false = 끝
    val start = LocalDate.parse(from)
    val end = LocalDate.parse(to)
    val days = start.daysUntil(end) + 1
    val error = when {
        end < start -> "끝나는 날이 시작보다 빨라요"
        days > card.remaining -> "남은 일수(${card.remaining}일)보다 많아요"
        else -> null
    }
    Screen("사용 등록", KkTopBarLeading.Close, onClose, footer = {
        KkPrimaryButton("등록", { onRegister(start, end, notify) }, Modifier.fillMaxWidth(), enabled = error == null)
    }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.lg)).background(c.bgMilitarySubtle).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(card.grant.title, style = KkTheme.type.titleS, color = c.textMilitary)
            Text("남은 ${card.remaining}일", style = KkTheme.type.bodyS, color = c.textSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KkInput(start.md(), {}, Modifier.weight(1f), label = "시작", onClick = { picking = true })
            KkInput(end.md(), {}, Modifier.weight(1f), label = "끝", onClick = { picking = false }, error = error)
        }
        LowCard {
            Kv("사용 일수", "${days.coerceAtLeast(0)}일", c.textMilitary, labelColor = c.textPrimary)
        }
        if (partnerName != null) {
            KkStatusChip("${partnerName}에게 알리기", selected = notify, onClick = { notify = !notify }, dot = if (notify) Color.White else c.statusWarning)
        }
    }
    picking?.let { isStart ->
        KkModalSheet(onDismiss = { picking = null }) {
            KkSheetHeader(if (isStart) "시작하는 날" else "끝나는 날")
            KkDatePicker(if (isStart) start else end, { d ->
                if (isStart) {
                    from = d.toString()
                    if (LocalDate.parse(to) < d) to = d.toString()
                } else {
                    to = d.toString()
                }
                picking = null
            })
        }
    }
}

// ── 휴가 추가 · 수정(154:1766) ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeaveEditScreen(
    initial: LeaveDraft,
    regularCount: Int,
    onClose: () -> Unit,
    onSave: (LeaveDraft) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = KkTheme.colors
    var d by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    val editing = initial.id != null
    Screen(if (editing) "휴가 수정" else "휴가 추가", KkTopBarLeading.Close, onClose, footer = {
        KkPrimaryButton(if (editing) "저장" else "추가", { onSave(d) }, Modifier.fillMaxWidth(), enabled = d.valid)
        if (onDelete != null) KkButton("휴가 지우기", onDelete, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost, danger = true)
    }) {
        Text("종류", style = KkTheme.type.labelM, color = c.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LeaveKind.entries.forEach { k ->
                KkStatusChip(k.label, selected = d.kind == k, dot = if (d.kind == k) Color.White else c.statusWarning, onClick = {
                    // 이름을 아직 안 고쳤으면 종류에 맞춰 채운다(정기휴가는 "N차 정기휴가").
                    val auto = d.name.isBlank() || LeaveKind.entries.any { d.name == suggestName(it, regularCount) }
                    d = d.copy(kind = k, name = if (auto) suggestName(k, regularCount) else d.name)
                })
            }
        }
        KkInput(d.name, { d = d.copy(name = it.take(30)) }, label = "이름", placeholder = suggestName(d.kind, regularCount))
        Text("일수", style = KkTheme.type.labelM, color = c.textSecondary)
        val shape = RoundedCornerShape(KkRadius.lg)
        Row(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("−", "하루 줄이기", d.days > 1) { d = d.copy(days = d.days - 1) }
            Text("${d.days}일", style = KkTheme.type.titleM, color = c.textPrimary)
            StepButton("+", "하루 늘리기", d.days < LeaveDraft.MAX_DAYS) { d = d.copy(days = d.days + 1) }
        }
        KkInput(d.grantedOn.dotted(), {}, label = "받은 날", onClick = { picking = true })
    }
    if (picking) {
        KkModalSheet(onDismiss = { picking = false }) {
            KkSheetHeader("받은 날")
            KkDatePicker(d.grantedOn, { d = d.copy(grantedOn = it); picking = false })
        }
    }
}

private fun suggestName(kind: LeaveKind, regularCount: Int): String =
    if (kind == LeaveKind.REGULAR) "${regularCount + 1}차 정기휴가" else kind.label

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = KkTheme.colors
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.bgSubtle)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = KkTheme.type.titleM, color = if (enabled) c.textPrimary else c.textTertiary)
    }
}

// ── 공용 ──

/** 상단 바 + 스크롤 본문(좌우 20 · 위아래 16, 사이 16) + 아래 버튼(위 24). */
@Composable
private fun Screen(
    title: String,
    leading: KkTopBarLeading,
    onLeading: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar(title, onLeading = onLeading, leading = leading)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
            content = content,
        )
        Column(
            Modifier.padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xxl, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
            content = footer,
        )
    }
}

/** 흰 카드(Elevation/Low, 반경 12, 좌우 20 · 위아래 18). */
@Composable
private fun LowCard(modifier: Modifier = Modifier, spacing: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(KkTheme.colors.bgSurface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
private fun Kv(label: String, value: String, valueColor: Color = KkTheme.colors.textPrimary, labelColor: Color = KkTheme.colors.textSecondary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = KkTheme.type.bodyM, color = labelColor)
        Text(value, style = KkTheme.type.titleS, color = valueColor)
    }
}

/** "11.14 (금)". */
private fun LocalDate.md() = "${month.number}.$day (${weekdayKo()})"

/** "2026.10.21". */
private fun LocalDate.dotted() = "$year.${month.number.toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"
