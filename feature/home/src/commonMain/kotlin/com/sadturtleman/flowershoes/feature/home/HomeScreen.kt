package com.sadturtleman.flowershoes.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.designsystem.KkCard
import com.sadturtleman.flowershoes.core.designsystem.KkHeartButton
import com.sadturtleman.flowershoes.core.designsystem.KkMilitaryBadge
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.military.Rank
import com.sadturtleman.flowershoes.core.model.EventType
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoute(
    onOpenPairing: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, viewModel::onIntent, onOpenPairing, onOpenCalendar, onOpenSettings)
}

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onOpenPairing: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val c = KkTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.screen, vertical = KkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("꽃신", style = KkTheme.type.titleM, color = c.textBrand)
            Spacer(Modifier.weight(1f))
            Text(
                "설정", style = KkTheme.type.labelM, color = c.textSecondary,
                modifier = Modifier.clickable(onClick = onOpenSettings).padding(KkSpacing.sm),
            )
        }
        state.dday?.let { DdayCardView(it) } ?: EmptyDday()

        KkCard {
            Text("마음 전하기", style = KkTheme.type.labelM, color = c.textSecondary)
            Spacer(Modifier.height(KkSpacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KkHeartButton(onClick = { onIntent(HomeIntent.SendMissYou) })
                Spacer(Modifier.weight(1f))
                Text("오늘 ${state.sentMissYouToday}번", style = KkTheme.type.bodyS, color = c.textTertiary)
            }
        }

        if (!state.linked) {
            KkCard {
                Text("아직 연인과 연결 전이에요", style = KkTheme.type.titleS, color = c.textPrimary)
                Spacer(Modifier.height(KkSpacing.xs))
                Text("연결하면 상대 상태 · 보고싶어 · 편지를 주고받을 수 있어요.", style = KkTheme.type.bodyS, color = c.textSecondary)
                Spacer(Modifier.height(KkSpacing.md))
                KkSecondaryButton("연결하기", onClick = onOpenPairing)
            }
        }

        KkCard(onClick = onOpenCalendar) {
            Text("다음 만남", style = KkTheme.type.labelM, color = c.textSecondary)
            Spacer(Modifier.height(KkSpacing.xs))
            val next = state.nextEvent
            if (next == null) {
                Text("아직 잡힌 일정이 없어요", style = KkTheme.type.bodyM, color = c.textTertiary)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(next.title.ifBlank { next.type.label() }, style = KkTheme.type.titleS, color = c.textPrimary)
                        Text(next.start.date.dotted(), style = KkTheme.type.bodyS, color = c.textSecondary)
                    }
                    state.daysToNextEvent?.let {
                        Text(if (it == 0) "오늘" else "D-$it", style = KkTheme.type.titleM, color = c.textBrand)
                    }
                }
            }
        }
    }
}

@Composable
private fun DdayCardView(d: DdayCard) {
    val c = KkTheme.colors
    val dday = when {
        d.daysLeft > 0 -> "D-${d.daysLeft}"
        d.daysLeft == 0 -> "D-DAY"
        else -> "전역 +${-d.daysLeft}"
    }
    KkCard(Modifier.semantics { contentDescription = "전역까지 ${d.daysLeft}일, 전역일 ${d.dischargeDate.dotted()}" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("전역까지", style = KkTheme.type.labelM, color = c.textSecondary)
            Spacer(Modifier.weight(1f))
            d.rank?.let { KkMilitaryBadge(listOfNotNull(it.label(), d.payStep?.let { p -> "${p}호봉" }).joinToString(" · ")) }
        }
        Spacer(Modifier.height(KkSpacing.xs))
        Text(dday, style = KkTheme.type.dday, color = c.textPrimary)
        Text(
            "전역일 ${d.dischargeDate.dotted()}" + if (d.isOverridden) " (직접 수정)" else "",
            style = KkTheme.type.bodyS, color = c.textSecondary,
        )
        Spacer(Modifier.height(KkSpacing.md))
        ProgressBar(d.progress)
        Spacer(Modifier.height(KkSpacing.xs))
        Row {
            Text("${d.servedDays}일째", style = KkTheme.type.caption, color = c.textTertiary)
            Spacer(Modifier.weight(1f))
            Text("${(d.progress * 1000).toInt() / 10.0}%", style = KkTheme.type.caption, color = c.textMilitary)
        }
        if (d.nextRank != null && d.daysToNextRank != null) {
            Spacer(Modifier.height(KkSpacing.sm))
            Text("${d.nextRank.label()} 진급까지 ${d.daysToNextRank}일", style = KkTheme.type.bodyS, color = c.textMilitary)
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val c = KkTheme.colors
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.bgMilitarySubtle)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.bgMilitary))
    }
}

@Composable
private fun EmptyDday() {
    KkCard {
        Text("입대일을 입력하면 D-day가 보여요", style = KkTheme.type.bodyM, color = KkTheme.colors.textSecondary)
    }
}

internal fun Rank.label() = when (this) {
    Rank.PRIVATE -> "이병"
    Rank.PRIVATE_FIRST_CLASS -> "일병"
    Rank.CORPORAL -> "상병"
    Rank.SERGEANT -> "병장"
}

internal fun EventType.label() = when (this) {
    EventType.VACATION -> "휴가"
    EventType.OVERNIGHT -> "외박"
    EventType.VISIT -> "면회"
    EventType.TRAINING -> "훈련"
    EventType.DUTY -> "당직"
}

internal fun LocalDate.dotted() = "$year.${(month.ordinal + 1).toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"
