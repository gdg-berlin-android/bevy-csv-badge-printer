package io.nayuki.fastqrcodegen

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap

fun QrCode.toBitmap(scale: Int = 32, border: Int = 64): Bitmap {
    val result = createBitmap(width = size * scale + border * 2, height = size * scale + border * 2)
    val blackPaint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
    val whitePaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }

    result.applyCanvas {
        drawRect(Rect(0, 0, result.width, result.height), whitePaint)

        for (y in 0..size) {
            for (x in 0..size) {
                drawRect(
                    Rect(
                        border + x * scale,
                        border + y * scale,
                        border + (x + 1) * scale,
                        border + (y + 1) * scale,
                    ),
                    if (getModule(x, y)) {
                        blackPaint
                    } else {
                        whitePaint
                    }
                )
            }
        }
    }

    return result
}
