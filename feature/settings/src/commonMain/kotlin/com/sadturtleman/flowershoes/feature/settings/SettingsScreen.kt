@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.sadturtleman.flowershoes.feature.settings

import com.sadturtleman.flowershoes.core.designsystem.KkPageFlip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import com.sadturtleman.flowershoes.core.designsystem.KkRadio
import com.sadturtleman.flowershoes.core.designsystem.KkBanner
import com.sadturtleman.flowershoes.core.designsystem.KkPaperTag
import com.sadturtleman.flowershoes.core.designsystem.KkPaperTagStyle
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.KkAdSlot
import com.sadturtleman.flowershoes.core.designsystem.KkAd
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.data.NotificationPrefs
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.data.sync.AfterUnlink
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkChoice
import com.sadturtleman.flowershoes.core.designsystem.KkDatePicker
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkListRow
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkRowTrailing
import com.sadturtleman.flowershoes.core.designsystem.KkSegmentedControl
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSheetOption
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTermsScreen
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkToast
import com.sadturtleman.flowershoes.core.designsystem.KkToastType
import com.sadturtleman.flowershoes.core.designsystem.KkServiceProgress
import kotlin.math.roundToInt
import com.sadturtleman.flowershoes.core.data.LIFETIME_PRICE
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.TermsDoc
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.military.ServiceRulesTable
import com.sadturtleman.flowershoes.core.military.expectedName
import com.sadturtleman.flowershoes.core.military.koreanName as rankName
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.koreanName
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * 플랫폼만 할 수 있는 일. Android 는 위젯 고정 요청, iOS 는 위젯을 직접 추가할 수 없어 안내만.
 */
interface PlatformActions {
    /** 홈 화면 위젯 종류. 지원하지 않으면 빈 목록. */
    val pinnableWidgets: List<Pair<String, String>> get() = emptyList()

    fun requestPinWidget(kind: String): Boolean = false

    /**
     * 기기 생체 · 암호 인증(설계 7-5). 되돌릴 수 없는 일(연결 해제 · 넘겨주기 · 탈퇴) 앞에서 부른다.
     * 화면 잠금이 없는 기기는 막을 방법이 없어 [DeviceAuth.NoLockScreen] 을 돌려주고, 화면이 경고를 띄운 뒤 진행한다.
     */
    suspend fun authenticate(reason: String): DeviceAuth = DeviceAuth.NoLockScreen

    /** 이 폰 이름(설정 › 로그인 계정 · 상대 폰의 "새 폰으로 바꿨어요"). */
    val deviceName: String get() = ""

    /** 앱 버전(설정 › 정보). */
    val appVersion: String get() = "1.0.0"

    /** OS 이름 · 버전(문의하기 › 기기 정보). */
    val osVersion: String get() = ""

    /** 알림 미리보기 숨기기: iOS 알림 확장이 상태 문구를 풀지 않게 App Group 에 표시한다. */
    fun setHidePreview(hide: Boolean) = Unit
}

enum class DeviceAuth { Passed, Failed, NoLockScreen }

object NoPlatformActions : PlatformActions

/** 설정 안의 화면들. 첫 화면은 [MAIN]. */
enum class SettingsPage {
    MAIN, LIFETIME, CONNECTION, MILITARY, ANNIVERSARY, THEME, DISPLAY_MODE, NOTIFICATIONS, WIDGETS, DEVICE, EXPORT,
    ACCOUNT, SIGN_IN_METHODS, DELETE_ACCOUNT, UNLINK, TERMS, LICENSES, INQUIRY, INQUIRY_HISTORY,
}

@Composable
fun SettingsRoute(
    platform: PlatformActions,
    onOpenPairing: () -> Unit,
    onBack: () -> Unit,
    startAt: SettingsPage = SettingsPage.MAIN,
    photoPicker: (@Composable (onPicked: (ByteArray) -> Unit) -> ((fromCamera: Boolean) -> Unit))? = null,
    /** 파일 공유 시트(내 기록 내보내기 ZIP). */
    shareFiles: ((List<Pair<String, ByteArray>>) -> Unit)? = null,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val inquiries = org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.InquiryTransport>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stack = remember { mutableStateListOf(startAt) }
    var notice by remember { mutableStateOf<SettingsViewModel.Notice?>(null) }
    LaunchedEffect(viewModel) { viewModel.notices.collect { notice = it } }
    LaunchedEffect(notice) { if (notice != null) { delay(2_500); notice = null } }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() }
    val open: (SettingsPage) -> Unit = { stack.add(it) }
    BackHandler(onBack = back)
    Box(Modifier.fillMaxSize()) {
        // 깊이가 같거나 깊어지면 앞으로 넘기고, 얕아지면 뒤로 넘긴다.
        KkPageFlip(stack.last() to stack.size, pageKey = { it.first }, isForward = { from, to -> to.second >= from.second }) { (page, _) ->
        when (page) {
            SettingsPage.MAIN -> MainPage(state, platform, open, back, onOpenPairing, viewModel)
            SettingsPage.NOTIFICATIONS -> NotificationsPage(state, platform, viewModel, back)
            SettingsPage.LIFETIME -> LifetimePage(state, viewModel, back)
            SettingsPage.CONNECTION -> ConnectionPage(state, back, onOpenPairing, open)
            SettingsPage.MILITARY -> MilitaryPage(state, viewModel, back)
            SettingsPage.ANNIVERSARY -> AnniversaryPage(state, viewModel, back)
            SettingsPage.THEME -> ThemePage(state, viewModel, back)
            SettingsPage.DISPLAY_MODE -> DisplayModePage(state, viewModel, back)
            SettingsPage.WIDGETS -> WidgetsPage(state, platform, back)
            SettingsPage.DEVICE -> DevicePage(state, platform, viewModel, back, open)
            SettingsPage.EXPORT -> ExportPage(shareFiles, viewModel, back)
            SettingsPage.ACCOUNT -> AccountPage(state, platform, viewModel, back, open)
            SettingsPage.SIGN_IN_METHODS -> SignInMethodsPage(state, viewModel, back)
            SettingsPage.DELETE_ACCOUNT -> DeleteAccountPage(state, platform, viewModel, back, open)
            SettingsPage.UNLINK -> UnlinkPage(state, platform, viewModel, shareFiles, back) { onBack() }
            SettingsPage.TERMS -> TermsPage(state, viewModel, back, open)
            SettingsPage.LICENSES -> LicensesPage(back)
            SettingsPage.INQUIRY -> InquiryPage(
                inquiries, platform, photoPicker,
                onHistory = { open(SettingsPage.INQUIRY_HISTORY) },
                onBack = back,
                onSent = { message, sent ->
                    viewModel.notice(message)
                    if (sent) stack[stack.lastIndex] = SettingsPage.INQUIRY_HISTORY
                },
            )
            SettingsPage.INQUIRY_HISTORY -> InquiryHistoryPage(inquiries, back, onNew = {
                // 문의하기 › 내역에서 왔으면 돌아가고, 보낸 뒤 바로 온 내역이면 문의하기로 바꾼다.
                if (stack.getOrNull(stack.lastIndex - 1) == SettingsPage.INQUIRY) back() else stack[stack.lastIndex] = SettingsPage.INQUIRY
            })
        }
        }
        notice?.let {
            KkToast(
                it.message, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
                type = if (it.error) KkToastType.Error else KkToastType.Default,
            )
        }
    }
}

// ── 공용 ──────────────────────────────────────────────────────────────────

/** 하위 화면 틀: 뒤로 가기 상단 바 + 스크롤 내용(좌우 20, 묶음 사이 20). */
@Composable
private fun Page(
    title: String,
    onBack: () -> Unit,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    leading: KkTopBarLeading = KkTopBarLeading.Back,
    trailingText: String? = null,
    onTrailing: () -> Unit = {},
    trailingEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar(title, onLeading = onBack, leading = leading, trailingText = trailingText, onTrailing = onTrailing, trailingEnabled = trailingEnabled)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.lg, bottom = KkSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.xl),
            content = content,
        )
        if (bottom != null) Column(Modifier.padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg), verticalArrangement = Arrangement.spacedBy(KkSpacing.sm), content = bottom)
    }
}

/** 묶음: 이름(Label/M) + 흰 카드 안의 줄들(사이 구분선). */
@Composable
private fun Group(label: String?, content: @Composable ColumnScope.() -> Unit) {
    val c = KkTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        if (label != null) Text(label, style = KkTheme.type.label, color = c.sub)
        val shape = RoundedCornerShape(KkRadius.lg)
        Column(Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface), content = content)
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(KkTheme.colors.line))

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) =
    KkListRow(label, trailing = KkRowTrailing.Switch, switchOn = on, onClick = { onChange(!on) })


/** "지수와" · "민준과". */
private fun String.withAnd(): String {
    val last = lastOrNull() ?: return this
    val batchim = last in '가'..'힣' && (last - '가') % 28 != 0
    return this + if (batchim) "과" else "와"
}

/** "민준이" · "지수가". */
internal fun String.withSubject(): String {
    val last = lastOrNull() ?: return this
    val batchim = last in '가'..'힣' && (last - '가') % 28 != 0
    return this + if (batchim) "이" else "가"
}

private fun hm(minute: Int) = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

// ── 설정(첫 화면) ─────────────────────────────────────────────────────────

/**
 * 설정(Figma 8 · 설정 `376:4461`): 프로필 · 우리 · 꾸미기 · 알림 · 데이터와 보안 · 도움 · 계정 + 광고 칸.
 * › 는 Figma List Group 규칙대로: 누르면 무언가 열리는 줄(이름 · 함께한 날 시트 포함)에만, 위험 줄(회원 탈퇴)과 값 줄엔 없다.
 */
@Composable
private fun MainPage(
    state: SettingsState,
    platform: PlatformActions,
    open: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    onOpenPairing: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val koin = org.koin.compose.getKoin()
    val owned by (koin.getOrNull<com.sadturtleman.flowershoes.core.data.LifetimeStore>()?.owned
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
    val shared by (koin.getOrNull<com.sadturtleman.flowershoes.core.data.SharedPerks>()?.lifetimeOn
        ?: remember { kotlinx.coroutines.flow.flowOf(false) }).collectAsStateWithLifecycle(false)
    SettingsMainScreen(
        state, platform.appVersion, viewModel.providerLabel(state.provider), lifetimeLabel(owned, shared, LIFETIME_PRICE),
        open, onBack, onOpenPairing, viewModel::setNickname, viewModel::setAnniversary,
    )
}

/** 설정 첫 화면 그리기(ViewModel · Koin 없이 — 견본 그림도 이것을 그린다). */
@Composable
internal fun SettingsMainScreen(
    state: SettingsState,
    appVersion: String,
    providerLabel: String,
    lifetime: String,
    open: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    onOpenPairing: () -> Unit,
    onNickname: (String) -> Unit,
    onAnniversary: (LocalDate) -> Unit,
) {
    val c = KkTheme.colors
    var sheet by remember { mutableStateOf<String?>(null) }
    Page("설정", onBack) {
        Group("프로필") {
            KkListRow("이름", value = state.myName, onClick = { sheet = "name" })
            Divider()
            KkListRow("함께한 날", value = daysTogetherLabel(state.daysTogether), onClick = { sheet = "together" })
        }
        Group("우리") {
            KkListRow("연결 정보", value = if (state.linked) state.partnerName else "연결 전", onClick = { open(SettingsPage.CONNECTION) })
            Divider()
            KkListRow(
                "군 정보",
                value = state.today?.let { militaryLabel(state.service, state.timeline, it) }.orEmpty(),
                onClick = { open(SettingsPage.MILITARY) },
            )
            Divider()
            KkListRow(
                "기념일", value = anniversaryCountLabel(state.anniversary != null, state.customAnniversaries.size),
                onClick = { open(SettingsPage.ANNIVERSARY) },
            )
        }
        Group("꾸미기") {
            KkListRow("꽃신 평생", value = lifetime, onClick = { open(SettingsPage.LIFETIME) })
            Divider()
            KkListRow("테마", value = state.theme.koreanName, onClick = { open(SettingsPage.THEME) })
            Divider()
            KkListRow("화면 모드", value = modeLabel(state.displayMode), onClick = { open(SettingsPage.DISPLAY_MODE) })
            // Figma 메인에는 없지만 위젯 안내 · 그림 위젯 프레임(평생 꾸미기)으로 가는 길이 여기뿐이라 남긴다.
            Divider()
            KkListRow("위젯", onClick = { open(SettingsPage.WIDGETS) })
        }
        Group("알림") {
            KkListRow("알림", value = if (state.notify.notifyMasterOn) "켜짐" else "꺼짐", onClick = { open(SettingsPage.NOTIFICATIONS) })
        }
        Group("데이터와 보안") {
            KkListRow("기기 바꾸기 · 복구", onClick = { open(SettingsPage.DEVICE) })
        }
        Group("도움") {
            KkListRow("문의하기", onClick = { open(SettingsPage.INQUIRY) })
            Divider()
            KkListRow("약관과 라이선스", onClick = { open(SettingsPage.TERMS) })
            Divider()
            KkListRow("버전", value = appVersion, trailing = KkRowTrailing.None)
        }
        Group("계정") {
            KkListRow("로그인", value = providerLabel, onClick = { open(SettingsPage.ACCOUNT) })
            Divider()
            if (state.linked) {
                KkListRow("연결 해제", onClick = { open(SettingsPage.UNLINK) })
            } else {
                KkListRow("연인과 연결하기", onClick = onOpenPairing)
            }
            Divider()
            KkListRow("회원 탈퇴", labelColor = c.danger, trailing = KkRowTrailing.None, onClick = { open(SettingsPage.DELETE_ACCOUNT) })
        }
        KkAd(KkAdSlot.SETTINGS)
    }
    when (sheet) {
        "name" -> {
            var name by remember { mutableStateOf(if (state.myName == "나") "" else state.myName) }
            KkModalSheet({ sheet = null }) {
                KkSheetHeader("이름")
                KkInput(name, { name = it.take(NICKNAME_MAX) }, label = "이름", helper = "${name.length} / $NICKNAME_MAX")
                KkPrimaryButton("저장", { onNickname(name); sheet = null }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
            }
        }
        "together" -> {
            // 함께한 날의 1일째 = 커플 기념일 일정(anniversary-couple). 기념일 화면과 같은 값이다.
            var pick by remember { mutableStateOf(state.anniversary ?: state.linkedSince ?: Clock.System.now().toLocalDateTime(SEOUL).date) }
            KkModalSheet({ sheet = null }) {
                KkSheetHeader("함께한 날")
                KkDatePicker(pick, { pick = it })
                KkPrimaryButton("선택 완료", { onAnniversary(pick); sheet = null }, Modifier.fillMaxWidth())
            }
        }
    }
}

// ── 알림 ─────────────────────────────────────────────────────────────────

/** 알림(Figma 8 · 알림 `378:4951`): 받을 종류 큰 스위치 · 3개월 기록 알림, 꺼지면 배너, 혜택 소식(광고성 정보 수신). */
@Composable
private fun NotificationsPage(state: SettingsState, platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val n = state.notify
    Page("알림", onBack) {
        Group(null) {
            SwitchRow("편지 · 보고싶어 · 일정 알림", n.notifyMasterOn) { viewModel.setNotifyMaster(it, platform) }
            Divider()
            SwitchRow("3개월마다 기록 받아 두기", n.exportReminder) { viewModel.setExportReminder(it, platform) }
        }
        // 설명 문장은 지운다(#71 결정, design.md 글 규칙).
        if (!n.notifyMasterOn) KkBanner("알림이 꺼져 있어요")
        Group("혜택 소식") {
            SwitchRow("광고성 정보 수신", state.marketing) { viewModel.setMarketing(it) }
        }
    }
}

// ── 꽃신 평생 ─────────────────────────────────────────────────────────────

/**
 * 꽃신 평생(Figma 8 · 꽃신 평생 `377:4834`): 한 번 결제(Android: RevenueCat). 구매 전 · 구매 중 · 구매 완료 · 구매 복원 · 실패.
 * 결제가 없는 빌드(iOS 아직)에서는 "앱 출시와 함께" 안내만. 실패 문구는 스토어 쪽(돈이 나갔는지 아는 곳)이 정해 오류 토스트로 띄운다.
 */
@Composable
private fun LifetimePage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val c = KkTheme.colors
    val scope = rememberCoroutineScope()
    val store = org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.LifetimeStore>()
    val owned by (store?.owned ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
    var storePrice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf(false) }
    LaunchedEffect(store) { storePrice = store?.price() }
    fun run(buy: Boolean, action: suspend (com.sadturtleman.flowershoes.core.data.LifetimeStore) -> com.sadturtleman.flowershoes.core.data.PurchaseOutcome) {
        if (store == null) { viewModel.notice("결제는 앱 출시와 함께 열려요"); return }
        if (busy) return
        busy = true
        buying = buy
        scope.launch {
            when (val r = action(store)) {
                // 구매 완료 화면으로 바뀐다. 복원이면 되찾았다고 한 번 알린다.
                com.sadturtleman.flowershoes.core.data.PurchaseOutcome.Purchased -> if (!buy) viewModel.notice("꽃신 평생을 되찾았어요")
                com.sadturtleman.flowershoes.core.data.PurchaseOutcome.NothingToRestore -> viewModel.notice("이 계정으로 산 기록이 없어요")
                com.sadturtleman.flowershoes.core.data.PurchaseOutcome.Cancelled -> Unit
                is com.sadturtleman.flowershoes.core.data.PurchaseOutcome.Failed -> viewModel.notice(r.message, error = true)
            }
            busy = false
            buying = false
        }
    }
    val price = storePrice ?: LIFETIME_PRICE
    val partner = if (state.linked) state.partnerName else "연인"
    Box(Modifier.fillMaxSize()) {
    Page("꽃신 평생", onBack, leading = KkTopBarLeading.Close, bottom = {
        if (owned) {
            KkPrimaryButton("좋아요", onBack, Modifier.fillMaxWidth())
        } else {
            KkPrimaryButton("${price}으로 평생 쓰기", { run(buy = true) { it.buy() } }, Modifier.fillMaxWidth(), enabled = !busy)
            KkButton(
                "구매 복원", { run(buy = false) { it.restore() } }, Modifier.align(Alignment.CenterHorizontally),
                style = KkButtonStyle.Secondary, size = com.sadturtleman.flowershoes.core.designsystem.KkButtonSize.M, enabled = !busy,
            )
        }
    }) {
        Box(Modifier.fillMaxWidth().padding(top = KkSpacing.lg), contentAlignment = Alignment.Center) {
            // 보여 주기만 하는 종이 태그라 살짝 기울인다(design.md).
            if (owned) {
                KkPaperTag("꽃신 평생", "평생 꽃신", valueStyle = KkPaperTagStyle.Hand, tilt = -3f)
            } else {
                KkPaperTag("한 번만 내면 평생", price, tilt = 2f)
            }
        }
        if (owned) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                Text("이제 평생 꽃신이에요", style = KkTheme.type.handL, color = c.ink)
                if (state.linked) Text("$partner 폰에도 함께 적용됐어요", style = KkTheme.type.body, color = c.sub)
            }
            return@Page
        }
        Text("한 번 사면 평생, 둘이 함께", style = KkTheme.type.handL, color = c.ink, modifier = Modifier.align(Alignment.CenterHorizontally))
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
            lifetimeBenefits(partner).forEach { b ->
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    KkIcon(KkIcons.Check, tint = c.accent, size = 16.dp)
                    Text(b, style = KkTheme.type.body, color = c.ink)
                }
            }
        }
        Text("구독 아니에요 · 한 번만 결제해요", style = KkTheme.type.caption, color = c.sub, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    // 구매 중(Figma 8 · 꽃신 평생 · 구매 중): 스토어 결제 창이 닫히고 확인을 기다리는 동안 화면을 막는다.
    if (buying) {
        Box(
            Modifier.fillMaxSize().background(c.ink.copy(alpha = 0.35f)).clickable(enabled = true, onClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(KkRadius.xl)
            Column(
                Modifier.kkElevation(KkElevation.High, shape).clip(shape).background(c.surface).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
            ) {
                com.sadturtleman.flowershoes.core.designsystem.KkSpinner(c.accent, 28.dp)
                Text("결제를 확인하고 있어요", style = KkTheme.type.labelBold, color = c.ink)
            }
        }
    }
    }
}

// ── 연결 정보 ─────────────────────────────────────────────────────────────

/** 연결 정보(Figma 최종 정리 › 설정 · 연결 정보): 연인 카드 · 연결한 날 · 연인 폰 + 연결 해제. */
@Composable
private fun ConnectionPage(
    state: SettingsState,
    onBack: () -> Unit,
    onOpenPairing: () -> Unit,
    open: (SettingsPage) -> Unit,
) {
    val c = KkTheme.colors
    Page("연결 정보", onBack) {
        if (!state.linked) {
            Group(null) { KkListRow("연인", value = "연결 전", trailing = KkRowTrailing.None) }
            KkPrimaryButton("연인과 연결하기", onOpenPairing, Modifier.fillMaxWidth())
            return@Page
        }
        val shape = RoundedCornerShape(KkRadius.lg)
        val partnerRole = if (state.role == Role.SOLDIER) "곰신" else "군인"
        Row(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.bar), contentAlignment = Alignment.Center) {
                Text(state.partnerName.take(1), style = KkTheme.type.labelBold, color = c.accent)
            }
            Text("${state.partnerName} · $partnerRole", style = KkTheme.type.labelBold, color = c.ink)
        }
        Group(null) {
            KkListRow("연결한 날", value = state.linkedSince?.dotted() ?: "-", trailing = KkRowTrailing.None)
            Divider()
            KkListRow("${state.partnerName} 폰", value = state.partnerDevice ?: "-", trailing = KkRowTrailing.None)
        }
        Group(null) {
            KkListRow("연결 해제", labelColor = c.danger, onClick = { open(SettingsPage.UNLINK) })
        }
    }
}

// ── 군 정보 ───────────────────────────────────────────────────────────────

/**
 * 군 정보(Figma 8 · 군 정보 `379:4970`): 기본(진행 막대 · 군종 · 계급 · 입대일 · 전역일 · 진급일) → "수정" → 수정 중(군종 · 입대일 · 전역일 + 저장).
 * 전역일은 계산한 날과 다르게 고르면 사용자 수정값(조기 전역 · 연장)으로 남는다 — 따로 켜는 스위치는 없다.
 * 진급일 입력은 출시 뒤(#32: 진급 누락 입력은 출시 뒤, 지금은 "예정"만).
 */
@Composable
private fun MilitaryPage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val s = state.service
    val t = state.timeline
    // 아직 없으면 수정 중으로 보인다(곰신이 온보딩에서 "나중에 넣을게요"를 고른 경우).
    var editing by remember { mutableStateOf(false) }
    if (editing || s == null || t == null) {
        MilitaryEdit(s, onBack = { if (s != null) editing = false else onBack() }) { branch, enlist, discharge ->
            viewModel.saveMilitary(branch, enlist, discharge)
            editing = false
        }
        return
    }
    val c = KkTheme.colors
    val today = state.today ?: s.enlistDate
    Page("군 정보", onBack, trailingText = "수정", onTrailing = { editing = true }) {
        val progress = t.progress(today)
        KkServiceProgress(
            progress.toFloat(), "입대 ${s.enlistDate.dotted()}", "전역 ${t.dischargeDate.dotted()}",
            "${(progress * 1000).roundToInt() / 10.0}%", style = com.sadturtleman.flowershoes.core.designsystem.KkServiceProgressStyle.Path,
        )
        Group(null) {
            KkListRow("군종", value = s.branch.koreanName, trailing = KkRowTrailing.None)
            t.rankAt(today)?.let {
                Divider()
                KkListRow("계급", value = it.rank.expectedName, trailing = KkRowTrailing.None)
            }
            Divider()
            KkListRow("입대일", value = s.enlistDate.dotted(), trailing = KkRowTrailing.None)
            Divider()
            KkListRow("전역일", value = "${t.dischargeDate.dotted()} · ${ddayLabel(today, t.dischargeDate)}", trailing = KkRowTrailing.None)
        }
        // 진급일: 이병 다음 계급부터.
        val promotions = t.ranks.drop(1)
        if (promotions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                Text("진급일", style = KkTheme.type.labelBold, color = c.ink)
                Group(null) {
                    promotions.forEachIndexed { i, step ->
                        if (i > 0) Divider()
                        KkListRow(step.rank.rankName, value = "${step.from.dotted()} · ${ddayLabel(today, step.from)}", trailing = KkRowTrailing.None)
                    }
                }
            }
        }
    }
}

/** 군 정보 · 수정 중: 군종(시트) · 입대일 · 전역일(달력 피커). 저장은 위 "저장"과 아래 "저장하기" 둘 다. */
@Composable
private fun MilitaryEdit(
    service: com.sadturtleman.flowershoes.core.model.Service?,
    onBack: () -> Unit,
    onSave: (Branch, LocalDate, LocalDate?) -> Unit,
) {
    var branch by remember { mutableStateOf(service?.branch ?: Branch.ARMY) }
    var enlist by remember { mutableStateOf(service?.enlistDate) }
    // null 이면 계산한 날을 따른다(군종 · 입대일을 바꾸면 같이 바뀐다).
    var discharge by remember { mutableStateOf(service?.dischargeOverride) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val shown = discharge ?: enlist?.let { computedDischarge(branch, it) }
    val months = ServiceRulesTable.BUNDLED.ruleFor(branch, enlist ?: Clock.System.now().toLocalDateTime(SEOUL).date).serviceMonths
    val save = { enlist?.let { onSave(branch, it, discharge) }; Unit }
    Page(
        "군 정보", onBack, trailingText = "저장", onTrailing = save, trailingEnabled = enlist != null,
        bottom = { KkPrimaryButton("저장하기", save, Modifier.fillMaxWidth(), enabled = enlist != null) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.lg)) {
            KkInput(branch.koreanName, {}, label = "군종", onClick = { sheet = "branch" })
            KkInput(enlist?.dotted().orEmpty(), {}, label = "입대일", onClick = { sheet = "enlist" })
            if (enlist != null) {
                KkInput(shown?.dotted().orEmpty(), {}, label = "전역일", helper = "${branch.koreanName} ${months}개월 기준", onClick = { sheet = "discharge" })
            }
        }
    }
    when (sheet) {
        "branch" -> KkModalSheet({ sheet = null }) {
            KkSheetHeader("군종")
            Branch.entries.forEach { b ->
                val m = ServiceRulesTable.BUNDLED.ruleFor(b, enlist ?: Clock.System.now().toLocalDateTime(SEOUL).date).serviceMonths
                // 군종 · 입대일이 바뀌면 전역일은 다시 계산한 날을 따른다.
                KkSheetOption(b.koreanName, branch == b, { if (branch != b) discharge = null; branch = b; sheet = null }, meta = "${m}개월")
            }
        }
        "enlist", "discharge" -> {
            val isEnlist = sheet == "enlist"
            var pick by remember(sheet) { mutableStateOf((if (isEnlist) enlist else shown) ?: Clock.System.now().toLocalDateTime(SEOUL).date) }
            KkModalSheet({ sheet = null }) {
                KkSheetHeader(if (isEnlist) "입대일" else "전역일")
                KkDatePicker(pick, { pick = it })
                KkPrimaryButton("선택 완료", {
                    if (isEnlist) {
                        if (enlist != pick) discharge = null
                        enlist = pick
                    } else {
                        // 계산한 날을 고르면 직접 고친 값을 지운다(조기 전역 · 연장을 되돌리는 길).
                        discharge = enlist?.let { dischargeOverrideFor(branch, it, pick) }
                    }
                    sheet = null
                }, Modifier.fillMaxWidth())
            }
        }
    }
}

// ── 기념일 ───────────────────────────────────────────────────────────────

/** 기념일 · 추가 시트에서 고치는 것. [id] 가 null 이면 새로 추가. */
private data class AnniversaryDraft(val id: String?, val title: String, val date: LocalDate?, val repeats: Boolean)

/**
 * 기념일(Figma 8 · 기념일 `379:5058`): 사귄 날 종이 태그(누르면 고르기) · 자동으로 챙겨요(100일 · 주년) · 직접 추가한 날 + "추가" 시트.
 * 직접 추가한 날은 기념일 일정이라 캘린더에도 보이고 매년 반복 알림을 받는다.
 */
@Composable
private fun AnniversaryPage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val c = KkTheme.colors
    var picking by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<AnniversaryDraft?>(null) }
    val today = state.today ?: Clock.System.now().toLocalDateTime(SEOUL).date
    Page("기념일", onBack, trailingText = "추가", onTrailing = { draft = AnniversaryDraft(null, "", null, true) }) {
        Box(
            Modifier.fillMaxWidth().padding(top = KkSpacing.md)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "사귄 날 고르기") { picking = true },
            contentAlignment = Alignment.Center,
        ) {
            // 보여 주기만 하는 종이 태그라 살짝 기울인다(design.md).
            KkPaperTag("사귄 날", state.anniversary?.dotted() ?: "정하기", valueStyle = KkPaperTagStyle.Hand, tilt = 2f)
        }
        state.anniversary?.let { a ->
            Column {
                Text("자동으로 챙겨요", style = KkTheme.type.labelBold, color = c.ink)
                anniversaryMilestones(a, today).forEach { m ->
                    KkListRow(m.label, value = "${m.date.dotted()} · ${ddayLabel(today, m.date)}", trailing = KkRowTrailing.None)
                    Divider()
                }
            }
        }
        if (state.customAnniversaries.isNotEmpty()) {
            Column {
                Text("직접 추가한 날", style = KkTheme.type.labelBold, color = c.ink)
                state.customAnniversaries.forEach { e ->
                    KkListRow(e.title.ifBlank { "기념일" }, value = customAnniversaryLabel(e.start.date, e.repeats), onClick = {
                        draft = AnniversaryDraft(e.id, e.title, e.start.date, e.repeatYearly)
                    })
                    Divider()
                }
            }
        }
    }
    if (picking) {
        var pick by remember { mutableStateOf(state.anniversary ?: state.linkedSince ?: today) }
        KkModalSheet({ picking = false }) {
            KkSheetHeader("사귄 날")
            KkDatePicker(pick, { pick = it })
            KkPrimaryButton("선택 완료", { viewModel.setAnniversary(pick); picking = false }, Modifier.fillMaxWidth())
        }
    }
    draft?.let { d -> AnniversarySheet(d, today, onDismiss = { draft = null }, onDelete = d.id?.let { id -> { viewModel.deleteAnniversary(id); draft = null } }) { saved ->
        viewModel.saveAnniversary(saved.id, saved.title, saved.date ?: return@AnniversarySheet, saved.repeats)
        draft = null
    } }
}

/** 기념일 추가 · 고치기 시트(Figma 8 · 기념일 · 추가 시트): 이름 · 날짜 · 매년 반복 + 추가하기(고칠 때는 저장 · 지우기). */
@Composable
private fun AnniversarySheet(initial: AnniversaryDraft, today: LocalDate, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (AnniversaryDraft) -> Unit) {
    var d by remember(initial) { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    KkModalSheet(onDismiss) {
        if (picking) {
            var pick by remember { mutableStateOf(d.date ?: today) }
            KkSheetHeader("날짜")
            KkDatePicker(pick, { pick = it })
            KkPrimaryButton("선택 완료", { d = d.copy(date = pick); picking = false }, Modifier.fillMaxWidth())
            return@KkModalSheet
        }
        KkSheetHeader(if (d.id == null) "기념일 추가" else "기념일")
        KkInput(d.title, { d = d.copy(title = it.take(ANNIVERSARY_TITLE_MAX)) }, label = "이름")
        KkInput(d.date?.dotted().orEmpty(), {}, label = "날짜", onClick = { picking = true })
        SwitchRow("매년 반복", d.repeats) { d = d.copy(repeats = it) }
        KkPrimaryButton(if (d.id == null) "추가하기" else "저장", { onSave(d) }, Modifier.fillMaxWidth(), enabled = d.title.isNotBlank() && d.date != null)
        if (onDelete != null) KkButton("지우기", onDelete, Modifier.fillMaxWidth(), style = KkButtonStyle.Secondary)
    }
}

private const val ANNIVERSARY_TITLE_MAX = 20

// ── 테마 · 화면 모드 ──────────────────────────────────────────────────────

/**
 * 테마(Figma 8 · 테마 `377:5050`): 두 테마 홈 미리보기를 나란히. 고르면 화면이 그 테마로 바뀌어 보이고, "이 테마로 할게요"로 정한다.
 * 화면 모드(라이트 · 다크)는 설정 메인 › 꾸미기에 따로 있다.
 */
@Composable
private fun ThemePage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    var pick by remember(state.theme) { mutableStateOf(state.theme) }
    val dark = when (state.displayMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val label = if (state.role == Role.GOMSIN && state.linked) "${state.partnerName.withSubject()} 전역까지" else "전역까지"
    val dday = state.timeline?.let { t -> state.today?.let { "D-${t.daysLeft(it).coerceAtLeast(0)}" } } ?: "D-day"
    // 고른 테마로 화면 전체를 미리 보여 준다.
    com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme(pick, dark) {
        Page("테마", onBack, bottom = {
            KkPrimaryButton("이 테마로 할게요", { viewModel.setTheme(pick); onBack() }, Modifier.fillMaxWidth())
        }) {
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg)) {
                AppTheme.entries.forEach { theme ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                        ThemePreview(theme, dark, selected = pick == theme, label = if (theme == AppTheme.GOMSIN) label else "전역까지", dday = dday) { pick = theme }
                        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            if (pick == theme) KkIcon(KkIcons.Check, tint = KkTheme.colors.accent, size = 16.dp)
                            Text(
                                theme.koreanName, style = if (pick == theme) KkTheme.type.labelBold else KkTheme.type.label,
                                color = if (pick == theme) KkTheme.colors.ink else KkTheme.colors.sub,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 테마 미리보기 카드: 그 테마 색 · 글꼴로 그린 작은 홈(D-day · 진행 막대 · 다음 휴가 태그 · 보고싶어 · 탭 바). */
@Composable
private fun ThemePreview(theme: AppTheme, dark: Boolean, selected: Boolean, label: String, dday: String, onClick: () -> Unit) {
    com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme(theme, dark) {
        val c = KkTheme.colors
        val shape = RoundedCornerShape(KkRadius.lg)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.canvas)
                .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, shape)
                .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
                .semantics { this.selected = selected },
        ) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = KkTheme.type.caption, color = c.sub, maxLines = 1)
                Text(dday, style = KkTheme.type.ddayS, color = c.ink, maxLines = 1)
                Spacer(Modifier.height(KkSpacing.xl))
                Box(Modifier.fillMaxWidth().height(2.dp).background(c.line)) {
                    Box(Modifier.fillMaxWidth(0.3f).height(2.dp).background(c.accent))
                }
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    Column(Modifier.align(Alignment.BottomStart).background(c.tag).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text("다음 휴가", style = KkTheme.type.caption, color = c.ink)
                        Text("D-17", style = KkTheme.type.labelBold, color = c.ink)
                    }
                    Box(Modifier.align(Alignment.BottomEnd).size(width = 52.dp, height = 18.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.love))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Row(Modifier.fillMaxWidth().background(c.bar).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(4) { i -> Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(if (i == 0) c.ink else c.sub)) }
            }
        }
    }
}

/** 화면 모드(Figma 최종 정리 › 설정 · 화면 모드): 시스템 설정 따름 · 라이트 · 다크. */
@Composable
private fun DisplayModePage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    Page("화면 모드", onBack) {
        Group(null) {
            listOf("SYSTEM", "LIGHT", "DARK").forEachIndexed { i, m ->
                if (i > 0) Divider()
                RadioRow(modeLabel(m), state.displayMode == m) { viewModel.setDisplayMode(m) }
            }
        }
    }
}

/** 줄 + 오른쪽 라디오. */
@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = KkTheme.type.body, color = KkTheme.colors.ink, modifier = Modifier.weight(1f))
        KkRadio(selected)
    }
}

private fun modeLabel(mode: String) = when (mode) {
    "LIGHT" -> "라이트"
    "DARK" -> "다크"
    else -> "시스템 따라"
}

// ── 위젯 안내 ─────────────────────────────────────────────────────────────

/** 위젯(Figma 최종 정리 › 설정 · 위젯 안내): 추가하는 방법 3단계 + 꽃신 위젯 미리보기(누르면 바로 추가 — 되는 런처만). */
@Composable
private fun WidgetsPage(state: SettingsState, platform: PlatformActions, onBack: () -> Unit) {
    val c = KkTheme.colors
    var message by remember { mutableStateOf<String?>(null) }
    fun pin(kind: String) {
        if (platform.pinnableWidgets.none { it.first == kind }) return
        message = if (platform.requestPinWidget(kind)) null else "이 런처는 앱에서 위젯을 추가할 수 없어요. 홈 화면을 길게 눌러 추가해 주세요."
    }
    Page("위젯", onBack) {
        val shape = RoundedCornerShape(KkRadius.lg)
        Column(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            listOf("홈 화면 빈 곳을 길게 누르기", "위젯 → 꽃신 선택", "원하는 크기를 골라 놓기").forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(c.line), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = KkTheme.type.label, color = c.sub)
                    }
                    Text(step, style = KkTheme.type.body, color = c.ink)
                }
            }
        }
        Text("꽃신 위젯", style = KkTheme.type.title, color = c.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val tile = RoundedCornerShape(KkRadius.xl)
            Column(
                Modifier.weight(1f).aspectRatio(1f).clip(tile).background(c.love)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { pin("missyou") }.padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${state.partnerName}에게", style = KkTheme.type.caption, color = c.loveOn)
                KkIcon(KkIcons.Heart, tint = c.loveOn, size = 28.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("보고싶어", style = KkTheme.type.labelBold, color = c.loveOn)
                    Text("오늘", style = KkTheme.type.caption, color = c.loveOn)
                }
            }
            Column(
                Modifier.weight(1f).aspectRatio(1f).clip(tile).background(c.bar)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { pin("nextmeet") }.padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("다음 만남", style = KkTheme.type.caption, color = c.accent)
                Column {
                    Text("D-3", style = KkTheme.type.title, color = c.ink)
                    Text("면회 · 외박 · 휴가", style = KkTheme.type.caption, color = c.sub)
                }
            }
        }
        message?.let { Text(it, style = KkTheme.type.body, color = c.sub) }
        WidgetFramePicker()
    }
}

/** 받은 그림 위젯 프레임(꽃신 평생 · 위젯 꾸미기). 기본은 누구나, 나머지는 이용권이 있으면. */
@Composable
private fun WidgetFramePicker() {
    val c = KkTheme.colors
    val koin = org.koin.compose.getKoin()
    val settings = koin.getOrNull<com.sadturtleman.flowershoes.core.data.DeviceSettings>() ?: return
    val lifetimeOn by (koin.getOrNull<com.sadturtleman.flowershoes.core.data.SharedPerks>()?.lifetimeOn
        ?: remember { kotlinx.coroutines.flow.flowOf(false) }).collectAsStateWithLifecycle(false)
    val frame by settings.widgetFrame.collectAsStateWithLifecycle("BASIC")
    val scope = rememberCoroutineScope()
    var locked by remember { mutableStateOf(false) }
    Text("그림 위젯 프레임", style = KkTheme.type.title, color = c.ink)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        WIDGET_FRAMES.forEach { (id, label, color) ->
            val on = frame == id
            val free = id == "BASIC"
            Column(
                Modifier.weight(1f).clickable(role = androidx.compose.ui.semantics.Role.RadioButton) {
                    if (free || lifetimeOn) scope.launch { settings.setWidgetFrame(id) } else locked = true
                },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val shape = RoundedCornerShape(KkRadius.md)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(color ?: c.surface)
                        .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.line, shape)
                        .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = if (id == "POLAROID") 14.dp else 6.dp),
                ) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(c.line))
                    if (!free) {
                        Text(
                            "평생", style = KkTheme.type.label, color = c.surface,
                            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(KkRadius.full))
                                .background(if (lifetimeOn) c.accent else c.sub).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(label, style = KkTheme.type.caption, color = if (on) c.accent else c.sub, maxLines = 1)
            }
        }
    }
    if (locked) {
        KkModalSheet({ locked = false }) {
            KkSheetHeader("위젯 프레임", "꽃신 평생 꾸미기예요. 설정 › 꽃신 평생에서 열 수 있어요")
            KkPrimaryButton("확인", { locked = false }, Modifier.fillMaxWidth())
        }
    }
}

private val WIDGET_FRAMES: List<Triple<String, String, Color?>> = listOf(
    Triple("BASIC", "기본", null),
    Triple("BLOSSOM", "벚꽃", Color(0xFFFFE0E7)),
    Triple("KRAFT", "크라프트", Color(0xFFE9DCC4)),
    Triple("POLAROID", "폴라로이드", Color(0xFFFFFFFF)),
    Triple("NIGHT", "밤하늘", Color(0xFF1B2235)),
)

// ── 로그인 계정 · 탈퇴 ────────────────────────────────────────────────────

/** 로그인 계정(Figma 최종 정리 › 설정 · 로그인 계정): 로그인 · 다른 로그인 방법 연결 · 이 폰. 회원 탈퇴는 설정 메인 › 계정(#71). */
@Composable
private fun AccountPage(state: SettingsState, platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit, open: (SettingsPage) -> Unit) {
    val linkable = com.sadturtleman.flowershoes.core.auth.LINKABLE_PROVIDERS.filter { it.name != state.provider }
    Page("로그인 계정", onBack) {
        Group(null) {
            KkListRow("로그인", value = "${viewModel.providerLabel(state.provider)} · ${state.myName}", trailing = KkRowTrailing.None)
            Divider()
            KkListRow("다른 로그인 방법 연결", value = linkable.joinToString(" · ") { viewModel.providerLabel(it.name) }, onClick = { open(SettingsPage.SIGN_IN_METHODS) })
            Divider()
            KkListRow("이 폰", value = platform.deviceName.ifBlank { "-" }, trailing = KkRowTrailing.None)
        }
    }
}

/**
 * 로그인 방법(Figma 최종 정리 › 설정 · 다른 로그인 방법): 지금 방법은 "연결됨", 나머지는 "연결하기".
 * 연결해 두면 폰을 잃어도 그 방법으로 같은 계정에 들어온다.
 */
@Composable
private fun SignInMethodsPage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val auth = org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.auth.AuthGateway>()
    var linked by remember { mutableStateOf<Set<com.sadturtleman.flowershoes.core.auth.AuthProvider>>(emptySet()) }
    var busy by remember { mutableStateOf<com.sadturtleman.flowershoes.core.auth.AuthProvider?>(null) }
    LaunchedEffect(auth) { linked = runCatching { auth?.linkedProviders() }.getOrNull().orEmpty() }
    val current = com.sadturtleman.flowershoes.core.auth.AuthProvider.entries.firstOrNull { it.name == state.provider }
    val providers = listOfNotNull(current) + com.sadturtleman.flowershoes.core.auth.LINKABLE_PROVIDERS.filter { it != current }
    Page("로그인 방법", onBack) {
        Group(null) {
            providers.forEachIndexed { i, p ->
                if (i > 0) Divider()
                val done = p == current || p in linked
                KkListRow(
                    viewModel.providerLabel(p.name),
                    value = when { done -> "연결됨"; busy == p -> "연결하는 중…"; else -> "연결하기" },
                    trailing = if (done) KkRowTrailing.None else KkRowTrailing.Chevron,
                    onClick = if (done) null else ({
                        if (busy == null && auth != null) {
                            busy = p
                            scope.launch {
                                when (val r = auth.link(p)) {
                                    is com.sadturtleman.flowershoes.core.auth.SignInResult.Success -> {
                                        linked = linked + p
                                        viewModel.notice("${viewModel.providerLabel(p.name)} 로그인을 연결했어요")
                                    }
                                    is com.sadturtleman.flowershoes.core.auth.SignInResult.Failed -> viewModel.notice(r.message)
                                    com.sadturtleman.flowershoes.core.auth.SignInResult.Cancelled -> Unit
                                }
                                busy = null
                            }
                        }
                    }),
                )
            }
        }
    }
}

/** 탈퇴 확인 문구(Figma 8 · 회원 탈퇴). */
private const val DELETE_CONFIRM = "탈퇴할게요"

/**
 * 회원 탈퇴(Figma 8 · 회원 탈퇴 `381:5334`): 지워지는 것 목록 + 먼저 내 기록 내보내기 + 확인 문구 입력 + 탈퇴하기.
 */
@Composable
private fun DeleteAccountPage(
    state: SettingsState,
    platform: PlatformActions,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    open: (SettingsPage) -> Unit,
) {
    val c = KkTheme.colors
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf("") }
    val ok = confirm.trim() == DELETE_CONFIRM
    Page("회원 탈퇴", onBack, bottom = {
        KkButton("탈퇴하기", {
            scope.launch {
                if (platform.authenticate("탈퇴하려면 본인 확인이 필요해요") != DeviceAuth.Failed) viewModel.deleteAccount()
                else viewModel.notice("본인 확인을 하지 못했어요")
            }
        }, Modifier.fillMaxWidth(), danger = true, enabled = ok)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
            Text("정말 떠나실 건가요?", style = KkTheme.type.handL, color = c.ink)
            // 되돌릴 수 없는 일 앞 한 줄 경고(design.md 글 규칙이 허용하는 것).
            Text("탈퇴하면 아래 기록이 이 폰에서 지워지고 되돌릴 수 없어요.", style = KkTheme.type.body, color = c.sub)
        }
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
            listOfNotNull(
                "내가 쓴 편지 · 그림 · 사진",
                "일정 · 기념일 · 휴가",
                if (state.linked) "${state.partnerName.withAnd()}의 연결" else null,
                "꽃신 평생 (환불되지 않아요)",
            ).forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    KkIcon(KkIcons.Close, tint = c.danger, size = 16.dp)
                    Text(item, style = KkTheme.type.body, color = c.ink)
                }
            }
        }
        Group(null) { KkListRow("먼저 내 기록 내보내기", onClick = { open(SettingsPage.EXPORT) }) }
        KkInput(confirm, { confirm = it }, label = "확인 문구", helper = "\"$DELETE_CONFIRM\"라고 똑같이 적어 주세요")
    }
}

// ── 연결 해제 ─────────────────────────────────────────────────────────────

/** 연결 해제(Figma 7 · 연결 해제): 해제 전 · 기록 받아 두기 → 선택 · 확인. */
@Composable
private fun UnlinkPage(
    state: SettingsState,
    platform: PlatformActions,
    viewModel: SettingsViewModel,
    shareFiles: ((List<Pair<String, ByteArray>>) -> Unit)?,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var prompting by remember { mutableStateOf(true) }
    if (prompting) {
        ExportPromptPage("연결 해제", "해제하기 전에 기록을 받아 두세요", "해제하면 다시 받을 수 없어요", shareFiles, viewModel, onBack) { prompting = false }
        return
    }
    val c = KkTheme.colors
    val scope = rememberCoroutineScope()
    var keep by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf("") }
    val ok = confirm.trim() == "연결 해제"
    Page("연결 해제", onBack, bottom = {
        KkButton("연결 해제", {
            scope.launch {
                val after = if (keep) AfterUnlink.KEEP_AS_MEMORIES else AfterUnlink.WIPE
                when (platform.authenticate("연결을 해제하려면 본인 확인이 필요해요")) {
                    DeviceAuth.Failed -> viewModel.notice("본인 확인을 하지 못했어요")
                    DeviceAuth.NoLockScreen -> {
                        viewModel.notice("이 폰에는 화면 잠금이 없어 본인 확인 없이 해제했어요")
                        viewModel.unlink(after)
                        onDone()
                    }
                    DeviceAuth.Passed -> {
                        viewModel.unlink(after)
                        onDone()
                    }
                }
            }
        }, Modifier.fillMaxWidth(), danger = true, enabled = ok)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text("${state.partnerName.withAnd()}의 연결을 해제할까요?", style = KkTheme.type.title, color = c.ink)
            Text("해제하면 서로 소식을 주고받을 수 없고, 되돌릴 수 없어요.", style = KkTheme.type.body, color = c.sub)
        }
        KeepChoice(keep) { keep = it }
        KkInput(confirm, { confirm = it }, label = "확인을 위해 \"연결 해제\"를 입력해 주세요", placeholder = "연결 해제", helper = "다음 단계에서 지문 · 얼굴 또는 기기 암호를 확인해요")
    }
}

/** 내 폰에 남은 기록: 추억으로 보관 · 모두 지우기. */
@Composable
private fun KeepChoice(keep: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        Text("내 폰에 남은 기록", style = KkTheme.type.label, color = KkTheme.colors.sub)
        KkChoice("추억으로 보관", keep, { onChange(true) }, radio = true)
        KkChoice("모두 지우기", !keep, { onChange(false) }, radio = true)
    }
}

/** 상대가 연결을 해제했을 때(Figma 6 · 연결 해제 / 상대 폰). 이 폰 사본을 보관할지 지울지 고른다. */
@Composable
fun PartnerUnlinkedScreen(partnerName: String, onConfirm: (AfterUnlink) -> Unit) {
    val c = KkTheme.colors
    var keep by remember { mutableStateOf(true) }
    // 고르기 전에는 닫지 않는다(사본을 어떻게 할지 정해야 한다).
    BackHandler { }
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding().padding(horizontal = KkSpacing.xl)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = KkSpacing.xxl), verticalArrangement = Arrangement.spacedBy(KkSpacing.lg)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(c.line), contentAlignment = Alignment.Center) { KkIcon(KkIcons.Heart, tint = c.ink) }
            Text("${partnerName.withSubject()} 연결을 해제했어요", style = KkTheme.type.title, color = c.ink)
            Text("이제 소식을 주고받을 수 없어요. 이 폰에 남은 기록을 어떻게 할까요?", style = KkTheme.type.body, color = c.sub)
            KkChoice("추억으로 보관", keep, { keep = true }, radio = true)
            KkChoice("모두 지우기", !keep, { keep = false }, radio = true)
        }
        KkPrimaryButton("확인", { onConfirm(if (keep) AfterUnlink.KEEP_AS_MEMORIES else AfterUnlink.WIPE) }, Modifier.fillMaxWidth().padding(vertical = KkSpacing.lg))
    }
}

// ── 약관 · 라이선스 ───────────────────────────────────────────────────────

/**
 * 약관과 라이선스(Figma 8 · 약관 · 개인정보처리방침 + 오픈소스 라이선스, #71 결정으로 한 줄): 문서들 · 광고성 정보 수신 동의 상태
 * + 오픈소스 라이선스.
 */
@Composable
private fun TermsPage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit, open: (SettingsPage) -> Unit) {
    var doc by remember { mutableStateOf<TermsDoc?>(null) }
    var marketingSheet by remember { mutableStateOf(false) }
    val reading = doc
    if (reading != null) {
        KkTermsScreen(reading) { doc = null }
        return
    }
    Page("약관과 라이선스", onBack) {
        Group(null) {
            TermsDoc.entries.forEachIndexed { i, d ->
                if (i > 0) Divider()
                if (d == TermsDoc.MARKETING) {
                    KkListRow("${d.title} 동의", value = if (state.marketing) "동의함" else "동의 안 함", onClick = { marketingSheet = true })
                } else {
                    KkListRow(d.title, onClick = { doc = d })
                }
            }
        }
        Group(null) { KkListRow("오픈소스 라이선스", onClick = { open(SettingsPage.LICENSES) }) }
    }
    if (marketingSheet) {
        KkModalSheet({ marketingSheet = false }) {
            KkSheetHeader("광고성 정보 수신")
            KkChoice("동의함", state.marketing, { viewModel.setMarketing(true); marketingSheet = false }, radio = true)
            KkChoice("동의 안 함", !state.marketing, { viewModel.setMarketing(false); marketingSheet = false }, radio = true)
            KkButton("전문 보기", { marketingSheet = false; doc = TermsDoc.MARKETING }, Modifier.fillMaxWidth(), style = KkButtonStyle.Secondary)
        }
    }
}

/** 오픈소스 라이선스 한 줄: 이름 · 라이선스 · 주소. */
private class License(val name: String, val license: String, val url: String)

private val ART_LICENSES = listOf(
    License("Microsoft Fluent Emoji", "MIT", "https://github.com/microsoft/fluentui-emoji"),
    License("Noto Emoji", "Apache 2.0", "https://github.com/googlefonts/noto-emoji"),
    License("Twemoji", "CC BY 4.0", "https://github.com/jdecked/twemoji"),
    License("Lucide Icons", "ISC", "https://lucide.dev/license"),
    License("Noto Sans KR · 고운돋움 · 고운바탕 · 개구 · 하이멜로디 · 감자꽃 · 싱글데이 · 연성 · 주아 · 동글 · 나눔손글씨 붓 · 나눔손글씨 펜 · 나눔명조", "OFL 1.1(Google Fonts, 글자 일부만 담음)", "https://openfontlicense.org"),
)

private val LIBRARY_LICENSES = listOf(
    License("libsodium", "ISC", "https://github.com/jedisct1/libsodium"),
    License("Kotlin · Compose Multiplatform", "Apache 2.0", "https://github.com/JetBrains/compose-multiplatform"),
    License("Room · Koin · Coil", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/room"),
    License("Firebase SDK", "Apache 2.0", "https://github.com/GitLiveApp/firebase-kotlin-sdk"),
    License("Kakao SDK", "Apache 2.0", "https://developers.kakao.com"),
    License("Okio · kotlinx", "Apache 2.0", "https://github.com/square/okio"),
    License("Zstandard", "BSD", "https://github.com/facebook/zstd"),
)

/** 오픈소스 라이선스(Figma 최종 정리): 스티커 · 아이콘 / 라이브러리. 누르면 라이선스와 주소. */
@Composable
private fun LicensesPage(onBack: () -> Unit) {
    var open by remember { mutableStateOf<License?>(null) }
    Page("오픈소스 라이선스", onBack) {
        listOf("스티커 · 아이콘" to ART_LICENSES, "라이브러리" to LIBRARY_LICENSES).forEach { (label, items) ->
            Group(label) {
                items.forEachIndexed { i, l ->
                    if (i > 0) Divider()
                    KkListRow(l.name, value = l.license, onClick = { open = l })
                }
            }
        }
    }
    open?.let { l ->
        KkModalSheet({ open = null }) {
            KkSheetHeader(l.name, "${l.license} 라이선스로 배포돼요.\n${l.url}")
            KkPrimaryButton("확인", { open = null }, Modifier.fillMaxWidth())
        }
    }
}

// ── 기기 바꾸기 · 복구 ───────────────────────────────────────────────────

/**
 * 기기 바꾸기 · 복구(Figma 8 · 설정 › 기기 바꾸기 · 복구 · 안내). 서버 백업은 없다(planning §5):
 * 새 폰에서 로그인하면 기기 교체 요청이 가고, 상대가 승인하면 상대 폰에서 기록을 받는다(feature/pairing DeviceChange).
 * 폰 밖에 남는 사본은 내 기록 내보내기(ZIP)뿐이라 같은 화면에 둔다.
 */
@Composable
private fun DevicePage(state: SettingsState, platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit, open: (SettingsPage) -> Unit) {
    val c = KkTheme.colors
    val partner = if (state.linked) state.partnerName else "연인"
    Page("기기 바꾸기 · 복구", onBack) {
        val shape = RoundedCornerShape(KkRadius.lg)
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text("폰을 바꾸거나 잃어버리면", style = KkTheme.type.label, color = c.sub)
            Column(
                Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).padding(KkSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
            ) {
                listOf("새 폰에서 로그인", "$partner 폰에서 승인", "$partner 폰에서 기록 받기").forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(c.tag), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = KkTheme.type.labelBold, color = c.ink)
                        }
                        Text(step, style = KkTheme.type.body, color = c.ink)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text("돌아오는 것", style = KkTheme.type.label, color = c.sub)
            Column(
                Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).padding(KkSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
            ) {
                listOf("편지 · 일정", "사진 · 그림").forEach { RecoveryLine(it, KkIcons.Check, c.success) }
                listOf("기기 설정", "임시저장한 편지", "현재 상태").forEach { RecoveryLine("$it · 안 돌아와요", KkIcons.Close, c.sub) }
            }
        }
        // 되돌릴 수 없는 일 앞 한 줄 경고(design.md 글 규칙이 허용하는 것).
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            KkIcon(KkIcons.Alert, tint = c.danger, size = 20.dp)
            Text("두 폰을 모두 잃으면 기록을 되찾을 수 없어요", style = KkTheme.type.labelBold, color = c.danger, modifier = Modifier.weight(1f))
        }
        Group("내 기록") {
            KkListRow("내 기록 내보내기", onClick = { open(SettingsPage.EXPORT) })
            Divider()
            SwitchRow("3개월마다 기록 받아 두기 알림", state.notify.exportReminder) { viewModel.setExportReminder(it, platform) }
        }
    }
}

/** 돌아오는 것 · 안 돌아오는 것 한 줄(아이콘 칸 20). */
@Composable
private fun RecoveryLine(text: String, icon: KkIcons, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { KkIcon(icon, tint = tint, size = 14.dp) }
        Text(text, style = KkTheme.type.body, color = KkTheme.colors.ink)
    }
}

// ── 내 기록 내보내기 ─────────────────────────────────────────────────────

/** 내 기록 ZIP: 담기는 것 미리보기 + 만들어 공유 시트로 넘기기. 넘기면 3개월 알림을 다시 센다. */
private class RecordExport(val preview: com.sadturtleman.flowershoes.core.data.RecordExporter.Preview?, val busy: Boolean, val start: () -> Unit)

@Composable
private fun rememberRecordExport(
    shareFiles: ((List<Pair<String, ByteArray>>) -> Unit)?,
    viewModel: SettingsViewModel,
    onShared: () -> Unit = {},
): RecordExport {
    val exporter = org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.RecordExporter>()
    var preview by remember { mutableStateOf<com.sadturtleman.flowershoes.core.data.RecordExporter.Preview?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(exporter) { preview = exporter?.let { runCatching { it.preview() }.getOrNull() } }
    return RecordExport(preview, busy) {
        if (exporter == null || shareFiles == null) {
            viewModel.notice("이 빌드에서는 아직 내보낼 수 없어요")
        } else if (!busy) {
            busy = true
            scope.launch {
                val zip = runCatching { exporter.zip() }.getOrNull()
                busy = false
                if (zip == null) {
                    viewModel.notice("내보내지 못했어요")
                } else {
                    shareFiles(listOf("꽃신_내_기록.zip" to zip))
                    viewModel.markExported()
                    onShared()
                }
            }
        }
    }
}

/**
 * 연결 해제 · 탈퇴 전 · 기록 받아 두기(Figma 7 · 연결 해제 · 해제 전 · 기록 받아 두기). 내보내거나 건너뛰면 [onNext].
 */
@Composable
private fun ExportPromptPage(
    title: String,
    heading: String,
    warning: String,
    shareFiles: ((List<Pair<String, ByteArray>>) -> Unit)?,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val c = KkTheme.colors
    val export = rememberRecordExport(shareFiles, viewModel, onShared = onNext)
    Page(title, onBack, bottom = {
        KkButton("건너뛰기", onNext, Modifier.align(Alignment.CenterHorizontally), style = KkButtonStyle.Secondary, size = com.sadturtleman.flowershoes.core.designsystem.KkButtonSize.M)
        KkPrimaryButton("내보내기", export.start, Modifier.fillMaxWidth(), loading = export.busy, enabled = export.preview != null)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
            Text(heading, style = KkTheme.type.title, color = c.ink)
            Text(warning, style = KkTheme.type.body, color = c.sub)
        }
        val shape = RoundedCornerShape(KkRadius.lg)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape)
                .clickable(enabled = export.preview != null, role = androidx.compose.ui.semantics.Role.Button, onClick = export.start)
                .padding(horizontal = KkSpacing.lg, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically,
        ) {
            KkIcon(KkIcons.Export, tint = c.accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("내 기록 내보내기", style = KkTheme.type.labelBold, color = c.ink)
                Text(
                    export.preview?.let { "편지 ${it.letters}통 · 사진 ${it.photos}장 · 그림 ${it.drawings}개 · ZIP" } ?: "…",
                    style = KkTheme.type.caption, color = c.sub,
                )
            }
            KkIcon(KkIcons.ChevronRight, tint = c.sub, size = 20.dp)
        }
    }
}

/**
 * 내 기록 내보내기(Figma 설정 · 내 기록 내보내기, 무료): 담기는 것 · ZIP 크기 + [내보내기].
 * 꾸밈 없는 원본(편지 글 · 그림 · 사진 · 일정 · 만남)을 폰 안에서 묶어 공유 시트로 넘긴다.
 */
@Composable
private fun ExportPage(shareFiles: ((List<Pair<String, ByteArray>>) -> Unit)?, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val c = KkTheme.colors
    val export = rememberRecordExport(shareFiles, viewModel)
    Page("내 기록 내보내기", onBack, bottom = {
        KkPrimaryButton("내보내기", export.start, Modifier.fillMaxWidth(), loading = export.busy, enabled = export.preview != null)
    }) {
        val p = export.preview
        Group(null) {
            KkListRow("편지", value = p?.let { "${it.letters}통 · 텍스트" } ?: "…", trailing = KkRowTrailing.None)
            Divider()
            KkListRow("그림 · 사진", value = p?.let { "${it.drawings + it.photos}장 · 원본 파일" } ?: "…", trailing = KkRowTrailing.None)
            Divider()
            KkListRow("일정 · 만남", value = p?.let { "${it.events}개 · 날짜 · 메모" } ?: "…", trailing = KkRowTrailing.None)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("ZIP 파일", style = KkTheme.type.caption, color = c.sub, modifier = Modifier.weight(1f))
            Text(p?.let { "약 ${bytesLabel(it.bytes)}" } ?: "", style = KkTheme.type.caption, color = c.sub)
        }
    }
}

private fun bytesLabel(b: Long): String = when {
    b == 0L -> "0KB"
    b >= 1_000_000_000 -> "${(b / 100_000_000) / 10.0}GB"
    b >= 1_000_000 -> "${b / 1_000_000}MB"
    else -> "${maxOf(1, b / 1_000)}KB"
}
