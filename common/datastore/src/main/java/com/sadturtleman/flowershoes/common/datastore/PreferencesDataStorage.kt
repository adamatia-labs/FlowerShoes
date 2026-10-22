package com.sadturtleman.flowershoes.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore Preferences 기반 [DataStorage] 구현.
 *
 * 잠금을 따로 두지 않는다. DataStore 가 파일마다 단일 writer 로 쓰기를 직렬화하므로
 * [update] 의 읽기-쓰기 사이에 다른 쓰기가 끼어들지 않는다.
 * (예전 SharedPreferences 구현의 Mutex 가 하던 일)
 *
 * IO 스레드 전환도 DataStore 가 생성 시 받은 스코프에서 처리한다 — DataStoreModule 참고.
 */
internal class PreferencesDataStorage(
    private val dataStore: DataStore<Preferences>,
) : DataStorage {

    override fun <T> observe(key: PreferenceKey<T>): Flow<T?> = dataStore.data
        // 저장 파일을 못 읽으면(깨짐 · 권한) 빈 값으로 시작한다. 화면이 안 뜨는 것보다 낫다.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { preferences -> preferences[key.toPreferencesKey()] }
        .distinctUntilChanged()

    override suspend fun <T> read(key: PreferenceKey<T>): T? = observe(key).first()

    override suspend fun <T> write(key: PreferenceKey<T>, value: T) {
        dataStore.edit { preferences -> preferences[key.toPreferencesKey()] = value }
    }

    override suspend fun <T> update(key: PreferenceKey<T>, transform: (T?) -> T) {
        dataStore.edit { preferences ->
            val preferencesKey = key.toPreferencesKey()
            preferences[preferencesKey] = transform(preferences[preferencesKey])
        }
    }

    override suspend fun <T> remove(key: PreferenceKey<T>) {
        dataStore.edit { preferences -> preferences.remove(key.toPreferencesKey()) }
    }
}

/**
 * 우리 키를 androidx 키로 옮긴다.
 * [PreferenceKey] 의 각 갈래가 자기 값 타입을 이미 못박고 있어 캐스팅은 안전하다.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> PreferenceKey<T>.toPreferencesKey(): Preferences.Key<T> = when (this) {
    is PreferenceKey.IntKey -> intPreferencesKey(name)
    is PreferenceKey.LongKey -> longPreferencesKey(name)
    is PreferenceKey.FloatKey -> floatPreferencesKey(name)
    is PreferenceKey.DoubleKey -> doublePreferencesKey(name)
    is PreferenceKey.BooleanKey -> booleanPreferencesKey(name)
    is PreferenceKey.StringKey -> stringPreferencesKey(name)
    is PreferenceKey.StringSetKey -> stringSetPreferencesKey(name)
} as Preferences.Key<T>
