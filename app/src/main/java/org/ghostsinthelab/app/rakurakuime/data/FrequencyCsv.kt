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
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads UTF-8 MOE 八十五年常用語詞調查報告 frequency CSVs and turns the
 * per-row 序號 (rank) into a static priority `weight` for use as a starter
 * prior in candidate ordering.
 *
 * The original government-released CSVs are Big5-encoded; the bundled
 * `assets/85rest01.csv` and `assets/85rest02.csv` were transcoded to UTF-8
 * with `iconv -c -f BIG5 -t UTF-8` so we can read them with the platform
 * default charset and skip the encoding-bridge in code.
 *
 * The corpus ships in this repo for transparency (auditable by F-Droid
 * reviewers); see README and the attribution block above each CSV.
 *
 * ## Weight formula
 *
 * `weight = max(0, [HEAD_BUDGET] - rank)`
 *
 * Rank is the 1-based 序號 column. With `HEAD_BUDGET = 100`, the top-100
 * most-frequent entries get monotonically decreasing seeds 99..1; rank
 * > 100 (the long tail) gets 0. Once the user picks a candidate, the
 * `frequency` column (incremented per selection) eventually overtakes the
 * static seed, so learning still wins long-term.
 */
object FrequencyCsv {
    /** Highest seed value; entries past this rank receive 0. */
    const val HEAD_BUDGET = 100

    /** `assets/85rest01.csv` — character frequency (字頻總表). */
    const val CHAR_ASSET = "85rest01.csv"

    /** `assets/85rest02.csv` — phrase frequency (詞頻總表). */
    const val PHRASE_ASSET = "85rest02.csv"

    /**
     * Read [assetName] and return a map of `key → weight` where `key` is
     * the 字 / 詞目 column and `weight` is computed from the 序號 column.
     */
    fun load(context: Context, assetName: String): Map<String, Int> {
        val out = HashMap<String, Int>(8192)
        context.assets.open(assetName).use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                var skippedHeader = false
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty()) continue
                    val cols = line.split(',')
                    if (cols.size < 2) continue
                    val first = cols[0].trim()
                    val rank = first.toIntOrNull()
                    if (rank == null) {
                        // Header row begins with non-numeric "序號" / "字頻序號".
                        if (!skippedHeader) {
                            skippedHeader = true
                            continue
                        }
                        // Stray non-numeric data row — ignore.
                        continue
                    }
                    skippedHeader = true
                    val key = cols[1].trim()
                    if (key.isEmpty()) continue
                    val weight = (HEAD_BUDGET - rank).coerceAtLeast(0)
                    if (weight == 0) {
                        // Past the head budget: skip storage to keep the map small.
                        // Once we've seen one zero-weight row, the rest are zero too
                        // because the CSV is sorted by ascending rank.
                        break
                    }
                    // Lowest-rank wins on duplicate keys (shouldn't happen in
                    // a well-formed corpus but documented for safety).
                    val existing = out[key]
                    if (existing == null || weight > existing) {
                        out[key] = weight
                    }
                }
            }
        }
        return out
    }

    /** Convenience: load both CSVs in one call. */
    data class Tables(
        val charWeights: Map<String, Int>,
        val phraseWeights: Map<String, Int>,
    )

    fun loadAll(context: Context): Tables = Tables(
        charWeights = load(context, CHAR_ASSET),
        phraseWeights = load(context, PHRASE_ASSET),
    )
}
