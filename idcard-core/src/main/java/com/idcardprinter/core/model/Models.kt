package com.idcardprinter.core.model

import java.io.Serializable
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * 2D point representing coordinate in an image.
 */
data class CardPoint(
    val x: Float,
    val y: Float
) : Serializable {
    fun distanceTo(other: CardPoint): Float = hypot(x - other.x, y - other.y)

    fun scaled(scaleX: Float, scaleY: Float): CardPoint =
        CardPoint(x * scaleX, y * scaleY)
}

/**
 * 4-corner quadrilateral boundary of an ID card in clockwise order:
 * Top-Left -> Top-Right -> Bottom-Right -> Bottom-Left.
 */
data class CardQuad(
    val topLeft: CardPoint,
    val topRight: CardPoint,
    val bottomRight: CardPoint,
    val bottomLeft: CardPoint
) : Serializable {

    /**
     * Converts quad corners to a float array of 8 coordinates:
     * [tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y]
     */
    fun toFloatArray(): FloatArray = floatArrayOf(
        topLeft.x, topLeft.y,
        topRight.x, topRight.y,
        bottomRight.x, bottomRight.y,
        bottomLeft.x, bottomLeft.y
    )

    fun widthTop(): Float = topLeft.distanceTo(topRight)
    fun widthBottom(): Float = bottomLeft.distanceTo(bottomRight)
    fun heightLeft(): Float = topLeft.distanceTo(bottomLeft)
    fun heightRight(): Float = topRight.distanceTo(bottomRight)

    fun averageWidth(): Float = (widthTop() + widthBottom()) / 2f
    fun averageHeight(): Float = (heightLeft() + heightRight()) / 2f

    fun aspectRatio(): Float {
        val h = max(averageHeight(), 1f)
        return averageWidth() / h
    }

    /**
     * Validates whether the quad is convex and non-degenerate.
     */
    fun isConvex(): Boolean {
        val pts = listOf(topLeft, topRight, bottomRight, bottomLeft)
        var sign = 0
        for (i in pts.indices) {
            val p1 = pts[i]
            val p2 = pts[(i + 1) % 4]
            val p3 = pts[(i + 2) % 4]
            val cross = (p2.x - p1.x) * (p3.y - p2.y) - (p2.y - p1.y) * (p3.x - p2.x)
            if (abs(cross) > 1e-4) {
                val currentSign = if (cross > 0) 1 else -1
                if (sign == 0) {
                    sign = currentSign
                } else if (sign != currentSign) {
                    return false
                }
            }
        }
        return sign != 0
    }

    fun scaled(scaleX: Float, scaleY: Float): CardQuad = CardQuad(
        topLeft = topLeft.scaled(scaleX, scaleY),
        topRight = topRight.scaled(scaleX, scaleY),
        bottomRight = bottomRight.scaled(scaleX, scaleY),
        bottomLeft = bottomLeft.scaled(scaleX, scaleY)
    )

    companion object {
        /**
         * Creates a default inset rectangular boundary for an image of given dimensions.
         */
        fun defaultFor(width: Int, height: Int, insetPercentX: Float = 0.12f, insetPercentY: Float = 0.15f): CardQuad {
            val left = width * insetPercentX
            val right = width * (1f - insetPercentX)
            val top = height * insetPercentY
            val bottom = height * (1f - insetPercentY)
            return CardQuad(
                topLeft = CardPoint(left, top),
                topRight = CardPoint(right, top),
                bottomRight = CardPoint(right, bottom),
                bottomLeft = CardPoint(left, bottom)
            )
        }
    }
}

enum class CardSide {
    FRONT,
    BACK
}

enum class PrintLayout(val title: String, val description: String) {
    DOCUMENT_KYC("Document / KYC Copy", "Large centered Front & Back cards for banking & official submissions"),
    WALLET_1TO1("Wallet Card 1:1 Scale", "Exact physical card size (85.6x54mm / 89x57mm) with center fold line and cut guides"),
    ALL_IN_ONE("All-In-One Page", "Wallet 1:1 cards on top half, enlarged Document copy on bottom half")
}

data class ProcessingConfig(
    val autoWhiteBalance: Boolean = true,
    val autoFlatField: Boolean = true,
    val brightness: Float = 1.0f,
    val contrast: Float = 1.0f,
    val targetWidth: Int = 1184,
    val targetHeight: Int = 758
) : Serializable
