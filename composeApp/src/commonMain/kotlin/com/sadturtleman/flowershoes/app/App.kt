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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
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
import com.sadturtleman.flowershoes.feature.pairing.PairingRoute
import org.koin.compose.viewmodel.koinViewModel
import com.sadturtleman.flowershoes.feature.onboarding.OnboardingRoute
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import com.sadturtleman.flowershoes.feature.settings.NoPlatformActions
import com.sadturtleman.flowershoes.feature.settings.PlatformActions
import com.sadturtleman.flowershoes.feature.settings.SettingsRoute
import com.sadturtleman.flowershoes.feature.settings.RestoreRoute

/** 하단 탭. 화면 이동은 여기서만 정한다 — feature 끼리는 서로 모른다. */
enum class Tab(val label: String) { HOME("홈"), CALENDAR("캘린더"), LETTER("편지"), MEMORY("추억") }

/** 탭 밖으로 덮어 띄우는 화면. */
private enum class Overlay { PAIRING, HAND_OVER, RESTORE, COMPOSE, DRAW_FOR_LETTER, DRAW_ONLY, SETTINGS }

@Composable
fun KkotsinApp() {
    val settings = koinInject<DeviceSettings>()
    val identity = koinInject<IdentityHolder>()

    // 신원(로컬 id)을 읽기 전에는 아무 화면도 그리지 않는다 — Repository 가 그 값을 쓴다.
    val device by produceState<DeviceState?>(null) {
        identity.update(settings.ensureIdentity())
        settings.state.collect {
            // 탈퇴로 설정이 비워졌다 → 새 로컬 id 를 만들고(다시 내보낸다) 처음 화면으로.
            if (it.userId.isEmpty() || it.deviceId.isEmpty()) {
                settings.ensureIdentity()
                return@collect
            }
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
        Overlay.PAIRING -> PairingRoute(onDone = { overlay = null }, onBack = { overlay = null })
        Overlay.RESTORE -> RestoreRoute(onBack = { overlay = null })
        else -> ComingSoon(overlay!!.title(), onBack = { overlay = null })
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MainShell() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    // 편지 쓰기 · 편지함 · 그림만 보내기가 같은 편지 상태를 본다(Activity 범위 ViewModel 하나).
    val letters = koinViewModel<LetterViewModel>()
    val c = KkTheme.colors
    val letterOpen = letters.state.collectAsState().value.open != null

    // 알림 · 위젯에서 들어온 딥링크
    val link by DeepLinks.next.collectAsState()
    LaunchedEffect(link) {
        when (val l = link) {
            null -> return@LaunchedEffect
            DeepLink.Home -> tab = Tab.HOME
            is DeepLink.Letter -> {
                overlay = null
                tab = Tab.LETTER
                letters.onIntent(LetterIntent.Open(l.id))
            }
            is DeepLink.Calendar -> {
                overlay = null
                tab = Tab.CALENDAR
            }
            DeepLink.Pairing -> overlay = Overlay.PAIRING
        }
        DeepLinks.consumed()
    }

    // 뒤로 가기: 덮어 띄운 화면 → 읽던 편지 → 홈 탭 순서로 닫는다. 홈에서는 시스템에 맡긴다(앱 나가기).
    BackHandler(enabled = overlay != null || letterOpen || tab != Tab.HOME) {
        when {
            overlay == Overlay.DRAW_FOR_LETTER -> overlay = Overlay.COMPOSE
            overlay != null -> overlay = null
            letterOpen -> letters.onIntent(LetterIntent.Close)
            else -> tab = Tab.HOME
        }
    }

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
        Overlay.PAIRING -> {
            PairingRoute(onDone = { overlay = null }, onBack = { overlay = null })
            return
        }
        Overlay.HAND_OVER -> {
            PairingRoute(onDone = { overlay = null }, onBack = { overlay = null }, handOver = true)
            return
        }
        Overlay.RESTORE -> {
            RestoreRoute(onBack = { overlay = null })
            return
        }
        Overlay.SETTINGS -> {
            SettingsRoute(
                platform = getKoin().getOrNull<PlatformActions>() ?: NoPlatformActions,
                onOpenPairing = { overlay = Overlay.PAIRING },
                onHandOver = { overlay = Overlay.HAND_OVER },
                onRestore = { overlay = Overlay.RESTORE },
                onBack = { overlay = null },
            )
            return
        }
        null -> Unit
    }
    val scheduler = getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.sync.SyncScheduler>()
    val restoreState = scheduler?.restoreState?.collectAsState()?.value
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding()) {
        if (restoreState == com.sadturtleman.flowershoes.core.data.sync.RestoreRotationState.WaitingForPartner) {
            // 복원 뒤 기기 교체(7-3-A): 상대 폰이 동기화하면 자동으로 확인된다.
            Text(
                "연인 폰에서 새 폰을 확인하는 중이에요. 그동안 보낸 것은 확인이 끝나면 전달돼요.",
                style = KkTheme.type.bodyS,
                color = c.textSecondary,
                modifier = Modifier.fillMaxWidth().background(c.bgSurface).padding(horizontal = KkSpacing.lg, vertical = KkSpacing.sm),
            )
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.HOME -> HomeRoute(
                    onOpenPairing = { overlay = Overlay.PAIRING },
                    onOpenCalendar = { tab = Tab.CALENDAR },
                    onOpenSettings = { overlay = Overlay.SETTINGS },
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
