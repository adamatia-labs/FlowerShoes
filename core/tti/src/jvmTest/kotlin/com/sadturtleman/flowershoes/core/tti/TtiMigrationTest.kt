package com.sadturtleman.flowershoes.core.tti

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.sadturtleman.flowershoes.core.tti.room.TtiDatabase
import com.sadturtleman.flowershoes.core.tti.room.TtiDatabaseConstructor
import com.sadturtleman.flowershoes.core.tti.room.buildTti
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.test.assertFailsWith

/**
 * 계측 DB 도 앱 DB 와 같은 규칙이다. 스키마를 바꾸면 버전을 올리고 이전 경로를 넣는다.
 * 내보낸 스키마 JSON(schemas/)으로 옛 버전 DB 를 만들어 현재 버전까지 이전해 본다.
 */
class TtiMigrationTest {

    @get:Rule
    val dir = TemporaryFolder()

    private val schemaRoot: Path = Paths.get(System.getProperty("room.schemaDirectory"))
    private val schemas: Path = schemaRoot.resolve(TtiDatabase::class.qualifiedName!!)

    private fun helper(file: Path) = MigrationTestHelper(
        schemaDirectoryPath = schemaRoot,
        databasePath = file,
        driver = BundledSQLiteDriver(),
        databaseClass = TtiDatabase::class,
        databaseFactory = { TtiDatabaseConstructor.initialize() },
    )

    @Test
    fun `첫 버전부터 현재까지 모든 버전의 스키마가 남아 있다`() {
        val missing = (1..TtiDatabase.VERSION).filterNot { schemas.resolve("$it.json").exists() }
        assertEquals(emptyList<Int>(), missing)
    }

    @Test
    fun `옛 버전마다 현재 버전으로 오는 이전 경로가 있다`() {
        (1 until TtiDatabase.VERSION).forEach { from ->
            val helper = helper(dir.root.toPath().resolve("from-$from.db"))
            helper.createDatabase(from).close()
            helper.runMigrationsAndValidate(TtiDatabase.VERSION, TtiDatabase.MIGRATIONS).close()
        }
    }

    @Test
    fun `현재 스키마로 만든 DB 를 빌더가 그대로 연다`() = runTest {
        val file = dir.root.toPath().resolve("current.db")
        helper(file).createDatabase(TtiDatabase.VERSION).use {
            it.execSQL("INSERT INTO tti_record (id, pageName, createdAt) VALUES ('t', 'home', 1)")
        }

        val database = Room.databaseBuilder<TtiDatabase>(name = file.toString()).buildTti()
        try {
            assertEquals("home", database.ttiDao().findRecord("t")?.record?.pageName)
        } finally {
            database.close()
        }
    }

    @Test
    fun `이전 경로가 없으면 표를 지우지 않고 실패한다`() = runTest {
        val file = dir.root.toPath().resolve("unknown.db")
        BundledSQLiteDriver().open(file.toString()).use {
            it.execSQL("CREATE TABLE keep (v TEXT)")
            it.execSQL("INSERT INTO keep VALUES ('data')")
            it.execSQL("PRAGMA user_version = ${TtiDatabase.VERSION + 1}")
        }

        val database = Room.databaseBuilder<TtiDatabase>(name = file.toString()).buildTti()
        try {
            assertFailsWith<IllegalStateException> { database.ttiDao().findAllRecords() }
        } finally {
            database.close()
        }
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.prepare("SELECT v FROM keep").use { assertTrue(it.step()); assertEquals("data", it.getText(0)) }
        }
    }
}
