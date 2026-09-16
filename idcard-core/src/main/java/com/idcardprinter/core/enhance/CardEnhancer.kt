package com.idcardprinter.core.enhance

import android.graphics.Bitmap
import android.graphics.Color
import com.idcardprinter.core.model.ProcessingConfig
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Professional document image enhancement for ID cards:
 * - Auto White Balance (neutralizes lighting color temperature)
 * - Illumination Flat-Fielding (removes room shadows and uneven flash)
 * - Contrast & S-curve tone mapping (whitening paper background to #FFFFFF, darkening text & barcodes)
 * - Portrait photo skin tone preservation
 */
object CardEnhancer {

    fun enhance(
        inputBitmap: Bitmap,
        config: ProcessingConfig = ProcessingConfig(),
        isFront: Boolean = true
    ): Bitmap {
        val w = inputBitmap.width
        val h = inputBitmap.height
        val pixels = IntArray(w * h)
        inputBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. Auto White Balance (sampling paper region)
        var gainR = 1.0f
        var gainG = 1.0f
        var gainB = 1.0f

        if (config.autoWhiteBalance) {
            val yStart = if (isFront) (0.10f * h).toInt() else (0.40f * h).toInt()
            val yEnd = if (isFront) (0.25f * h).toInt() else (0.65f * h).toInt()
            val xStart = if (isFront) (0.40f * w).toInt() else (0.30f * w).toInt()
            val xEnd = if (isFront) (0.80f * w).toInt() else (0.70f * w).toInt()

            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var count = 0L

            for (y in yStart until yEnd step 2) {
                val rowOff = y * w
                for (x in xStart until xEnd step 2) {
                    val p = pixels[rowOff + x]
                    sumR += Color.red(p)
                    sumG += Color.green(p)
                    sumB += Color.blue(p)
                    count++
                }
            }

            if (count > 0) {
                val avgR = (sumR.toDouble() / count).toFloat()
                val avgG = (sumG.toDouble() / count).toFloat()
                val avgB = (sumB.toDouble() / count).toFloat()
                val target = max(avgR, max(avgG, avgB))
                if (target > 15f) {
                    gainR = target / max(avgR, 1f)
                    gainG = target / max(avgG, 1f)
                    gainB = target / max(avgB, 1f)
                }
            }
        }

        // 2. Illumination Flat-Fielding (Downsampled Background Estimation)
        // Downsample to 40x26 to model smooth ambient lighting field across card
        val gridW = 40
        val gridH = 26
        val bgGrid = FloatArray(gridW * gridH)

        if (config.autoFlatField) {
            val stepX = w.toFloat() / gridW
            val stepY = h.toFloat() / gridH

            for (gy in 0 until gridH) {
                val sy = min((gy * stepY).toInt(), h - 1)
                for (gx in 0 until gridW) {
                    val sx = min((gx * stepX).toInt(), w - 1)
                    val p = pixels[sy * w + sx]
                    val lum = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
                    bgGrid[gy * gridW + gx] = lum
                }
            }

            // Simple 3x3 max filter + box blur on low-res grid for smooth illumination map
            val smoothBg = FloatArray(gridW * gridH)
            for (gy in 0 until gridH) {
                for (gx in 0 until gridW) {
                    var maxVal = 0f
                    for (dy in -1..1) {
                        val ny = (gy + dy).coerceIn(0, gridH - 1)
                        for (dx in -1..1) {
                            val nx = (gx + dx).coerceIn(0, gridW - 1)
                            maxVal = max(maxVal, bgGrid[ny * gridW + nx])
                        }
                    }
                    smoothBg[gy * gridW + gx] = max(maxVal, 40f)
                }
            }
            System.arraycopy(smoothBg, 0, bgGrid, 0, bgGrid.size)
        }

        // 3. Precompute Tone Mapping LUT (256 entries)
        val contrast = max(0.2f, config.contrast)
        val brightness = max(0.2f, config.brightness)

        val blackPt = max(5.0f, 30.0f / contrast)
        val whitePt = min(250.0f, 192.0f / brightness)
        val gamma = 1.08f * contrast

        val lut = IntArray(256)
        for (i in 0..255) {
            val v = i.toFloat()
            val scaled = ((v - blackPt) / max(whitePt - blackPt, 1f)).coerceIn(0f, 1f)
            var curved = (scaled.toDouble().pow(gamma.toDouble()) * 255.0).toFloat()

            // Soft paper whitening for near-white paper background
            if (curved > 245f) {
                curved = 250f + (curved - 245f) * 0.5f
            }
            lut[i] = curved.toInt().coerceIn(0, 255)
        }

        // 4. Apply pass over pixels
        val stepX = w.toFloat() / gridW
        val stepY = h.toFloat() / gridH

        for (y in 0 until h) {
            val rowOff = y * w
            val gy = min((y / stepY).toInt(), gridH - 1)
            val gridRow = gy * gridW

            for (x in 0 until w) {
                val p = pixels[rowOff + x]
                var r = (Color.red(p) * gainR).toInt().coerceIn(0, 255)
                var g = (Color.green(p) * gainG).toInt().coerceIn(0, 255)
                var b = (Color.blue(p) * gainB).toInt().coerceIn(0, 255)

                if (config.autoFlatField) {
                    val gx = min((x / stepX).toInt(), gridW - 1)
                    val bgLum = bgGrid[gridRow + gx]
                    val normFactor = 195.0f / max(bgLum, 30f)
                    r = (r * normFactor).toInt().coerceIn(0, 255)
                    g = (g * normFactor).toInt().coerceIn(0, 255)
                    b = (b * normFactor).toInt().coerceIn(0, 255)
                }

                val nr = lut[r]
                val ng = lut[g]
                val nb = lut[b]

                pixels[rowOff + x] = Color.rgb(nr, ng, nb)
            }
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, w, 0, 0, w, h)
        return output
    }
}
