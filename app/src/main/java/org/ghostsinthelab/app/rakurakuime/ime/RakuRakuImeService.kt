package org.ghostsinthelab.app.rakurakuime.ime

import android.app.Application
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.ghostsinthelab.app.rakurakuime.ui.KeyboardViewModel
import org.ghostsinthelab.app.rakurakuime.ui.InputMode
import org.ghostsinthelab.app.rakurakuime.ui.theme.KeyboardTheme
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
        hapticHelper.vibrate()
    }

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
                KeyboardTheme(darkTheme = isSystemInDarkTheme()) {
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
