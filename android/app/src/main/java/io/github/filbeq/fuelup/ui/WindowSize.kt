package io.github.filbeq.fuelup.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
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

