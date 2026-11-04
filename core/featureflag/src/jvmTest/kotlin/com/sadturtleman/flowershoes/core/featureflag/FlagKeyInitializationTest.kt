package com.sadturtleman.flowershoes.core.featureflag

import java.net.URLClassLoader
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 앱 기동 때 어떤 키가 먼저 접근돼도 목록에 초기화 중인 null 이 들어가면 안 된다. */
class FlagKeyInitializationTest {
    @Test
    fun `각 플래그가 첫 접근이어도 전체 목록을 초기화할 수 있다`() {
        val base = FlagKey::class.java.name
        val location = FlagKey::class.java.protectionDomain.codeSource.location
        val keys = listOf("MinVersion", "Maintenance", "NewOnboarding", "HomePageSize", "NetworkRetryCount", "FetchTimeoutMillis", "RetryBackoffMillis", "HomeBanner", "HomeLayoutAb")
        keys.forEach { first ->
            // 매번 새 프로세스처럼 해당 클래스만 새로 로드해 JVM 정적 초기화 순서를 검증한다.
            val loader = object : URLClassLoader(arrayOf(location), FlagKey::class.java.classLoader) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    if (name != base && !name.startsWith("${base}\$")) return super.loadClass(name, resolve)
                    synchronized(getClassLoadingLock(name)) {
                        val loaded = findLoadedClass(name) ?: findClass(name)
                        if (resolve) resolveClass(loaded)
                        return loaded
                    }
                }
            }
            loader.use {
                val firstClass = Class.forName("$base\$$first", true, loader)
                assertNotNull(firstClass.getField("INSTANCE").get(null), first)
                val companion = Class.forName(base, true, loader).getField("Companion").get(null)
                val all = companion.javaClass.getMethod("getAll").invoke(companion) as List<*>
                assertTrue(all.size == keys.size && all.all { it != null }, first)
            }
        }
    }
}
