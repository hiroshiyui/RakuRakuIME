/*
 * RakuRaku IME - EZ Input Method for Android
 * Copyright (C) 2026  RakuRaku IME Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.ghostsinthelab.app.rakurakuime

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.ghostsinthelab.app.rakurakuime.data.MIGRATION_2_3
import org.ghostsinthelab.app.rakurakuime.data.MIGRATION_3_4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImeDatabase::class.java,
    )

    /**
     * Verifies the v2 → v3 migration:
     *  - existing user-learned `frequency` data is preserved exactly,
     *  - the two new weight columns are added with default 0,
     *  - the post-migration schema matches the exported v3 JSON.
     */
    @Test
    @Throws(IOException::class)
    fun migrate2To3_preservesUserDataAndAddsWeightColumns() {
        // Create v2 schema and seed a row that mimics post-learning state.
        helper.createDatabase(testDb, 2).use { db ->
            db.execSQL(
                "INSERT INTO `dictionary` (`keystroke`, `character`, `frequency`) " +
                    "VALUES (?, ?, ?)",
                arrayOf("8", "八", 7),
            )
        }

        // Run the migration. MigrationTestHelper validates the resulting
        // schema against schemas/3.json automatically when validateDroppedTables=true.
        val migrated = helper.runMigrationsAndValidate(
            testDb,
            3,
            /* validateDroppedTables = */ true,
            MIGRATION_2_3,
        )

        migrated.query(
            "SELECT keystroke, character, frequency, character_weight, phrase_weight " +
                "FROM dictionary"
        ).use { c ->
            assertTrue("expected exactly one row", c.moveToFirst())
            assertEquals("8", c.getString(0))
            assertEquals("八", c.getString(1))
            assertEquals(7, c.getInt(2))
            assertEquals(0, c.getInt(3)) // character_weight default
            assertEquals(0, c.getInt(4)) // phrase_weight default
            assertTrue("expected exactly one row", !c.moveToNext())
        }
    }

    /**
     * Verifies the v3 → v4 migration:
     *  - dictionary data is preserved,
     *  - the new `user_phrases` table exists, is empty, and accepts inserts,
     *  - the post-migration schema matches schemas/4.json.
     */
    @Test
    @Throws(IOException::class)
    fun migrate3To4_createsUserPhrasesTable() {
        helper.createDatabase(testDb, 3).use { db ->
            db.execSQL(
                "INSERT INTO `dictionary` (`keystroke`, `character`, `frequency`, `character_weight`, `phrase_weight`) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf("8", "八", 3, 0, 0),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            testDb,
            4,
            /* validateDroppedTables = */ true,
            MIGRATION_3_4,
        )

        // dictionary survives
        migrated.query("SELECT COUNT(*) FROM dictionary").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        // user_phrases is empty and accepts an insert
        migrated.query("SELECT COUNT(*) FROM user_phrases").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        migrated.execSQL(
            "INSERT INTO user_phrases (character, keystroke, created_at) VALUES (?, ?, ?)",
            arrayOf("輕鬆", "2mm/", 12345L),
        )
        migrated.query("SELECT character, keystroke FROM user_phrases").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("輕鬆", c.getString(0))
            assertEquals("2mm/", c.getString(1))
        }
    }
}
