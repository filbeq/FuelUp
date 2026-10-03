package io.github.filbeq.fuelup.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R

/**
 * On wide windows (landscape phones, tablets, split screen) the station details
 * and the "near me" list sit in this card over the left side of the map, like
 * Google Maps, instead of in the bottom sheet. Its height follows the content,
 * up to the height it is given. The contents place the close button
 * ([content]'s parameter) at the end of their title; it does what swiping the
 * sheet away does. [onRightEdge] reports where the card ends (window x, 0 once it's
 * gone), so the map credits and the top controls can stay clear of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidePanel(
    visible: Boolean,
    onClose: () -> Unit,
    onRightEdge: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(closeButton: @Composable () -> Unit) -> Unit,
) {
    val currentOnRightEdge by rememberUpdatedState(onRightEdge)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut(),
    ) {
        DisposableEffect(Unit) { onDispose { currentOnRightEdge(0f) } }
        Surface(
            modifier = Modifier
                .width(SIDE_PANEL_WIDTH)
                .onGloballyPositioned { currentOnRightEdge(it.boundsInWindow().right) },
            shape = MaterialTheme.shapes.large,
            color = BottomSheetDefaults.ContainerColor,
            shadowElevation = 3.dp,
        ) {
            // Insets are handled around the card: the contents' own padding for
            // the navigation bar (needed in the bottom sheet) adds nothing here.
            Column(Modifier.consumeWindowInsets(WindowInsets.safeDrawing).padding(top = 8.dp)) {
                content {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
}

/**
 * About a portrait phone's width, so the sheet's contents fit as they are.
 * Also on phones in landscape: 320 dp was tried there, but the near-me rows
 * wrapped to a third line and fewer of them fitted.
 */
val SIDE_PANEL_WIDTH = 360.dp

/** Space between the panel and the edges of the map. */
val SIDE_PANEL_MARGIN = 8.dp
