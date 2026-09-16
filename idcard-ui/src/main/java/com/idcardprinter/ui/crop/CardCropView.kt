package com.idcardprinter.ui.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.idcardprinter.core.model.CardPoint
import com.idcardprinter.core.model.CardQuad
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive touch canvas for 4-corner card boundary adjustment.
 * Features:
 * - 4 draggable corner handles (TL, TR, BR, BL)
 * - Magnifying Loupe (Zoom Glass): displays an offset 2.5x magnified view with crosshairs
 *   above the dragged corner, so fingers never obscure the view.
 * - Boundary validation and haptic feedback.
 */
class CardCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var imageBitmap: Bitmap? = null
    private var corners: CardQuad? = null
    private var onCornersChangedListener: ((CardQuad) -> Unit)? = null

    // Transformation mapping image coordinates to view coordinates
    private var imgScale = 1.0f
    private var imgOffsetX = 0.0f
    private var imgOffsetY = 0.0f

    // Interaction
    private var activeHandleIndex = -1
    private val touchRadiusPx = dpToPx(38f)
    private val handleRadiusPx = dpToPx(12f)

    // Magnifying Loupe
    private val loupeRadiusPx = dpToPx(62f)
    private val loupeOffsetPx = dpToPx(100f)
    private val loupeZoomFactor = 2.5f
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Paints
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(239, 68, 68) // Red
        style = Paint.Style.FILL
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.5f)
    }
    private val handleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(16, 185, 129) // Emerald neon
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.5f)
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(6f), dpToPx(4f)), 0f)
    }
    private val polygonFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(35, 16, 185, 129)
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dpToPx(11f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 15, 23, 42)
        style = Paint.Style.FILL
    }

    // Loupe Paints
    private val loupeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3f)
    }
    private val loupeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(6f)
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(239, 68, 68)
        strokeWidth = dpToPx(1.5f)
    }
    private var loupeShader: BitmapShader? = null
    private val loupeMatrix = Matrix()

    private val vibrator by lazy {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun setImage(bitmap: Bitmap, initialCorners: CardQuad? = null) {
        this.imageBitmap = bitmap
        this.loupeShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        val w = bitmap.width
        val h = bitmap.height
        this.corners = initialCorners ?: CardQuad.defaultFor(w, h)
        if (width > 0 && height > 0) {
            calculateImageScale(width, height)
        }
        requestLayout()
        invalidate()
    }

    fun getImageBitmap(): Bitmap? = imageBitmap

    fun setCorners(quad: CardQuad) {
        this.corners = quad
        invalidate()
    }

    fun getCorners(): CardQuad? = corners

    fun setOnCornersChangedListener(listener: (CardQuad) -> Unit) {
        this.onCornersChangedListener = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateImageScale(w, h)
    }

    private fun calculateImageScale(viewW: Int, viewH: Int) {
        val bmp = imageBitmap ?: return
        if (viewW <= 0 || viewH <= 0) return

        // Inset padding so corner handles and badges are never clipped at screen edges
        val padding = dpToPx(32f)
        val availW = max(10f, viewW - padding * 2)
        val availH = max(10f, viewH - padding * 2)

        val scaleX = availW / bmp.width
        val scaleY = availH / bmp.height
        imgScale = min(scaleX, scaleY)

        val dispW = bmp.width * imgScale
        val dispH = bmp.height * imgScale
        imgOffsetX = (viewW - dispW) / 2f
        imgOffsetY = (viewH - dispH) / 2f
    }

    private fun imgToView(point: CardPoint): Pair<Float, Float> =
        Pair(point.x * imgScale + imgOffsetX, point.y * imgScale + imgOffsetY)

    private fun viewToImg(vx: Float, vy: Float): CardPoint {
        val bmp = imageBitmap ?: return CardPoint(0f, 0f)
        val ix = ((vx - imgOffsetX) / max(imgScale, 1e-6f)).coerceIn(0f, bmp.width.toFloat())
        val iy = ((vy - imgOffsetY) / max(imgScale, 1e-6f)).coerceIn(0f, bmp.height.toFloat())
        return CardPoint(ix, iy)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = imageBitmap ?: return
        val quad = corners ?: return

        if (imgScale <= 0.0001f && width > 0 && height > 0) {
            calculateImageScale(width, height)
        }

        // 1. Draw base image scaled & centered
        val matrix = Matrix().apply {
            postScale(imgScale, imgScale)
            postTranslate(imgOffsetX, imgOffsetY)
        }
        canvas.drawBitmap(bmp, matrix, null)

        // 2. Draw Quad boundary polygon
        val pTL = imgToView(quad.topLeft)
        val pTR = imgToView(quad.topRight)
        val pBR = imgToView(quad.bottomRight)
        val pBL = imgToView(quad.bottomLeft)

        val polyPath = Path().apply {
            moveTo(pTL.first, pTL.second)
            lineTo(pTR.first, pTR.second)
            lineTo(pBR.first, pBR.second)
            lineTo(pBL.first, pBL.second)
            close()
        }
        canvas.drawPath(polyPath, polygonFillPaint)
        canvas.drawPath(polyPath, linePaint)

        // 3. Draw 4 Corner Handles with Badges
        val points = listOf(pTL, pTR, pBR, pBL)
        val labels = listOf("TL", "TR", "BR", "BL")

        for (i in points.indices) {
            val (cx, cy) = points[i]
            // Outer shadow
            canvas.drawCircle(cx, cy, handleRadiusPx + dpToPx(3f), handleShadowPaint)
            // Handle core
            canvas.drawCircle(cx, cy, handleRadiusPx, handlePaint)
            canvas.drawCircle(cx, cy, handleRadiusPx, handleStrokePaint)

            // Label badge
            val badgeW = dpToPx(24f)
            val badgeH = dpToPx(16f)
            val badgeRect = RectF(cx - badgeW / 2, cy - handleRadiusPx - badgeH - dpToPx(4f), cx + badgeW / 2, cy - handleRadiusPx - dpToPx(4f))
            canvas.drawRoundRect(badgeRect, dpToPx(4f), dpToPx(4f), badgePaint)
            canvas.drawText(labels[i], cx, badgeRect.centerY() + dpToPx(4f), textPaint)
        }

        // 4. Draw Magnifying Loupe if currently dragging a handle
        if (isDragging && activeHandleIndex != -1) {
            drawMagnifyingLoupe(canvas)
        }
    }

    private fun drawMagnifyingLoupe(canvas: Canvas) {
        val bmp = imageBitmap ?: return
        val shader = loupeShader ?: return

        // Center of loupe: place it above finger, or flip below if too close to top
        var loupeCenterX = lastTouchX
        var loupeCenterY = lastTouchY - loupeOffsetPx

        if (loupeCenterY - loupeRadiusPx < dpToPx(10f)) {
            loupeCenterY = lastTouchY + loupeOffsetPx
        }
        loupeCenterX = loupeCenterX.coerceIn(loupeRadiusPx + dpToPx(10f), width - loupeRadiusPx - dpToPx(10f))

        // Get touch position in image space
        val touchImgPoint = viewToImg(lastTouchX, lastTouchY)

        // Setup shader matrix to scale and center on touchImgPoint
        loupeMatrix.reset()
        loupeMatrix.postTranslate(-touchImgPoint.x, -touchImgPoint.y)
        loupeMatrix.postScale(loupeZoomFactor, loupeZoomFactor)
        loupeMatrix.postTranslate(loupeCenterX, loupeCenterY)
        shader.setLocalMatrix(loupeMatrix)

        val loupePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }

        // Outer glow/shadow
        canvas.drawCircle(loupeCenterX, loupeCenterY, loupeRadiusPx + dpToPx(2f), loupeShadowPaint)
        // Magnified circular content
        canvas.drawCircle(loupeCenterX, loupeCenterY, loupeRadiusPx, loupePaint)
        // White circular border
        canvas.drawCircle(loupeCenterX, loupeCenterY, loupeRadiusPx, loupeBorderPaint)

        // Precision crosshairs
        val chLen = dpToPx(14f)
        canvas.drawLine(loupeCenterX - chLen, loupeCenterY, loupeCenterX + chLen, loupeCenterY, crosshairPaint)
        canvas.drawLine(loupeCenterX, loupeCenterY - chLen, loupeCenterX, loupeCenterY + chLen, crosshairPaint)
        canvas.drawCircle(loupeCenterX, loupeCenterY, dpToPx(3f), crosshairPaint)
    }

    private fun distToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val l2 = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)
        if (l2 == 0f) return hypot(px - x1, py - y1)
        var t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2
        t = max(0f, min(1f, t))
        return hypot(px - (x1 + t * (x2 - x1)), py - (y1 + t * (y2 - y1)))
    }

    private fun isPointInPolygon(px: Float, py: Float, points: List<Pair<Float, Float>>): Boolean {
        var c = false
        var j = points.size - 1
        for (i in points.indices) {
            val (xi, yi) = points[i]
            val (xj, yj) = points[j]
            if (((yi > py) != (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi) + xi)) {
                c = !c
            }
            j = i
        }
        return c
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val quad = corners ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val tx = event.x
                val ty = event.y

                val points = listOf(
                    imgToView(quad.topLeft),
                    imgToView(quad.topRight),
                    imgToView(quad.bottomRight),
                    imgToView(quad.bottomLeft)
                )

                var bestDist = touchRadiusPx
                activeHandleIndex = -1

                // 1. Check Corners (0, 1, 2, 3)
                for (i in points.indices) {
                    val d = hypot(tx - points[i].first, ty - points[i].second)
                    if (d < bestDist) {
                        bestDist = d
                        activeHandleIndex = i
                    }
                }

                // 2. Check Edges (4: Top, 5: Right, 6: Bottom, 7: Left)
                if (activeHandleIndex == -1) {
                    val edges = listOf(
                        Pair(0, 1), // Top
                        Pair(1, 2), // Right
                        Pair(2, 3), // Bottom
                        Pair(3, 0)  // Left
                    )
                    bestDist = touchRadiusPx
                    for (i in edges.indices) {
                        val (idx1, idx2) = edges[i]
                        val (x1, y1) = points[idx1]
                        val (x2, y2) = points[idx2]
                        val d = distToSegment(tx, ty, x1, y1, x2, y2)
                        if (d < bestDist) {
                            bestDist = d
                            activeHandleIndex = 4 + i
                        }
                    }
                }

                // 3. Check Center (8: Center Box)
                if (activeHandleIndex == -1) {
                    if (isPointInPolygon(tx, ty, points)) {
                        activeHandleIndex = 8
                    }
                }

                if (activeHandleIndex != -1) {
                    isDragging = true
                    lastTouchX = tx
                    lastTouchY = ty
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging && activeHandleIndex != -1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y

                    // Convert screen dx, dy to image dx, dy
                    val imgDx = dx / imgScale
                    val imgDy = dy / imgScale

                    val updated = when (activeHandleIndex) {
                        // Corners
                        0 -> quad.copy(topLeft = viewToImg(event.x, event.y))
                        1 -> quad.copy(topRight = viewToImg(event.x, event.y))
                        2 -> quad.copy(bottomRight = viewToImg(event.x, event.y))
                        3 -> quad.copy(bottomLeft = viewToImg(event.x, event.y))
                        
                        // Edges
                        4 -> quad.copy(
                            topLeft = CardPoint(quad.topLeft.x + imgDx, quad.topLeft.y + imgDy),
                            topRight = CardPoint(quad.topRight.x + imgDx, quad.topRight.y + imgDy)
                        )
                        5 -> quad.copy(
                            topRight = CardPoint(quad.topRight.x + imgDx, quad.topRight.y + imgDy),
                            bottomRight = CardPoint(quad.bottomRight.x + imgDx, quad.bottomRight.y + imgDy)
                        )
                        6 -> quad.copy(
                            bottomRight = CardPoint(quad.bottomRight.x + imgDx, quad.bottomRight.y + imgDy),
                            bottomLeft = CardPoint(quad.bottomLeft.x + imgDx, quad.bottomLeft.y + imgDy)
                        )
                        7 -> quad.copy(
                            bottomLeft = CardPoint(quad.bottomLeft.x + imgDx, quad.bottomLeft.y + imgDy),
                            topLeft = CardPoint(quad.topLeft.x + imgDx, quad.topLeft.y + imgDy)
                        )
                        
                        // Center Box
                        8 -> quad.copy(
                            topLeft = CardPoint(quad.topLeft.x + imgDx, quad.topLeft.y + imgDy),
                            topRight = CardPoint(quad.topRight.x + imgDx, quad.topRight.y + imgDy),
                            bottomRight = CardPoint(quad.bottomRight.x + imgDx, quad.bottomRight.y + imgDy),
                            bottomLeft = CardPoint(quad.bottomLeft.x + imgDx, quad.bottomLeft.y + imgDy)
                        )
                        else -> quad
                    }

                    this.corners = updated
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    activeHandleIndex = -1
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()

                    corners?.let { onCornersChangedListener?.invoke(it) }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dpToPx(dp: Float): Float =
        dp * context.resources.displayMetrics.density
}
