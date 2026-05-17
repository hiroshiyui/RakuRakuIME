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

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.ghostsinthelab.app.rakurakuime.R
import org.ghostsinthelab.app.rakurakuime.data.CinParser
import org.ghostsinthelab.app.rakurakuime.data.DictionaryDao
import org.ghostsinthelab.app.rakurakuime.data.EzKeystrokeLookup
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.ghostsinthelab.app.rakurakuime.data.UserPhraseCsv
import org.ghostsinthelab.app.rakurakuime.data.UserPhraseEntry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

private const val LOG_TAG = "UserPhraseManager"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPhraseManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { ImeDatabase.getDatabase(context) }
    val dao = remember { db.userPhraseDao() }

    var phrases by remember { mutableStateOf<List<UserPhraseEntry>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<UserPhraseEntry?>(null) }
    var pendingReset by remember { mutableStateOf(false) }
    var pendingResetSecond by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<List<UserPhraseCsv.Row>?>(null) }
    var validKeystrokeChars by remember { mutableStateOf<Set<Char>>(emptySet()) }

    suspend fun reload() {
        phrases = dao.enumerateAll()
    }

    LaunchedEffect(Unit) {
        validKeystrokeChars = CinParser.validKeystrokeChars(context)
        reload()
    }

    // Capture format-template strings during composition so lambda callbacks
    // (which fire after composition) don't reach back into LocalContext to
    // resolve resources — Lint's LocalContextGetResourceValueCall flags that.
    val backupSuccessTemplate = stringResource(R.string.user_phrases_backup_success)
    val restoreSuccessTemplate = stringResource(R.string.user_phrases_restore_success)
    val malformedRowTemplate = stringResource(R.string.user_phrases_restore_malformed_row)
    val invalidKeystrokeRowTemplate = stringResource(R.string.user_phrases_restore_invalid_keystroke)

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val rows = dao.enumerateAll().map {
                UserPhraseCsv.Row(it.character, it.keystroke)
            }
            if (rows.isEmpty()) {
                Toast.makeText(context, R.string.user_phrases_backup_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    OutputStreamWriter(os, Charsets.UTF_8).use { w ->
                        w.write(UserPhraseCsv.export(rows))
                    }
                }
                Toast.makeText(
                    context,
                    backupSuccessTemplate.format(rows.size),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Backup failed", e)
                Toast.makeText(context, R.string.user_phrases_backup_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val lines = mutableListOf<String>()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { r ->
                        lines.addAll(r.readLines())
                    }
                }
                when (val result = UserPhraseCsv.parse(lines, validKeystrokeChars)) {
                    is UserPhraseCsv.ParseResult.Ok -> {
                        if (result.rows.isEmpty()) {
                            Toast.makeText(context, R.string.user_phrases_restore_no_data, Toast.LENGTH_SHORT).show()
                        } else {
                            pendingRestore = result.rows
                        }
                    }
                    UserPhraseCsv.ParseResult.Error.InvalidFile ->
                        Toast.makeText(context, R.string.user_phrases_restore_invalid_file, Toast.LENGTH_LONG).show()
                    UserPhraseCsv.ParseResult.Error.InvalidHeader ->
                        Toast.makeText(context, R.string.user_phrases_restore_invalid_header, Toast.LENGTH_LONG).show()
                    is UserPhraseCsv.ParseResult.Error.MalformedRow ->
                        Toast.makeText(
                            context,
                            malformedRowTemplate.format(result.lineNumber),
                            Toast.LENGTH_LONG,
                        ).show()
                    is UserPhraseCsv.ParseResult.Error.InvalidKeystroke ->
                        Toast.makeText(
                            context,
                            invalidKeystrokeRowTemplate.format(result.lineNumber),
                            Toast.LENGTH_LONG,
                        ).show()
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Restore failed", e)
                Toast.makeText(context, R.string.user_phrases_restore_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val backupFilename = stringResource(R.string.user_phrases_backup_filename)
    val filtered = remember(phrases, search) {
        if (search.isBlank()) phrases
        else phrases.filter {
            it.character.contains(search, ignoreCase = true) ||
                it.keystroke.contains(search, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.user_phrases_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = "←", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                text = { Text(stringResource(R.string.user_phrases_add)) },
                icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text(stringResource(R.string.user_phrases_search_hint)) },
                singleLine = true,
            )

            Row(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(
                    onClick = { backupLauncher.launch(backupFilename) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.user_phrases_backup)) }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("text/*")) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.user_phrases_restore)) }
            }
            TextButton(
                onClick = { pendingReset = true },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text(stringResource(R.string.user_phrases_reset), color = MaterialTheme.colorScheme.error) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (search.isBlank()) R.string.user_phrases_empty
                            else R.string.user_phrases_search_empty
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { entry ->
                        UserPhraseRow(
                            entry = entry,
                            onDelete = { pendingDelete = entry },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddUserPhraseDialog(
            validKeystrokeChars = validKeystrokeChars,
            dictionaryDao = db.dictionaryDao(),
            onDismiss = { showAddDialog = false },
            onAdd = { character, keystroke ->
                scope.launch {
                    val id = dao.insert(UserPhraseEntry(character = character, keystroke = keystroke))
                    if (id <= 0L) {
                        Toast.makeText(context, R.string.user_phrases_add_failed, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.user_phrases_add_success, Toast.LENGTH_SHORT).show()
                        showAddDialog = false
                        reload()
                    }
                }
            },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.user_phrases_delete_title)) },
            text = { Text(stringResource(R.string.user_phrases_delete_message, entry.character)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        dao.deleteById(entry.id)
                        reload()
                    }
                }) { Text(stringResource(R.string.user_phrases_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (pendingReset) {
        AlertDialog(
            onDismissRequest = { pendingReset = false },
            title = { Text(stringResource(R.string.user_phrases_reset)) },
            text = { Text(stringResource(R.string.user_phrases_reset_confirm_first)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingReset = false
                    pendingResetSecond = true
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingReset = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (pendingResetSecond) {
        AlertDialog(
            onDismissRequest = { pendingResetSecond = false },
            title = { Text(stringResource(R.string.user_phrases_reset)) },
            text = { Text(stringResource(R.string.user_phrases_reset_confirm_second)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingResetSecond = false
                    scope.launch {
                        dao.clearAll()
                        reload()
                        Toast.makeText(context, R.string.user_phrases_reset_done, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.user_phrases_reset), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingResetSecond = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    pendingRestore?.let { rows ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.user_phrases_restore)) },
            text = { Text(stringResource(R.string.user_phrases_restore_confirm, rows.size)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        var added = 0
                        for (row in rows) {
                            val id = dao.insert(UserPhraseEntry(character = row.character, keystroke = row.keystroke))
                            if (id > 0L) added++
                        }
                        reload()
                        Toast.makeText(
                            context,
                            restoreSuccessTemplate.format(added),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun UserPhraseRow(
    entry: UserPhraseEntry,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.character,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = entry.keystroke,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) {
            Text(
                text = stringResource(R.string.user_phrases_delete),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddUserPhraseDialog(
    validKeystrokeChars: Set<Char>,
    dictionaryDao: DictionaryDao,
    onDismiss: () -> Unit,
    onAdd: (character: String, keystroke: String) -> Unit,
) {
    var character by remember { mutableStateOf("") }
    var keystroke by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestionsExpanded by remember { mutableStateOf(false) }
    var analysing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.user_phrases_add_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = character,
                    onValueChange = {
                        character = it
                        // The previous batch of suggestions was derived from
                        // the old phrase value; stale chips would mislead.
                        suggestions = emptyList()
                    },
                    label = { Text(stringResource(R.string.user_phrases_field_character)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val phrase = character.trim()
                        if (phrase.isEmpty()) {
                            Toast.makeText(context, R.string.user_phrases_analyse_phrase_empty, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        analysing = true
                        scope.launch {
                            val results = EzKeystrokeLookup.suggest(phrase, dictionaryDao)
                            analysing = false
                            if (results.isEmpty()) {
                                Toast.makeText(context, R.string.user_phrases_analyse_no_results, Toast.LENGTH_LONG).show()
                            } else {
                                suggestions = results
                                keystroke = results.first()
                                suggestionsExpanded = true
                            }
                        }
                    },
                    enabled = !analysing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (analysing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.user_phrases_analyse_roots))
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (suggestions.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = suggestionsExpanded,
                        onExpandedChange = { suggestionsExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = keystroke,
                            onValueChange = {
                                keystroke = it.trim()
                                // User edited away from a suggestion → drop
                                // the dropdown so they're not steered back.
                                suggestionsExpanded = false
                            },
                            label = { Text(stringResource(R.string.user_phrases_analyse_choose)) },
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = suggestionsExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                        )
                        ExposedDropdownMenu(
                            expanded = suggestionsExpanded,
                            onDismissRequest = { suggestionsExpanded = false },
                        ) {
                            suggestions.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s) },
                                    onClick = {
                                        keystroke = s
                                        suggestionsExpanded = false
                                    },
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = keystroke,
                        onValueChange = { keystroke = it.trim() },
                        label = { Text(stringResource(R.string.user_phrases_field_keystroke)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.user_phrases_field_keystroke_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = character.trim()
                val k = keystroke.trim()
                if (c.isEmpty() || k.isEmpty()) {
                    Toast.makeText(context, R.string.user_phrases_input_empty, Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                if (!UserPhraseCsv.isValidKeystroke(k, validKeystrokeChars)) {
                    Toast.makeText(context, R.string.user_phrases_input_invalid_keystroke, Toast.LENGTH_LONG).show()
                    return@TextButton
                }
                onAdd(c, k)
            }) { Text(stringResource(R.string.user_phrases_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
