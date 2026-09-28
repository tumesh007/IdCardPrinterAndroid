package com.idcardprinter.core.warp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.idcardprinter.core.model.CardQuad

/**
 * Rectifies a 4-corner quadrilateral perspective region into a flat rectangular bitmap.
 * Uses Android's native hardware-accelerated projective Matrix (setPolyToPoly).
 */
object PerspectiveWarper {

    /**
     * Warps the card boundary in [srcBitmap] defined by [quad] to a rectangular [Bitmap] of
     * dimensions [targetWidth] x [targetHeight].
     */
    fun rectify(
        srcBitmap: Bitmap,
        quad: CardQuad,
        targetWidth: Int = 1184,
        targetHeight: Int = 758
    ): Bitmap {
        val src = floatArrayOf(
            quad.topLeft.x, quad.topLeft.y,
            quad.topRight.x, quad.topRight.y,
            quad.bottomRight.x, quad.bottomRight.y,
            quad.bottomLeft.x, quad.bottomLeft.y
        )

        val dst = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isDither = true
        }

        canvas.drawBitmap(srcBitmap, matrix, paint)
        return output
    }

    /**
     * Warps the boundary dynamically computing the physical dimensions
     * based on the euclidean distance of the quad's edges, preserving aspect ratio.
     */
    fun rectifyDynamic(srcBitmap: Bitmap, quad: CardQuad): Bitmap {
        val widthTop = Math.hypot((quad.topRight.x - quad.topLeft.x).toDouble(), (quad.topRight.y - quad.topLeft.y).toDouble())
        val widthBot = Math.hypot((quad.bottomRight.x - quad.bottomLeft.x).toDouble(), (quad.bottomRight.y - quad.bottomLeft.y).toDouble())
        val targetWidth = Math.max(widthTop, widthBot).toInt()

        val heightLeft = Math.hypot((quad.bottomLeft.x - quad.topLeft.x).toDouble(), (quad.bottomLeft.y - quad.topLeft.y).toDouble())
        val heightRight = Math.hypot((quad.bottomRight.x - quad.topRight.x).toDouble(), (quad.bottomRight.y - quad.topRight.y).toDouble())
        val targetHeight = Math.max(heightLeft, heightRight).toInt()

        return rectify(srcBitmap, quad, targetWidth, targetHeight)
    }
}
