package com.idcardprinter.core.layout

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.idcardprinter.core.model.PrintLayout
import java.io.File
import java.io.FileOutputStream

/**
 * Composes Front and Back ID cards onto a clean 300 DPI single-page A4 canvas (2480 x 3508 px).
 * Plain output for direct printing: Front at top, Back at bottom, with zero extra decorations.
 */
object A4LayoutComposer {

    const val A4_WIDTH_300DPI = 2480
    const val A4_HEIGHT_300DPI = 3508

    // Width of the printed card on A4 (leaves balanced margins on 2480px width)
    const val PRINT_CARD_WIDTH = 1600

    fun compose(
        frontCard: Bitmap,
        backCard: Bitmap? = null,
        layout: PrintLayout = PrintLayout.DOCUMENT_KYC
    ): Bitmap {
        val a4 = Bitmap.createBitmap(A4_WIDTH_300DPI, A4_HEIGHT_300DPI, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(a4)
        canvas.drawColor(Color.WHITE)

        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225) // subtle boundary line so white card is defined on white paper
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val cardW = PRINT_CARD_WIDTH
        val cardX = (A4_WIDTH_300DPI - cardW) / 2

        // Front Card at top
        val frontH = ((cardW.toFloat() * frontCard.height) / frontCard.width).toInt()
        val frontY = 350
        val frontRect = Rect(cardX, frontY, cardX + cardW, frontY + frontH)
        canvas.drawBitmap(frontCard, null, frontRect, bmpPaint)
        canvas.drawRect(frontRect, borderPaint)

        // Back Card at bottom (if provided)
        if (backCard != null) {
            val backH = ((cardW.toFloat() * backCard.height) / backCard.width).toInt()
            val backY = 1850
            val backRect = Rect(cardX, backY, cardX + cardW, backY + backH)
            canvas.drawBitmap(backCard, null, backRect, bmpPaint)
            canvas.drawRect(backRect, borderPaint)
        }

        return a4
    }

    fun saveAsPng(bitmap: Bitmap, outputFile: File, quality: Int = 100): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, quality, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
