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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import com.sadturtleman.flowershoes.core.data.today
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
import com.sadturtleman.flowershoes.feature.pairing.InviteConnect
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
enum class Tab(val label: String, val icon: com.sadturtleman.flowershoes.core.designsystem.KkIcons) {
    HOME("홈", com.sadturtleman.flowershoes.core.designsystem.KkIcons.Home),
    CALENDAR("캘린더", com.sadturtleman.flowershoes.core.designsystem.KkIcons.Calendar),
    LETTER("편지", com.sadturtleman.flowershoes.core.designsystem.KkIcons.Mail),
    MEMORY("추억", com.sadturtleman.flowershoes.core.designsystem.KkIcons.Album),
}

/** 탭 밖으로 덮어 띄우는 화면. */
private enum class Overlay { PAIRING, HAND_OVER, RECEIVE, RESTORE, COMPOSE, COMPOSE_SCHEDULED, DRAW_FOR_LETTER, DRAW_ONLY, SETTINGS, LEAVE }

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
    val share = rememberPlatformShare()
    // 그림의 배경 사진: 문서는 첨부 id 만 갖고 있어 렌더러가 여기서 읽는다.
    val attachmentStore = getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.AttachmentStore>()
    val photoLoader: suspend (String) -> ByteArray? = remember(attachmentStore) { { id -> attachmentStore?.read(id) } }
    val displayMode by (getKoin().getOrNull<DeviceSettings>()?.displayMode ?: kotlinx.coroutines.flow.flowOf("SYSTEM")).collectAsState("SYSTEM")
    val dark = when (displayMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    KkotsinTheme(accent = current?.theme ?: ThemeAccent.ROSE, dark = dark) {
        CompositionLocalProvider(
            com.sadturtleman.flowershoes.core.designsystem.LocalKkShare provides share,
            com.sadturtleman.flowershoes.core.drawingui.LocalDrawingPhotoLoader provides photoLoader,
        ) {
            when {
                current == null -> Box(Modifier.fillMaxSize().background(KkTheme.colors.bgCanvas))
                !current.onboarded -> OnboardingFlow()
                else -> MainShell()
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun OnboardingFlow() {
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    // 복원 · 연결 화면에서 뒤로 가면 온보딩으로(복원을 원하지 않으면 약관부터 새로 시작).
    BackHandler(enabled = overlay != null) { overlay = null }
    when (overlay) {
        null -> OnboardingRoute(
            // 온보딩 완료는 DeviceSettings.onboarded 로 바뀌어 셸이 알아서 바뀐다.
            onFinished = {},
            onOpenRestore = { overlay = Overlay.RESTORE },
            onOpenPairing = { overlay = Overlay.PAIRING },
            connect = { onAdvance -> InviteConnect(onAdvance) },
        )
        Overlay.PAIRING -> PairingRoute(onDone = { overlay = null }, onBack = { overlay = null })
        Overlay.RECEIVE -> PairingRoute(onDone = { overlay = null }, onBack = { overlay = Overlay.RESTORE }, mode = com.sadturtleman.flowershoes.feature.pairing.PairingMode.RECEIVE)
        Overlay.RESTORE -> {
            val onboarding = koinViewModel<com.sadturtleman.flowershoes.feature.onboarding.OnboardingViewModel>()
            RestoreRoute(
                onBack = { overlay = null },
                onStartFresh = { overlay = null },
                // 복구 코드도 없으면 연인 폰에서 넘겨받는다(6 · 재연결).
                onRelink = { overlay = Overlay.RECEIVE },
                onOtherAccount = {
                    onboarding.onIntent(com.sadturtleman.flowershoes.feature.onboarding.OnboardingIntent.SignOut)
                    overlay = null
                },
                platform = getKoin().getOrNull<PlatformActions>() ?: NoPlatformActions,
            )
        }
        else -> ComingSoon(overlay!!.title(), onBack = { overlay = null })
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MainShell() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var settingsStart by remember { mutableStateOf(com.sadturtleman.flowershoes.feature.settings.SettingsPage.MAIN) }
    // 휴가 관리를 연 곳(설정 › 군 정보면 닫을 때 그리로).
    var leaveFromSettings by remember { mutableStateOf(false) }
    // 편지 쓰기 · 편지함 · 그림만 보내기가 같은 편지 상태를 본다(Activity 범위 ViewModel 하나).
    val letters = koinViewModel<LetterViewModel>()
    val calendar = koinViewModel<com.sadturtleman.flowershoes.feature.calendar.CalendarViewModel>()
    val imageSaver = rememberImageSaver()
    val imageShare = rememberImageShare()
    val c = KkTheme.colors
    val letterOpen = letters.state.collectAsState().value.open != null
    val memory = koinViewModel<com.sadturtleman.flowershoes.feature.memory.MemoryViewModel>()
    val memoryDetail = memory.state.collectAsState().value.detail != null

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
            overlay == Overlay.LEAVE && leaveFromSettings -> overlay = Overlay.SETTINGS
            overlay != null -> overlay = null
            letterOpen -> letters.onIntent(LetterIntent.Close)
            else -> tab = Tab.HOME
        }
    }

    when (overlay) {
        Overlay.COMPOSE, Overlay.COMPOSE_SCHEDULED -> {
            LetterComposeRoute(
                onAddDrawing = { overlay = Overlay.DRAW_FOR_LETTER },
                onClose = { overlay = null },
                scheduleFirst = overlay == Overlay.COMPOSE_SCHEDULED,
                viewModel = letters,
            )
            return
        }
        Overlay.DRAW_FOR_LETTER -> {
            DrawingEditorRoute(
                photoPicker = { rememberPhotoPicker(it) },
                onSaved = {
                    letters.onIntent(LetterIntent.AddDrawing(it))
                    overlay = Overlay.COMPOSE
                },
                onClose = { overlay = Overlay.COMPOSE },
            )
            return
        }
        Overlay.DRAW_ONLY -> {
            // 그림만 보내기 = 글 없는 편지 한 통(그림 + 한 줄).
            val saver = rememberImageSaver()
            DrawingEditorRoute(
                onSaved = {},
                onClose = { overlay = null },
                forLetter = false,
                photoPicker = { rememberPhotoPicker(it) },
                saveImage = saver,
                onSend = { meta, caption, at ->
                    letters.onIntent(LetterIntent.SendDrawing(meta, caption, at))
                    overlay = null
                    tab = Tab.LETTER
                },
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
        Overlay.RECEIVE -> {
            PairingRoute(onDone = { overlay = null }, onBack = { overlay = null }, mode = com.sadturtleman.flowershoes.feature.pairing.PairingMode.RECEIVE)
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
                onBack = { overlay = null; settingsStart = com.sadturtleman.flowershoes.feature.settings.SettingsPage.MAIN },
                startAt = settingsStart,
                photoPicker = { rememberPhotoPicker(it) },
                onOpenLeave = {
                    leaveFromSettings = true
                    settingsStart = com.sadturtleman.flowershoes.feature.settings.SettingsPage.MILITARY
                    overlay = Overlay.LEAVE
                },
            )
            return
        }
        Overlay.LEAVE -> {
            com.sadturtleman.flowershoes.feature.calendar.LeaveRoute(onBack = {
                overlay = if (leaveFromSettings) Overlay.SETTINGS else null
                leaveFromSettings = false
            })
            return
        }
        null -> Unit
    }
    val scheduler = getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.sync.SyncScheduler>()
    val restoreState = scheduler?.restoreState?.collectAsState()?.value
    val settingsVm = koinViewModel<com.sadturtleman.flowershoes.feature.settings.SettingsViewModel>()
    val settingsState by settingsVm.state.collectAsState()
    // 상대가 연결을 해제했다(Figma 6 · 연결 해제 / 상대 폰): 사본을 어떻게 할지 고를 때까지 다른 화면을 막는다.
    if (scheduler?.partnerUnlinked?.collectAsState()?.value == true) {
        com.sadturtleman.flowershoes.feature.settings.PartnerUnlinkedScreen(settingsState.partnerName) { after ->
            settingsVm.afterPartnerUnlinked(after)
            scheduler.partnerUnlinkHandled()
        }
        return
    }
    // 상대가 새 폰으로 바꿨다(Figma 6 · 기기 교체 · 상대 폰 알림): 자동 확인 뒤 한 번.
    val rotation = scheduler?.approvedRotations?.collectAsState()?.value?.firstOrNull()
    if (rotation != null) {
        val at = kotlin.time.Instant.fromEpochMilliseconds(rotation.createdAtMs)
        com.sadturtleman.flowershoes.feature.settings.PartnerDeviceChangedScreen(
            partnerName = settingsState.partnerName,
            deviceName = rotation.deviceName,
            at = com.sadturtleman.flowershoes.feature.settings.relativeTime(at.toEpochMilliseconds()),
            providerLabel = rotation.provider.takeIf { it.isNotBlank() }?.let(settingsVm::providerLabel),
            onAcknowledge = { scheduler.rotationSeen(rotation.id) },
            onUnknown = {
                scheduler.rotationSeen(rotation.id)
                settingsStart = com.sadturtleman.flowershoes.feature.settings.SettingsPage.UNLINK
                overlay = Overlay.SETTINGS
            },
        )
        return
    }
    // 복원 뒤 다시 시작했을 때 한 번: "거의 다 됐어요"(복원 · 지수 폰 확인 대기). 둘러보기를 누르면 배너만 남는다.
    var waitingSeen by rememberSaveable { mutableStateOf(false) }
    if (restoreState == com.sadturtleman.flowershoes.core.data.sync.RestoreRotationState.WaitingForPartner && !waitingSeen) {
        com.sadturtleman.flowershoes.feature.settings.RestoreWaitingScreen(partnerName = settingsState.partnerName, onBrowse = { waitingSeen = true })
        return
    }
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
                    onWriteLetter = { overlay = Overlay.COMPOSE },
                    onOpenLeave = { leaveFromSettings = false; overlay = Overlay.LEAVE },
                )
                Tab.CALENDAR -> CalendarRoute(viewModel = calendar)
                Tab.LETTER -> LetterInboxRoute(
                    onCreate = { creating = true },
                    onReply = {
                        letters.onIntent(LetterIntent.Close)
                        overlay = Overlay.COMPOSE
                    },
                    onReplyDrawing = {
                        letters.onIntent(LetterIntent.Close)
                        overlay = Overlay.DRAW_ONLY
                    },
                    viewModel = letters,
                )
                Tab.MEMORY -> MemoryRoute(
                    onOpenLetter = {
                        letters.onIntent(LetterIntent.Open(it))
                        tab = Tab.LETTER
                    },
                    // 만남은 캘린더 일정(휴가 · 외박 · 면회)으로 남긴다.
                    onRecordMeeting = {
                        calendar.onIntent(com.sadturtleman.flowershoes.feature.calendar.CalendarIntent.NewEvent(kotlin.time.Clock.System.today()))
                        tab = Tab.CALENDAR
                    },
                    saveImage = imageSaver,
                    shareImage = imageShare,
                    viewModel = memory,
                )
            }
        }
        // 받은 편지 · 그림 · 추억 그림 상세는 탭 바 없이 전체 화면(Figma).
        val reading = (tab == Tab.LETTER && letterOpen) || (tab == Tab.MEMORY && memoryDetail)
        if (!reading) BottomBar(tab, onSelect = { tab = it })
    }
    if (creating) {
        com.sadturtleman.flowershoes.feature.letter.LetterCreateSheet(
            onDismiss = { creating = false },
            onDrawOnly = { overlay = Overlay.DRAW_ONLY },
            onCompose = { overlay = Overlay.COMPOSE },
            onScheduled = { overlay = Overlay.COMPOSE_SCHEDULED },
        )
    }
}

/** 하단 탭 바(Figma Tab Bar): 홈 · 캘린더 · 편지 · 추억. */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    com.sadturtleman.flowershoes.core.designsystem.KkTabBar(
        tabs = Tab.entries.map { com.sadturtleman.flowershoes.core.designsystem.KkTab(it.label, it.icon) },
        selected = current.ordinal,
        onSelect = { onSelect(Tab.entries[it]) },
    )
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
