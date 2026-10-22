package com.sadturtleman.flowershoes.common.presentation.ui.component

import android.os.SystemClock
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** 클릭 하나가 처리된 뒤 이 시간 동안 같은 자리의 클릭을 무시한다. */
const val CLICK_DEBOUNCE_MILLIS: Long = 300L

/**
 * 중복 클릭을 막은 클릭 콜백.
 *
 * **첫 클릭은 즉시 실행하고** 이어지는 [debounceMillis] 동안의 클릭만 버린다.
 * (끝에서 실행하는 진짜 debounce 로 만들면 모든 탭에 0.3초 지연이 생겨 버튼이 굼떠진다)
 *
 * 상태는 이 modifier 가 붙은 자리마다 따로 잡히므로, 같은 카드를 연타하면 막히지만
 * 서로 다른 카드를 빠르게 누르는 것은 둘 다 처리된다.
 *
 * 화면 이동 · 저장 토글처럼 두 번 실행되면 곤란한 동작이 대부분이라 클릭의 기본으로 쓴다.
 * `selectable` 처럼 다른 modifier 를 쓸 때는 이 함수로 콜백만 감싼다.
 */
@Composable
fun rememberDebouncedClick(
    debounceMillis: Long = CLICK_DEBOUNCE_MILLIS,
    onClick: () -> Unit,
): () -> Unit {
    // 매 재구성마다 최신 람다를 보게 하되, 반환하는 함수 자체는 새로 만들지 않는다.
    val latestOnClick by rememberUpdatedState(onClick)
    val lastClickAt = remember { mutableLongStateOf(0L) }
    return remember(debounceMillis) {
        {
            // 사용자가 기기 시각을 바꿔도 흔들리지 않도록 단조 증가 시계를 쓴다.
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickAt.longValue >= debounceMillis) {
                lastClickAt.longValue = now
                latestOnClick()
            }
        }
    }
}

/**
 * 중복 클릭을 막은 클릭 영역. 앱의 모든 클릭은 이 경로를 지난다.
 *
 * @param showRipple false 면 눌림 표현을 끈다. 검색 바 안의 지우기 버튼처럼
 *  원본 디자인에 눌림 표현이 없는 자리에 쓴다.
 */
@Composable
fun Modifier.debouncedClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    showRipple: Boolean = true,
    debounceMillis: Long = CLICK_DEBOUNCE_MILLIS,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = if (showRipple) LocalIndication.current else null,
    enabled = enabled,
    role = role,
    onClick = rememberDebouncedClick(debounceMillis, onClick),
)
