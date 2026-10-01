package io.github.filbeq.fuelup.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import io.github.filbeq.fuelup.data.PriceClass

/**
 * Station marker icons, one per [PriceClass]. Colour is never the only signal:
 * each class also has its own shape (down chevron = cheap, up chevron =
 * expensive, plain = average, hollow = not compared, "?" = to verify).
 * Colours from the Okabe–Ito palette, distinguishable with common colour-vision
 * deficiencies. Used both on the map and in the legend.
 */
object StationIcons {
    const val CHEAP_COLOR = 0xFF009E73.toInt()      // bluish green
    const val EXPENSIVE_COLOR = 0xFFD55E00.toInt()  // vermillion
    const val NEUTRAL_COLOR = 0xFF7D8590.toInt()    // grey
    private const val OUTLINE_COLOR = 0xFFFFFFFF.toInt()

    private const val SIZE_DP = 20f

    /** Name of the map image for [priceClass]. */
    fun imageName(priceClass: PriceClass) = "fuelup-${priceClass.name.lowercase()}"

    fun draw(priceClass: PriceClass, density: Float): Bitmap {
        val size = (SIZE_DP * density).toInt()
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val outline = 1.5f * density
        val radius = center - outline
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // White outline, so markers stand out on both light and dark maps.
        paint.color = OUTLINE_COLOR
        canvas.drawCircle(center, center, center, paint)

        when (priceClass) {
            PriceClass.NOT_COMPARED -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * density
                paint.color = NEUTRAL_COLOR
                canvas.drawCircle(center, center, radius - 1.5f * density, paint)
            }
            else -> {
                paint.color = when (priceClass) {
                    PriceClass.CHEAP -> CHEAP_COLOR
                    PriceClass.EXPENSIVE -> EXPENSIVE_COLOR
                    else -> NEUTRAL_COLOR
                }
                canvas.drawCircle(center, center, radius, paint)
            }
        }

        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OUTLINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val w = radius * 0.45f
        val h = radius * 0.28f
        when (priceClass) {
            PriceClass.CHEAP -> canvas.drawPath(chevron(center, center - h / 2, w, h, down = true), glyph)
            PriceClass.EXPENSIVE -> canvas.drawPath(chevron(center, center + h / 2, w, h, down = false), glyph)
            PriceClass.TO_VERIFY -> {
                glyph.style = Paint.Style.FILL
                glyph.textAlign = Paint.Align.CENTER
                glyph.isFakeBoldText = true
                glyph.textSize = radius * 1.4f
                canvas.drawText("?", center, center - (glyph.descent() + glyph.ascent()) / 2, glyph)
            }
            PriceClass.AVERAGE, PriceClass.NOT_COMPARED -> Unit
        }
        return bitmap
    }

    private fun chevron(cx: Float, cy: Float, halfWidth: Float, height: Float, down: Boolean): Path {
        val tip = if (down) cy + height else cy - height
        return Path().apply {
            moveTo(cx - halfWidth, cy)
            lineTo(cx, tip)
            lineTo(cx + halfWidth, cy)
        }
    }
}
