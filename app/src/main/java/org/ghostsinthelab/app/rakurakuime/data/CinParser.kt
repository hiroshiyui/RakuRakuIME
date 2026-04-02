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
