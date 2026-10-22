package com.sadturtleman.flowershoes.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.sadturtleman.flowershoes.logging.domain.BizEvent
import com.sadturtleman.flowershoes.logging.domain.BizLogger

/**
 * 화면 트리에 내려주는 기록기. Activity 가 주입받은 것을 그대로 꽂는다.
 * 꽂히지 않았으면 [ViewEnterEffect] 가 아무 일도 하지 않는다(계측이 화면을 막지 않는다).
 */
val LocalBizLogger = staticCompositionLocalOf<BizLogger?> { null }

/**
 * 백스택 맨 위가 바뀔 때마다 [BizEvent.ViewEnter] 를 남긴다.
 *
 * 화면마다 따로 부르지 않고 여기 한 곳에 두는 이유는 두 가지다 —
 * 진입 경로가 여럿이라(탭 · 딥링크 · 뒤로 가기 · 백스택 복원) 화면 안에서는 빠뜨리기 쉽고,
 * 직전 화면([BizEvent.ViewEnter.from])은 백스택을 쥔 이 자리에서만 알 수 있다.
 *
 * 뒤로 가서 돌아온 것도 진입으로 센다 — 사용자가 그 화면을 다시 보고 있는 것은 같다.
 *
 * @param currentPath 지금 백스택 맨 위 화면의 경로. 아직 없으면 null.
 */
@Composable
fun ViewEnterEffect(currentPath: String?) {
    val bizLogger = LocalBizLogger.current ?: return
    // 앱을 열자마자 들어온 첫 화면과, 프로세스가 죽었다 복원된 화면은 직전이 없다.
    var previousPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPath) {
        val path = currentPath ?: return@LaunchedEffect
        bizLogger.record(path, BizEvent.ViewEnter(from = previousPath))
        previousPath = path
    }
}
