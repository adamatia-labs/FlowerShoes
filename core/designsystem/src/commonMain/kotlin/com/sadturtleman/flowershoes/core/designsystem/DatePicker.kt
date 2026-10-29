package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
