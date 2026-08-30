package com.example.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalNeumorphicShadows

fun Modifier.neumorphic(
    isSunken: Boolean = false,
    lightShadowColor: Color = Color.Unspecified,
    darkShadowColor: Color = Color.Unspecified,
    elevation: Dp = 6.dp,
    blur: Dp = 12.dp,
    cornerRadius: Dp = 16.dp,
    shapeBackgroundColor: Color = Color.Unspecified
) = composed {
    val shadows = LocalNeumorphicShadows.current
    val finalLightShadowColor = if (lightShadowColor == Color.Unspecified) shadows.lightShadow else lightShadowColor
    val finalDarkShadowColor = if (darkShadowColor == Color.Unspecified) shadows.darkShadow else darkShadowColor
    val finalShapeBackgroundColor = if (shapeBackgroundColor == Color.Unspecified) MaterialTheme.colorScheme.surface else shapeBackgroundColor

    this.drawBehind {
        drawIntoCanvas { canvas ->
            if (!isSunken) {
                val paintLight = Paint().apply {
                    color = finalShapeBackgroundColor
                    asFrameworkPaint().apply {
                        setShadowLayer(
                            blur.toPx(),
                            -elevation.toPx(),
                            -elevation.toPx(),
                            finalLightShadowColor.toArgb()
                        )
                    }
                }
                val paintDark = Paint().apply {
                    color = finalShapeBackgroundColor
                    asFrameworkPaint().apply {
                        setShadowLayer(
                            blur.toPx(),
                            elevation.toPx(),
                            elevation.toPx(),
                            finalDarkShadowColor.toArgb()
                        )
                    }
                }
                
                canvas.drawRoundRect(
                    0f, 0f, size.width, size.height, cornerRadius.toPx(), cornerRadius.toPx(), paintLight
                )
                canvas.drawRoundRect(
                    0f, 0f, size.width, size.height, cornerRadius.toPx(), cornerRadius.toPx(), paintDark
                )
                
                val paintMain = Paint().apply { color = finalShapeBackgroundColor }
                canvas.drawRoundRect(
                    0f, 0f, size.width, size.height, cornerRadius.toPx(), cornerRadius.toPx(), paintMain
                )
            } else {
                val paintMain = Paint().apply { color = finalShapeBackgroundColor }
                canvas.drawRoundRect(
                    0f, 0f, size.width, size.height, cornerRadius.toPx(), cornerRadius.toPx(), paintMain
                )

                val shadowOutline = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            0f, 0f, size.width, size.height,
                            androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx())
                        )
                    )
                }

                val holePath = androidx.compose.ui.graphics.Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(-100.dp.toPx(), -100.dp.toPx(), size.width + 100.dp.toPx(), size.height + 100.dp.toPx()))
                    op(this, shadowOutline, androidx.compose.ui.graphics.PathOperation.Difference)
                }

                canvas.save()
                canvas.clipPath(shadowOutline)

                val paintDark = Paint().apply {
                    color = finalShapeBackgroundColor
                    asFrameworkPaint().apply {
                        setShadowLayer(
                            blur.toPx(),
                            elevation.toPx(),
                            elevation.toPx(),
                            finalDarkShadowColor.toArgb()
                        )
                    }
                }
                canvas.drawPath(holePath, paintDark)

                val paintLight = Paint().apply {
                    color = finalShapeBackgroundColor
                    asFrameworkPaint().apply {
                        setShadowLayer(
                            blur.toPx(),
                            -elevation.toPx(),
                            -elevation.toPx(),
                            finalLightShadowColor.toArgb()
                        )
                    }
                }
                canvas.drawPath(holePath, paintLight)

                canvas.restore()
            }
        }
    }
}
