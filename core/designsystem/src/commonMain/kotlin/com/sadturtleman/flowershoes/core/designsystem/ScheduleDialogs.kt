package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * 시간 입력(Figma Time Input): 시 · 분 두 칸(글자 그대로) + 오전 · 오후. [time] 은 칸이 맞을 때만 있다(시 1–12 · 분 0–59).
 */
data class KkTimeInput(val hour: String, val minute: String, val pm: Boolean) {
    val time: LocalTime?
        get() {
            val h = hour.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            val m = minute.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            return LocalTime(h % 12 + if (pm) 12 else 0, m)
        }

    /** [date] 의 이 시각이 [now] 보다 뒤인지(서울). 칸이 틀리면 false. */
    fun isFuture(date: LocalDate, now: LocalDateTime): Boolean = time?.let { LocalDateTime(date, it) > now } ?: false

    companion object {
        fun of(t: LocalTime) = KkTimeInput(
            (t.hour % 12).let { if (it == 0) 12 else it }.toString().padStart(2, '0'),
            t.minute.toString().padStart(2, '0'),
            pm = t.hour >= 12,
        )
    }
}

/** 시간 칸에 들어갈 글자: 숫자만 두 자리까지. */
fun digits2(text: String): String = text.filter { it.isDigit() }.take(2)

/**
 * 예약 보내기(Figma 편지 쓰기 · 예약): 도착 날짜(Material 3 모달 캘린더, 오늘부터) → 다음 → 도착 시간(입력형) → 확인.
 * 시각은 서울 기준이고, 지난 시각은 고를 수 없다. 시간 단계의 취소는 날짜로 돌아간다.
 */
@Composable
fun KkScheduleDialogs(initial: LocalDateTime?, onDismiss: () -> Unit, onPick: (LocalDateTime) -> Unit) {
    val now = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Seoul"))
    var date by remember { mutableStateOf(initial?.date ?: now.date.plus(DatePeriod(days = 1))) }
    var timeStep by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(KkTimeInput.of(initial?.time ?: LocalTime(9, 0))) }
    Dialog(onDismissRequest = onDismiss) {
        if (!timeStep) {
            KkDatePanel(date, now.date, onPick = { date = it }, onCancel = onDismiss, onNext = { timeStep = true })
        } else {
            KkTimePanel(input, { input = it }, ok = input.isFuture(date, now), onCancel = { timeStep = false }) {
                input.time?.let { onPick(LocalDateTime(date, it)) }
            }
        }
    }
}

/** 창 바탕(Figma Date Picker): surface · 반경 24 · 높은 그림자. */
@Composable
private fun DialogPanel(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(KkRadius.xl)
    Column(
        Modifier.fillMaxWidth().kkElevation(KkElevation.High, shape).clip(shape).background(KkTheme.colors.surface)
            .padding(start = 10.dp, end = 10.dp, top = KkSpacing.xl, bottom = KkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        content = content,
    )
}

/** 창 아래 글자 버튼(Figma Button · Text · M): accent 굵은 글자, 터치 48. */
@Composable
private fun TextAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(KkRadius.full))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = KkSpacing.lg),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = KkTheme.type.labelBold, color = KkTheme.colors.accent) }
}

/** 도착 날짜: "도착 날짜" · "11월 1일 (일)" · 달 넘김 · 달력(오늘 전은 흐리게 · 못 고름) · 취소 · 다음. */
@Composable
private fun KkDatePanel(selected: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onCancel: () -> Unit, onNext: () -> Unit) {
    val c = KkTheme.colors
    var month by remember { mutableStateOf(LocalDate(selected.year, selected.month, 1)) }
    DialogPanel {
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
            Text("도착 날짜", style = KkTheme.type.caption, color = c.sub)
            Text("${selected.month.number}월 ${selected.day}일 (${WEEKDAYS[selected.dayOfWeek.isoDayNumber % 7]})", style = KkTheme.type.title, color = c.ink)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${month.year}년 ${month.month.number}월", style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
            val canBack = month > LocalDate(today.year, today.month, 1)
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(enabled = canBack, role = Role.Button) { month = month.minus(DatePeriod(months = 1)) }
                    .alpha(if (canBack) 1f else 0.3f),
                contentAlignment = Alignment.Center,
            ) { KkIcon(KkIcons.ChevronRight, tint = c.ink, contentDescription = "이전 달", modifier = Modifier.rotate(180f)) }
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { month = month.plus(DatePeriod(months = 1)) },
                contentAlignment = Alignment.Center,
            ) { KkIcon(KkIcons.ChevronRight, tint = c.ink, contentDescription = "다음 달") }
        }
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                WEEKDAYS.forEachIndexed { i, w ->
                    Box(Modifier.weight(1f).height(20.dp), contentAlignment = Alignment.Center) {
                        Text(w, style = KkTheme.type.caption, color = if (i == 0) c.danger else c.sub)
                    }
                }
            }
            val lead = month.dayOfWeek.isoDayNumber % 7 // 일요일 = 0
            val days = month.daysUntil(month.plus(DatePeriod(months = 1)))
            (0 until (lead + days + 6) / 7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (0 until 7).forEach { col ->
                        val n = week * 7 + col - lead + 1
                        val date = LocalDate(month.year, month.month, n.coerceIn(1, days))
                        val inMonth = n in 1..days
                        val open = inMonth && date >= today
                        val on = inMonth && date == selected
                        Box(
                            Modifier.weight(1f).height(48.dp).then(
                                if (open) {
                                    Modifier.clickable(role = Role.Button) { onPick(date) }
                                        .semantics { contentDescription = "${month.month.number}월 ${n}일"; this.selected = on }
                                } else Modifier,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (inMonth) {
                                Box(Modifier.size(30.dp).clip(CircleShape).background(if (on) c.accent else Color.Transparent), contentAlignment = Alignment.Center) {
                                    Text("$n", style = KkTheme.type.label, color = if (on) c.canvas else if (open) c.ink else c.line)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm, Alignment.End)) {
            TextAction("취소", onClick = onCancel)
            TextAction("다음", onClick = onNext)
        }
    }
}

/** 도착 시간: "도착 시간" · 시 : 분 두 칸(누른 칸은 accent 테두리) · 오전 · 오후 · 취소 · 확인. */
@Composable
private fun KkTimePanel(input: KkTimeInput, onChange: (KkTimeInput) -> Unit, ok: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = KkTheme.colors
    DialogPanel {
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(KkSpacing.lg)) {
            Text("도착 시간", style = KkTheme.type.caption, color = c.sub)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                TimeBox(input.hour, "시") { onChange(input.copy(hour = digits2(it))) }
                Text(":", style = KkTheme.type.ddayM, color = c.ink)
                TimeBox(input.minute, "분") { onChange(input.copy(minute = digits2(it))) }
            }
            val shape = RoundedCornerShape(KkRadius.md)
            Row(Modifier.border(1.dp, c.line, shape).padding(KkSpacing.xxs)) {
                listOf(false to "오전", true to "오후").forEach { (pm, label) ->
                    val on = input.pm == pm
                    Box(
                        Modifier.width(64.dp).height(48.dp).clip(RoundedCornerShape(KkRadius.sm)).background(if (on) c.accent else Color.Transparent)
                            .clickable(role = Role.RadioButton) { onChange(input.copy(pm = pm)) }.semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = KkTheme.type.labelBold, color = if (on) c.canvas else c.sub) }
                }
            }
            if (input.time != null && !ok) Text("이미 지난 시각이에요", style = KkTheme.type.caption, color = c.danger)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm, Alignment.End)) {
            TextAction("취소", onClick = onCancel)
            TextAction("확인", enabled = ok, onClick = onConfirm)
        }
    }
}

/** 시 · 분 한 칸(104×94): 큰 숫자, 숫자 자판. */
@Composable
private fun TimeBox(value: String, label: String, onValue: (String) -> Unit) {
    val c = KkTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(KkRadius.md)
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = KkTheme.type.ddayM.copy(color = c.ink, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.size(104.dp, 94.dp).clip(shape)
            .background(if (focused) c.surface else c.canvas)
            .border(if (focused) 2.dp else 1.dp, if (focused) c.accent else c.line, shape)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label },
        decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } },
    )
}

private val WEEKDAYS = listOf("일", "월", "화", "수", "목", "금", "토")
