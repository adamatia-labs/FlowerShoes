package com.sadturtleman.flowershoes.core.widget

import okio.Path

// Android 위젯은 스냅샷을 읽기만 한다. 쓰기는 앱 발행기 하나가 담당한다.
internal actual fun <T> withSnapshotLock(file: Path, block: () -> T): T = block()
