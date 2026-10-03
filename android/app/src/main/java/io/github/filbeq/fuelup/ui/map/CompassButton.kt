package io.github.filbeq.fuelup.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.ui.theme.floatingSurfaceColor

/**
 * Small round button with a compass needle pointing to north on screen
 * ([bearing] read at draw time, so turning the map doesn't recompose).
 * Tapping it turns the map back to north up, without tilt. Shown by the caller
 * only while the map is rotated or tilted.
 */
@Composable
fun CompassButton(bearing: () -> Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.action_reset_north)
    // North half in the UI's ink blue (never red: red is for prices), south half grey.
    val north = MaterialTheme.colorScheme.primary
    val south = MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        modifier = modifier.size(COMPASS_SIZE).semantics { contentDescription = label },
        shape = CircleShape,
        color = floatingSurfaceColor(),
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(NEEDLE_SIZE).graphicsLayer { rotationZ = -bearing() }) {
                val cx = size.width / 2
                val half = size.height / 2
                val w = size.width * 0.22f
                fun triangle(tipY: Float) = Path().apply {
                    moveTo(cx, tipY)
                    lineTo(cx - w, half)
                    lineTo(cx + w, half)
                    close()
                }
                drawPath(triangle(0f), north)
                drawPath(triangle(size.height), south)
                drawCircle(south, radius = w * 0.35f, center = Offset(cx, half))
            }
        }
    }
}

/** Size of the compass button; the my-location FAB is 56 dp. */
val COMPASS_SIZE = 40.dp
private val NEEDLE_SIZE = 22.dp
