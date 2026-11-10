package com.sadturtleman.flowershoes.core.data.sync

/**
 * 기존 항목은 지우지 않고 갱신한다. 갱신할 항목이 없을 때만 추가하며,
 * 추가 직전 다른 작업이 항목을 만든 경우에는 충돌을 알리고 그 값을 보존한다.
 * 각 작업은 실패 시 예외를 던지고, false는 각각 항목 없음/중복만 뜻한다.
 */
internal inline fun upsertKeychainItem(update: () -> Boolean, add: () -> Boolean) {
    if (update() || add()) return
    error("키체인 항목이 동시에 생성되어 저장하지 못했어요")
}
