package com.sadturtleman.flowershoes.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 출시 뒤 스키마 변경 규칙. 버전을 올리면 옛 버전마다 AutoMigration 또는 Migration 이 있어야 한다.
 *
 * 내보낸 스키마 JSON(schemas/)으로 옛 버전 DB 를 실제로 만들고, 현재 버전까지 이전한 뒤 스키마가 맞는지 확인한다.
 * 경로가 하나라도 빠지면 여기서 실패한다 — 파괴적 폴백이 없으므로 사용자 폰에서는 앱이 열리지 않는다.
 */
class MigrationTest {

    @get:Rule
    val dir = TemporaryFolder()

    private val schemaRoot: Path = Paths.get(System.getProperty("room.schemaDirectory"))
    private val schemas: Path = schemaRoot.resolve(AppDatabase::class.qualifiedName!!)

    private fun helper(file: Path) = MigrationTestHelper(
        schemaDirectoryPath = schemaRoot,
        databasePath = file,
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
        databaseFactory = { AppDatabaseConstructor.initialize() },
    )

    @Test
    fun `출시 기준부터 현재까지 모든 버전의 스키마가 남아 있다`() {
        val missing = (1..AppDatabase.VERSION).filterNot { schemas.resolve("$it.json").exists() }
        assertEquals(emptyList(), missing, "지운 스키마 JSON 이 있으면 그 버전에서 오는 이전을 검증할 수 없다")
    }

    /** 버전 1 에서는 돌 것이 없다. 버전을 올리는 순간부터 옛 버전마다 경로를 요구한다. */
    @Test
    fun `옛 버전마다 현재 버전으로 오는 이전 경로가 있다`() {
        (1 until AppDatabase.VERSION).forEach { from ->
            val file = dir.root.toPath().resolve("from-$from.db")
            val helper = helper(file)
            helper.createDatabase(from).close()
            helper.runMigrationsAndValidate(AppDatabase.VERSION, AppDatabase.MIGRATIONS).close()
        }
    }

    @Test
    fun `현재 스키마로 만든 DB 를 앱 빌더가 그대로 연다`() = runTest {
        val file = dir.root.toPath().resolve("current.db")
        helper(file).createDatabase(AppDatabase.VERSION).use { it.execSQL("INSERT INTO meta (key, value) VALUES ('clock', 7)") }

        val db = Room.databaseBuilder<AppDatabase>(name = file.toString()).buildKkotsin()
        try {
            assertEquals(7L, db.syncDao().meta("clock"))
        } finally {
            db.close()
        }
    }

    /** 경로가 없을 때 표를 지우고 새로 만들면 사용자 데이터가 사라진다. 열리지 않는 편이 낫다. */
    @Test
    fun `이전 경로가 없으면 표를 지우지 않고 실패한다`() = runTest {
        val file = dir.root.toPath().resolve("unknown.db")
        BundledSQLiteDriver().open(file.toString()).use {
            it.execSQL("CREATE TABLE keep (v TEXT)")
            it.execSQL("INSERT INTO keep VALUES ('data')")
            it.execSQL("PRAGMA user_version = ${AppDatabase.VERSION + 1}")
        }

        val db = Room.databaseBuilder<AppDatabase>(name = file.toString()).buildKkotsin()
        try {
            assertFailsWith<IllegalStateException> { db.syncDao().meta("clock") }
        } finally {
            db.close()
        }
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.prepare("SELECT v FROM keep").use {
                assertTrue(it.step())
                assertEquals("data", it.getText(0))
            }
        }
    }
}
