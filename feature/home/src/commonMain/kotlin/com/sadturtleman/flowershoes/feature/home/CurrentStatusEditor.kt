package com.sadturtleman.flowershoes.feature.home

import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.data.StatusEditChangedException
import com.sadturtleman.flowershoes.core.data.StatusEditSession
import com.sadturtleman.flowershoes.core.data.StatusEditorStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

enum class StatusDuration(val label: String) {
    KEEP("기존 시각 유지"), HOUR("1시간"), TODAY("오늘 자정까지"), TOMORROW_MORNING("내일 오전 8시까지"), UNTIL_CLEARED("직접 지울 때까지");

    fun expiresAt(now: Instant, previous: Instant?): Instant? {
        val tomorrow = now.toLocalDateTime(SEOUL).date.plus(DatePeriod(days = 1))
        return when (this) {
            KEEP -> previous
            HOUR -> now + 1.hours
            TODAY -> tomorrow.atStartOfDayIn(SEOUL)
            TOMORROW_MORNING -> LocalDateTime(tomorrow, LocalTime(8, 0)).toInstant(SEOUL)
            UNTIL_CLEARED -> null
        }
    }
}

data class CurrentStatusEditState(
    val label: String = "",
    val duration: StatusDuration = StatusDuration.HOUR,
    val previousExpiry: Instant? = null,
    val hadStatus: Boolean = false,
    val ready: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = ready && !saving && label.trim().isNotEmpty() && label.length <= StatusEditorStore.MAX_LABEL_LENGTH
}

class CurrentStatusEditor(
    private val scope: CoroutineScope,
    private val store: StatusEditorStore,
    private val ownerUid: String,
    private val clock: Clock = Clock.System,
) {
    private val _state = MutableStateFlow<CurrentStatusEditState?>(null)
    val state = _state.asStateFlow()
    private var session: StatusEditSession? = null
    private var opening = 0

    fun open() {
        if (_state.value != null) return
        val request = ++opening
        _state.value = CurrentStatusEditState()
        scope.launch {
            try {
                val permission = store.open(ownerUid) ?: throw StatusEditChangedException()
                val current = store.observe(ownerUid).first()
                if (request != opening) return@launch
                session = permission
                _state.value = CurrentStatusEditState(
                    label = current?.label.orEmpty(), previousExpiry = current?.expiresAt, hadStatus = current != null, ready = true,
                    duration = if (current == null) StatusDuration.HOUR else if (current.expiresAt == null) StatusDuration.UNTIL_CLEARED else StatusDuration.KEEP,
                )
            } catch (changed: StatusEditChangedException) {
                if (request == opening) _state.value = CurrentStatusEditState(error = changed.message)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (request == opening) _state.value = CurrentStatusEditState(error = "상태를 불러오지 못했어요. 닫은 뒤 다시 열어 주세요")
            }
        }
    }

    fun close() {
        if (_state.value?.saving == true) return
        opening++
        session = null
        _state.value = null
    }

    fun label(value: String) {
        val s = _state.value ?: return
        if (s.ready && !s.saving) _state.value = s.copy(label = value.take(StatusEditorStore.MAX_LABEL_LENGTH), error = null)
    }

    fun duration(value: StatusDuration) {
        val s = _state.value ?: return
        if (s.ready && !s.saving) _state.value = s.copy(duration = value, error = null)
    }

    fun save(color: Long) = submit(clear = false, color = color)
    fun clear() = submit(clear = true, color = 0)

    private fun submit(clear: Boolean, color: Long) {
        val s = _state.value ?: return
        val permission = session ?: return
        if (!s.ready || s.saving || (!clear && !s.canSave) || (clear && !s.hadStatus)) return
        _state.value = s.copy(saving = true, error = null)
        scope.launch {
            try {
                if (clear) store.clear(permission) else store.save(permission, s.label, color, s.duration.expiresAt(clock.now(), s.previousExpiry))
                session = null
                _state.value = null
            } catch (changed: StatusEditChangedException) {
                session = null
                _state.value = s.copy(ready = false, error = changed.message)
            } catch (cancelled: CancellationException) {
                _state.value = s.copy(saving = false)
                throw cancelled
            } catch (invalid: IllegalArgumentException) {
                _state.value = s.copy(error = invalid.message)
            } catch (_: Exception) {
                _state.value = s.copy(error = "저장하지 못했어요. 다시 시도해 주세요")
            }
        }
    }
}
