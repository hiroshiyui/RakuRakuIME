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

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object CinParser {
    suspend fun parseAndPopulate(context: Context, database: ImeDatabase) = withContext(Dispatchers.IO) {
        val assetManager = context.assets
        val inputStream = assetManager.open("ezbig.utf-8.cin")
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        
        var inKeyname = false
        val entries = mutableListOf<DictionaryEntry>()
        
        var line: String? = reader.readLine()
        while (line != null) {
            line = line.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                line = reader.readLine()
                continue
            }

            if (line == "%keyname begin") {
                inKeyname = true
            } else if (line == "%keyname end") {
                inKeyname = false
            } else if (!inKeyname && !line.startsWith("%")) {
                // If it's not a % command and not in keyname, it's a data line
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val keystroke = parts[0]
                    val character = parts[1]
                    entries.add(DictionaryEntry(keystroke = keystroke, character = character))
                    
                    if (entries.size >= 5000) {
                        database.dictionaryDao().insertAll(entries)
                        entries.clear()
                    }
                }
            }
            line = reader.readLine()
        }
        if (entries.isNotEmpty()) {
            database.dictionaryDao().insertAll(entries)
        }
        inputStream.close()
    }
}
