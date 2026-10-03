package io.github.filbeq.fuelup.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * The window is at least 600 dp wide (Material's "medium" width class or more):
 * landscape phones, tablets, a wide split-screen half. The map then shows its
 * details in a side panel instead of a bottom sheet. Decided by width, not by
 * orientation, so every kind of window gets the layout that fits it.
 */
@Composable
fun isWideWindow(): Boolean =
    currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

/**
 * Keeps a screen's content (settings rows, text) at most 600 dp wide, centred:
 * on a landscape phone or a tablet, a switch at the far end of a 1200 dp row is
 * hard to link to its label. No effect on portrait phones. Put it after the
 * scroll modifier, so the whole width still scrolls.
 */
fun Modifier.readableWidth(): Modifier = wrapContentWidth().widthIn(max = 600.dp)
