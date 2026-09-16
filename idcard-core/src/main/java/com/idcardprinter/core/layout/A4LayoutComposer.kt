package com.idcardprinter.core.layout

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.idcardprinter.core.model.PrintLayout
import java.io.File
import java.io.FileOutputStream

/**
 * Composes Front and Back ID cards onto a 300 DPI single-page A4 canvas (2480 x 3508 px).
 * Provides precise physical scaling (100% actual size), cutting guides, fold lines,
 * and 5cm calibration ruler.
 */
object A4LayoutComposer {

    const val A4_WIDTH_300DPI = 2480
    const val A4_HEIGHT_300DPI = 3508

    // Physical dimensions at 300 DPI (89mm x 57mm)
    const val CARD_WIDTH_1TO1 = 1051
    const val CARD_HEIGHT_1TO1 = 673

    // 5 cm calibration ruler (50mm = ~591 px at 300 DPI)
    const val RULER_5CM_PX = 591

    fun compose(
        frontCard: Bitmap,
        backCard: Bitmap? = null,
        layout: PrintLayout = PrintLayout.WALLET_1TO1
    ): Bitmap {
        val a4 = Bitmap.createBitmap(A4_WIDTH_300DPI, A4_HEIGHT_300DPI, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(a4)
        canvas.drawColor(Color.WHITE)

        if (backCard == null) {
            composeSingleCardWallet(canvas, frontCard)
        } else {
            when (layout) {
                PrintLayout.DOCUMENT_KYC -> composeDocumentLayout(canvas, frontCard, backCard)
                PrintLayout.WALLET_1TO1 -> composeWalletLayout(canvas, frontCard, backCard)
                PrintLayout.ALL_IN_ONE -> composeAllInOneLayout(canvas, frontCard, backCard)
            }
        }

        drawPageFooter(canvas)
        return a4
    }

    private fun composeSingleCardWallet(canvas: Canvas, front: Bitmap) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 50f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("ID CARD — 1:1 PHYSICAL TRUE SCALE PRINT", A4_WIDTH_300DPI / 2f, 220f, titlePaint)
        canvas.drawText("Exact 1:1 Dimensions (85.6mm × 54mm) • 300 DPI • Cut along dashed guides", A4_WIDTH_300DPI / 2f, 275f, subPaint)

        // Card centered
        val startX = (A4_WIDTH_300DPI - CARD_WIDTH_1TO1) / 2
        val startY = 950

        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val frontRect = Rect(startX, startY, startX + CARD_WIDTH_1TO1, startY + CARD_HEIGHT_1TO1)
        canvas.drawBitmap(front, null, frontRect, bmpPaint)
        canvas.drawRect(frontRect, borderPaint)

        // Outer dashed cut guide
        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(20f, 14f), 0f)
            style = Paint.Style.STROKE
        }
        val outerCut = RectF(
            (startX - 14).toFloat(),
            (startY - 14).toFloat(),
            (startX + CARD_WIDTH_1TO1 + 14).toFloat(),
            (startY + CARD_HEIGHT_1TO1 + 14).toFloat()
        )
        canvas.drawRect(outerCut, cutPaint)

        // 5cm Calibration Check Ruler
        draw5cmRuler(canvas, A4_WIDTH_300DPI / 2, startY + CARD_HEIGHT_1TO1 + 350)
    }

    fun saveAsPng(bitmap: Bitmap, outputFile: File, quality: Int = 100): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            java.io.FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, quality, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun composeDocumentLayout(canvas: Canvas, front: Bitmap, back: Bitmap) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("ID CARD — DOCUMENT VERIFICATION COPY", A4_WIDTH_300DPI / 2f, 180f, titlePaint)
        canvas.drawText("Print-Ready Layout • Single Page A4 • 300 DPI", A4_WIDTH_300DPI / 2f, 230f, subPaint)

        // Divider line
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 3f
        }
        canvas.drawLine(200f, 270f, (A4_WIDTH_300DPI - 200).toFloat(), 270f, linePaint)

        // Document cards (larger centered copy: 1560 x 998 px)
        val docW = 1560
        val docH = 998
        val startX = (A4_WIDTH_300DPI - docW) / 2

        // Front Card
        val frontY = 400
        drawCardWithBadge(canvas, front, startX, frontY, docW, docH, "FRONT SIDE")

        // Back Card
        val backY = 1800
        drawCardWithBadge(canvas, back, startX, backY, docW, docH, "BACK SIDE")
    }

    private fun composeWalletLayout(canvas: Canvas, front: Bitmap, back: Bitmap) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 50f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("ID CARD — WALLET SIZE (1:1 PHYSICAL SCALE)", A4_WIDTH_300DPI / 2f, 180f, titlePaint)
        canvas.drawText("Exact 1:1 Dimensions (89mm × 57mm) • Cut along dashed guides and fold at center", A4_WIDTH_300DPI / 2f, 230f, subPaint)

        // Wallet Card block: Side-by-side with fold line in middle
        // Total width: CARD_WIDTH_1TO1 * 2 = 2102 px
        val totalW = CARD_WIDTH_1TO1 * 2
        val startX = (A4_WIDTH_300DPI - totalW) / 2
        val startY = 700

        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        // Draw Front
        val frontRect = Rect(startX, startY, startX + CARD_WIDTH_1TO1, startY + CARD_HEIGHT_1TO1)
        canvas.drawBitmap(front, null, frontRect, bmpPaint)
        canvas.drawRect(frontRect, borderPaint)

        // Draw Back
        val backStartX = startX + CARD_WIDTH_1TO1
        val backRect = Rect(backStartX, startY, backStartX + CARD_WIDTH_1TO1, startY + CARD_HEIGHT_1TO1)
        canvas.drawBitmap(back, null, backRect, bmpPaint)
        canvas.drawRect(backRect, borderPaint)

        // Center fold line (dashed)
        val foldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            strokeWidth = 4f
            pathEffect = DashPathEffect(floatArrayOf(16f, 12f), 0f)
        }
        canvas.drawLine(backStartX.toFloat(), (startY - 60).toFloat(), backStartX.toFloat(), (startY + CARD_HEIGHT_1TO1 + 60).toFloat(), foldPaint)

        val foldLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("✂ FOLD LINE", backStartX.toFloat(), (startY - 75).toFloat(), foldLabelPaint)
        canvas.drawText("✂ FOLD LINE", backStartX.toFloat(), (startY + CARD_HEIGHT_1TO1 + 95).toFloat(), foldLabelPaint)

        // Outer cut guides
        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(20f, 14f), 0f)
            style = Paint.Style.STROKE
        }
        val outerCut = RectF(
            (startX - 10).toFloat(),
            (startY - 10).toFloat(),
            (backStartX + CARD_WIDTH_1TO1 + 10).toFloat(),
            (startY + CARD_HEIGHT_1TO1 + 10).toFloat()
        )
        canvas.drawRect(outerCut, cutPaint)

        // 5cm Calibration Check Ruler
        draw5cmRuler(canvas, A4_WIDTH_300DPI / 2, startY + CARD_HEIGHT_1TO1 + 350)
    }

    private fun composeAllInOneLayout(canvas: Canvas, front: Bitmap, back: Bitmap) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("ID CARD — ALL-IN-ONE PRINT PAGE", A4_WIDTH_300DPI / 2f, 160f, titlePaint)

        // Top half: Wallet 1:1
        val totalW = CARD_WIDTH_1TO1 * 2
        val startX = (A4_WIDTH_300DPI - totalW) / 2
        val startY = 320

        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val frontRect = Rect(startX, startY, startX + CARD_WIDTH_1TO1, startY + CARD_HEIGHT_1TO1)
        val backRect = Rect(startX + CARD_WIDTH_1TO1, startY, startX + totalW, startY + CARD_HEIGHT_1TO1)
        canvas.drawBitmap(front, null, frontRect, bmpPaint)
        canvas.drawBitmap(back, null, backRect, bmpPaint)

        val foldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
        val foldX = (startX + CARD_WIDTH_1TO1).toFloat()
        canvas.drawLine(foldX, (startY - 30).toFloat(), foldX, (startY + CARD_HEIGHT_1TO1 + 30).toFloat(), foldPaint)

        // Middle separator
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        }
        canvas.drawLine(150f, 1200f, (A4_WIDTH_300DPI - 150).toFloat(), 1200f, linePaint)

        // Bottom half: Document Verification Copy (1300 x 830 px each)
        val docW = 1300
        val docH = 832
        val docX = (A4_WIDTH_300DPI - docW) / 2

        drawCardWithBadge(canvas, front, docX, 1320, docW, docH, "DOCUMENT COPY (FRONT)")
        drawCardWithBadge(canvas, back, docX, 2320, docW, docH, "DOCUMENT COPY (BACK)")
    }

    private fun drawCardWithBadge(
        canvas: Canvas,
        card: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        badgeText: String
    ) {
        val destRect = Rect(x, y, x + width, y + height)
        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(card, null, destRect, bmpPaint)

        // Card outer border
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(destRect, borderPaint)

        // Badge tag
        val badgePaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val badgeW = 260f
        val badgeH = 46f
        val badgeRect = RectF(x.toFloat(), (y - badgeH).toFloat(), x + badgeW, y.toFloat())
        canvas.drawRoundRect(badgeRect, 6f, 6f, badgePaint)
        canvas.drawText(badgeText, badgeRect.centerX(), badgeRect.centerY() + 9f, textPaint)
    }

    private fun draw5cmRuler(canvas: Canvas, centerX: Int, y: Int) {
        val startX = centerX - RULER_5CM_PX / 2
        val endX = startX + RULER_5CM_PX

        val rulerPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            strokeWidth = 4f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }

        // Main bar
        canvas.drawLine(startX.toFloat(), y.toFloat(), endX.toFloat(), y.toFloat(), rulerPaint)

        // Ticks for each cm (5 cm total, 5 intervals)
        val cmStep = RULER_5CM_PX / 5f
        for (i in 0..5) {
            val tx = startX + i * cmStep
            val tickH = if (i == 0 || i == 5) 26f else 16f
            canvas.drawLine(tx, (y - tickH), tx, (y + tickH), rulerPaint)
            canvas.drawText("${i}cm", tx, y + 54f, textPaint)
        }

        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("5 cm Calibration Ruler (Place physical scale here to verify 100% actual size print)", centerX.toFloat(), y - 36f, captionPaint)
    }

    private fun drawPageFooter(canvas: Canvas) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Print Settings: Paper Size = A4 | Scale = 100% (Actual Size) | Do NOT select 'Fit to Page'",
            A4_WIDTH_300DPI / 2f,
            (A4_HEIGHT_300DPI - 80).toFloat(),
            footerPaint
        )
    }
}
