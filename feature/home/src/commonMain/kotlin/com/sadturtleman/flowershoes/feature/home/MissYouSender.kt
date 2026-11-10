package com.sadturtleman.flowershoes.feature.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 보낼 작업을 기기에 저장하는 동안 중복 입력과 잘못된 성공 표시를 막는다. */
enum class MissYouSendStatus { IDLE, SAVING, SAVED, FAILED }

internal class MissYouSender(
    private val scope: CoroutineScope,
    private val save: suspend () -> Unit,
    private val feedbackMs: Long = 2_000,
) {
    private val _status = MutableStateFlow(MissYouSendStatus.IDLE)
    val status: StateFlow<MissYouSendStatus> = _status.asStateFlow()

    fun send() {
        if (!_status.compareAndSet(MissYouSendStatus.IDLE, MissYouSendStatus.SAVING)) return
        scope.launch {
            try {
                save()
                _status.value = MissYouSendStatus.SAVED
            } catch (cancelled: CancellationException) {
                _status.value = MissYouSendStatus.IDLE
                throw cancelled
            } catch (_: Exception) {
                _status.value = MissYouSendStatus.FAILED
            }
            delay(feedbackMs)
            _status.value = MissYouSendStatus.IDLE
        }
    }
}
