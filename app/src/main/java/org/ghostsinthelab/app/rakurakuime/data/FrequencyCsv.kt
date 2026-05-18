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
 * `weight = floor([SEED_NUMERATOR] / rank)`
 *
 * Rank is the 1-based 序號 column. The reciprocal shape gives a wide
 * dynamic range: rank 1 ⇒ 10 000, rank 10 ⇒ 1 000, rank 100 ⇒ 100,
 * rank 1 000 ⇒ 10. Once `rank > [SEED_NUMERATOR]` the formula floors
 * to 0 and we stop reading the file.
 *
 * ### Why reciprocal and not a simple "head budget"
 *
 * The original tuning was `max(0, 100 - rank)` — easy to reason about,
 * but every entry past rank 100 ended up with weight 0. That worked for
 * direct keystroke → character lookups, but broke the next-character
 * prediction strip: all next-char groups for a given prefix would tie at
 * `SUM(phrase_weight) = 0`, and the alphabetical tiebreaker dropped
 * common follow-ups like 鬆 after 輕 (rank 1 424 — well past 100) to the
 * bottom of the strip. The reciprocal keeps long-tail entries
 * differentiated while still letting one user selection overtake the
 * prior, because [DictionaryDao]'s `ORDER BY` places `frequency DESC`
 * ahead of the weight columns.
 */
object FrequencyCsv {
    /** Numerator of the reciprocal weight formula. */
    const val SEED_NUMERATOR = 10_000

    /** `assets/85rest01.csv` — character frequency (字頻總表). */
    const val CHAR_ASSET = "85rest01.csv"

    /** `assets/85rest02.csv` — phrase frequency (詞頻總表). */
    const val PHRASE_ASSET = "85rest02.csv"

    /**
     * Read [assetName] and return a map of `key → weight` where `key` is
     * the 字 / 詞目 column and `weight` is computed from the 序號 column.
     */
    fun load(context: Context, assetName: String): Map<String, Int> {
        return context.assets.open(assetName).use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                parseLines(lines)
            }
        }
    }

    /**
     * Pure-Kotlin parser entry point — no Android dependencies, so it can be
     * exercised from a JVM unit test. [lines] is consumed lazily; the parser
     * stops reading on the first row whose rank yields a zero weight, so
     * passing a giant sequence with the cutoff far away is fine.
     *
     * Contract:
     *  - Skips the first non-numeric row as a header.
     *  - Ignores blank lines and rows with fewer than two columns.
     *  - Returns `floor(SEED_NUMERATOR / rank)` per row.
     *  - Stops at the first `weight == 0` row (the CSV is sorted by ascending
     *    rank, so every following row is also zero).
     *  - On duplicate keys, keeps the highest weight (i.e. the lowest rank).
     */
    internal fun parseLines(lines: Sequence<String>): Map<String, Int> {
        val out = HashMap<String, Int>(8192)
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
            val weight = if (rank > 0) SEED_NUMERATOR / rank else 0
            if (weight == 0) {
                // Past the cutoff (rank > SEED_NUMERATOR). The CSV is
                // sorted by ascending rank, so every following row is
                // also zero — stop reading.
                break
            }
            // Lowest-rank wins on duplicate keys (shouldn't happen in
            // a well-formed corpus but documented for safety).
            val existing = out[key]
            if (existing == null || weight > existing) {
                out[key] = weight
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
