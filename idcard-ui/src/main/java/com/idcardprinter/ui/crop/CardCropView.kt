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
        requestLayout()
        invalidate()
    }

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

        val scaleX = viewW.toFloat() / bmp.width
        val scaleY = viewH.toFloat() / bmp.height
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

                // Find closest corner within touch target
                var bestDist = touchRadiusPx
                activeHandleIndex = -1

                for (i in points.indices) {
                    val d = hypot(tx - points[i].first, ty - points[i].second)
                    if (d < bestDist) {
                        bestDist = d
                        activeHandleIndex = i
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
                    lastTouchX = event.x
                    lastTouchY = event.y

                    val imgPoint = viewToImg(event.x, event.y)
                    val updated = when (activeHandleIndex) {
                        0 -> quad.copy(topLeft = imgPoint)
                        1 -> quad.copy(topRight = imgPoint)
                        2 -> quad.copy(bottomRight = imgPoint)
                        3 -> quad.copy(bottomLeft = imgPoint)
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
