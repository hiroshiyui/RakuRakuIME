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
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import org.ghostsinthelab.app.rakurakuime.data.BackupArchive
import org.ghostsinthelab.app.rakurakuime.data.CinParser
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.ghostsinthelab.app.rakurakuime.data.UserPhraseEntry
import org.ghostsinthelab.app.rakurakuime.data.UserPreferences
import org.ghostsinthelab.app.rakurakuime.ui.theme.DynamicColorAvailable
import org.ghostsinthelab.app.rakurakuime.ui.theme.ThemeMode

private const val LOG_TAG = "Settings"

private fun isImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    onOpenUserPhraseManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { ImeDatabase.getDatabase(context) }

    val vibration by userPreferences.vibrationEnabled.collectAsState(initial = true)
    val splitLayout by userPreferences.splitLayoutLandscape.collectAsState(initial = true)
    val heightScale by userPreferences.keyboardHeightScale.collectAsState(initial = 1.0f)
    val themeMode by userPreferences.themeMode.collectAsState(initial = ThemeMode.DYNAMIC)

    var showReimportDialog by remember { mutableStateOf(false) }
    var showResetFreqDialog by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var busyMessage by remember { mutableStateOf("") }
    var assetHash by remember { mutableStateOf<String?>(null) }
    var imeEnabled by remember { mutableStateOf(isImeEnabled(context)) }
    var pendingRestore by remember { mutableStateOf<BackupArchive.Archive?>(null) }

    LaunchedEffect(isBusy) {
        if (!isBusy) {
            assetHash = CinParser.assetHash(context)
        }
    }

    // Capture format-string resources during composition so the SAF result
    // lambdas (which fire after composition) don't reach back into
    // LocalContext for resources — Lint's LocalContextGetResourceValueCall
    // flags that.
    val exportSuccessTemplate = stringResource(R.string.settings_backup_export_success)
    val importConfirmTemplate = stringResource(R.string.settings_backup_import_confirm)
    val importSuccessTemplate = stringResource(R.string.settings_backup_import_success)
    val invalidPhraseTemplate = stringResource(R.string.settings_backup_import_failed_invalid_phrase)
    val invalidFrequencyTemplate = stringResource(R.string.settings_backup_import_failed_invalid_frequency)
    val unknownFieldTemplate = stringResource(R.string.settings_backup_import_failed_unknown_field)
    val backupFilename = stringResource(R.string.settings_backup_filename)

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip"),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val userPhrases = db.userPhraseDao().enumerateAll()
            val freqRows = db.dictionaryDao().snapshotNonZeroFrequencies()
            if (userPhrases.isEmpty() && freqRows.isEmpty()) {
                Toast.makeText(
                    context,
                    R.string.settings_backup_export_empty,
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            val archive = BackupArchive.Archive(
                createdAt = System.currentTimeMillis(),
                userPhrases = userPhrases.map {
                    BackupArchive.UserPhraseRow(
                        character = it.character,
                        keystroke = it.keystroke,
                        frequency = it.frequency,
                        createdAt = it.createdAt,
                    )
                },
                dictionaryFrequencies = freqRows.map {
                    BackupArchive.FrequencyRow(
                        character = it.character,
                        keystroke = it.keystroke,
                        frequency = it.frequency,
                    )
                },
            )
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    BackupArchive.writeTo(os, archive)
                }
                Toast.makeText(
                    context,
                    exportSuccessTemplate.format(userPhrases.size, freqRows.size),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Backup export failed", e)
                Toast.makeText(context, R.string.settings_backup_export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val validChars = CinParser.validKeystrokeChars(context)
            val result = try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BackupArchive.parse(input, validChars)
                } ?: BackupArchive.ParseResult.Error.InvalidFile
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Backup restore parse failed", e)
                BackupArchive.ParseResult.Error.InvalidFile
            }
            when (result) {
                is BackupArchive.ParseResult.Ok -> {
                    pendingRestore = result.archive
                }
                BackupArchive.ParseResult.Error.InvalidFile ->
                    Toast.makeText(context, R.string.settings_backup_import_failed_invalid, Toast.LENGTH_LONG).show()
                is BackupArchive.ParseResult.Error.UnsupportedSchema ->
                    Toast.makeText(context, R.string.settings_backup_import_failed_schema, Toast.LENGTH_LONG).show()
                is BackupArchive.ParseResult.Error.WrongApplication ->
                    Toast.makeText(context, R.string.settings_backup_import_failed_application, Toast.LENGTH_LONG).show()
                is BackupArchive.ParseResult.Error.UnknownField ->
                    Toast.makeText(
                        context,
                        unknownFieldTemplate.format(result.path),
                        Toast.LENGTH_LONG,
                    ).show()
                is BackupArchive.ParseResult.Error.InvalidUserPhrase ->
                    Toast.makeText(
                        context,
                        invalidPhraseTemplate.format(result.rowIndex + 1),
                        Toast.LENGTH_LONG,
                    ).show()
                is BackupArchive.ParseResult.Error.InvalidFrequencyRow ->
                    Toast.makeText(
                        context,
                        invalidFrequencyTemplate.format(result.rowIndex + 1),
                        Toast.LENGTH_LONG,
                    ).show()
                BackupArchive.ParseResult.Error.TooManyRows ->
                    Toast.makeText(context, R.string.settings_backup_import_failed_too_many_rows, Toast.LENGTH_LONG).show()
            }
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
                .padding(top = 8.dp),
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
        }
        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            Text(stringResource(R.string.settings_ime_enable_button))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Theme
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        ThemeModeOption(
            label = stringResource(R.string.settings_theme_dynamic),
            selected = themeMode == ThemeMode.DYNAMIC,
            enabled = DynamicColorAvailable,
            onSelect = { scope.launch { userPreferences.setThemeMode(ThemeMode.DYNAMIC) } }
        )
        ThemeModeOption(
            label = stringResource(R.string.settings_theme_solarized),
            selected = themeMode == ThemeMode.SOLARIZED,
            enabled = true,
            onSelect = { scope.launch { userPreferences.setThemeMode(ThemeMode.SOLARIZED) } }
        )
        if (!DynamicColorAvailable) {
            Text(
                text = stringResource(R.string.settings_theme_dynamic_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
            )
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
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenUserPhraseManager,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_user_phrases))
            }
            Text(
                text = stringResource(R.string.settings_user_phrases_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Backup & Restore
        Text(
            text = stringResource(R.string.settings_backup_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_backup_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { backupLauncher.launch(backupFilename) },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.settings_backup_export))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            // SAF mime filter: allow gzip / octet-stream / *.* since the
            // `.rkbak.gz` extension isn't a standard MIME type and pickers
            // are inconsistent about what they advertise.
            onClick = { restoreLauncher.launch(arrayOf("application/gzip", "application/octet-stream", "*/*")) },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.settings_backup_import))
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

    pendingRestore?.let { archive ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.settings_backup_import)) },
            text = {
                Text(
                    importConfirmTemplate.format(
                        archive.userPhrases.size,
                        archive.dictionaryFrequencies.size,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        var userInserted = 0
                        var freqApplied = 0
                        try {
                            for (row in archive.userPhrases) {
                                val id = db.userPhraseDao().insert(
                                    UserPhraseEntry(
                                        character = row.character,
                                        keystroke = row.keystroke,
                                        createdAt = row.createdAt.takeIf { it > 0 }
                                            ?: System.currentTimeMillis(),
                                        frequency = row.frequency,
                                    )
                                )
                                if (id > 0L) userInserted++
                            }
                            for (row in archive.dictionaryFrequencies) {
                                val rows = db.dictionaryDao().incrementFrequencyExactBy(
                                    character = row.character,
                                    keystroke = row.keystroke,
                                    delta = row.frequency,
                                )
                                if (rows > 0) freqApplied++
                            }
                            Toast.makeText(
                                context,
                                importSuccessTemplate.format(userInserted, freqApplied),
                                Toast.LENGTH_LONG,
                            ).show()
                        } catch (e: Exception) {
                            Log.e(LOG_TAG, "Backup restore apply failed", e)
                            Toast.makeText(
                                context,
                                R.string.settings_backup_import_failed_generic,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
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

@Composable
private fun ThemeModeOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            enabled = enabled,
            onClick = if (enabled) onSelect else null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
