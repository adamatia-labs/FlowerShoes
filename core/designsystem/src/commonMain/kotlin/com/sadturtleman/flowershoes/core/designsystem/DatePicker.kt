package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * 날짜 고르기(Figma 입대일 시트의 달력): "2026년 10월" ‹ › + 요일 줄(24) + 주마다 44×40 칸, 고른 날은 36 brand 원. 일요일 시작.
 * 시트 안에 그대로 넣어 쓴다(입대일 · 일정 시작 · 끝).
 */
@Composable
fun KkDatePicker(selected: LocalDate, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    var month by remember(selected) { mutableStateOf(LocalDate(selected.year, selected.month, 1)) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            KkIcon(
                KkIcons.ChevronRight, tint = c.sub, contentDescription = "이전 달",
                modifier = Modifier.rotate(180f).clickable(role = Role.Button) { month = month.minus(DatePeriod(months = 1)) },
            )
            Text("${month.year}년 ${month.month.number}월", style = KkTheme.type.labelBold, color = c.ink)
            KkIcon(
                KkIcons.ChevronRight, tint = c.sub, contentDescription = "다음 달",
                modifier = Modifier.clickable(role = Role.Button) { month = month.plus(DatePeriod(months = 1)) },
            )
        }
        KkMonthGrid(month, selected, onSelect)
    }
}

/** 한 달 칸. [KkDatePicker] 안에서 쓴다. */
@Composable
fun KkMonthGrid(month: LocalDate, selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                Box(Modifier.width(44.dp).height(24.dp), contentAlignment = Alignment.Center) { Text(it, style = KkTheme.type.caption, color = c.sub) }
            }
        }
        val lead = month.dayOfWeek.isoDayNumber % 7 // 일요일 = 0
        val days = month.daysUntil(month.plus(DatePeriod(months = 1)))
        (0 until (lead + days + 6) / 7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (0 until 7).forEach { col ->
                    val n = week * 7 + col - lead + 1
                    Box(Modifier.width(44.dp).height(40.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..days) {
                            val date = LocalDate(month.year, month.month, n)
                            val on = date == selected
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(if (on) c.accent else Color.Transparent)
                                    .clickable(role = Role.Button) { onPick(date) }
                                    .semantics { contentDescription = "${month.month.number}월 ${n}일" + if (on) ", 선택됨" else "" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$n", style = KkTheme.type.label, color = if (on) c.surface else c.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 고르는 중인 기간. [pickingEnd] 면 다음 탭이 끝, 아니면 시작이다. */
data class KkDateRange(val start: LocalDate, val end: LocalDate, val pickingEnd: Boolean = false) {
    /** 시작 · 끝 포함 일수. */
    val days: Int get() = start.daysUntil(end) + 1

    /** 한 번 탭: 시작을 고르면 다음은 끝. 끝을 시작보다 앞에 고르면 그날부터 다시 시작한다. */
    fun pick(date: LocalDate): KkDateRange = when {
        !pickingEnd || date < start -> KkDateRange(date, date, pickingEnd = true)
        else -> KkDateRange(start, date, pickingEnd = false)
    }
}

/**
 * 기간 고르기(Figma 사용 등록 · 기간 고르기): 가운데 창에 [title] + "11월 14일 – 11월 18일 · 5일", 달 넘김, 기간 띠, 취소 · 확인.
 * 탭 두 번(시작 → 끝)으로 고른다.
 */
@Composable
fun KkDateRangeDialog(
    title: String,
    start: LocalDate,
    end: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val c = KkTheme.colors
    var range by remember { mutableStateOf(KkDateRange(start, end)) }
    var month by remember { mutableStateOf(LocalDate(start.year, start.month, 1)) }
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(KkRadius.xl)
        Column(
            Modifier.fillMaxWidth().kkElevation(KkElevation.High, shape).clip(shape).background(c.surface)
                .padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xl, bottom = KkSpacing.md),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
                Text(title, style = KkTheme.type.caption, color = c.sub)
                val head = if (range.start == range.end) range.start.monthDay() else "${range.start.monthDay()} – ${range.end.monthDay()}"
                Text("$head · ${range.days}일", style = KkTheme.type.title, color = c.ink)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${month.year}년 ${month.month.number}월", style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { month = month.minus(DatePeriod(months = 1)) },
                    contentAlignment = Alignment.Center,
                ) { KkIcon(KkIcons.ChevronRight, tint = c.ink, contentDescription = "이전 달", modifier = Modifier.rotate(180f)) }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { month = month.plus(DatePeriod(months = 1)) },
                    contentAlignment = Alignment.Center,
                ) { KkIcon(KkIcons.ChevronRight, tint = c.ink, contentDescription = "다음 달") }
            }
            RangeGrid(month, range) { range = range.pick(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs, Alignment.End)) {
                KkButton("취소", onDismiss, style = KkButtonStyle.Secondary, size = KkButtonSize.M)
                KkButton("확인", { onConfirm(range.start, range.end) }, style = KkButtonStyle.Secondary, size = KkButtonSize.M)
            }
        }
    }
}

/** 기간 달력 칸: 시작 · 끝은 brand 원, 사이는 연한 brand 띠. 일요일은 빨강. */
@Composable
private fun RangeGrid(month: LocalDate, range: KkDateRange, onPick: (LocalDate) -> Unit) {
    val c = KkTheme.colors
    val band = c.accent.copy(alpha = 0.15f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, w ->
                Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
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
                    val edge = date == range.start || date == range.end
                    // 칸 전체(48)가 눌리는 영역.
                    Box(
                        Modifier.weight(1f).height(48.dp).then(
                            if (n in 1..days) {
                                Modifier.clickable(role = Role.Button) { onPick(date) }
                                    .semantics { contentDescription = "${month.month.number}월 ${n}일" + if (edge) ", 선택됨" else "" }
                            } else Modifier,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (n in 1..days) {
                            // 띠: 시작은 오른쪽 반, 끝은 왼쪽 반, 사이는 칸 전체.
                            if (range.start != range.end && date >= range.start && date <= range.end) {
                                Row(Modifier.fillMaxWidth().height(36.dp)) {
                                    Box(Modifier.weight(1f).fillMaxHeight().background(if (date == range.start) Color.Transparent else band))
                                    Box(Modifier.weight(1f).fillMaxHeight().background(if (date == range.end) Color.Transparent else band))
                                }
                            }
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(if (edge) c.accent else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$n", style = KkTheme.type.label, color = if (edge) c.surface else c.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "11월 14일". */
private fun LocalDate.monthDay() = "${month.number}월 ${day}일"
