package com.seasaw.theta.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.seasaw.theta.core.data.db.ThetaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Users upgrading from the first release must keep their open tabs. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ThetaDatabase::class.java)

    @Test
    fun version1TabsSurviveAndGainNoGroup() {
        helper.createDatabase(NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO tabs (id, url, title, position, isSelected, lastAccessedAt) " +
                    "VALUES ('t1', 'https://a.example', 'A', 0, 1, 5)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 2, true).use { db ->
            db.query("SELECT id, groupId FROM tabs").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("t1", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
            db.query("SELECT COUNT(*) FROM tab_groups").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val NAME = "migration-test.db"
    }
}
