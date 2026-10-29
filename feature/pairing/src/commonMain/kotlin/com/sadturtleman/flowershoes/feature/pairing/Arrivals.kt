package com.sadturtleman.flowershoes.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.ticker
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonSize
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkDialogHost
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.model.Letter
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** 이 기간 안에 도착해야 "도착" 순간으로 띄운다. 그 뒤에 오는 옛 편지는 평소 편지함으로. */
private val ARRIVAL_WINDOW = 7.days

/** 연결 전에 상대가 혼자 쓴 편지: 공개 시각이 연결 시각 이전이고 지우지 않은 것. */
internal fun soloLetterCount(letters: List<Letter>, partnerId: String, linkedAt: Instant): Int =
    letters.count { it.authorId == partnerId && it.deletedAt == null && it.visibleAt <= linkedAt }

/** 지금 띄울 도착 수. 연결한 지 [ARRIVAL_WINDOW] 가 지났으면 0. */
internal fun arrivalToShow(letters: List<Letter>, partnerId: String, linkedAt: Instant, now: Instant): Int =
    if (now - linkedAt > ARRIVAL_WINDOW) 0 else soloLetterCount(letters, partnerId, linkedAt)

/** "훈련소 동안 쓴 편지 23통 도착". */
internal fun arrivalTitle(letters: Int): String = "훈련소 동안 쓴 편지 ${letters}통 도착"

/**
 * 처음 연결 뒤 상대가 혼자 쓴 편지가 들어오면 한 번 "훈련소 동안 쓴 편지 N통 도착"(planning §3).
 * 첫 동기화로 편지가 몇 번에 나뉘어 들어오니, 수가 3초 동안 그대로면 그 수로 띄운다.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SoloArrivalViewModel(
    private val settings: DeviceSettings,
    profile: ProfileRepository,
    letters: LetterRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _arrived = MutableStateFlow<Int?>(null)
    val arrived: StateFlow<Int?> = _arrived.asStateFlow()

    init {
        viewModelScope.launch {
            // 띄울 수 > 0, 기다릴 것 없음(연결한 지 오래됨) = EXPIRED, 아직 = 0.
            val count = settings.soloArrivalWatch.flatMapLatest { on ->
                if (!on) flowOf(0)
                else combine(profile.observeCouple(), letters.observeInbox(), clock.ticker(1.hours)) { couple, inbox, now ->
                    val partner = couple?.partnerUserId?.takeIf { it.isNotBlank() } ?: return@combine 0
                    val linkedAt = Instant.fromEpochMilliseconds(couple.linkedAtMs)
                    if (now - linkedAt > ARRIVAL_WINDOW) EXPIRED else arrivalToShow(inbox, partner, linkedAt, now)
                }
            }
            val settled = count.distinctUntilChanged().debounce(SETTLE).filter { it != 0 }.first()
            settings.setSoloArrivalWatch(false)
            if (settled > 0) _arrived.value = settled
        }
    }

    fun dismiss() {
        _arrived.value = null
    }

    private companion object {
        val SETTLE = 3.seconds
        const val EXPIRED = -1
    }
}

/** 셸(홈 위)에 놓는다. [onOpenLetters] 편지함으로. */
@Composable
fun SoloArrivals(onOpenLetters: () -> Unit, viewModel: SoloArrivalViewModel = koinViewModel()) {
    val arrived by viewModel.arrived.collectAsStateWithLifecycle()
    val n = arrived ?: return
    ArrivalDialog(n, onOpen = { viewModel.dismiss(); onOpenLetters() }, onClose = viewModel::dismiss)
}

/** 도착 창: 봉투 · "훈련소 동안 쓴 편지 N통 도착" · [닫기] [편지함 열기]. */
@Composable
internal fun ArrivalDialog(letters: Int, onOpen: () -> Unit, onClose: () -> Unit) {
    val c = KkTheme.colors
    KkDialogHost(onDismiss = onClose) {
        val shape = RoundedCornerShape(KkRadius.xl)
        Column(
            Modifier.width(312.dp).kkElevation(KkElevation.High, shape).clip(shape).background(c.surface).padding(KkSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            KkIcon(KkIcons.Mail, tint = c.stamp, size = 32.dp)
            Text(arrivalTitle(letters), style = KkTheme.type.title, color = c.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm, Alignment.End)) {
                KkButton("닫기", onClose, style = KkButtonStyle.Ghost, size = KkButtonSize.M)
                KkButton("편지함 열기", onOpen, size = KkButtonSize.M)
            }
        }
    }
}
