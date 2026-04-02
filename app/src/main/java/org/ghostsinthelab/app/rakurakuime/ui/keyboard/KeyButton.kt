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
import kotlinx.coroutines.launch

@Composable
fun KeyButton(
    keyDef: KeyDefinition,
    isUppercase: Boolean = true,
    modifier: Modifier = Modifier,
    keyHeight: androidx.compose.ui.unit.Dp = KeyboardLayout.KEY_HEIGHT,
    backgroundColorOverride: androidx.compose.ui.graphics.Color? = null,
    textColorOverride: androidx.compose.ui.graphics.Color? = null,
    onSwipeUp: (() -> Unit)? = null,
    onAlternateSelected: ((String) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnAlternateSelected by rememberUpdatedState(onAlternateSelected)
    var isPressed by remember { mutableStateOf(false) }
    var showAlternates by remember { mutableStateOf(false) }
    var activeAlternateIndex by remember { mutableStateOf(-1) }
    val colors = KeyboardTheme.current
    val backgroundColor = backgroundColorOverride ?: if (isPressed && !showAlternates) colors.keyPressedBackground else colors.keyBackground
    val textColor = textColorOverride ?: colors.keyTextColor

    val displayLabel = if (isUppercase) keyDef.qwertyChar.uppercase() else keyDef.qwertyChar.lowercase()

    val density = LocalDensity.current
    val popupOffsetY = with(density) { -(keyHeight + 12.dp).roundToPx() }
    val swipeThresholdPx = with(density) { 24.dp.toPx() }

    Box(
        modifier = modifier
            .height(keyHeight)
            .widthIn(min = 28.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .pointerInput(Unit) {
                coroutineScope {
                    val scope = this
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPressed = true
                            showAlternates = false
                            activeAlternateIndex = -1
                            val startY = down.position.y
                            val startX = down.position.x
                            var gestureHandled = false

                            val longPressJob = scope.launch {
                                kotlinx.coroutines.delay(400)
                                if (isPressed && !gestureHandled && keyDef.alternates.isNotEmpty()) {
                                    showAlternates = true
                                    activeAlternateIndex = 0
                                }
                            }

                            var released = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.changedToUp()) {
                                        released = true
                                        break
                                    }

                                    if (showAlternates) {
                                        val dx = change.position.x - startX
                                        val itemWidthPx = with(density) { 40.dp.toPx() }
                                        val steps = (dx / itemWidthPx).toInt()
                                        activeAlternateIndex = (steps).coerceIn(0, keyDef.alternates.lastIndex)
                                    } else {
                                        val dy = change.position.y - startY
                                        if (!gestureHandled && dy < -swipeThresholdPx) {
                                            gestureHandled = true
                                            isPressed = false
                                            longPressJob.cancel()
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
                                }
                            } catch (_: Exception) {
                                released = false
                            }

                            longPressJob.cancel()
                            isPressed = false
                            
                            val wasShowingAlternates = showAlternates
                            val finalActiveIndex = activeAlternateIndex
                            showAlternates = false
                            
                            if (released && !gestureHandled) {
                                if (wasShowingAlternates && finalActiveIndex in keyDef.alternates.indices) {
                                    currentOnAlternateSelected?.invoke(keyDef.alternates[finalActiveIndex])
                                } else {
                                    currentOnClick()
                                }
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
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.rootLabelColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 5.dp, top = 2.dp),
            )
        }
        
        // Main character label (qwertyChar) at bottom-right
        Text(
            text = displayLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = textColor,
            modifier = if (keyDef.ezRoot.isNotEmpty()) {
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 2.dp)
            } else {
                Modifier.align(Alignment.Center)
            },
        )

        // Popup preview when pressed
        if (isPressed && !showAlternates) {
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
                        text = if (keyDef.ezRoot.isNotEmpty()) keyDef.ezRoot else displayLabel,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.keyTextColor,
                    )
                }
            }
        }
        
        // Alternates popup
        if (showAlternates) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, popupOffsetY),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .height(60.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.keyBackground)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    keyDef.alternates.forEachIndexed { index, alt ->
                        val isSelected = index == activeAlternateIndex
                        Box(
                            modifier = Modifier
                                .size(40.dp, 50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.keyPressedBackground else androidx.compose.ui.graphics.Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isUppercase) alt.uppercase() else alt.lowercase(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.keyTextColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
