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

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.ghostsinthelab.app.rakurakuime.R
import org.ghostsinthelab.app.rakurakuime.data.CinParser
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.ghostsinthelab.app.rakurakuime.data.UserPreferences

private fun isImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { ImeDatabase.getDatabase(context) }

    val vibration by userPreferences.vibrationEnabled.collectAsState(initial = true)
    val splitLayout by userPreferences.splitLayoutLandscape.collectAsState(initial = true)
    val heightScale by userPreferences.keyboardHeightScale.collectAsState(initial = 1.0f)

    var showReimportDialog by remember { mutableStateOf(false) }
    var showResetFreqDialog by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var busyMessage by remember { mutableStateOf("") }
    var assetHash by remember { mutableStateOf<String?>(null) }
    var imeEnabled by remember { mutableStateOf(isImeEnabled(context)) }

    LaunchedEffect(isBusy) {
        if (!isBusy) {
            assetHash = CinParser.assetHash(context)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                imeEnabled = isImeEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_ime_status_label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    if (imeEnabled) R.string.settings_ime_status_enabled
                    else R.string.settings_ime_status_disabled
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = if (imeEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }) {
                Text(stringResource(R.string.settings_ime_enable_button))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Vibration
        SettingsSwitch(
            label = stringResource(R.string.settings_vibration),
            checked = vibration,
            onCheckedChange = { scope.launch { userPreferences.setVibrationEnabled(it) } }
        )

        SettingsSwitch(
            label = stringResource(R.string.settings_split_landscape),
            checked = splitLayout,
            onCheckedChange = { scope.launch { userPreferences.setSplitLayoutLandscape(it) } }
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

        assetHash?.let { hash ->
            Text(
                text = "CIN SHA-256: $hash",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

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
        Text(text = stringResource(R.string.settings_about_inventor), style = MaterialTheme.typography.bodyMedium)
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
                        CinParser.forceReimport(context, db)
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
