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

package org.ghostsinthelab.app.rakurakuime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ghostsinthelab.app.rakurakuime.ui.theme.KeyboardTheme

@Composable
fun CandidateBar(
    candidates: List<String>,
    hasPrev: Boolean,
    hasNext: Boolean,
    onCandidateSelected: (String) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    val colors = KeyboardTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.candidateBarBackground),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Prev button
        if (hasPrev) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onPrevPage() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "◀", color = colors.candidateTextColor, fontWeight = FontWeight.Bold)
            }
        }

        // Candidates list
        LazyRow(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            itemsIndexed(candidates) { index, candidate ->
                val selKey = if (index < 9) (index + 1).toString() else if (index == 9) "0" else ""
                Row(
                    modifier = Modifier
                        .clickable { onCandidateSelected(candidate) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (selKey.isNotEmpty()) {
                        Text(
                            text = selKey,
                            fontSize = 10.sp,
                            color = colors.rootLabelColor,
                            modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                        )
                    }
                    Text(
                        text = candidate,
                        fontSize = 20.sp,
                        color = colors.candidateTextColor
                    )
                }
            }
        }

        // Next button
        if (hasNext) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onNextPage() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "▶", color = colors.candidateTextColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}
