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
import androidx.core.content.edit
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest

object CinParser {
    private const val ASSET_NAME = "ezbig.utf-8.cin"
    private const val PREFS_NAME = "cin_parser"
    private const val KEY_ASSET_HASH = "ezbig_asset_hash"

    enum class SyncResult { AlreadyCurrent, Reimported }

    suspend fun assetHash(context: Context): String = withContext(Dispatchers.IO) {
        computeAssetHash(context)
    }

    suspend fun syncWithAsset(context: Context, database: ImeDatabase): SyncResult =
        withContext(Dispatchers.IO) {
            val currentHash = computeAssetHash(context)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val storedHash = prefs.getString(KEY_ASSET_HASH, null)
            val hasData = database.dictionaryDao().count() > 0

            // Trust the pre-packaged DB on first launch: it ships alongside the asset,
            // so we record the current hash rather than re-parsing.
            val upToDate = hasData && (storedHash == null || storedHash == currentHash)
            if (upToDate) {
                if (storedHash != currentHash) {
                    prefs.edit { putString(KEY_ASSET_HASH, currentHash) }
                }
                return@withContext SyncResult.AlreadyCurrent
            }

            database.dictionaryDao().clearAll()
            parseAndPopulate(context, database)
            prefs.edit { putString(KEY_ASSET_HASH, currentHash) }
            SyncResult.Reimported
        }

    suspend fun forceReimport(context: Context, database: ImeDatabase) =
        withContext(Dispatchers.IO) {
            val currentHash = computeAssetHash(context)
            database.dictionaryDao().clearAll()
            parseAndPopulate(context, database)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit { putString(KEY_ASSET_HASH, currentHash) }
        }

    suspend fun parseAndPopulate(context: Context, database: ImeDatabase) = withContext(Dispatchers.IO) {
        context.assets.open(ASSET_NAME).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                var inKeyname = false
                val entries = mutableListOf<DictionaryEntry>()

                database.withTransaction {
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
                }
            }
        }
    }

    private fun computeAssetHash(context: Context): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(ASSET_NAME).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
