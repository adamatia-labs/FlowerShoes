package com.sadturtleman.flowershoes.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.designsystem.KkAd
import com.sadturtleman.flowershoes.core.designsystem.KkAdSlot
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkDatePicker
import com.sadturtleman.flowershoes.core.designsystem.KkDateRangeDialog
import com.sadturtleman.flowershoes.core.designsystem.KkDialog
import com.sadturtleman.flowershoes.core.designsystem.KkDialogHost
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkStatusChip
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.LeaveKind
import com.sadturtleman.flowershoes.core.model.koreanName
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.koin.compose.viewmodel.koinViewModel

private sealed interface LeavePage {
    data object List : LeavePage
    data object Use : LeavePage
    data class Detail(val target: LeaveTarget) : LeavePage
    data class Edit(val draft: LeaveDraft) : LeavePage
}

/**
 * 휴가 관리 흐름(캘린더 맨 위 휴가 요약 띠에서 들어온다): 목록 → 휴가 쓰기, 받은 휴가 › 상세 → 고치기, 추가 → 등록.
 * 쓴 휴가 한 줄은 캘린더 일정이라 [onOpenEvent] 로 일정 고치기를 연다. 뒤로 가기는 한 단계씩.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LeaveRoute(onBack: () -> Unit, onOpenEvent: (Event) -> Unit = {}, viewModel: LeaveViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var stack by remember { mutableStateOf(listOf<LeavePage>(LeavePage.List)) }
    val push = { p: LeavePage -> stack = stack + p }
    val pop = { if (stack.size > 1) stack = stack.dropLast(1) else onBack() }
    BackHandler(onBack = pop)
    when (val page = stack.last()) {
        LeavePage.List -> LeaveListScreen(
            state, onBack = onBack,
            onUse = { push(LeavePage.Use) },
            onAdd = { push(LeavePage.Edit(viewModel.newDraft())) },
            onOpen = { t -> if (t is LeaveTarget.Use) onOpenEvent(t.event) else push(LeavePage.Detail(t)) },
        )
        LeavePage.Use -> LeaveUseScreen(state, viewModel.defaultRange(), onClose = pop) { from, to, notify ->
            viewModel.use(from, to, notify)
            pop()
        }
        is LeavePage.Detail -> {
            // 받은 휴가를 고치거나 지운 뒤에는 최신 값으로(지웠으면 목록으로).
            val target = (page.target as? LeaveTarget.Grant)?.let { g -> state.grant(g.grant.id)?.let { LeaveTarget.Grant(it) } } ?: page.target
            LeaveDetailScreen(state, target, onBack = pop, onEdit = { g -> push(LeavePage.Edit(viewModel.editDraft(g))) })
        }
        is LeavePage.Edit -> LeaveEditScreen(
            page.draft,
            onClose = pop,
            onSave = { viewModel.save(it); pop() },
            onDelete = page.draft.id?.let { id -> { viewModel.delete(id); stack = listOf(LeavePage.List) } },
        )
    }
}

// ── 휴가 관리(356:2207 · 처음 415:9300) ──

@Composable
private fun LeaveListScreen(state: LeaveState, onBack: () -> Unit, onUse: () -> Unit, onAdd: () -> Unit, onOpen: (LeaveTarget) -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar("휴가 관리", onLeading = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = KkSpacing.md)) {
            Column(Modifier.padding(horizontal = KkSpacing.xxl), verticalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
                Text("남은 휴가", style = KkTheme.type.label, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${state.remaining}일", style = KkTheme.type.ddayM, color = if (state.pool.overdrawn) c.danger else c.ink,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text("/ 전체 ${state.total}일", style = KkTheme.type.body, color = c.sub, modifier = Modifier.alignByBaseline())
                }
                Spacer(Modifier.height(KkSpacing.sm))
                RemainingBar(state.remainingFraction)
            }
            Spacer(Modifier.height(KkSpacing.xl))
            SectionHeader("받은 휴가", Modifier.padding(horizontal = KkSpacing.xxl), action = "추가", onAction = onAdd)
            state.received.forEach { row -> ValueRow(row.title, row.value) { onOpen(row.target) } }
            Spacer(Modifier.height(KkSpacing.lg))
            SectionHeader("쓴 휴가", Modifier.padding(horizontal = KkSpacing.xxl))
            if (state.used.isEmpty()) {
                Text("아직 없어요", style = KkTheme.type.caption, color = c.sub, modifier = Modifier.padding(horizontal = KkSpacing.xxl, vertical = KkSpacing.sm))
            }
            state.used.forEach { row -> ValueRow(row.title, row.value) { onOpen(row.target) } }
        }
        Column(
            Modifier.padding(start = KkSpacing.xxl, end = KkSpacing.xxl, top = KkSpacing.md, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            KkPrimaryButton("휴가 쓰기", onUse, Modifier.fillMaxWidth())
            // 광고 지면(Figma 3 · 휴가 관리): 아래 배너 1개. 꽃신 평생이면 비어 있다.
            KkAd(KkAdSlot.LEAVE)
        }
    }
}

/** 남은 휴가 막대(높이 8): line 바탕 위에 남은 비율만큼 brand. */
@Composable
private fun RemainingBar(fraction: Float) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(4.dp)
    Box(Modifier.fillMaxWidth().height(8.dp).clip(shape).background(c.line)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(shape).background(c.accent))
    }
}

// ── 휴가 상세(356:2293) ──

@Composable
private fun LeaveDetailScreen(state: LeaveState, target: LeaveTarget, onBack: () -> Unit, onEdit: (com.sadturtleman.flowershoes.core.model.LeaveGrant) -> Unit) {
    val c = KkTheme.colors
    val (title, days) = when (target) {
        is LeaveTarget.Regular -> "정기휴가" to target.source.days
        is LeaveTarget.Grant -> target.grant.kind.label to target.grant.days
        is LeaveTarget.Use -> return
    }
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar(title, onLeading = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = KkSpacing.md)) {
            Column(Modifier.padding(horizontal = KkSpacing.xxl), verticalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
                Text("받은 휴가", style = KkTheme.type.caption, color = c.sub)
                Text("${days}일", style = KkTheme.type.ddayM, color = c.ink)
            }
            Spacer(Modifier.height(KkSpacing.md))
            when (target) {
                // 정기휴가는 군종으로 계산하는 값이라 고칠 수 없다(설정 › 군 정보의 군종 · 입대일을 따른다).
                is LeaveTarget.Regular -> {
                    ValueRow("종류", "정기휴가", null)
                    state.enlistDate?.let { ValueRow("받은 날", it.dotted(), null) }
                    ValueRow("메모", "${target.source.branch.koreanName} · 자동 등록", null)
                }
                is LeaveTarget.Grant -> {
                    val g = target.grant
                    val edit = { onEdit(g) }
                    ValueRow("종류", g.kind.label, edit)
                    ValueRow("받은 날", g.grantedOn.dotted(), edit)
                    ValueRow("메모", g.name.takeUnless { it == g.kind.label }.orEmpty(), edit)
                }
                is LeaveTarget.Use -> Unit
            }
        }
        Box(Modifier.padding(start = KkSpacing.xxl, end = KkSpacing.xxl, bottom = 14.dp)) { KkAd(KkAdSlot.LEAVE) }
    }
}

// ── 휴가 쓰기(356:2476 · 기간 고르기 415:9657 · 넘침 356:2647) ──

@Composable
private fun LeaveUseScreen(
    state: LeaveState,
    initial: Pair<LocalDate, LocalDate>,
    onClose: () -> Unit,
    onRegister: (LocalDate, LocalDate, Boolean) -> Unit,
) {
    val c = KkTheme.colors
    var from by rememberSaveable { mutableStateOf(initial.first.toString()) }
    var to by rememberSaveable { mutableStateOf(initial.second.toString()) }
    var notify by rememberSaveable { mutableStateOf(true) }
    var picking by remember { mutableStateOf(false) }
    val start = LocalDate.parse(from)
    val end = LocalDate.parse(to)
    val days = start.daysUntil(end) + 1
    // 넘쳐도 쓴다(결정: 초과 사용 허용) — 남은 휴가가 음수로 빨갛게 보인다.
    val use = LeaveUse(days, state.pool.remainingAfter(start, end))
    Screen("휴가 쓰기", KkTopBarLeading.Close, onClose, footer = {
        KkPrimaryButton("${days}일 쓰기", { onRegister(start, end, notify) }, Modifier.fillMaxWidth())
    }) {
        KkInput("${start.md()} – ${end.md()}", {}, label = "기간", onClick = { picking = true })
        Text(use.label, style = KkTheme.type.body, color = if (use.overdrawn) c.danger else c.ink)
        if (state.partnerName != null) {
            KkStatusChip("${state.partnerName}에게 알리기", selected = notify, onClick = { notify = !notify }, dot = c.danger)
        }
    }
    if (picking) {
        KkDateRangeDialog("휴가 기간", start, end, onDismiss = { picking = false }) { s, e ->
            from = s.toString()
            to = e.toString()
            picking = false
        }
    }
}

// ── 받은 휴가 등록 · 고치기(356:2428 · 받은 날 고르기 415:9388) ──

@Composable
private fun LeaveEditScreen(
    initial: LeaveDraft,
    onClose: () -> Unit,
    onSave: (LeaveDraft) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = KkTheme.colors
    var d by remember { mutableStateOf(initial) }
    var daysText by remember { mutableStateOf(initial.days.toString()) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val editing = initial.id != null
    Screen(if (editing) "받은 휴가 고치기" else "받은 휴가 등록", KkTopBarLeading.Close, onClose, footer = {
        KkPrimaryButton("저장", { onSave(d) }, Modifier.fillMaxWidth(), enabled = d.valid)
        if (onDelete != null) KkButton("휴가 지우기", { confirmDelete = true }, Modifier.fillMaxWidth(), style = KkButtonStyle.Secondary, danger = true)
    }) {
        Text("종류", style = KkTheme.type.labelBold, color = c.ink)
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            LeaveKind.entries.forEach { k -> SelectCard(k.label, d.kind == k, { d = d.copy(kind = k) }, Modifier.fillMaxWidth()) }
        }
        KkInput(
            daysText,
            { text ->
                daysText = text.filter { it.isDigit() }.take(2)
                d = d.copy(days = daysText.toIntOrNull() ?: 0)
            },
            label = "일수",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            error = if (daysText.isNotEmpty() && !d.valid) "1 – ${LeaveDraft.MAX_DAYS}일" else null,
            trailing = { Text("일", style = KkTheme.type.body, color = c.ink) },
        )
        KkInput(d.grantedOn.dotted(), {}, label = "받은 날", onClick = { picking = true })
        KkInput(d.memo, { d = d.copy(memo = it.take(30)) }, label = "메모")
    }
    if (picking) {
        KkModalSheet(onDismiss = { picking = false }) {
            KkSheetHeader("받은 날")
            KkDatePicker(d.grantedOn, { d = d.copy(grantedOn = it); picking = false })
        }
    }
    if (confirmDelete && onDelete != null) {
        KkDialogHost({ confirmDelete = false }) {
            KkDialog("받은 휴가를 지울까요?", "되돌릴 수 없어요", "지우기", onConfirm = onDelete, onCancel = { confirmDelete = false }, destructive = true)
        }
    }
}

// ── 공용 ──

/** 상단 바 + 스크롤 본문(좌우 24 · 위아래 16, 사이 16) + 아래 버튼. */
@Composable
private fun Screen(
    title: String,
    leading: KkTopBarLeading,
    onLeading: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar(title, onLeading = onLeading, leading = leading)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xxl, vertical = KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
            content = content,
        )
        Column(
            Modifier.padding(start = KkSpacing.xxl, end = KkSpacing.xxl, top = KkSpacing.md, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
            content = footer,
        )
    }
}

/** "11월 14일 (토)". */
internal fun LocalDate.md() = "${monthDay()} (${weekdayKo()})"
