package com.sadturtleman.flowershoes.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.sync.AfterUnlink
import com.sadturtleman.flowershoes.core.data.sync.UnlinkService
import com.sadturtleman.flowershoes.core.designsystem.KkTextField
import com.sadturtleman.flowershoes.core.designsystem.KkCard
import com.sadturtleman.flowershoes.core.designsystem.KkChoice
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.model.Service
import com.sadturtleman.flowershoes.core.model.ThemeAccent
import kotlin.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
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
}

enum class DeviceAuth { Passed, Failed, NoLockScreen }

object NoPlatformActions : PlatformActions

data class SettingsState(
    val theme: ThemeAccent = ThemeAccent.ROSE,
    val service: Service? = null,
    val timeline: ServiceTimeline? = null,
    val linked: Boolean = false,
)

class SettingsViewModel(
    private val settings: DeviceSettings,
    private val profile: ProfileRepository,
    private val unlink: UnlinkService,
) : ViewModel() {

    /** 되돌릴 수 없다. 화면이 확인 문구 입력(+ 기기 인증)을 받은 뒤에만 부른다. */
    fun unlink(after: AfterUnlink) = viewModelScope.launch { unlink.unlink(after) }


    val state: StateFlow<SettingsState> = combine(settings.state, profile.observeTimelines()) { d, t ->
        SettingsState(theme = d.theme, service = t.firstOrNull()?.first, timeline = t.firstOrNull()?.second, linked = d.keyEpoch > 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setTheme(theme: ThemeAccent) = viewModelScope.launch { settings.setTheme(theme) }

    /** 조기 전역 · 연장 같은 사용자 수정값(공유 데이터, LWW). null 이면 규칙으로 계산한 날로 돌아간다. */
    fun overrideDischarge(date: LocalDate?) {
        val s = state.value.service ?: return
        viewModelScope.launch { profile.saveService(s.copy(dischargeOverride = date)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    platform: PlatformActions,
    onOpenPairing: () -> Unit,
    onHandOver: () -> Unit = {},
    onRestore: () -> Unit = {},
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = KkTheme.colors
    var picking by remember { mutableStateOf(false) }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().verticalScroll(rememberScrollState()).padding(KkSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        Text("← 돌아가기", style = KkTheme.type.labelM, color = c.textSecondary, modifier = Modifier.clickable(onClick = onBack))
        Text("설정", style = KkTheme.type.titleL, color = c.textPrimary)

        Section("테마")
        Text("내 폰에만 적용돼요. 하트 · 보고싶어는 어느 테마든 로즈예요.", style = KkTheme.type.bodyS, color = c.textSecondary)
        ThemeAccent.entries.forEach { t -> KkChoice(t.label(), state.theme == t, { viewModel.setTheme(t) }) }

        Section("복무")
        val tl = state.timeline
        if (tl == null) {
            Text("입대일이 아직 없어요", style = KkTheme.type.bodyM, color = c.textTertiary)
        } else {
            KkCard {
                Text("전역일 ${tl.dischargeDate}" + if (tl.isOverridden) " (직접 수정)" else " (규칙으로 계산)", style = KkTheme.type.titleS, color = c.textPrimary)
                Text("조기 전역 · 연장처럼 규칙과 다르면 직접 고쳐요. 연인 폰에도 같이 바뀌어요.", style = KkTheme.type.bodyS, color = c.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                    Link("전역일 고치기") { picking = true }
                    if (tl.isOverridden) Link("계산값으로 되돌리기") { viewModel.overrideDischarge(null) }
                }
            }
        }

        Section("연결")
        if (state.linked) {
            Text("연인과 연결돼 있어요.", style = KkTheme.type.bodyM, color = c.textPrimary)
            Text("연인이 폰을 잃어버렸고 백업도 없다면, 이 폰이 데이터를 넘겨줄 수 있어요.", style = KkTheme.type.bodyS, color = c.textSecondary)
            Link("연인 폰 교체 받기") {
                scope.launch {
                    if (platform.authenticate("데이터를 넘겨주려면 본인 확인이 필요해요") != DeviceAuth.Failed) onHandOver()
                    else authMessage = "본인 확인을 하지 못했어요"
                }
            }
            var confirm by remember { mutableStateOf("") }
            var keep by remember { mutableStateOf(true) }
            KkCard {
                Text("연결 해제", style = KkTheme.type.titleS, color = c.statusDanger)
                Text("되돌릴 수 없어요. 해제하면 상대도 더 이상 받을 수 없어요. 상대 폰에 남은 사본은 내가 지울 수 없어요.", style = KkTheme.type.bodyS, color = c.textSecondary)
                KkChoice("추억으로 보관(읽기 전용)", keep, { keep = true })
                KkChoice("이 폰에서 모두 지우기", !keep, { keep = false })
                KkTextField(confirm, { confirm = it }, placeholder = "'연결 해제'라고 입력")
                Link(if (confirm.trim() == "연결 해제") "해제하기" else "확인 문구를 입력하면 해제할 수 있어요") {
                    if (confirm.trim() == "연결 해제") {
                        scope.launch {
                            when (platform.authenticate("연결을 해제하려면 본인 확인이 필요해요")) {
                                DeviceAuth.Failed -> authMessage = "본인 확인을 하지 못했어요"
                                DeviceAuth.NoLockScreen -> {
                                    authMessage = "이 폰에는 화면 잠금이 없어 본인 확인 없이 해제했어요"
                                    viewModel.unlink(if (keep) AfterUnlink.KEEP_AS_MEMORIES else AfterUnlink.WIPE)
                                }
                                DeviceAuth.Passed -> viewModel.unlink(if (keep) AfterUnlink.KEEP_AS_MEMORIES else AfterUnlink.WIPE)
                            }
                        }
                    }
                }
                authMessage?.let { Text(it, style = KkTheme.type.bodyS, color = c.textSecondary) }
            }
        } else {
            Link("연인과 연결하기", onOpenPairing)
        }

        if (platform.pinnableWidgets.isNotEmpty()) {
            Section("홈 화면 위젯")
            platform.pinnableWidgets.forEach { (kind, label) ->
                Link("$label 위젯 추가") {
                    pinMessage = if (platform.requestPinWidget(kind)) null else "이 런처는 앱에서 위젯을 추가할 수 없어요. 홈 화면을 길게 눌러 추가해 주세요."
                }
            }
            pinMessage?.let { Text(it, style = KkTheme.type.bodyS, color = c.textTertiary) }
        }

        Section("백업")
        BackupSection(platform)
        Link("백업에서 복원하기", onRestore)

        Section("알림")
        Text("조용한 시간 23:00–07:00에는 알림 없이 모아 두었다가 아침에 한 번 알려요.", style = KkTheme.type.bodyS, color = c.textSecondary)

        Section("정보")
        Text("오픈소스 라이선스 · 개인정보처리방침", style = KkTheme.type.bodyS, color = c.textTertiary)
        Spacer(Modifier.padding(KkSpacing.xl))
    }
    if (picking) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.timeline?.dischargeDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.overrideDischarge(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
                    picking = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("취소") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = KkTheme.type.labelM, color = KkTheme.colors.textSecondary, modifier = Modifier.padding(top = KkSpacing.lg))
}

@Composable
private fun Link(label: String, onClick: () -> Unit) {
    Text(
        label, style = KkTheme.type.labelM, color = KkTheme.colors.textBrand,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = KkSpacing.sm),
    )
}

private fun ThemeAccent.label() = when (this) {
    ThemeAccent.ROSE -> "로즈"
    ThemeAccent.OLIVE -> "올리브"
    ThemeAccent.NAVY -> "네이비"
    ThemeAccent.MONO -> "모노"
}
