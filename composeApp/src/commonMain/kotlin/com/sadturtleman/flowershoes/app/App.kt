package com.sadturtleman.flowershoes.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.DeviceState
import com.sadturtleman.flowershoes.core.data.IdentityHolder
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.ThemeAccent
import com.sadturtleman.flowershoes.feature.calendar.CalendarRoute
import com.sadturtleman.flowershoes.feature.drawing.DrawingEditorRoute
import com.sadturtleman.flowershoes.feature.home.HomeRoute
import com.sadturtleman.flowershoes.feature.letter.LetterComposeRoute
import com.sadturtleman.flowershoes.feature.letter.LetterInboxRoute
import com.sadturtleman.flowershoes.feature.letter.LetterIntent
import com.sadturtleman.flowershoes.feature.letter.LetterViewModel
import com.sadturtleman.flowershoes.feature.memory.MemoryRoute
import org.koin.compose.viewmodel.koinViewModel
import com.sadturtleman.flowershoes.feature.onboarding.OnboardingRoute
import org.koin.compose.koinInject

/** 하단 탭. 화면 이동은 여기서만 정한다 — feature 끼리는 서로 모른다. */
enum class Tab(val label: String) { HOME("홈"), CALENDAR("캘린더"), LETTER("편지"), MEMORY("추억") }

/** 탭 밖으로 덮어 띄우는 화면. */
private enum class Overlay { PAIRING, RESTORE, COMPOSE, DRAW_FOR_LETTER, DRAW_ONLY }

@Composable
fun KkotsinApp() {
    val settings = koinInject<DeviceSettings>()
    val identity = koinInject<IdentityHolder>()

    // 신원(로컬 id)을 읽기 전에는 아무 화면도 그리지 않는다 — Repository 가 그 값을 쓴다.
    val device by produceState<DeviceState?>(null) {
        identity.update(settings.ensureIdentity())
        settings.state.collect {
            identity.update(it)
            value = it
        }
    }
    val current = device
    KkotsinTheme(accent = current?.theme ?: ThemeAccent.ROSE) {
        when {
            current == null -> Box(Modifier.fillMaxSize().background(KkTheme.colors.bgCanvas))
            !current.onboarded -> OnboardingFlow()
            else -> MainShell()
        }
    }
}

@Composable
private fun OnboardingFlow() {
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    when (overlay) {
        null -> OnboardingRoute(
            // 온보딩 완료는 DeviceSettings.onboarded 로 바뀌어 셸이 알아서 바뀐다.
            onFinished = {},
            onOpenRestore = { overlay = Overlay.RESTORE },
            onOpenPairing = { overlay = Overlay.PAIRING },
        )
        else -> ComingSoon(overlay!!.title(), onBack = { overlay = null })
    }
}

@Composable
private fun MainShell() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    // 편지 쓰기 · 편지함 · 그림만 보내기가 같은 편지 상태를 본다(Activity 범위 ViewModel 하나).
    val letters = koinViewModel<LetterViewModel>()
    val c = KkTheme.colors

    when (overlay) {
        Overlay.COMPOSE -> {
            LetterComposeRoute(onAddDrawing = { overlay = Overlay.DRAW_FOR_LETTER }, onClose = { overlay = null }, viewModel = letters)
            return
        }
        Overlay.DRAW_FOR_LETTER -> {
            DrawingEditorRoute(
                onSaved = {
                    letters.onIntent(LetterIntent.AddDrawing(it))
                    overlay = Overlay.COMPOSE
                },
                onClose = { overlay = Overlay.COMPOSE },
            )
            return
        }
        Overlay.DRAW_ONLY -> {
            // 그림만 보내기 = 글 없는 편지 한 통.
            DrawingEditorRoute(
                onSaved = {
                    letters.onIntent(LetterIntent.ResetDraft)
                    letters.onIntent(LetterIntent.AddDrawing(it))
                    letters.onIntent(LetterIntent.Send)
                    overlay = null
                    tab = Tab.LETTER
                },
                onClose = { overlay = null },
            )
            return
        }
        Overlay.PAIRING, Overlay.RESTORE -> {
            ComingSoon(overlay!!.title(), onBack = { overlay = null })
            return
        }
        null -> Unit
    }
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.HOME -> HomeRoute(
                    onOpenPairing = { overlay = Overlay.PAIRING },
                    onOpenCalendar = { tab = Tab.CALENDAR },
                )
                Tab.CALENDAR -> CalendarRoute()
                Tab.LETTER -> LetterInboxRoute(
                    onCompose = { overlay = Overlay.COMPOSE },
                    onDrawOnly = { overlay = Overlay.DRAW_ONLY },
                    viewModel = letters,
                )
                Tab.MEMORY -> MemoryRoute(onOpenLetter = {
                    letters.onIntent(LetterIntent.Open(it))
                    tab = Tab.LETTER
                })
            }
        }
        BottomBar(tab, onSelect = { tab = it })
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = KkTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.bgSurface).navigationBarsPadding().padding(vertical = KkSpacing.xs),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { t ->
            val selected = t == current
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab) { onSelect(t) },
                contentAlignment = Alignment.Center,
            ) {
                Text(t.label, style = KkTheme.type.labelM, color = if (selected) c.textBrand else c.textTertiary)
            }
        }
    }
}

/** 아직 붙지 않은 화면. 개발 순서(설계 10장)대로 채운다. */
@Composable
private fun ComingSoon(title: String, onBack: (() -> Unit)?) {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().padding(KkSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        if (onBack != null) {
            Text("← 돌아가기", style = KkTheme.type.labelM, color = c.textSecondary, modifier = Modifier.clickable(onClick = onBack))
        }
        Text(title, style = KkTheme.type.titleL, color = c.textPrimary)
        Text("곧 만나요. 이 화면은 다음 단계에서 열려요.", style = KkTheme.type.bodyM, color = c.textSecondary)
    }
}

private fun Overlay.title() = when (this) {
    Overlay.RESTORE -> "백업에서 복원하기"
    else -> "연인과 연결하기"
}
