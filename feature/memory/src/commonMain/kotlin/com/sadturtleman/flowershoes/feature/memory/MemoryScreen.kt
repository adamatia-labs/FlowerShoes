package com.sadturtleman.flowershoes.feature.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.designsystem.KkCard
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/** 추억 타임라인 한 줄. 편지(그림은 편지 안) · 만남(지난 휴가 · 외박 · 면회). */
sealed interface MemoryRow {
    val date: LocalDate

    data class LetterRow(override val date: LocalDate, val letterId: String, val title: String, val drawings: Int) : MemoryRow
    data class MeetingRow(override val date: LocalDate, val title: String, val type: EventType) : MemoryRow
}

/**
 * 추억. 예약 편지는 공개 전이라 여기에도 없다(편지함과 같은 쿼리를 쓴다).
 */
class MemoryViewModel(letters: LetterRepository, events: EventRepository, clock: Clock = Clock.System) : ViewModel() {
    private val today = clock.today()

    val rows: StateFlow<List<MemoryRow>> = combine(
        letters.observeInbox(),
        events.observeRange(today.minus(DatePeriod(years = 3)), today),
    ) { ls, es ->
        val letterRows = ls.map { l ->
            MemoryRow.LetterRow(
                l.visibleAt.toLocalDateTime(SEOUL).date, l.id,
                l.blocks.filterIsInstance<LetterBlock.Text>().firstOrNull()?.text?.lineSequence()?.first() ?: "그림 편지",
                l.drawingCount,
            )
        }
        val meetings = es.filter { it.type in MEETINGS && it.end.date <= today }
            .map { MemoryRow.MeetingRow(it.start.date, it.title, it.type) }
        (letterRows + meetings).sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private companion object {
        val MEETINGS = setOf(EventType.VACATION, EventType.OVERNIGHT, EventType.VISIT)
    }
}

@Composable
fun MemoryRoute(onOpenLetter: (String) -> Unit, viewModel: MemoryViewModel = koinViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.screen, vertical = KkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        Text("추억", style = KkTheme.type.titleL, color = c.textPrimary)
        if (rows.isEmpty()) Text("편지를 주고받고 만나면 여기에 쌓여요", style = KkTheme.type.bodyM, color = c.textTertiary)
        var lastMonth: Pair<Int, Int>? = null
        rows.forEach { row ->
            val month = row.date.year to row.date.month.ordinal + 1
            if (month != lastMonth) {
                Spacer(Modifier.padding(top = KkSpacing.xs))
                Text("${month.first}년 ${month.second}월", style = KkTheme.type.labelM, color = c.textSecondary)
                lastMonth = month
            }
            when (row) {
                is MemoryRow.LetterRow -> KkCard(onClick = { onOpenLetter(row.letterId) }) {
                    Text(row.title, style = KkTheme.type.titleS, color = c.textPrimary, maxLines = 1)
                    Text("${row.date.day}일 · 편지" + if (row.drawings > 0) " · 그림 ${row.drawings}장" else "", style = KkTheme.type.bodyS, color = c.textSecondary)
                }
                is MemoryRow.MeetingRow -> KkCard {
                    Text(row.title.ifBlank { row.type.label() }, style = KkTheme.type.titleS, color = c.textPrimary)
                    Text("${row.date.day}일 · 만남 기록", style = KkTheme.type.bodyS, color = c.textMilitary)
                }
            }
        }
    }
}

private fun EventType.label() = when (this) {
    EventType.VACATION -> "휴가"
    EventType.OVERNIGHT -> "외박"
    EventType.VISIT -> "면회"
    EventType.TRAINING -> "훈련"
    EventType.DUTY -> "당직"
}
