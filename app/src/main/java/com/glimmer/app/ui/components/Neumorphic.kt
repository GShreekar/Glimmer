package com.glimmer.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glimmer.app.ui.theme.LocalNeumorphicShadows

/**
 * PERF-01: this used to be `composed { … this.drawBehind { drawIntoCanvas { … } } }`, which had
 * two costs stacked on top of each other on what is the hottest draw path in the app (behind
 * nearly every surface, including up to ~42 CalendarScreen day cells at once):
 *  - `composed {}` is deprecated and defeats modifier equality/skipping.
 *  - `drawBehind`'s lambda re-runs on every DRAW pass (scrolling, an unrelated animation
 *    invalidating the same layer, …), not just on recomposition — and it was allocating 2–3
 *    `Paint` objects, plus (for the sunken variant) 2 `Path` objects and a `PathOperation`
 *    boolean op, EVERY SINGLE TIME.
 *
 * Fixed by making this `@Composable` (every call site already runs inside composition — that's
 * how `composed{}` was able to read LocalNeumorphicShadows/MaterialTheme.colorScheme in the first
 * place — so this needs no call-site changes) and moving the Paint/Path construction into
 * `drawWithCache`'s cache-building block, which only re-runs when the draw size (or a captured
 * input) actually changes. Its `onDrawBehind` block — the part that runs on every redraw — now
 * only references already-built objects, so a scroll or an unrelated recomposition costs zero
 * allocations here.
 */
@Composable
fun Modifier.neumorphic(
    isSunken: Boolean = false,
    lightShadowColor: Color = Color.Unspecified,
    darkShadowColor: Color = Color.Unspecified,
    elevation: Dp = 6.dp,
    blur: Dp = 12.dp,
    cornerRadius: Dp = 16.dp,
    shapeBackgroundColor: Color = Color.Unspecified
): Modifier {
    val shadows = LocalNeumorphicShadows.current
    val resolvedLight = lightShadowColor.takeOrElse { shadows.lightShadow }
    val resolvedDark = darkShadowColor.takeOrElse { shadows.darkShadow }
    val resolvedBackground = shapeBackgroundColor.takeOrElse { MaterialTheme.colorScheme.surface }

    return this.drawWithCache {
        val radiusPx = cornerRadius.toPx()
        val elevationPx = elevation.toPx()
        val blurPx = blur.toPx()
        val paintMain = Paint().apply { color = resolvedBackground }

        if (!isSunken) {
            val paintLight = Paint().apply {
                color = resolvedBackground
                asFrameworkPaint().setShadowLayer(blurPx, -elevationPx, -elevationPx, resolvedLight.toArgb())
            }
            val paintDark = Paint().apply {
                color = resolvedBackground
                asFrameworkPaint().setShadowLayer(blurPx, elevationPx, elevationPx, resolvedDark.toArgb())
            }

            onDrawBehind {
                drawIntoCanvas { canvas ->
                    canvas.drawRoundRect(0f, 0f, size.width, size.height, radiusPx, radiusPx, paintLight)
                    canvas.drawRoundRect(0f, 0f, size.width, size.height, radiusPx, radiusPx, paintDark)
                    canvas.drawRoundRect(0f, 0f, size.width, size.height, radiusPx, radiusPx, paintMain)
                }
            }
        } else {
            val shadowOutline = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radiusPx)))
            }
            // A generously oversized rect minus the shape's own outline, so the shadow — clipped
            // to the shape below — reads as an inset/engraved hole rather than stopping abruptly
            // at the shape's edge.
            val overscanPx = 100.dp.toPx()
            val holePath = Path().apply {
                addRect(Rect(-overscanPx, -overscanPx, size.width + overscanPx, size.height + overscanPx))
                op(this, shadowOutline, PathOperation.Difference)
            }
            val paintDark = Paint().apply {
                color = resolvedBackground
                asFrameworkPaint().setShadowLayer(blurPx, elevationPx, elevationPx, resolvedDark.toArgb())
            }
            val paintLight = Paint().apply {
                color = resolvedBackground
                asFrameworkPaint().setShadowLayer(blurPx, -elevationPx, -elevationPx, resolvedLight.toArgb())
            }

            onDrawBehind {
                drawIntoCanvas { canvas ->
                    canvas.drawRoundRect(0f, 0f, size.width, size.height, radiusPx, radiusPx, paintMain)
                    canvas.save()
                    canvas.clipPath(shadowOutline)
                    canvas.drawPath(holePath, paintDark)
                    canvas.drawPath(holePath, paintLight)
                    canvas.restore()
                }
            }
        }
    }
}
