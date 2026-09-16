package com.idcardprinter.core.layout

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Generates an A4 PDF document from a 300 DPI composed A4 Bitmap.
 * Uses Android's native android.graphics.pdf.PdfDocument.
 */
object PdfGenerator {

    // Standard A4 dimensions in PostScript points (72 points per inch)
    const val A4_POINTS_WIDTH = 595
    const val A4_POINTS_HEIGHT = 842

    /**
     * Generates a single-page print-ready A4 PDF from [a4Bitmap] and saves it to [outputFile].
     */
    fun generatePdf(a4Bitmap: Bitmap, outputFile: File): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(A4_POINTS_WIDTH, A4_POINTS_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isDither = true
        }
        val destRect = Rect(0, 0, A4_POINTS_WIDTH, A4_POINTS_HEIGHT)
        canvas.drawBitmap(a4Bitmap, null, destRect, paint)

        pdfDocument.finishPage(page)

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        return outputFile
    }
}
