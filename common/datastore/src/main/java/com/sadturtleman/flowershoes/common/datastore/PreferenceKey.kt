package com.sadturtleman.flowershoes.common.datastore

/**
 * 저장 키. 키 이름과 값 타입을 한 쌍으로 묶는다.
 *
 * androidx 의 `Preferences.Key` 를 그대로 내보내지 않는다.
 * 그러면 키를 선언하는 모든 피처 모듈이 androidx.datastore 에 딸려 붙어,
 * [DataStorage] 를 인터페이스로 둔 의미가 사라지기 때문이다.
 *
 * 지원 타입은 DataStore Preferences 가 다룰 수 있는 것과 같다.
 * 그 밖의 모양(목록 · 객체)은 [StringKey] 에 직렬화해 담는다.
 */
sealed class PreferenceKey<T>(val name: String) {
    class IntKey(name: String) : PreferenceKey<Int>(name)
    class LongKey(name: String) : PreferenceKey<Long>(name)
    class FloatKey(name: String) : PreferenceKey<Float>(name)
    class DoubleKey(name: String) : PreferenceKey<Double>(name)
    class BooleanKey(name: String) : PreferenceKey<Boolean>(name)
    class StringKey(name: String) : PreferenceKey<String>(name)
    class StringSetKey(name: String) : PreferenceKey<Set<String>>(name)
}
