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

package org.ghostsinthelab.app.rakurakuime.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3: add static corpus-derived weight columns to the `dictionary`
 * table. Existing user data (learned frequencies) is preserved untouched;
 * the new columns default to 0 and are populated either by:
 *
 *  - shipping a v3 prebuilt asset that already has them filled, or
 *  - the next CIN re-import (`CinParser.parseAndPopulate`) which seeds
 *    weights from `assets/85rest01.csv` and `assets/85rest02.csv`.
 *
 * The FTS4 shadow table `dictionary_fts` does not reference the new
 * columns, so it does not need rebuilding.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `dictionary` ADD COLUMN `character_weight` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE `dictionary` ADD COLUMN `phrase_weight` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/**
 * v3 → v4: introduce the `user_phrases` table backing the User Phrase
 * Manager. The bundled v3 asset (corpus + FTS shadow) is untouched; the
 * new table is created empty on first launch after upgrade.
 *
 * The unique `(character, keystroke)` index lets `INSERT … OR IGNORE`
 * dedupe naturally — a user pressing "add" twice on the same pair is a
 * no-op rather than an error.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_phrases` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`character` TEXT NOT NULL, " +
                "`keystroke` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_user_phrases_keystroke` " +
                "ON `user_phrases` (`keystroke`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_user_phrases_character_keystroke` " +
                "ON `user_phrases` (`character`, `keystroke`)"
        )
    }
}
