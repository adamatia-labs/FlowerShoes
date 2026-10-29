package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** 초대 링크 연결(#15): 링크 만들기 → 기다림 → 수락됨 / 만료 · 사용됨 / 자기 링크 / 이미 연결됨. */
class PairingSessionTest {

    private val cloud = InMemoryCloud()
    private val phones = mutableListOf<Phone>()

    private inner class Phone(val userId: String, val deviceId: String, clock: Clock = Clock.System) {
        val db: AppDatabase = inMemoryDatabaseBuilder().buildKkotsin()
        val vault = InMemoryKeyVault()
        val pending = InMemoryPendingInvite()
        val identity = object : Identity {
            override val userId = this@Phone.userId
            override val deviceId = this@Phone.deviceId
            override val keyEpoch = 1
        }
        val pairing = PairingSession(db, identity, vault, cloud, clock, pending)

        /** 앱을 껐다 켠 것: 메모리 상태는 사라지고 기기 키 · DB · 보낸 링크 기록만 남는다. */
        fun relaunch() = PairingSession(db, identity, vault, cloud, Clock.System, pending)

        init {
            phones += this
        }
    }

    @BeforeTest
    fun setUp() = Sodium.init()

    @AfterTest
    fun tearDown() = phones.forEach { it.db.close() }

    @Test
    fun `링크를 누른 사람과 연결되고 양쪽이 같은 커플 키를 얻는다`() = runTest {
        val jisu = Phone("jisu", "dev-a")
        val minjun = Phone("minjun", "dev-b")
        val waiting = assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수"))
        assertEquals(22, waiting.token.length)

        val accepted = minjun.pairing.accept(waiting.token)
        assertIs<PairingState.Linked>(accepted)
        assertIs<PairingState.Linked>(jisu.pairing.poll())

        assertContentEquals(jisu.vault.currentCoupleKey()!!.export(), minjun.vault.currentCoupleKey()!!.export())
        assertEquals("minjun", jisu.db.coupleDao().get()?.partnerUserId)
        assertEquals("jisu", minjun.db.coupleDao().get()?.partnerUserId)
        assertEquals(listOf("jisu"), cloud.acceptedPushes, "수락되면 보낸 사람에게 푸시")
    }

    @Test
    fun `받는 쪽은 수락 전 보낸 사람 이름을 안다`() = runTest {
        val jisu = Phone("jisu", "dev-a")
        val minjun = Phone("minjun", "dev-b")
        val token = assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수")).token
        cloud.holdAccepts = true // 서버 함수가 아직 돌지 않았다
        assertEquals("지수", assertIs<PairingState.Accepting>(minjun.pairing.accept(token)).inviterName)
        cloud.releaseAccepts()
        assertIs<PairingState.Linked>(minjun.pairing.poll())
    }

    @Test
    fun `한 번 쓴 링크는 만료로 보인다`() = runTest {
        val jisu = Phone("jisu", "dev-a")
        val token = assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수")).token
        Phone("minjun", "dev-b").pairing.accept(token)
        assertIs<PairingState.Expired>(Phone("mallory", "dev-m").pairing.accept(token))
    }

    @Test
    fun `새 링크를 만들면 이전 링크는 만료된다`() = runTest {
        val jisu = Phone("jisu", "dev-a")
        val old = assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수")).token
        val new = assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수")).token
        val minjun = Phone("minjun", "dev-b")
        assertIs<PairingState.Expired>(minjun.pairing.accept(old))
        assertIs<PairingState.Linked>(minjun.pairing.accept(new))
    }

    @Test
    fun `7일이 지난 링크는 만료된다`() = runTest {
        val eightDaysAgo = object : Clock {
            override fun now(): Instant = Clock.System.now() - 8.days
        }
        val jisu = Phone("jisu", "dev-a", eightDaysAgo)
        val token = assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수")).token
        assertIs<PairingState.Expired>(Phone("minjun", "dev-b").pairing.accept(token))
        assertIs<PairingState.Expired>(jisu.relaunch().poll(), "보낸 쪽도 만료를 본다")
    }

    @Test
    fun `내가 보낸 링크를 내가 누르면 자기 링크다`() = runTest {
        val jisu = Phone("jisu", "dev-a")
        val token = assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수")).token
        assertEquals(token, assertIs<PairingState.OwnLink>(jisu.relaunch().accept(token)).token)
    }

    @Test
    fun `자기 링크를 눌러도 보낸 링크는 계속 기다린다`() = runTest {
        val jisu = Phone("jisu", "dev-a")
        val token = assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수")).token
        assertIs<PairingState.OwnLink>(jisu.pairing.accept(token))
        Phone("minjun", "dev-b").pairing.accept(token)
        assertIs<PairingState.Linked>(jisu.pairing.poll())
    }

    @Test
    fun `서버가 처리하기 전 두 번째로 누른 사람은 만료를 본다`() = runTest {
        val token = assertIs<PairingState.Waiting>(Phone("jisu", "dev-a").pairing.createLink("지수")).token
        cloud.holdAccepts = true
        assertIs<PairingState.Accepting>(Phone("minjun", "dev-b").pairing.accept(token))
        assertIs<PairingState.Expired>(Phone("mallory", "dev-m").pairing.accept(token))
    }

    @Test
    fun `이미 연결돼 있으면 링크를 만들지도 받지도 않는다`() = runTest {
        val jisu = Phone("jisu", "dev-a")
        val minjun = Phone("minjun", "dev-b")
        minjun.pairing.accept(assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수")).token)
        jisu.pairing.poll()

        val other = assertIs<PairingState.Waiting>(Phone("hana", "dev-h").pairing.createLink("하나")).token
        assertIs<PairingState.AlreadyLinked>(minjun.relaunch().accept(other))
        assertIs<PairingState.AlreadyLinked>(jisu.relaunch().createLink("지수"))
    }

    @Test
    fun `앱을 다시 켜도 보낸 링크의 수락을 이어 받는다`() = runTest {
        val jisu = Phone("jisu", "dev-a")
        val token = assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수")).token
        val restarted = jisu.relaunch()
        assertEquals(token, assertIs<PairingState.Waiting>(restarted.poll()).token)
        Phone("minjun", "dev-b").pairing.accept(token)
        assertIs<PairingState.Linked>(restarted.poll())
        assertIs<PairingState.Idle>(jisu.relaunch().poll(), "연결되면 보낸 링크 기록을 지운다")
    }

    @Test
    fun `수락 뒤 앱이 꺼졌다가 같은 링크를 다시 누르면 연결을 마친다`() = runTest {
        val jisu = Phone("jisu", "dev-a")
        val minjun = Phone("minjun", "dev-b")
        val token = assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수")).token
        minjun.pairing.accept(token)
        minjun.db.coupleDao().clear() // 커플 행을 쓰기 전에 꺼진 것처럼
        assertIs<PairingState.Linked>(minjun.relaunch().accept(token))
    }

    @Test
    fun `형식이 아닌 토큰은 만료로 본다`() = runTest {
        assertIs<PairingState.Expired>(Phone("minjun", "dev-b").pairing.accept("K7M2Q9XA"))
    }
}
