@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.sadturtleman.flowershoes.feature.settings

import com.sadturtleman.flowershoes.core.designsystem.KkPageFlip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadio
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.data.NotificationPrefs
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.data.sync.AfterUnlink
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkChoice
import com.sadturtleman.flowershoes.core.designsystem.KkDatePicker
import com.sadturtleman.flowershoes.core.designsystem.KkDialog
import com.sadturtleman.flowershoes.core.designsystem.KkDialogHost
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
import com.sadturtleman.flowershoes.core.designsystem.KkThemeCard
import com.sadturtleman.flowershoes.core.designsystem.KkToast
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.TermsDoc
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.military.ServiceRulesTable
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.ThemeAccent
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
     * 기기 생체 · 암호 인증(설계 7-5). 되돌릴 수 없는 일(연결 해제 · 넘겨주기) · 비밀(복구 코드) 앞에서 부른다.
     * 화면 잠금이 없는 기기는 막을 방법이 없어 [DeviceAuth.NoLockScreen] 을 돌려주고, 화면이 경고를 띄운 뒤 진행한다.
     */
    suspend fun authenticate(reason: String): DeviceAuth = DeviceAuth.NoLockScreen

    /** 백업 열쇠를 동기화하는 곳의 이름(복원 · 백업 찾음 화면). */
    val keySyncName: String get() = "기기 백업"

    /** 탈퇴: 플랫폼에 동기화해 둔 백업 키(Block Store · iCloud 키체인)를 지운다. */
    suspend fun deleteSyncedBackupKey() = Unit

    /** 이 폰 이름(설정 › 로그인 계정 · 상대 폰의 "새 폰으로 바꿨어요"). */
    val deviceName: String get() = ""

    /** 앱 버전(설정 › 정보). */
    val appVersion: String get() = "1.0.0"

    /** OS 이름 · 버전(문의하기 › 기기 정보). */
    val osVersion: String get() = ""

    /** 알림 미리보기 숨기기: iOS 알림 확장이 상태 문구를 풀지 않게 App Group 에 표시한다. */
    fun setHidePreview(hide: Boolean) = Unit

    /** 복구 코드를 비밀번호 관리자에 저장(Android Credential Manager). 못 하면 false — 화면이 복사로 대신한다. */
    suspend fun savePassword(id: String, secret: String): Boolean = false
}

enum class DeviceAuth { Passed, Failed, NoLockScreen }

object NoPlatformActions : PlatformActions

/** 설정 안의 화면들. 첫 화면은 [MAIN]. */
enum class SettingsPage {
    MAIN, LIFETIME, CONNECTION, MILITARY, ANNIVERSARY, THEME, DISPLAY_MODE, WIDGETS, BACKUP, EXPORT, RECOVERY,
    ACCOUNT, SIGN_IN_METHODS, DELETE_ACCOUNT, UNLINK, TERMS, LICENSES, INQUIRY, INQUIRY_HISTORY,
}

/** 백업 저장 한도(설정 › 백업의 사용량 막대). 사진은 줄여서 올려 이 안에 머문다. */
/** 백업 용량(수익 모델 확정): 무료 1GB · 꽃신 평생 5GB. */
private const val GB = 1024L * 1024 * 1024
private const val FREE_BACKUP_GB = 1
private const val LIFETIME_BACKUP_GB = 5
/** 백업 용량 추가(한 번 결제 3,300원). */
private const val PLUS_BACKUP_GB = 5
private const val PLUS_PRICE = "3,300원"

@Composable
fun SettingsRoute(
    platform: PlatformActions,
    onOpenPairing: () -> Unit,
    onHandOver: () -> Unit = {},
    onRestore: () -> Unit = {},
    onBack: () -> Unit,
    startAt: SettingsPage = SettingsPage.MAIN,
    onOpenLeave: () -> Unit = {},
    photoPicker: (@Composable (onPicked: (ByteArray) -> Unit) -> ((fromCamera: Boolean) -> Unit))? = null,
    /** 파일 공유 시트(내 기록 내보내기 ZIP). */
    shareFiles: ((List<Pair<String, ByteArray>>) -> Unit)? = null,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val inquiries = org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.InquiryTransport>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stack = remember { mutableStateListOf(startAt) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.notices.collect { notice = it } }
    LaunchedEffect(notice) { if (notice != null) { delay(2_500); notice = null } }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() }
    val open: (SettingsPage) -> Unit = { stack.add(it) }
    BackHandler(onBack = back)
    Box(Modifier.fillMaxSize()) {
        // 깊이가 같거나 깊어지면 앞으로 넘기고, 얕아지면 뒤로 넘긴다.
        KkPageFlip(stack.last() to stack.size, pageKey = { it.first }, isForward = { from, to -> to.second >= from.second }) { (page, _) ->
        when (page) {
            SettingsPage.MAIN -> MainPage(state, platform, open, back, onOpenPairing, onHandOver, viewModel)
            SettingsPage.LIFETIME -> LifetimePage(state, viewModel, back)
            SettingsPage.CONNECTION -> ConnectionPage(state, platform, viewModel, back, onOpenPairing, onHandOver, open)
            SettingsPage.MILITARY -> MilitaryPage(state, viewModel, back, onOpenLeave)
            SettingsPage.ANNIVERSARY -> AnniversaryPage(state, platform, viewModel, back)
            SettingsPage.THEME -> ThemePage(state, viewModel, back, open)
            SettingsPage.DISPLAY_MODE -> DisplayModePage(state, viewModel, back)
            SettingsPage.WIDGETS -> WidgetsPage(state, platform, back)
            SettingsPage.BACKUP -> BackupPage(state, platform, viewModel, back, open)
            SettingsPage.EXPORT -> ExportPage(shareFiles, back) { notice = it }
            SettingsPage.RECOVERY -> RecoveryPage(platform, viewModel, back)
            SettingsPage.ACCOUNT -> AccountPage(state, platform, viewModel, back, open)
            SettingsPage.SIGN_IN_METHODS -> SignInMethodsPage(state, viewModel, back)
            SettingsPage.DELETE_ACCOUNT -> DeleteAccountPage(state, platform, viewModel, back)
            SettingsPage.UNLINK -> UnlinkPage(state, platform, viewModel, back) { onBack() }
            SettingsPage.TERMS -> TermsPage(state, viewModel, back)
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
            SettingsPage.INQUIRY_HISTORY -> InquiryHistoryPage(inquiries, back)
        }
        }
        notice?.let { KkToast(it, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg)) }
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar(title, onLeading = onBack, leading = leading)
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
        if (label != null) Text(label, style = KkTheme.type.labelM, color = c.textSecondary)
        val shape = RoundedCornerShape(KkRadius.lg)
        Column(Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface), content = content)
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(KkTheme.colors.borderDefault))

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) =
    KkListRow(label, trailing = KkRowTrailing.Switch, switchOn = on, onClick = { onChange(!on) })

private fun LocalDate.dotted() = "$year.${month.number.toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"

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

/** "오늘 21:40" · "어제 08:10" · "10.21 21:40". */
fun relativeTime(ms: Long): String {
    val at = Instant.fromEpochMilliseconds(ms).toLocalDateTime(SEOUL)
    val today = Clock.System.now().toLocalDateTime(SEOUL).date
    val hm = "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
    return when (at.date.daysUntil(today)) {
        0 -> "오늘 $hm"
        1 -> "어제 $hm"
        else -> "${at.month.number}.${at.day} $hm"
    }
}

private fun hm(minute: Int) = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

// ── 설정(첫 화면) ─────────────────────────────────────────────────────────

/** 꽃신 평생 값(출시 기념가). 결제는 스토어 결제 연결과 함께 열린다. */
private const val LIFETIME_PRICE = "7,900원"
private const val LIFETIME_LIST_PRICE = "9,900원"

/**
 * 설정(Figma 최종 정리 › 설정): 프로필 · 꽃신 평생 + 우리 · 앱 · 데이터 · (광고) · 계정 · 정보.
 */
@Composable
private fun MainPage(
    state: SettingsState,
    platform: PlatformActions,
    open: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    onOpenPairing: () -> Unit,
    onHandOver: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val c = KkTheme.colors
    val scope = rememberCoroutineScope()
    var nameSheet by remember { mutableStateOf(false) }
    Page("설정", onBack) {
        val shape = RoundedCornerShape(KkRadius.lg)
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Row(
                Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { nameSheet = true }.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.bgMilitarySubtle), contentAlignment = Alignment.Center) {
                    Text(state.myName.take(1), style = KkTheme.type.titleS, color = c.textMilitary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${state.myName} · ${if (state.role == Role.SOLDIER) "군인" else "곰신"}", style = KkTheme.type.titleS, color = c.textPrimary)
                    Text(
                        if (state.linked) "${state.partnerName.withAnd()} 연결됨" else "아직 연결 전이에요",
                        style = KkTheme.type.bodyS, color = c.textSecondary,
                    )
                }
            }
            // 꽃신 평생(Figma): 프로필 바로 아래 옅은 색 한 줄.
            val lifetime = org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.LifetimeStore>()
            val owned by (lifetime?.owned ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
            Box(Modifier.fillMaxWidth().clip(shape).background(c.bgSubtle)) {
                val shared by (org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.SharedPerks>()?.lifetimeOn
                    ?: remember { kotlinx.coroutines.flow.flowOf(false) }).collectAsStateWithLifecycle(false)
                val value = when {
                    owned -> "이용 중"
                    shared -> "${state.partnerName.withAnd()} 함께 이용 중"
                    else -> "광고 없이 · $LIFETIME_PRICE"
                }
                KkListRow("꽃신 평생", value = value, onClick = { open(SettingsPage.LIFETIME) })
            }
        }
        Group("우리") {
            KkListRow("연결 정보", value = if (state.linked) state.partnerName else "연결 전", onClick = { open(SettingsPage.CONNECTION) })
            Divider()
            KkListRow("군 정보", value = state.timeline?.let { "전역 ${it.dischargeDate.dotted()}" } ?: "입력 전", onClick = { open(SettingsPage.MILITARY) })
            Divider()
            KkListRow("기념일", value = state.anniversary?.dotted() ?: "정하기", onClick = { open(SettingsPage.ANNIVERSARY) })
        }
        Group("앱") {
            KkListRow("테마", value = state.theme.koreanName, onClick = { open(SettingsPage.THEME) })
            Divider()
            val n = state.notify
            SwitchRow("알림 받기", n.letters || n.missYou || n.status || n.eventEve || n.anniversary) { viewModel.setAllNotify(it, platform) }
            Divider()
            SwitchRow("이벤트 · 혜택 알림", state.marketing) { viewModel.setMarketing(it) }
            Divider()
            KkListRow("위젯 안내", onClick = { open(SettingsPage.WIDGETS) })
        }
        Group("데이터") {
            KkListRow("백업", value = state.backup.lastAtMs?.let(::relativeTime) ?: "아직 없음", onClick = { open(SettingsPage.BACKUP) })
            Divider()
            KkListRow("내 기록 내보내기", onClick = { open(SettingsPage.EXPORT) })
            Divider()
            KkListRow("복구 코드", onClick = { open(SettingsPage.RECOVERY) })
        }
        KkAd(KkAdSlot.SETTINGS)
        Group("계정") {
            KkListRow("로그인 계정", value = viewModel.providerLabel(state.provider), onClick = { open(SettingsPage.ACCOUNT) })
            Divider()
            if (state.linked) {
                KkListRow("상대 폰 교체 받기", onClick = { handOver(scope, platform, viewModel, onHandOver) })
                Divider()
                KkListRow("연결 해제", labelColor = c.statusDanger, onClick = { open(SettingsPage.UNLINK) })
            } else {
                KkListRow("연인과 연결하기", onClick = onOpenPairing)
            }
        }
        Group("정보") {
            KkListRow("문의하기", onClick = { open(SettingsPage.INQUIRY) })
            Divider()
            KkListRow("이용약관 · 개인정보처리방침", onClick = { open(SettingsPage.TERMS) })
            Divider()
            KkListRow("오픈소스 라이선스", onClick = { open(SettingsPage.LICENSES) })
            Divider()
            KkListRow("버전", value = platform.appVersion, trailing = KkRowTrailing.None)
        }
    }
    if (nameSheet) {
        var name by remember { mutableStateOf(if (state.myName == "나") "" else state.myName) }
        KkModalSheet({ nameSheet = false }) {
            KkSheetHeader("내 이름", "연인 화면에 \"민준 ♥ 지수\"처럼 보여요")
            KkInput(name, { name = it.take(10) }, placeholder = "이름이나 애칭", helper = "${name.length} / 10")
            KkPrimaryButton("저장", { viewModel.setNickname(name); nameSheet = false }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
        }
    }
}

/** 상대 폰 교체 받기: 넘겨주기 전 본인 확인. */
private fun handOver(scope: kotlinx.coroutines.CoroutineScope, platform: PlatformActions, viewModel: SettingsViewModel, onHandOver: () -> Unit) {
    scope.launch {
        if (platform.authenticate("데이터를 넘겨주려면 본인 확인이 필요해요") != DeviceAuth.Failed) onHandOver()
        else viewModel.notice("본인 확인을 하지 못했어요")
    }
}

// ── 꽃신 평생 ─────────────────────────────────────────────────────────────

private val LIFETIME_BENEFITS = listOf(
    "광고 전부 제거",
    "글꼴 · 편지지 · 펜 · 색 전체",
    "위젯 꾸미기",
    "백업 5GB · 마지막 사용 후 10년 보관",
)

/**
 * 꽃신 평생(Figma 최종 정리 › 설정 · 꽃신 평생): 한 번 결제(Android: RevenueCat).
 * 결제가 없는 빌드(iOS 아직)에서는 "앱 출시와 함께" 안내만.
 */
@Composable
private fun LifetimePage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val c = KkTheme.colors
    val scope = rememberCoroutineScope()
    val store = org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.LifetimeStore>()
    val owned by (store?.owned ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
    var storePrice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(store) { storePrice = store?.price() }
    fun run(action: suspend (com.sadturtleman.flowershoes.core.data.LifetimeStore) -> com.sadturtleman.flowershoes.core.data.PurchaseOutcome) {
        if (store == null) { viewModel.notice("결제는 앱 출시와 함께 열려요"); return }
        if (busy) return
        busy = true
        scope.launch {
            when (val r = action(store)) {
                com.sadturtleman.flowershoes.core.data.PurchaseOutcome.Purchased -> viewModel.notice("꽃신 평생이 열렸어요. 고마워요!")
                com.sadturtleman.flowershoes.core.data.PurchaseOutcome.NothingToRestore -> viewModel.notice("이 계정으로 산 기록이 없어요")
                com.sadturtleman.flowershoes.core.data.PurchaseOutcome.Cancelled -> Unit
                is com.sadturtleman.flowershoes.core.data.PurchaseOutcome.Failed -> viewModel.notice(r.message)
            }
            busy = false
        }
    }
    val price = storePrice ?: LIFETIME_PRICE
    Page("", onBack, leading = KkTopBarLeading.Close, bottom = {
        if (owned) {
            KkPrimaryButton("꽃신 평생 이용 중이에요", {}, Modifier.fillMaxWidth(), enabled = false)
        } else {
            KkPrimaryButton("${price}에 평생 이용하기", { run { it.buy() } }, Modifier.fillMaxWidth(), loading = busy)
            KkSecondaryButton("구매 복원", { run { it.restore() } }, enabled = !busy)
        }
    }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("꽃신 평생", style = KkTheme.type.titleL, color = c.textPrimary)
            Text("한 번 결제로 광고 없이, 더 예쁘게", style = KkTheme.type.bodyM, color = c.textSecondary)
        }
        val shape = RoundedCornerShape(KkRadius.lg)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.bgBrandSubtle).padding(vertical = KkSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("출시 기념", style = KkTheme.type.labelS, color = c.textBrand)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                Text(
                    LIFETIME_LIST_PRICE, style = KkTheme.type.bodyM.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                    color = c.textTertiary, modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(price, style = KkTheme.type.display, color = c.textPrimary)
            }
        }
        Column(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            (LIFETIME_BENEFITS + "${if (state.linked) state.partnerName else "연인"}도 광고 없이 · 꾸미기 함께 (연결된 동안)").forEach { b ->
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(c.bgBrand))
                    Text(b, style = KkTheme.type.bodyM, color = c.textPrimary)
                }
            }
        }
    }
}

// ── 연결 정보 ─────────────────────────────────────────────────────────────

/** 연결 정보(Figma 최종 정리 › 설정 · 연결 정보): 연인 카드 · 연결한 날 · 연인 폰 + 교체 받기 · 연결 해제. */
@Composable
private fun ConnectionPage(
    state: SettingsState,
    platform: PlatformActions,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenPairing: () -> Unit,
    onHandOver: () -> Unit,
    open: (SettingsPage) -> Unit,
) {
    val c = KkTheme.colors
    val scope = rememberCoroutineScope()
    Page("연결 정보", onBack) {
        if (!state.linked) {
            Group(null) { KkListRow("연인", value = "연결 전", trailing = KkRowTrailing.None) }
            KkPrimaryButton("연인과 연결하기", onOpenPairing, Modifier.fillMaxWidth())
            return@Page
        }
        val shape = RoundedCornerShape(KkRadius.lg)
        val partnerRole = if (state.role == Role.SOLDIER) "곰신" else "군인"
        Row(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.bgBrandSubtle), contentAlignment = Alignment.Center) {
                Text(state.partnerName.take(1), style = KkTheme.type.titleS, color = c.textBrand)
            }
            Text("${state.partnerName} · $partnerRole", style = KkTheme.type.titleS, color = c.textPrimary)
        }
        Group(null) {
            KkListRow("연결한 날", value = state.linkedSince?.dotted() ?: "-", trailing = KkRowTrailing.None)
            Divider()
            KkListRow("${state.partnerName} 폰", value = state.partnerDevice ?: "-", trailing = KkRowTrailing.None)
        }
        Group(null) {
            KkListRow("상대 폰 교체 받기", onClick = { handOver(scope, platform, viewModel, onHandOver) })
            Divider()
            KkListRow("연결 해제", labelColor = c.statusDanger, onClick = { open(SettingsPage.UNLINK) })
        }
    }
}

// ── 군 정보 ───────────────────────────────────────────────────────────────

/** 군 정보(Figma 7 · 군 정보): 역할 · 군종 · 입대일 · 전역일, 전역일 직접 고치기. */
@Composable
private fun MilitaryPage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit, onOpenLeave: () -> Unit) {
    val c = KkTheme.colors
    var sheet by remember { mutableStateOf<String?>(null) }
    val s = state.service
    val t = state.timeline
    Page("군 정보", onBack) {
        if (s == null || t == null) {
            Text("입대일이 아직 없어요. 군인 폰에서 입대일을 넣으면 여기에도 보여요.", style = KkTheme.type.bodyM, color = c.textTertiary)
            return@Page
        }
        val months = ServiceRulesTable.BUNDLED.ruleFor(s.branch, s.enlistDate).serviceMonths
        Group(null) {
            KkListRow("역할", value = if (state.role == Role.SOLDIER) "군인" else "곰신", trailing = KkRowTrailing.None)
            Divider()
            KkListRow("군종", value = "${s.branch.koreanName} · ${months}개월", onClick = { sheet = "branch" })
            Divider()
            KkListRow("입대일", value = s.enlistDate.dotted(), onClick = { sheet = "enlist" })
            Divider()
            KkListRow(
                "전역일", value = t.dischargeDate.dotted(),
                trailing = if (t.isOverridden) KkRowTrailing.Chevron else KkRowTrailing.None,
                onClick = if (t.isOverridden) ({ sheet = "discharge" }) else null,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text("전역일이 달라졌나요?", style = KkTheme.type.labelM, color = c.textSecondary)
            Group(null) {
                SwitchRow("전역일 직접 고치기", t.isOverridden) { on -> if (on) sheet = "discharge" else viewModel.overrideDischarge(null) }
            }
        }
        // 휴가 관리(Figma 3 · 휴가): 홈 "남은 휴가" 칸 말고도 연결 뒤에 들어갈 곳.
        Group("휴가") { KkListRow("휴가 관리", onClick = onOpenLeave) }
    }
    when (sheet) {
        "branch" -> KkModalSheet({ sheet = null }) {
            KkSheetHeader("군종", "복무 기간이 군종마다 달라요")
            Branch.entries.forEach { b ->
                val m = ServiceRulesTable.BUNDLED.ruleFor(b, s?.enlistDate ?: Clock.System.now().toLocalDateTime(SEOUL).date).serviceMonths
                KkSheetOption(b.koreanName, s?.branch == b, { viewModel.updateService(branch = b); sheet = null }, meta = "${m}개월")
            }
        }
        "enlist", "discharge" -> {
            val enlist = sheet == "enlist"
            val initial = if (enlist) s?.enlistDate else t?.dischargeDate
            var pick by remember(sheet) { mutableStateOf(initial ?: Clock.System.now().toLocalDateTime(SEOUL).date) }
            KkModalSheet({ sheet = null }) {
                KkSheetHeader(if (enlist) "입대일" else "전역일", if (enlist) "훈련소 입소일을 골라 주세요" else "조기 전역 · 연장처럼 바뀐 날을 골라요. 연인 폰에도 같이 바뀌어요")
                KkDatePicker(pick, { pick = it })
                KkPrimaryButton("선택 완료", {
                    if (enlist) viewModel.updateService(enlist = pick) else viewModel.overrideDischarge(pick)
                    sheet = null
                }, Modifier.fillMaxWidth())
            }
        }
    }
}

// ── 기념일 ───────────────────────────────────────────────────────────────

/** 기념일(Figma 최종 정리 › 설정 · 기념일): 사귄 날 + 기념일 알림(100일 단위 · 1년 단위) + 저장. */
@Composable
private fun AnniversaryPage(state: SettingsState, platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val c = KkTheme.colors
    var date by remember { mutableStateOf(state.anniversary) }
    var hundreds by remember { mutableStateOf(state.notify.anniversary100) }
    var years by remember { mutableStateOf(state.notify.anniversaryYear) }
    var picking by remember { mutableStateOf(false) }
    Page("기념일", onBack, bottom = {
        KkPrimaryButton("저장", {
            if (date != state.anniversary) viewModel.setAnniversary(date)
            viewModel.setAnniversaryAlerts(hundreds, years, platform)
            onBack()
        }, Modifier.fillMaxWidth())
    }) {
        KkInput(date?.dotted() ?: "", {}, label = "사귄 날", placeholder = "날짜를 골라 주세요", onClick = { picking = true })
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text("기념일 알림", style = KkTheme.type.labelM, color = c.textSecondary)
            Group(null) {
                SwitchRow("100일 단위", hundreds) { hundreds = it }
                Divider()
                SwitchRow("1년 단위", years) { years = it }
            }
        }
    }
    if (picking) {
        var pick by remember { mutableStateOf(date ?: state.linkedSince ?: Clock.System.now().toLocalDateTime(SEOUL).date) }
        KkModalSheet({ picking = false }) {
            KkSheetHeader("사귄 날")
            KkDatePicker(pick, { pick = it })
            KkPrimaryButton("선택 완료", { date = pick; picking = false }, Modifier.fillMaxWidth())
        }
    }
}

// ── 테마 · 화면 모드 ──────────────────────────────────────────────────────

/** 테마(Figma 7 · 테마): 네 가지 카드 + 화면 모드. */
@Composable
private fun ThemePage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit, open: (SettingsPage) -> Unit) {
    val recommended = state.role?.let { ThemeAccent.defaultFor(it) }
    Page("테마", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeAccent.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { theme ->
                        KkThemeCard(theme, selected = state.theme == theme, recommended = theme == recommended, modifier = Modifier.weight(1f)) { viewModel.setTheme(theme) }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text("화면 모드", style = KkTheme.type.labelM, color = KkTheme.colors.textSecondary)
            Group(null) { KkListRow("라이트 · 다크", value = modeLabel(state.displayMode), onClick = { open(SettingsPage.DISPLAY_MODE) }) }
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
        Text(label, style = KkTheme.type.bodyM, color = KkTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        KkRadio(selected)
    }
}

private fun modeLabel(mode: String) = when (mode) {
    "LIGHT" -> "라이트"
    "DARK" -> "다크"
    else -> "시스템 설정 따름"
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
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            listOf("홈 화면 빈 곳을 길게 누르기", "위젯 → 꽃신 선택", "원하는 크기를 골라 놓기").forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(c.bgSubtle), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = KkTheme.type.labelS, color = c.textSecondary)
                    }
                    Text(step, style = KkTheme.type.bodyM, color = c.textPrimary)
                }
            }
        }
        Text("꽃신 위젯", style = KkTheme.type.titleM, color = c.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val tile = RoundedCornerShape(KkRadius.xl)
            Column(
                Modifier.weight(1f).aspectRatio(1f).clip(tile).background(c.bgBrand)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { pin("missyou") }.padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${state.partnerName}에게", style = KkTheme.type.caption, color = c.textOnBrand)
                KkIcon(KkIcons.Heart, tint = c.textOnBrand, size = 28.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("보고싶어", style = KkTheme.type.titleS, color = c.textOnBrand)
                    Text("오늘", style = KkTheme.type.caption, color = c.textOnBrand)
                }
            }
            Column(
                Modifier.weight(1f).aspectRatio(1f).clip(tile).background(c.bgMilitarySubtle)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { pin("nextmeet") }.padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("다음 만남", style = KkTheme.type.caption, color = c.textMilitary)
                Column {
                    Text("D-3", style = KkTheme.type.display, color = c.textPrimary)
                    Text("면회 · 외박 · 휴가", style = KkTheme.type.caption, color = c.textSecondary)
                }
            }
        }
        message?.let { Text(it, style = KkTheme.type.bodyS, color = c.textTertiary) }
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
    Text("그림 위젯 프레임", style = KkTheme.type.titleM, color = c.textPrimary)
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
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(color ?: c.bgSurface)
                        .border(if (on) 2.dp else 1.dp, if (on) c.borderBrand else c.borderDefault, shape)
                        .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = if (id == "POLAROID") 14.dp else 6.dp),
                ) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(c.bgSubtle))
                    if (!free) {
                        Text(
                            "평생", style = KkTheme.type.labelS, color = c.textOnBrand,
                            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(KkRadius.full))
                                .background(if (lifetimeOn) c.bgBrand else c.textTertiary).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(label, style = KkTheme.type.caption, color = if (on) c.textBrand else c.textTertiary, maxLines = 1)
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

// ── 백업 ─────────────────────────────────────────────────────────────────

/** 백업(Figma 7 · 백업): 마지막 백업 · 사용량 · 열쇠 보관 안내, 자동 · 셀룰러 · 지금 백업 · 복구 코드. */
@Composable
private fun BackupPage(state: SettingsState, platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit, open: (SettingsPage) -> Unit) {
    val c = KkTheme.colors
    val b = state.backup
    Page("백업", onBack) {
        if (!b.available) {
            Text("이 빌드에서는 아직 백업을 쓸 수 없어요.", style = KkTheme.type.bodyM, color = c.textTertiary)
            return@Page
        }
        val shape = RoundedCornerShape(KkRadius.lg)
        Column(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Mid, shape).clip(shape).background(c.bgSurface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
        ) {
            Text("마지막 백업", style = KkTheme.type.caption, color = c.textTertiary)
            Text(b.lastAtMs?.let(::relativeTime) ?: "아직 없어요", style = KkTheme.type.titleM, color = c.textPrimary)
            val lifetime = org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.LifetimeStore>()
            val owned by (lifetime?.owned ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
            val plus = org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.BackupPlusStore>()
            val plusOwned by (plus?.owned ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
            val quotaGb = (if (owned) LIFETIME_BACKUP_GB else FREE_BACKUP_GB) + if (plusOwned) PLUS_BACKUP_GB else 0
            val fraction = (b.bytes.toFloat() / (quotaGb * GB)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.bgSubtle)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(if (b.bytes > 0) fraction.coerceAtLeast(0.02f) else 0f).clip(RoundedCornerShape(3.dp)).background(c.bgBrand))
            }
            Text("${bytesLabel(b.bytes)} / ${quotaGb}GB", style = KkTheme.type.caption, color = c.textSecondary)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.md)).background(c.bgMilitarySubtle).padding(horizontal = KkSpacing.md, vertical = KkSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(c.bgBrand))
                Text("${platform.keySyncName} 사용 여부와 관계없이 복구 코드도 따로 보관해 주세요", style = KkTheme.type.caption, color = c.textPrimary)
            }
        }
        Group(null) {
            SwitchRow("자동 백업", state.autoBackup) { viewModel.setAutoBackup(it) }
            Divider()
            SwitchRow("셀룰러로 사진 백업", state.cellularBackup) { viewModel.setCellularBackup(it) }
            Divider()
            KkListRow(if (b.busy) "백업하는 중…" else "지금 백업하기", enabled = !b.busy, onClick = { viewModel.backupNow() })
            Divider()
            BackupPlusRow(viewModel)
            Divider()
            KkListRow("복구 코드", value = if (state.codeSeen) "확인함" else "확인 안 함", onClick = { open(SettingsPage.RECOVERY) })
        }
    }
}

// ── 복구 코드 ─────────────────────────────────────────────────────────────

/** 복구 코드(Figma 6 · 설정 · 백업 · 복구 코드). 기기 인증 뒤에만 보여 준다. */
@Composable
private fun RecoveryPage(platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val c = KkTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf<String?>(null) }
    var denied by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (platform.authenticate("복구 코드를 보려면 본인 확인이 필요해요") == DeviceAuth.Failed) denied = true
        else code = viewModel.recoveryCode()
        loaded = true
    }
    Page("복구 코드", onBack, bottom = {
        KkButton("새 코드 만들기", { confirmNew = true }, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost, enabled = code != null)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text("백업을 여는 마지막 열쇠예요", style = KkTheme.type.titleL, color = c.textPrimary)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.lg)).background(c.bgBrandSubtle).padding(vertical = KkSpacing.xxl, horizontal = KkSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
        ) {
            val shown = code
            when {
                denied -> Text("본인 확인을 하지 못했어요", style = KkTheme.type.bodyM, color = c.textSecondary)
                shown == null && loaded -> Text("복구 코드를 불러오지 못했어요. 백업 설정을 확인해 주세요", style = KkTheme.type.bodyM, color = c.textSecondary)
                shown == null -> Text("불러오는 중…", style = KkTheme.type.bodyM, color = c.textTertiary)
                else -> shown.filter { it.isLetterOrDigit() }.chunked(4).chunked(3).forEach { line ->
                    Text(
                        line.joinToString("  "), style = KkTheme.type.titleM.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                        color = c.textPrimary, textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            CodeAction("복사", Modifier.weight(1f), enabled = code != null) {
                code?.let { clipboard.setText(AnnotatedString(it)) }
                viewModel.notice("복사했어요. 비밀번호 앱이나 안전한 곳에 붙여 넣어 주세요")
            }
            CodeAction("비밀번호 관리자에 저장", Modifier.weight(1f), enabled = code != null) {
                val secret = code ?: return@CodeAction
                scope.launch {
                    if (platform.savePassword("꽃신 복구 코드", secret)) {
                        viewModel.notice("비밀번호 관리자에 저장했어요")
                    } else {
                        clipboard.setText(AnnotatedString(secret))
                        viewModel.notice("이 기기에서는 바로 저장할 수 없어 복사했어요. 비밀번호 앱에 붙여 넣어 주세요")
                    }
                }
            }
        }
    }
    if (confirmNew) {
        KkDialogHost(onDismiss = { confirmNew = false }) {
            KkDialog(
                title = "새 코드를 만들까요?", body = "지금 코드는 더 이상 쓸 수 없어요. 새 코드를 다시 적어 두세요",
                confirm = "새로 만들기",
                onConfirm = {
                    confirmNew = false
                    scope.launch { viewModel.regenerateCode()?.let { code = it } }
                },
                onCancel = { confirmNew = false },
            )
        }
    }
}

@Composable
private fun CodeAction(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val c = KkTheme.colors
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.bgBrandSubtle)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = KkTheme.type.labelM, color = if (enabled) c.textBrand else c.textTertiary) }
}

// ── 로그인 계정 · 탈퇴 ────────────────────────────────────────────────────

/** 로그인 계정(Figma 최종 정리 › 설정 · 로그인 계정): 로그인 · 다른 로그인 방법 연결 · 이 폰 + 회원 탈퇴. */
@Composable
private fun AccountPage(state: SettingsState, platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit, open: (SettingsPage) -> Unit) {
    val c = KkTheme.colors
    val linkable = com.sadturtleman.flowershoes.core.auth.LINKABLE_PROVIDERS.filter { it.name != state.provider }
    Page("로그인 계정", onBack) {
        Group(null) {
            KkListRow("로그인", value = "${viewModel.providerLabel(state.provider)} · ${state.myName}", trailing = KkRowTrailing.None)
            Divider()
            KkListRow("다른 로그인 방법 연결", value = linkable.joinToString(" · ") { viewModel.providerLabel(it.name) }, onClick = { open(SettingsPage.SIGN_IN_METHODS) })
            Divider()
            KkListRow("이 폰", value = platform.deviceName.ifBlank { "-" }, trailing = KkRowTrailing.None)
        }
        Group(null) { KkListRow("회원 탈퇴", labelColor = c.statusDanger, onClick = { open(SettingsPage.DELETE_ACCOUNT) }) }
    }
}

/**
 * 로그인 방법(Figma 최종 정리 › 설정 · 다른 로그인 방법): 지금 방법은 "연결됨", 나머지는 "연결하기".
 * 연결해 두면 폰을 잃어도 그 방법으로 같은 계정 · 백업에 들어온다.
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

/** 회원 탈퇴(Figma 최종 정리 › 설정 · 회원 탈퇴): 지워지는 것 목록 + "탈퇴" 입력 + 탈퇴하기. */
@Composable
private fun DeleteAccountPage(state: SettingsState, platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val c = KkTheme.colors
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf("") }
    val ok = confirm.trim() == "탈퇴"
    Page("회원 탈퇴", onBack, bottom = {
        KkButton("탈퇴하기", {
            scope.launch {
                if (platform.authenticate("탈퇴하려면 본인 확인이 필요해요") != DeviceAuth.Failed) viewModel.deleteAccount(platform::deleteSyncedBackupKey)
                else viewModel.notice("본인 확인을 하지 못했어요")
            }
        }, Modifier.fillMaxWidth(), danger = true, enabled = ok)
    }) {
        Text("탈퇴하면 지워지는 것", style = KkTheme.type.titleL, color = c.textPrimary)
        val shape = RoundedCornerShape(KkRadius.lg)
        Column(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            listOfNotNull(
                if (state.linked) "${state.partnerName.withAnd()}의 연결" else null,
                "이 폰의 편지 · 그림 · 일정",
                "암호화 백업 전부",
                "꽃신 평생 이용권",
            ).forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(c.statusDanger))
                    Text(item, style = KkTheme.type.bodyM, color = c.textPrimary)
                }
            }
        }
        KkInput(confirm, { confirm = it }, label = "확인을 위해 \"탈퇴\"를 입력해 주세요", placeholder = "탈퇴")
    }
}

// ── 연결 해제 ─────────────────────────────────────────────────────────────

/** 연결 해제(Figma 6 · 연결 해제 / 설정). */
@Composable
private fun UnlinkPage(state: SettingsState, platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit, onDone: () -> Unit) {
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
            Text("${state.partnerName.withAnd()}의 연결을 해제할까요?", style = KkTheme.type.titleL, color = c.textPrimary)
            Text("해제하면 서로 소식을 주고받을 수 없고, 되돌릴 수 없어요.", style = KkTheme.type.bodyM, color = c.textSecondary)
        }
        KeepChoice(keep) { keep = it }
        KkInput(confirm, { confirm = it }, label = "확인을 위해 \"연결 해제\"를 입력해 주세요", placeholder = "연결 해제", helper = "다음 단계에서 지문 · 얼굴 또는 기기 암호를 확인해요")
    }
}

/** 내 폰에 남은 기록: 추억으로 보관 · 모두 지우기. */
@Composable
private fun KeepChoice(keep: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        Text("내 폰에 남은 기록", style = KkTheme.type.labelM, color = KkTheme.colors.textSecondary)
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
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding().padding(horizontal = KkSpacing.xl)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = KkSpacing.xxl), verticalArrangement = Arrangement.spacedBy(KkSpacing.lg)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(c.bgSubtle), contentAlignment = Alignment.Center) { KkIcon(KkIcons.Heart, tint = c.textPrimary) }
            Text("${partnerName.withSubject()} 연결을 해제했어요", style = KkTheme.type.titleL, color = c.textPrimary)
            Text("이제 소식을 주고받을 수 없어요. 이 폰에 남은 기록을 어떻게 할까요?", style = KkTheme.type.bodyM, color = c.textSecondary)
            KkChoice("추억으로 보관", keep, { keep = true }, radio = true)
            KkChoice("모두 지우기", !keep, { keep = false }, radio = true)
        }
        KkPrimaryButton("확인", { onConfirm(if (keep) AfterUnlink.KEEP_AS_MEMORIES else AfterUnlink.WIPE) }, Modifier.fillMaxWidth().padding(vertical = KkSpacing.lg))
    }
}

/**
 * 상대가 새 폰으로 바꿨을 때(Figma 6 · 기기 교체 · 상대 폰 알림). 자동으로 확인한 뒤 한 번 알려 준다.
 * "내가 모르는 일이에요"는 연결 해제로 이어진다.
 */
@Composable
fun PartnerDeviceChangedScreen(partnerName: String, deviceName: String, at: String, providerLabel: String?, onAcknowledge: () -> Unit, onUnknown: () -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding().padding(horizontal = KkSpacing.xl)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = KkSpacing.xxl), verticalArrangement = Arrangement.spacedBy(KkSpacing.lg)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(c.bgBrandSubtle), contentAlignment = Alignment.Center) { KkIcon(KkIcons.Home, tint = c.textPrimary) }
            Text("${partnerName.withSubject()} 새 폰으로 바꿨어요", style = KkTheme.type.titleL, color = c.textPrimary)
            val shape = RoundedCornerShape(KkRadius.lg)
            Column(
                Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.xs),
            ) {
                Text("새 기기", style = KkTheme.type.caption, color = c.textTertiary)
                Text(listOf(deviceName.ifBlank { "새 폰" }, at).joinToString(" · "), style = KkTheme.type.titleS, color = c.textPrimary)
                providerLabel?.let { Text("$it 계정으로 로그인", style = KkTheme.type.caption, color = c.textSecondary) }
            }
        }
        Column(Modifier.padding(vertical = KkSpacing.lg), verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            KkPrimaryButton("확인했어요", onAcknowledge, Modifier.fillMaxWidth())
            KkButton("내가 모르는 일이에요", onUnknown, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost)
        }
    }
}

// ── 약관 · 라이선스 ───────────────────────────────────────────────────────

/** 약관 · 정책(Figma 최종 정리 › 설정 · 약관 · 개인정보처리방침): 문서들 + 이벤트 · 혜택 알림 동의 상태. */
@Composable
private fun TermsPage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    var doc by remember { mutableStateOf<TermsDoc?>(null) }
    var marketingSheet by remember { mutableStateOf(false) }
    val open = doc
    if (open != null) {
        KkTermsScreen(open) { doc = null }
        return
    }
    Page("약관 · 정책", onBack) {
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
    }
    if (marketingSheet) {
        KkModalSheet({ marketingSheet = false }) {
            KkSheetHeader("이벤트 · 혜택 알림", "새 스티커 · 할인 소식을 알림으로 받아요. 언제든 끌 수 있어요")
            KkChoice("동의함", state.marketing, { viewModel.setMarketing(true); marketingSheet = false }, radio = true)
            KkChoice("동의 안 함", !state.marketing, { viewModel.setMarketing(false); marketingSheet = false }, radio = true)
            KkButton("전문 보기", { marketingSheet = false; doc = TermsDoc.MARKETING }, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost)
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

// ── 내 기록 내보내기 ─────────────────────────────────────────────────────

/**
 * 내 기록 내보내기(Figma 설정 · 내 기록 내보내기, 무료): 담기는 것 · ZIP 크기 + [내보내기].
 * 꾸밈 없는 원본(편지 글 · 그림 · 사진 · 일정 · 만남)을 폰 안에서 묶어 공유 시트로 넘긴다.
 */
@Composable
private fun ExportPage(shareFiles: ((List<Pair<String, ByteArray>>) -> Unit)?, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val c = KkTheme.colors
    val exporter = org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.RecordExporter>()
    var preview by remember { mutableStateOf<com.sadturtleman.flowershoes.core.data.RecordExporter.Preview?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(exporter) { preview = exporter?.let { runCatching { it.preview() }.getOrNull() } }
    Page("내 기록 내보내기", onBack, bottom = {
        KkPrimaryButton("내보내기", {
            if (exporter == null || shareFiles == null) { onNotice("이 빌드에서는 아직 내보낼 수 없어요"); return@KkPrimaryButton }
            busy = true
            scope.launch {
                val zip = runCatching { exporter.zip() }.getOrNull()
                busy = false
                if (zip == null) onNotice("내보내지 못했어요") else shareFiles(listOf("꽃신_내_기록.zip" to zip))
            }
        }, Modifier.fillMaxWidth(), loading = busy, enabled = preview != null)
    }) {
        val p = preview
        Group(null) {
            KkListRow("편지", value = p?.let { "${it.letters}통 · 텍스트" } ?: "…", trailing = KkRowTrailing.None)
            Divider()
            KkListRow("그림 · 사진", value = p?.let { "${it.drawings + it.photos}장 · 원본 파일" } ?: "…", trailing = KkRowTrailing.None)
            Divider()
            KkListRow("일정 · 만남", value = p?.let { "${it.events}개 · 날짜 · 메모" } ?: "…", trailing = KkRowTrailing.None)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("ZIP 파일", style = KkTheme.type.caption, color = c.textTertiary, modifier = Modifier.weight(1f))
            Text(p?.let { "약 ${bytesLabel(it.bytes)}" } ?: "", style = KkTheme.type.caption, color = c.textSecondary)
        }
    }
}

/** 백업 용량 추가(+5GB · 한 번 결제). 산 뒤에는 "추가됨". 결제가 없는 빌드에서는 안내만. */
@Composable
private fun BackupPlusRow(viewModel: SettingsViewModel) {
    val store = org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.BackupPlusStore>()
    val owned by (store?.owned ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
    var price by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(store) { price = store?.price() }
    KkListRow(
        "백업 용량 추가 · +${PLUS_BACKUP_GB}GB",
        value = if (owned) "추가됨" else price ?: PLUS_PRICE,
        enabled = !owned && !busy,
        trailing = if (owned) KkRowTrailing.None else KkRowTrailing.Chevron,
        onClick = {
            if (store == null) { viewModel.notice("결제는 앱 출시와 함께 열려요"); return@KkListRow }
            busy = true
            scope.launch {
                when (val r = store.buy()) {
                    com.sadturtleman.flowershoes.core.data.PurchaseOutcome.Purchased -> viewModel.notice("백업 용량이 ${PLUS_BACKUP_GB}GB 늘었어요")
                    is com.sadturtleman.flowershoes.core.data.PurchaseOutcome.Failed -> viewModel.notice(r.message)
                    else -> Unit
                }
                busy = false
            }
        },
    )
}
