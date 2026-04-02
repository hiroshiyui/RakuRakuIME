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

package org.ghostsinthelab.app.rakurakuime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.ghostsinthelab.app.rakurakuime.R
import org.ghostsinthelab.app.rakurakuime.data.CinParser
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.ghostsinthelab.app.rakurakuime.data.UserPreferences

@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { ImeDatabase.getDatabase(context) }

    val vibration by userPreferences.vibrationEnabled.collectAsState(initial = true)
    val heightScale by userPreferences.keyboardHeightScale.collectAsState(initial = 1.0f)

    var showReimportDialog by remember { mutableStateOf(false) }
    var showResetFreqDialog by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var busyMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Vibration
        SettingsSwitch(
            label = stringResource(R.string.settings_vibration),
            checked = vibration,
            onCheckedChange = { scope.launch { userPreferences.setVibrationEnabled(it) } }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Keyboard Height
        Text(
            text = stringResource(R.string.settings_keyboard_height),
            style = MaterialTheme.typography.titleMedium
        )
        Slider(
            value = heightScale,
            onValueChange = { scope.launch { userPreferences.setKeyboardHeightScale(it) } },
            valueRange = 0.5f..1.5f,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Dictionary Management
        Text(
            text = stringResource(R.string.settings_dict_management),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (isBusy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = busyMessage)
            }
        } else {
            Button(
                onClick = { showReimportDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_dict_reimport))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showResetFreqDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_clear_freq))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // About
        Text(
            text = stringResource(R.string.settings_about),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.settings_about_author), style = MaterialTheme.typography.bodyMedium)
        Text(text = stringResource(R.string.settings_about_org), style = MaterialTheme.typography.bodyMedium)
        Text(text = stringResource(R.string.settings_about_license), style = MaterialTheme.typography.bodyMedium)
    }

    if (showReimportDialog) {
        AlertDialog(
            onDismissRequest = { showReimportDialog = false },
            title = { Text(stringResource(R.string.settings_dict_reimport)) },
            text = { Text(stringResource(R.string.settings_dict_reimport_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showReimportDialog = false
                    isBusy = true
                    busyMessage = "Re-importing..."
                    scope.launch {
                        db.dictionaryDao().clearAll()
                        CinParser.parseAndPopulate(context, db)
                        isBusy = false
                    }
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReimportDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showResetFreqDialog) {
        AlertDialog(
            onDismissRequest = { showResetFreqDialog = false },
            title = { Text(stringResource(R.string.settings_clear_freq)) },
            text = { Text(stringResource(R.string.settings_clear_freq_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetFreqDialog = false
                    scope.launch {
                        db.dictionaryDao().resetFrequencies()
                    }
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetFreqDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
