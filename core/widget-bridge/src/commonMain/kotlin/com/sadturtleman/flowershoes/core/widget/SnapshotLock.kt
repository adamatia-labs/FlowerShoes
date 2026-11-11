package com.sadturtleman.flowershoes.core.widget

import okio.Path

/** iOS의 SharedStore와 같은 잠금. 파일 교체와 확장의 읽기-수정-쓰기가 서로 끼어들지 않는다. */
internal expect fun <T> withSnapshotLock(file: Path, block: () -> T): T
