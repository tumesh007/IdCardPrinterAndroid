package com.idcardprinter.core

import android.graphics.Bitmap
import com.idcardprinter.core.detector.CornerDetector
import com.idcardprinter.core.enhance.CardEnhancer
import com.idcardprinter.core.layout.A4LayoutComposer
import com.idcardprinter.core.layout.PdfGenerator
import com.idcardprinter.core.model.CardQuad
import com.idcardprinter.core.model.PrintLayout
import com.idcardprinter.core.model.ProcessingConfig
import com.idcardprinter.core.warp.PerspectiveWarper
import java.io.File

/**
 * Public high-level facade for the ID Card Processing Engine.
 * Designed for headless, background, or custom UI integration in external Android projects.
 */
class IdCardEngine {

    /**
     * Detects 4 corners of an ID card inside a photo.
     */
    fun detectCorners(bitmap: Bitmap): CardQuad {
        return CornerDetector.detect(bitmap)
    }

    /**
     * Warps a perspective region defined by [quad] into a standard flat card bitmap.
     */
    fun rectifyCard(
        bitmap: Bitmap,
        quad: CardQuad,
        targetWidth: Int = 1184,
        targetHeight: Int = 758
    ): Bitmap {
        return PerspectiveWarper.rectify(bitmap, quad, targetWidth, targetHeight)
    }

    /**
     * Applies lighting flat-fielding, auto white balance, and document paper whitening.
     */
    fun enhanceCard(
        bitmap: Bitmap,
        config: ProcessingConfig = ProcessingConfig(),
        isFront: Boolean = true
    ): Bitmap {
        return CardEnhancer.enhance(bitmap, config, isFront)
    }

    /**
     * Convenience pipeline: Rectifies corners and enhances in one call.
     */
    fun processCard(
        bitmap: Bitmap,
        quad: CardQuad,
        config: ProcessingConfig = ProcessingConfig(),
        isFront: Boolean = true
    ): Bitmap {
        val rectified = rectifyCard(bitmap, quad, config.targetWidth, config.targetHeight)
        return enhanceCard(rectified, config, isFront)
    }

    /**
     * Composes Front and optional Back card onto a 300 DPI single-page A4 Bitmap.
     */
    fun composeA4(
        frontCard: Bitmap,
        backCard: Bitmap? = null,
        layout: PrintLayout = PrintLayout.WALLET_1TO1
    ): Bitmap {
        return A4LayoutComposer.compose(frontCard, backCard, layout)
    }

    /**
     * Composes Front and optional Back card and outputs a print-ready A4 PDF.
     */
    fun generateA4Pdf(
        frontCard: Bitmap,
        backCard: Bitmap? = null,
        layout: PrintLayout = PrintLayout.WALLET_1TO1,
        outputFile: File
    ): File {
        val a4Bitmap = composeA4(frontCard, backCard, layout)
        return PdfGenerator.generatePdf(a4Bitmap, outputFile)
    }

    /**
     * Composes Front and optional Back card and outputs a high-res 300 DPI A4 PNG.
     */
    fun generateA4Png(
        frontCard: Bitmap,
        backCard: Bitmap? = null,
        layout: PrintLayout = PrintLayout.WALLET_1TO1,
        outputFile: File
    ): File {
        val a4Bitmap = composeA4(frontCard, backCard, layout)
        A4LayoutComposer.saveAsPng(a4Bitmap, outputFile)
        return outputFile
    }
}
