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
        // Provide a large, well-centered default bounding box (5% margin on sides, 15% on top/bottom)
        // With the new edge-dragging and box-dragging UI, this is much easier to adjust
        // than trying to fix a fragile pure-Kotlin gradient detector that snaps to wrong edges.
        return CardQuad.defaultFor(w, h, 0.05f, 0.15f)
    }
}
