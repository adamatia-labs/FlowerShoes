@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.sadturtleman.flowershoes.feature.settings

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
enum class SettingsPage { MAIN, CONNECTION, MILITARY, THEME, NOTIFY, WIDGETS, BACKUP, RECOVERY, ACCOUNT, DELETE_ACCOUNT, UNLINK, TERMS, LICENSES, INQUIRY, INQUIRY_HISTORY }

/** 백업 저장 한도(설정 › 백업의 사용량 막대). 사진은 줄여서 올려 이 안에 머문다. */
private const val BACKUP_QUOTA_BYTES = 2L * 1024 * 1024 * 1024

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
        when (stack.last()) {
            SettingsPage.MAIN -> MainPage(state, platform, open, back, onOpenPairing, onHandOver, viewModel)
            SettingsPage.CONNECTION -> ConnectionPage(state, back, onOpenPairing, open)
            SettingsPage.MILITARY -> MilitaryPage(state, viewModel, back, onOpenLeave)
            SettingsPage.THEME -> ThemePage(state, viewModel, back)
            SettingsPage.NOTIFY -> NotifyPage(state, platform, viewModel, back)
            SettingsPage.WIDGETS -> WidgetsPage(platform, back)
            SettingsPage.BACKUP -> BackupPage(state, platform, viewModel, back, open, onRestore)
            SettingsPage.RECOVERY -> RecoveryPage(platform, viewModel, back)
            SettingsPage.ACCOUNT -> AccountPage(state, platform, viewModel, back, open)
            SettingsPage.DELETE_ACCOUNT -> DeleteAccountPage(platform, viewModel, back)
            SettingsPage.UNLINK -> UnlinkPage(state, platform, viewModel, back) { onBack() }
            SettingsPage.TERMS -> TermsPage(back)
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
        notice?.let { KkToast(it, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg)) }
    }
}

// ── 공용 ──────────────────────────────────────────────────────────────────

/** 하위 화면 틀: 뒤로 가기 상단 바 + 스크롤 내용(좌우 20, 묶음 사이 20). */
@Composable
private fun Page(title: String, onBack: () -> Unit, bottom: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar(title, onLeading = onBack)
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

/** 설정(Figma 7 · 설정): 프로필 + 우리 · 앱 · 데이터 · 계정 · 정보. */
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
    var anniversarySheet by remember { mutableStateOf(false) }
    var nameSheet by remember { mutableStateOf(false) }
    Page("설정", onBack) {
        val shape = RoundedCornerShape(KkRadius.lg)
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
                    if (state.linked) "${state.partnerName.withAnd()} 연결됨" + (state.daysTogether?.let { " · D+$it" } ?: "") else "아직 연결 전이에요",
                    style = KkTheme.type.bodyS, color = c.textSecondary,
                )
            }
        }
        Group("우리") {
            KkListRow("연결 정보", value = if (state.linked) state.partnerName else "연결 전", onClick = { open(SettingsPage.CONNECTION) })
            Divider()
            KkListRow("군 정보", value = state.timeline?.let { "전역 ${it.dischargeDate.dotted()}" } ?: "입력 전", onClick = { open(SettingsPage.MILITARY) })
            Divider()
            KkListRow("기념일", value = state.anniversary?.dotted() ?: "정하기", onClick = { anniversarySheet = true })
        }
        Group("앱") {
            KkListRow("테마", value = state.theme.koreanName, onClick = { open(SettingsPage.THEME) })
            Divider()
            KkListRow(
                "알림",
                value = if (state.notify.quietEnabled) "조용한 시간 ${state.notify.quietStart / 60}–${(state.notify.quietEnd / 60).toString().padStart(2, '0')}" else "켜짐",
                onClick = { open(SettingsPage.NOTIFY) },
            )
            Divider()
            KkListRow("위젯 안내", onClick = { open(SettingsPage.WIDGETS) })
        }
        Group("데이터") {
            KkListRow("백업", value = state.backup.lastAtMs?.let(::relativeTime) ?: "아직 없음", onClick = { open(SettingsPage.BACKUP) })
            Divider()
            KkListRow("복구 코드", onClick = { open(SettingsPage.RECOVERY) })
        }
        Group("계정") {
            KkListRow("로그인 계정", value = viewModel.providerLabel(state.provider), onClick = { open(SettingsPage.ACCOUNT) })
            Divider()
            if (state.linked) {
                KkListRow("상대 폰 교체 받기", onClick = {
                    scope.launch {
                        if (platform.authenticate("데이터를 넘겨주려면 본인 확인이 필요해요") != DeviceAuth.Failed) onHandOver()
                        else viewModel.notice("본인 확인을 하지 못했어요")
                    }
                })
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
    if (anniversarySheet) {
        var pick by remember { mutableStateOf(state.anniversary ?: state.linkedSince ?: Clock.System.now().toLocalDateTime(SEOUL).date) }
        KkModalSheet({ anniversarySheet = false }) {
            KkSheetHeader("기념일", "사귄 날을 고르면 D+ 와 100일마다 기념일을 세어 드려요")
            KkDatePicker(pick, { pick = it })
            KkPrimaryButton("이 날로 정하기", { viewModel.setAnniversary(pick); anniversarySheet = false }, Modifier.fillMaxWidth())
            if (state.anniversary != null) KkButton("기념일 지우기", { viewModel.setAnniversary(null); anniversarySheet = false }, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost)
        }
    }
}

// ── 연결 정보 ─────────────────────────────────────────────────────────────

@Composable
private fun ConnectionPage(state: SettingsState, onBack: () -> Unit, onOpenPairing: () -> Unit, open: (SettingsPage) -> Unit) {
    Page("연결 정보", onBack) {
        if (!state.linked) {
            Group(null) { KkListRow("연인", value = "연결 전", trailing = KkRowTrailing.None) }
            KkPrimaryButton("연인과 연결하기", onOpenPairing, Modifier.fillMaxWidth())
            return@Page
        }
        Group(null) {
            KkListRow("연인", value = state.partnerName, trailing = KkRowTrailing.None)
            Divider()
            KkListRow("연결한 날", value = state.linkedSince?.dotted() ?: "-", trailing = KkRowTrailing.None)
            Divider()
            KkListRow("함께한 날", value = state.daysTogether?.let { "D+$it" } ?: "-", trailing = KkRowTrailing.None)
        }
        Group(null) { KkListRow("연결 해제", labelColor = KkTheme.colors.statusDanger, onClick = { open(SettingsPage.UNLINK) }) }
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

// ── 테마 ─────────────────────────────────────────────────────────────────

/** 테마(Figma 7 · 테마): 네 가지 카드 + 화면 모드. */
@Composable
private fun ThemePage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    var modeSheet by remember { mutableStateOf(false) }
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
            Group(null) { KkListRow("라이트 · 다크", value = modeLabel(state.displayMode), onClick = { modeSheet = true }) }
        }
    }
    if (modeSheet) {
        KkModalSheet({ modeSheet = false }) {
            KkSheetHeader("화면 모드")
            listOf("SYSTEM", "LIGHT", "DARK").forEach { m -> KkSheetOption(modeLabel(m), state.displayMode == m, { viewModel.setDisplayMode(m); modeSheet = false }) }
        }
    }
}

private fun modeLabel(mode: String) = when (mode) {
    "LIGHT" -> "라이트"
    "DARK" -> "다크"
    else -> "시스템 설정 따름"
}

// ── 알림 ─────────────────────────────────────────────────────────────────

private val QUIET_STARTS = listOf(21 * 60, 22 * 60, 23 * 60, 0)
private val QUIET_ENDS = listOf(6 * 60, 7 * 60, 8 * 60, 9 * 60)

/** 알림(Figma 7 · 알림): 받을 종류 · 방해 금지 · 미리보기. 바꾸면 서버 정책에도 올라간다. */
@Composable
private fun NotifyPage(state: SettingsState, platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val n = state.notify
    val set: (NotificationPrefs) -> Unit = { viewModel.setNotify(it, platform) }
    var sheet by remember { mutableStateOf<String?>(null) }
    Page("알림", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text("알림 받기", style = KkTheme.type.labelM, color = KkTheme.colors.textSecondary)
            Group(null) {
                SwitchRow("편지 · 그림", n.letters) { set(n.copy(letters = it)) }
                Divider()
                SwitchRow("보고싶어", n.missYou) { set(n.copy(missYou = it)) }
                Divider()
                SwitchRow("상태", n.status) { set(n.copy(status = it)) }
                Divider()
                SwitchRow("일정 전날 (20:00)", n.eventEve) { set(n.copy(eventEve = it)) }
                Divider()
                SwitchRow("기념일 · D-day", n.anniversary) { set(n.copy(anniversary = it)) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text("방해 금지", style = KkTheme.type.labelM, color = KkTheme.colors.textSecondary)
            Group(null) {
                KkListRow("조용한 시간", value = if (n.quietEnabled) "${hm(n.quietStart)} – ${hm(n.quietEnd)}" else "끔", onClick = { sheet = "quiet" })
                Divider()
                KkListRow("보고싶어 묶어서 받기", value = if (n.missYouBundle <= 1) "매번" else "${n.missYouBundle}번마다", onClick = { sheet = "bundle" })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text("개인정보", style = KkTheme.type.labelM, color = KkTheme.colors.textSecondary)
            Group(null) { SwitchRow("알림 미리보기 숨기기", n.hidePreview) { set(n.copy(hidePreview = it)) } }
        }
    }
    when (sheet) {
        "quiet" -> {
            var start by remember { mutableStateOf(if (n.quietEnabled) n.quietStart else 23 * 60) }
            var end by remember { mutableStateOf(if (n.quietEnabled) n.quietEnd else 7 * 60) }
            KkModalSheet({ sheet = null }) {
                KkSheetHeader("조용한 시간", "이 시간에는 알림 없이 모아 두었다가 끝나면 한 번 알려요")
                Text("시작", style = KkTheme.type.labelS, color = KkTheme.colors.textSecondary)
                KkSegmentedControl(QUIET_STARTS.map(::hm), QUIET_STARTS.indexOf(start).coerceAtLeast(0), { start = QUIET_STARTS[it] })
                Text("끝", style = KkTheme.type.labelS, color = KkTheme.colors.textSecondary)
                KkSegmentedControl(QUIET_ENDS.map(::hm), QUIET_ENDS.indexOf(end).coerceAtLeast(0), { end = QUIET_ENDS[it] })
                KkPrimaryButton("이 시간으로", { set(n.copy(quietStart = start, quietEnd = end)); sheet = null }, Modifier.fillMaxWidth())
                KkButton("조용한 시간 끄기", { set(n.copy(quietStart = 0, quietEnd = 0)); sheet = null }, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost)
            }
        }
        "bundle" -> KkModalSheet({ sheet = null }) {
            KkSheetHeader("보고싶어 묶어서 받기", "연달아 눌러도 정한 횟수마다 한 번만 알려요. 숫자는 위젯 · 홈에 그대로 쌓여요")
            listOf(1, 5, 10, 20).forEach { k ->
                KkSheetOption(if (k == 1) "매번" else "${k}번마다", n.missYouBundle == k, { set(n.copy(missYouBundle = k)); sheet = null })
            }
        }
    }
}

// ── 위젯 안내 ─────────────────────────────────────────────────────────────

@Composable
private fun WidgetsPage(platform: PlatformActions, onBack: () -> Unit) {
    val c = KkTheme.colors
    var message by remember { mutableStateOf<String?>(null) }
    Page("위젯 안내", onBack) {
        Text("홈 화면에 D-day · 보고싶어 · 다음 만남 · 받은 그림을 띄워 두면 앱을 열지 않아도 바로 보여요.", style = KkTheme.type.bodyM, color = c.textSecondary)
        if (platform.pinnableWidgets.isEmpty()) {
            Group("추가하는 방법") {
                listOf("홈 화면 빈 곳을 길게 누르기", "왼쪽 위 + 누르기", "꽃신을 찾아 크기 고르기").forEachIndexed { i, step ->
                    if (i > 0) Divider()
                    KkListRow("${i + 1}. $step", trailing = KkRowTrailing.None)
                }
            }
        } else {
            Group("위젯 추가") {
                platform.pinnableWidgets.forEachIndexed { i, (kind, label) ->
                    if (i > 0) Divider()
                    KkListRow(label, onClick = {
                        message = if (platform.requestPinWidget(kind)) null else "이 런처는 앱에서 위젯을 추가할 수 없어요. 홈 화면을 길게 눌러 추가해 주세요."
                    })
                }
            }
            message?.let { Text(it, style = KkTheme.type.bodyS, color = c.textTertiary) }
        }
    }
}

// ── 백업 ─────────────────────────────────────────────────────────────────

/** 백업(Figma 7 · 백업): 마지막 백업 · 사용량 · 열쇠 보관 안내, 자동 · 셀룰러 · 지금 백업 · 복구 코드. */
@Composable
private fun BackupPage(state: SettingsState, platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit, open: (SettingsPage) -> Unit, onRestore: () -> Unit) {
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
            val fraction = (b.bytes.toFloat() / BACKUP_QUOTA_BYTES).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.bgSubtle)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(if (b.bytes > 0) fraction.coerceAtLeast(0.02f) else 0f).clip(RoundedCornerShape(3.dp)).background(c.bgBrand))
            }
            Text("${bytesLabel(b.bytes)} / 2GB 사용 · 사진은 줄여서 저장돼요", style = KkTheme.type.caption, color = c.textSecondary)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.md)).background(c.bgMilitarySubtle).padding(horizontal = KkSpacing.md, vertical = KkSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(c.statusSuccess))
                Text("백업 열쇠가 ${platform.keySyncName}에 안전하게 저장돼 있어요", style = KkTheme.type.caption, color = c.textPrimary)
            }
        }
        Group(null) {
            SwitchRow("자동 백업", state.autoBackup) { viewModel.setAutoBackup(it) }
            Divider()
            SwitchRow("셀룰러로 사진 백업", state.cellularBackup) { viewModel.setCellularBackup(it) }
            Divider()
            KkListRow(if (b.busy) "백업하는 중…" else "지금 백업하기", enabled = !b.busy, onClick = { viewModel.backupNow() })
            Divider()
            KkListRow("복구 코드", value = if (state.codeSeen) "확인함" else "확인 안 함", onClick = { open(SettingsPage.RECOVERY) })
        }
        Group(null) { KkListRow("백업에서 복원하기", onClick = onRestore) }
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
    var confirmNew by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (platform.authenticate("복구 코드를 보려면 본인 확인이 필요해요") == DeviceAuth.Failed) denied = true
        else code = viewModel.recoveryCode()
    }
    Page("복구 코드", onBack, bottom = {
        KkButton("새 코드 만들기", { confirmNew = true }, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost, enabled = code != null)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text("백업을 여는 마지막 열쇠예요", style = KkTheme.type.titleL, color = c.textPrimary)
            Text("다른 기종으로 바꾸거나 백업 열쇠를 찾을 수 없을 때 이 코드로 백업을 열 수 있어요. 꽃신도 이 코드를 알 수 없어요.", style = KkTheme.type.bodyM, color = c.textSecondary)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.lg)).background(c.bgBrandSubtle).padding(vertical = KkSpacing.xxl, horizontal = KkSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
        ) {
            val shown = code
            when {
                denied -> Text("본인 확인을 하지 못했어요", style = KkTheme.type.bodyM, color = c.textSecondary)
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

/** 로그인 계정(Figma 7 · 로그인 계정). */
@Composable
private fun AccountPage(state: SettingsState, platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit, open: (SettingsPage) -> Unit) {
    val c = KkTheme.colors
    val scope = rememberCoroutineScope()
    val auth = org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.auth.AuthGateway>()
    var linked by remember { mutableStateOf<Set<com.sadturtleman.flowershoes.core.auth.AuthProvider>>(emptySet()) }
    var sheet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<com.sadturtleman.flowershoes.core.auth.AuthProvider?>(null) }
    LaunchedEffect(auth) { linked = runCatching { auth?.linkedProviders() }.getOrNull().orEmpty() }
    val linkable = com.sadturtleman.flowershoes.core.auth.LINKABLE_PROVIDERS.filter { it.name != state.provider }
    Page("로그인 계정", onBack) {
        Group(null) {
            KkListRow("로그인", value = "${viewModel.providerLabel(state.provider)} · ${state.myName}", trailing = KkRowTrailing.None)
            Divider()
            // 다른 로그인 방법 연결(Figma): 폰을 잃어도 다른 방법으로 같은 계정 · 백업에 들어온다.
            KkListRow(
                "다른 로그인 방법 연결",
                value = linkable.joinToString(" · ") { viewModel.providerLabel(it.name) + if (it in linked) " ✓" else "" },
                onClick = { sheet = true },
            )
            Divider()
            KkListRow("이 폰", value = platform.deviceName.ifBlank { "-" }, trailing = KkRowTrailing.None)
        }
        Group(null) { KkListRow("회원 탈퇴", labelColor = c.statusDanger, onClick = { open(SettingsPage.DELETE_ACCOUNT) }) }
    }
    if (sheet) {
        KkModalSheet({ sheet = false }) {
            KkSheetHeader("다른 로그인 방법 연결", "연결해 두면 그 방법으로도 이 계정과 백업에 들어올 수 있어요")
            linkable.forEach { p ->
                val done = p in linked
                KkSheetOption(
                    viewModel.providerLabel(p.name), selected = done,
                    meta = when { done -> "연결됨"; busy == p -> "연결하는 중…"; else -> "연결하기" },
                    onClick = {
                        if (done || busy != null || auth == null) return@KkSheetOption
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
                    },
                )
            }
        }
    }
}

@Composable
private fun DeleteAccountPage(platform: PlatformActions, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val c = KkTheme.colors
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf("") }
    val ok = confirm.trim() == "탈퇴"
    Page("회원 탈퇴", onBack, bottom = {
        KkButton("탈퇴", {
            scope.launch {
                if (platform.authenticate("탈퇴하려면 본인 확인이 필요해요") != DeviceAuth.Failed) viewModel.deleteAccount(platform::deleteSyncedBackupKey)
                else viewModel.notice("본인 확인을 하지 못했어요")
            }
        }, Modifier.fillMaxWidth(), danger = true, enabled = ok)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Text("정말 탈퇴할까요?", style = KkTheme.type.titleL, color = c.textPrimary)
            Text("연결을 끊고, 이 폰의 편지 · 그림 · 일정 · 키와 서버의 백업 · 계정을 모두 지워요. 되돌릴 수 없어요.", style = KkTheme.type.bodyM, color = c.textSecondary)
        }
        KkInput(confirm, { confirm = it }, label = "확인을 위해 \"탈퇴\"를 입력해 주세요", placeholder = "탈퇴", helper = "다음 단계에서 지문 · 얼굴 또는 기기 암호를 확인해요")
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
        KkChoice("추억으로 보관", keep, { onChange(true) }, description = "읽기만 할 수 있어요. 나중에 언제든 지울 수 있어요.", radio = true)
        KkChoice("모두 지우기", !keep, { onChange(false) }, description = "이 폰의 편지 · 그림 · 일정을 모두 지워요.", radio = true)
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
            KkChoice("추억으로 보관", keep, { keep = true }, description = "읽기만 할 수 있어요. 나중에 언제든 지울 수 있어요.", radio = true)
            KkChoice("모두 지우기", !keep, { keep = false }, description = "이 폰의 편지 · 그림 · 일정을 모두 지워요.", radio = true)
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
            Text("${partnerName.withSubject()} 새 폰에서 로그인해 기록을 복원했어요. 연결은 그대로 이어지고, 이전 폰은 더 이상 소식을 받을 수 없어요.", style = KkTheme.type.bodyM, color = c.textSecondary)
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

@Composable
private fun TermsPage(onBack: () -> Unit) {
    var doc by remember { mutableStateOf<TermsDoc?>(null) }
    val open = doc
    if (open != null) {
        KkTermsScreen(open) { doc = null }
        return
    }
    Page("이용약관 · 개인정보처리방침", onBack) {
        Group(null) {
            TermsDoc.entries.forEachIndexed { i, d ->
                if (i > 0) Divider()
                KkListRow(d.title, onClick = { doc = d })
            }
        }
    }
}

private val LICENSES = listOf(
    "Kotlin · kotlinx (coroutines · serialization · datetime)" to "Apache License 2.0",
    "Compose Multiplatform · AndroidX" to "Apache License 2.0",
    "Room · DataStore" to "Apache License 2.0",
    "Koin" to "Apache License 2.0",
    "Okio" to "Apache License 2.0",
    "Firebase Kotlin SDK (GitLive) · Firebase" to "Apache License 2.0",
    "libsodium" to "ISC License",
    "Zstandard" to "BSD License",
    "Kakao SDK" to "Apache License 2.0",
    "Noto Sans KR · 나눔손글씨 펜 · 나눔명조 · Jua" to "SIL Open Font License 1.1",
    "Fluent Emoji(스티커 그림, Microsoft)" to "MIT License",
)

@Composable
private fun LicensesPage(onBack: () -> Unit) {
    Page("오픈소스 라이선스", onBack) {
        Group(null) {
            LICENSES.forEachIndexed { i, (name, license) ->
                if (i > 0) Divider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = KkTheme.type.bodyM, color = KkTheme.colors.textPrimary)
                    Text(license, style = KkTheme.type.caption, color = KkTheme.colors.textSecondary)
                }
            }
        }
        Spacer(Modifier.height(KkSpacing.lg))
    }
}
