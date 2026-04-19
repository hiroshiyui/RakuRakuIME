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

package org.ghostsinthelab.app.rakurakuime.ime

import android.app.Application
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.ghostsinthelab.app.rakurakuime.ui.KeyboardViewModel
import org.ghostsinthelab.app.rakurakuime.ui.InputMode
import org.ghostsinthelab.app.rakurakuime.ui.theme.KeyboardTheme
import org.ghostsinthelab.app.rakurakuime.ui.theme.RakuRakuIMETheme
import org.ghostsinthelab.app.rakurakuime.ui.theme.ThemeMode
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.ghostsinthelab.app.rakurakuime.ui.keyboard.*
import org.ghostsinthelab.app.rakurakuime.util.HapticHelper

class RakuRakuImeService : InputMethodService() {
    private val lifecycleOwner = ImeLifecycleOwner()
    private lateinit var viewModel: KeyboardViewModel
    private lateinit var hapticHelper: HapticHelper

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
        hapticHelper = HapticHelper(this)
        viewModel = ViewModelProvider(
            lifecycleOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[KeyboardViewModel::class.java]

        // Link ViewModel callback to InputConnection
        viewModel.onUpdateComposingText = { text ->
            currentInputConnection?.setComposingText(text, 1)
        }
    }

    private fun handleKeyPress() {
        // Collect current vibration settings
        val enabled = viewModel.vibrationEnabled.asStateFlow(lifecycleOwner.lifecycleScope, true).value
        val intensity = viewModel.vibrationIntensity.asStateFlow(lifecycleOwner.lifecycleScope, 0.5f).value
        
        if (enabled) {
            hapticHelper.vibrate(intensity)
        }
    }

    // Helper extension to convert Flow to StateFlow for one-off reads if needed, 
    // though better to collect in a scope. For simplicity in handleKeyPress:
    private fun <T> Flow<T>.asStateFlow(
        scope: CoroutineScope,
        initialValue: T
    ): StateFlow<T> = 
        this.stateIn(scope, SharingStarted.Eagerly, initialValue)

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(lifecycleOwner)
            decorView.setViewTreeViewModelStoreOwner(lifecycleOwner)
            decorView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }

        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                val themeMode by viewModel.themeMode.collectAsState(initial = ThemeMode.DYNAMIC)
                RakuRakuIMETheme(themeMode = themeMode) {
                    KeyboardTheme {
                        KeyboardScreen(
                            viewModel = viewModel,
                            currentInputConnection = currentInputConnection,
                            onKeyPress = { handleKeyPress() },
                            onSwitchIme = {
                                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                imm.showInputMethodPicker()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleOwner.onResume()
        viewModel.updateEditorInfo(info)
        viewModel.clearComposing()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleOwner.onPause()
        viewModel.clearComposing()
        currentInputConnection?.finishComposingText()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleOwner.onDestroy()
    }
}
