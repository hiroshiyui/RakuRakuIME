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
