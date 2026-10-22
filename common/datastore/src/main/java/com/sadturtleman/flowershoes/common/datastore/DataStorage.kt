package com.sadturtleman.flowershoes.common.datastore

import kotlinx.coroutines.flow.Flow

/**
 * 키-값 로컬 저장소.
 *
 * 원시 연산은 [observe] 와 [update] 둘이다. [read] · [write] 는 그 위에 얹은 편의 함수다.
 *
 * 왜 [write] 만으로 부족한가: "읽고 → 고치고 → 다시 쓰는" 복합 연산을 [read] + [write] 로 쪼개면
 * 그 사이에 다른 코루틴이 끼어들어 앞의 저장을 덮어쓴다.
 * 값을 이전 값으로부터 계산해야 한다면 반드시 [update] 를 쓴다 — 판단이 저장 트랜잭션 안에서 일어난다.
 *
 * 구현은 이 모듈의 DataStore 하나뿐이다. 피처는 이 인터페이스를 구현하지 않고,
 * 자기 키와 스키마만 소유한 채 주입받아 쓴다.
 */
interface DataStorage {

    /** 현재 값 + 이후 변경. 값이 없으면 null 을 흘린다. */
    fun <T> observe(key: PreferenceKey<T>): Flow<T?>

    /** 현재 값 한 번만. 이전 값에 기대는 쓰기에는 쓰지 말 것 — [update] 를 쓴다. */
    suspend fun <T> read(key: PreferenceKey<T>): T?

    /** 이전 값과 무관하게 덮어쓴다. */
    suspend fun <T> write(key: PreferenceKey<T>, value: T)

    /** 현재 값을 읽어 [transform] 결과로 바꾼다. 읽기와 쓰기가 한 트랜잭션이다. */
    suspend fun <T> update(key: PreferenceKey<T>, transform: (T?) -> T)

    suspend fun <T> remove(key: PreferenceKey<T>)
}
