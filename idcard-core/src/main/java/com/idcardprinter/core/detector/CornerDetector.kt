package com.idcardprinter.core.detector

import android.graphics.Bitmap
import android.graphics.Color
import com.idcardprinter.core.model.CardPoint
import com.idcardprinter.core.model.CardQuad
import kotlin.math.abs
import kotlin.math.max

/**
 * Detects 4 corners of an ID card inside a photo.
 * Uses gradient profile analysis along vertical and horizontal axes.
 * Falls back to centered aspect-ratio bounded rectangle if edges are ambiguous.
 */
object CornerDetector {

    fun detect(bitmap: Bitmap): CardQuad {
        val w = bitmap.width
        val h = bitmap.height
        val defaultQuad = CardQuad.defaultFor(w, h, 0.12f, 0.15f)

        if (w < 100 || h < 100) {
            return defaultQuad
        }

        try {
            val midX = w / 2
            val midY = h / 2

            // 1. Scan vertical middle column
            val colPixels = IntArray(h)
            bitmap.getPixels(colPixels, 0, 1, midX, 0, 1, h)
            val colGray = FloatArray(h) { i ->
                val p = colPixels[i]
                0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
            }

            // Top candidates in [0.18*h .. 0.55*h]
            val topStart = (0.18f * h).toInt()
            val topEnd = (0.55f * h).toInt()
            var topCand = -1
            for (y in topStart until topEnd - 1) {
                if (abs(colGray[y + 1] - colGray[y]) > 14f) {
                    topCand = y
                    break
                }
            }

            // Bottom candidates in [0.60*h .. 0.90*h]
            val botStart = (0.60f * h).toInt()
            val botEnd = (0.90f * h).toInt()
            var botCand = -1
            for (y in botEnd - 1 downTo botStart) {
                if (abs(colGray[y] - colGray[y - 1]) > 14f) {
                    botCand = y
                    break
                }
            }

            // 2. Scan horizontal middle row
            val rowPixels = IntArray(w)
            bitmap.getPixels(rowPixels, 0, w, 0, midY, w, 1)
            val rowGray = FloatArray(w) { i ->
                val p = rowPixels[i]
                0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
            }

            // Left candidates in [0.08*w .. 0.38*w]
            val leftStart = (0.08f * w).toInt()
            val leftEnd = (0.38f * w).toInt()
            var leftCand = -1
            for (x in leftStart until leftEnd - 1) {
                if (abs(rowGray[x + 1] - rowGray[x]) > 14f) {
                    leftCand = x
                    break
                }
            }

            // Right candidates in [0.62*w .. 0.94*w]
            val rightStart = (0.62f * w).toInt()
            val rightEnd = (0.94f * w).toInt()
            var rightCand = -1
            for (x in rightEnd - 1 downTo rightStart) {
                if (abs(rowGray[x] - rowGray[x - 1]) > 14f) {
                    rightCand = x
                    break
                }
            }

            if (topCand != -1 && botCand != -1 && leftCand != -1 && rightCand != -1) {
                val bw = (rightCand - leftCand).toFloat()
                val bh = (botCand - topCand).toFloat()
                val ratio = bw / max(bh, 1f)

                // Sanity check: standard ID card aspect ratio is around ~1.58
                if (ratio in 1.15f..1.85f && bw > 0.30f * w && bh > 0.20f * h) {
                    val l = leftCand.toFloat()
                    val r = rightCand.toFloat()
                    val t = topCand.toFloat()
                    val b = botCand.toFloat()
                    return CardQuad(
                        topLeft = CardPoint(l, t),
                        topRight = CardPoint(r, t),
                        bottomRight = CardPoint(r, b),
                        bottomLeft = CardPoint(l, b)
                    )
                }
            }
        } catch (_: Exception) {
            // Fallback to default
        }

        return defaultQuad
    }
}
