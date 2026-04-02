package org.ghostsinthelab.app.rakurakuime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import org.ghostsinthelab.app.rakurakuime.ui.theme.KeyboardTheme
import kotlinx.coroutines.coroutineScope

@Composable
fun KeyButton(
    keyDef: KeyDefinition,
    isUppercase: Boolean = true,
    modifier: Modifier = Modifier,
    backgroundColorOverride: androidx.compose.ui.graphics.Color? = null,
    textColorOverride: androidx.compose.ui.graphics.Color? = null,
    onSwipeUp: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    var isPressed by remember { mutableStateOf(false) }
    val colors = KeyboardTheme.current
    val backgroundColor = backgroundColorOverride ?: if (isPressed) colors.keyPressedBackground else colors.keyBackground
    val textColor = textColorOverride ?: colors.keyTextColor

    val displayLabel = if (isUppercase) keyDef.qwertyChar.uppercase() else keyDef.qwertyChar.lowercase()

    val density = LocalDensity.current
    val popupOffsetY = with(density) { -(KeyboardLayout.KEY_HEIGHT + 12.dp).roundToPx() }
    val swipeThresholdPx = with(density) { 24.dp.toPx() }

    Box(
        modifier = modifier
            .height(KeyboardLayout.KEY_HEIGHT)
            .widthIn(min = 28.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .pointerInput(Unit) {
                coroutineScope {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPressed = true
                            val startY = down.position.y
                            var gestureHandled = false

                            var released = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.changedToUp()) {
                                        released = true
                                        break
                                    }

                                    val dy = change.position.y - startY
                                    if (!gestureHandled && dy < -swipeThresholdPx) {
                                        gestureHandled = true
                                        isPressed = false
                                        currentOnSwipeUp?.invoke()
                                        // Consume remaining until up
                                        while (true) {
                                            val ev = awaitPointerEvent(PointerEventPass.Main)
                                            if (ev.changes.all { it.changedToUp() }) break
                                        }
                                        released = true
                                        break
                                    }
                                }
                            } catch (_: Exception) {
                                released = false
                            }

                            isPressed = false
                            if (released && !gestureHandled) {
                                currentOnClick()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // EZ Root label in top-left
        if (keyDef.ezRoot.isNotEmpty()) {
            Text(
                text = keyDef.ezRoot,
                fontSize = 10.sp,
                color = colors.rootLabelColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 2.dp),
            )
        }
        
        // Main character label
        Text(
            text = displayLabel,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center,
        )

        // Popup preview when pressed
        if (isPressed) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, popupOffsetY),
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp, 60.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.keyBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayLabel,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.keyTextColor,
                    )
                }
            }
        }
    }
}
