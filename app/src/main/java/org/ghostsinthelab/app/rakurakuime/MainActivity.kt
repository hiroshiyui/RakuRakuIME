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

package org.ghostsinthelab.app.rakurakuime

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.ghostsinthelab.app.rakurakuime.data.CinParser
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.ghostsinthelab.app.rakurakuime.ui.theme.RakuRakuIMETheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            RakuRakuIMETheme {
                var initStatus by remember { mutableStateOf("Checking dictionary...") }
                var isReady by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val db = ImeDatabase.getDatabase(this@MainActivity)
                    val count = db.dictionaryDao().count()
                    
                    if (count == 0) {
                        initStatus = "Importing dictionary... Please wait."
                        try {
                            CinParser.parseAndPopulate(this@MainActivity, db)
                            val finalCount = db.dictionaryDao().count()
                            initStatus = "Dictionary initialized ($finalCount entries)!"
                            isReady = true
                        } catch (e: Exception) {
                            initStatus = "Error initializing dictionary: ${e.localizedMessage}"
                        }
                    } else {
                        initStatus = "Dictionary ready ($count entries). Please enable RakuRaku IME in system settings."
                        isReady = true
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = initStatus,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    if (isReady) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "To use the keyboard, please enable RakuRakuIME in your system settings.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        }) {
                            Text("Enable in Settings")
                        }
                    }
                }
            }
        }
    }
}
