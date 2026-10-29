package com.sadturtleman.flowershoes.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.sadturtleman.flowershoes.core.designsystem.KkPageFlip
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
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

/** 하단 탭. 화면 이동은 여기서만 정한다 — feature 끼리는 서로 모른다. */
enum class Tab(val label: String, val icon: com.sadturtleman.flowershoes.core.designsystem.KkIcons) {
    HOME("홈", com.sadturtleman.flowershoes.core.designsystem.KkIcons.Home),
    CALENDAR("캘린더", com.sadturtleman.flowershoes.core.designsystem.KkIcons.Calendar),
    LETTER("편지", com.sadturtleman.flowershoes.core.designsystem.KkIcons.Mail),
    MEMORY("추억", com.sadturtleman.flowershoes.core.designsystem.KkIcons.Album),
}

/** 탭 밖으로 덮어 띄우는 화면. */
private enum class Overlay { PAIRING, HAND_OVER, RECEIVE, COMPOSE, DRAW_FOR_LETTER, DRAW, DRAFTS, SETTINGS, LEAVE }

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
    KkotsinTheme(theme = current?.theme ?: AppTheme.GOMSIN, dark = dark) {
        CompositionLocalProvider(
            com.sadturtleman.flowershoes.core.designsystem.LocalKkShare provides share,
            com.sadturtleman.flowershoes.feature.pairing.LocalInviteShare provides rememberInviteShare(),
            com.sadturtleman.flowershoes.core.drawingui.LocalDrawingPhotoLoader provides photoLoader,
        ) {
            when {
                current == null -> Box(Modifier.fillMaxSize().background(KkTheme.colors.canvas))
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
    val settings = koinInject<DeviceSettings>()
    val scope = rememberCoroutineScope()
    // 온보딩 전에 초대 링크를 눌렀다: 로그인 · 역할 · 테마를 마친 뒤 시작 방법 대신 바로 수락 화면.
    val link by DeepLinks.next.collectAsState()
    var inviteToken by rememberSaveable { mutableStateOf<String?>(null) }
    val closePairing = {
        overlay = null
        inviteToken = null
    }
    // 연결 화면에서 뒤로 가면 온보딩으로.
    BackHandler(enabled = overlay != null) { closePairing() }
    KkPageFlip(overlay, pageKey = { it ?: "onboarding" }, isForward = ::overlayForward) { shown ->
    when (shown) {
        null -> OnboardingRoute(
            // 온보딩 완료는 DeviceSettings.onboarded 로 바뀌어 셸이 알아서 바뀐다.
            onFinished = {},
            onOpenPairing = {
                (link as? DeepLink.Invite)?.let {
                    inviteToken = it.token
                    DeepLinks.consumed()
                }
                overlay = Overlay.PAIRING
            },
            invited = link is DeepLink.Invite,
        )
        // 초대를 보냈거나(바로 홈) 받은 초대로 연결됐다 → 온보딩 끝.
        Overlay.PAIRING -> PairingRoute(onDone = { scope.launch { settings.setOnboarded() } }, onBack = closePairing, invite = inviteToken)
        else -> ComingSoon(shown!!.title(), onBack = { overlay = null })
    }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MainShell() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    // 초대 링크로 들어왔으면 그 토큰(연결 화면이 받는 쪽으로 열린다).
    var inviteToken by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsStart by remember { mutableStateOf(com.sadturtleman.flowershoes.feature.settings.SettingsPage.MAIN) }
    // 편지 쓰기 · 편지함이 같은 편지 상태를 본다(Activity 범위 ViewModel 하나).
    val letters = koinViewModel<LetterViewModel>()
    val calendar = koinViewModel<com.sadturtleman.flowershoes.feature.calendar.CalendarViewModel>()
    val imageSaver = rememberImageSaver()
    val c = KkTheme.colors
    val letterOpen = letters.state.collectAsState().value.open != null
    val memory = koinViewModel<com.sadturtleman.flowershoes.feature.memory.MemoryViewModel>()
    val memoryDetail = memory.state.collectAsState().value.stack.isNotEmpty()
    val fileShare = rememberFileShare()
    // 편지 쓰기를 새로 연다 — 쓰던 편지가 있으면 편지 쓰기 화면이 "이어 쓸까요?" 를 묻는다.
    val writeLetter = {
        letters.onIntent(LetterIntent.NewCompose)
        overlay = Overlay.COMPOSE
    }
    val bookStore = getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.BookStore>()
    val bookPromo = getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.BookPromo>()
    val promoScope = androidx.compose.runtime.rememberCoroutineScope()
    // 그날의 한 줄 쓰기(다음 날 알림 · 홈 카드 · 일정 상세): 추억 탭 위에 쓰기 화면을 연다.
    val openDayLine = { eventId: String ->
        overlay = null
        letters.onIntent(LetterIntent.Close)
        tab = Tab.MEMORY
        memory.onIntent(com.sadturtleman.flowershoes.feature.memory.MemoryIntent.Open(com.sadturtleman.flowershoes.feature.memory.MemoryPage.DayLine(eventId)))
    }

    // 이 폰 기종을 내 정보에 싣는다(연인의 설정 › 연결 정보).
    val koin = getKoin()
    // 산 것(꽃신 평생 · 추억 책)을 내 정보에 실어 연결된 상대도 함께 쓰게 한다.
    LaunchedEffect(Unit) { koin.getOrNull<com.sadturtleman.flowershoes.core.data.SharedPerks>()?.publish() }
    LaunchedEffect(Unit) {
        val device = koin.getOrNull<com.sadturtleman.flowershoes.feature.settings.PlatformActions>()?.deviceName.orEmpty()
        runCatching { koin.get<com.sadturtleman.flowershoes.core.data.ProfileRepository>().publishDevice(device) }
    }

    // 알림 · 위젯 · 초대 링크로 들어온 딥링크
    val link by DeepLinks.next.collectAsState()
    LaunchedEffect(link) {
        when (val l = link) {
            null -> return@LaunchedEffect
            DeepLink.Home -> {
                overlay = null
                letters.onIntent(LetterIntent.Close)
                tab = Tab.HOME
            }
            DeepLink.Inbox -> {
                overlay = null
                letters.onIntent(LetterIntent.Close)
                tab = Tab.LETTER
            }
            is DeepLink.Letter -> {
                overlay = null
                tab = Tab.LETTER
                letters.onIntent(LetterIntent.Open(l.id))
            }
            is DeepLink.Calendar -> {
                overlay = null
                tab = Tab.CALENDAR
            }
            is DeepLink.DayLine -> openDayLine(l.eventId)
            DeepLink.Pairing -> {
                inviteToken = null
                overlay = Overlay.PAIRING
            }
            is DeepLink.Invite -> {
                inviteToken = l.token
                overlay = Overlay.PAIRING
            }
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

    KkPageFlip(overlay, pageKey = { it ?: "main" }, isForward = ::overlayForward) { shown ->
    when (shown) {
        Overlay.COMPOSE -> {
            LetterComposeRoute(
                onAddDrawing = { overlay = Overlay.DRAW_FOR_LETTER },
                onClose = { overlay = null },
                photoPicker = { rememberPhotoPicker(it) },
                // 잠긴 편지지 → 설정 › 꽃신 평생(쓰던 편지는 화면을 나갈 때 저장된다).
                onOpenLifetime = {
                    settingsStart = com.sadturtleman.flowershoes.feature.settings.SettingsPage.LIFETIME
                    overlay = Overlay.SETTINGS
                },
                viewModel = letters,
            )
        }
        Overlay.DRAW_FOR_LETTER -> {
            DrawingEditorRoute(
                photoPicker = { rememberPhotoPicker(it) },
                saveImage = imageSaver,
                onSaved = {
                    letters.onIntent(LetterIntent.AddDrawing(it))
                    overlay = Overlay.COMPOSE
                },
                onClose = { overlay = Overlay.COMPOSE },
            )
        }
        // 그림만 보내기(편지 탭 쓰기 메뉴 › 그림 그리기 · 받은 그림 › 답장 그리기): 그림 한 장짜리 편지.
        Overlay.DRAW -> {
            DrawingEditorRoute(
                photoPicker = { rememberPhotoPicker(it) },
                saveImage = imageSaver,
                forLetter = false,
                onSaved = { overlay = null },
                onSend = { meta, caption, revealAt ->
                    letters.onIntent(LetterIntent.SendDrawing(meta, caption, revealAt))
                    overlay = null
                    tab = Tab.LETTER
                },
                onClose = { overlay = null },
            )
        }
        Overlay.DRAFTS -> {
            com.sadturtleman.flowershoes.feature.letter.LetterDraftsRoute(
                onBack = { overlay = null },
                onResume = {
                    letters.onIntent(LetterIntent.ResumeDraft(it))
                    overlay = Overlay.COMPOSE
                },
                viewModel = letters,
            )
        }
        Overlay.PAIRING -> {
            val closePairing = {
                overlay = null
                inviteToken = null
            }
            PairingRoute(onDone = closePairing, onBack = closePairing, invite = inviteToken)
        }
        Overlay.HAND_OVER -> {
            PairingRoute(onDone = { overlay = null }, onBack = { overlay = null }, handOver = true)
        }
        Overlay.RECEIVE -> {
            PairingRoute(onDone = { overlay = null }, onBack = { overlay = null }, mode = com.sadturtleman.flowershoes.feature.pairing.PairingMode.RECEIVE)
        }
        Overlay.SETTINGS -> {
            SettingsRoute(
                platform = getKoin().getOrNull<PlatformActions>() ?: NoPlatformActions,
                onOpenPairing = { overlay = Overlay.PAIRING },
                onHandOver = { overlay = Overlay.HAND_OVER },
                onBack = { overlay = null; settingsStart = com.sadturtleman.flowershoes.feature.settings.SettingsPage.MAIN },
                startAt = settingsStart,
                photoPicker = { rememberPhotoPicker(it) },
                shareFiles = fileShare,
            )
        }
        Overlay.LEAVE -> {
            // 휴가 관리는 캘린더 탭 아래 화면이다 — 닫으면 캘린더로. 쓴 휴가 한 줄은 그 일정 고치기를 연다.
            com.sadturtleman.flowershoes.feature.calendar.LeaveRoute(
                onBack = { overlay = null },
                onOpenEvent = { e ->
                    overlay = null
                    tab = Tab.CALENDAR
                    calendar.onIntent(com.sadturtleman.flowershoes.feature.calendar.CalendarIntent.Edit(e))
                },
            )
        }
        null -> {
        val scheduler = getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.sync.SyncScheduler>()
        val settingsVm = koinViewModel<com.sadturtleman.flowershoes.feature.settings.SettingsViewModel>()
        val settingsState by settingsVm.state.collectAsState()
        // 상대가 연결을 해제했다(Figma 6 · 연결 해제 / 상대 폰): 사본을 어떻게 할지 고를 때까지 다른 화면을 막는다.
        if (scheduler?.partnerUnlinked?.collectAsState()?.value == true) {
            com.sadturtleman.flowershoes.feature.settings.PartnerUnlinkedScreen(settingsState.partnerName) { after ->
                settingsVm.afterPartnerUnlinked(after)
                scheduler.partnerUnlinkHandled()
            }
            return@KkPageFlip
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
            return@KkPageFlip
        }
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                Crossfade(tab, animationSpec = tween(TAB_FADE_MS), label = "tab") { t ->
                when (t) {
                    Tab.HOME -> HomeRoute(
                        onOpenPairing = { overlay = Overlay.PAIRING },
                        onOpenCalendar = { tab = Tab.CALENDAR },
                        onOpenSettings = { overlay = Overlay.SETTINGS },
                        onWriteLetter = writeLetter,
                        onOpenLeave = { tab = Tab.CALENDAR; overlay = Overlay.LEAVE },
                        onOpenDayLine = openDayLine,
                    )
                    Tab.CALENDAR -> CalendarRoute(onOpenLeave = { overlay = Overlay.LEAVE }, onWriteDayLine = openDayLine, viewModel = calendar)
                    Tab.LETTER -> LetterInboxRoute(
                        onWriteLetter = writeLetter,
                        onDraw = { overlay = Overlay.DRAW },
                        onOpenDrafts = { overlay = Overlay.DRAFTS },
                        onReply = {
                            letters.onIntent(LetterIntent.Close)
                            writeLetter()
                        },
                        // 받은 그림 › 답장 그리기: 바로 그림판.
                        onReplyDrawing = {
                            letters.onIntent(LetterIntent.Close)
                            overlay = Overlay.DRAW
                        },
                        viewModel = letters,
                    )
                    Tab.MEMORY -> MemoryRoute(
                        onOpenLetter = {
                            letters.onIntent(LetterIntent.Open(it))
                            tab = Tab.LETTER
                        },
                        onWriteLetter = writeLetter,
                        photoPicker = { rememberPhotoPicker(it) },
                        book = com.sadturtleman.flowershoes.feature.memory.MemoryBookActions(
                            bookStore, ::newBookPdf, fileShare,
                            sharedOwned = getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.SharedPerks>()?.bookOn,
                        ),
                        viewModel = memory,
                    )
                }
                }
            }
            // 받은 편지 · 그림 · 추억 안쪽 화면(한 달 · 그날의 한 줄 · 책으로 보기)은 탭 바 없이 전체 화면(Figma).
            val reading = (tab == Tab.LETTER && letterOpen) || (tab == Tab.MEMORY && memoryDetail)
            if (!reading) BottomBar(tab, onSelect = { tab = it })
        }
        // 추억 책 권유(전역 D-30 · 전역일, 각자 폰 한 번씩): 홈 위 가운데 팝업. 홈 화면은 그대로 둔다.
        // 연결 모달 · 보내는 중 화면이 떠 있으면 권유 팝업은 띄우지 않는다(연결이 이긴다 — 두 덮개가 겹치지 않게).
        val linkWatch = koinViewModel<com.sadturtleman.flowershoes.feature.pairing.LinkWatchViewModel>()
        val linking = linkWatch.ui.collectAsState().value?.phase.let {
            it == com.sadturtleman.flowershoes.feature.pairing.AfterLinkUi.Phase.MODAL || it == com.sadturtleman.flowershoes.feature.pairing.AfterLinkUi.Phase.SENDING
        }
        if (tab == Tab.HOME && !linking) bookPromo?.let { promo ->
            val day by promo.day.collectAsState(initial = null)
            day?.let { d ->
                val memoryState by memory.state.collectAsState()
                BackHandler { promoScope.launch { promo.close(d) } }
                com.sadturtleman.flowershoes.feature.memory.BookPromoPopup(
                    memoryState.cover, memoryState.summary,
                    onOpenBook = {
                        promoScope.launch { promo.close(d) }
                        tab = Tab.MEMORY
                        memory.onIntent(com.sadturtleman.flowershoes.feature.memory.MemoryIntent.Open(com.sadturtleman.flowershoes.feature.memory.MemoryPage.Book))
                    },
                    onNever = { promoScope.launch { promo.never() } },
                    onClose = { promoScope.launch { promo.close(d) } },
                )
            }
        }
        // 보낸 초대가 수락됐다 → 연결 모달 · 편지 보내는 중 · 다 보냄 토스트.
        com.sadturtleman.flowershoes.feature.pairing.LinkWatcher(linkWatch)
        }
        }
    }
    }
}

/** 탭 사이는 짧은 페이드(자주 오가는 곳이라 책장 넘김은 무겁다). */
private const val TAB_FADE_MS = 180

/** 덮어 띄운 화면의 넘김 방향: 닫거나 연 곳으로 돌아가면 뒤로, 나머지는 앞으로. */
private fun overlayForward(from: Overlay?, to: Overlay?): Boolean = when {
    to == null -> false
    from == null -> true
    from == Overlay.DRAW_FOR_LETTER && to == Overlay.COMPOSE -> false
    else -> true
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
        Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().padding(KkSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        if (onBack != null) {
            Text("← 돌아가기", style = KkTheme.type.label, color = c.sub, modifier = Modifier.clickable(onClick = onBack))
        }
        Text(title, style = KkTheme.type.title, color = c.ink)
        Text("곧 만나요. 이 화면은 다음 단계에서 열려요.", style = KkTheme.type.body, color = c.sub)
    }
}

private fun Overlay.title() = "연인과 연결하기"
