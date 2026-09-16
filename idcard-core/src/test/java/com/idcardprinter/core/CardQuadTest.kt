package com.idcardprinter.core

import com.idcardprinter.core.model.CardPoint
import com.idcardprinter.core.model.CardQuad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardQuadTest {

    @Test
    fun testDefaultQuadCreation() {
        val quad = CardQuad.defaultFor(1000, 1000, 0.10f, 0.20f)
        assertEquals(100f, quad.topLeft.x, 0.01f)
        assertEquals(200f, quad.topLeft.y, 0.01f)
        assertEquals(900f, quad.topRight.x, 0.01f)
        assertEquals(200f, quad.topRight.y, 0.01f)
        assertEquals(900f, quad.bottomRight.x, 0.01f)
        assertEquals(800f, quad.bottomRight.y, 0.01f)
        assertEquals(100f, quad.bottomLeft.x, 0.01f)
        assertEquals(800f, quad.bottomLeft.y, 0.01f)
    }

    @Test
    fun testQuadConvexity() {
        val convexQuad = CardQuad(
            topLeft = CardPoint(10f, 10f),
            topRight = CardPoint(100f, 10f),
            bottomRight = CardPoint(100f, 70f),
            bottomLeft = CardPoint(10f, 70f)
        )
        assertTrue(convexQuad.isConvex())

        // Non-convex / self-intersecting quad (crossed corners)
        val selfIntersecting = CardQuad(
            topLeft = CardPoint(10f, 10f),
            topRight = CardPoint(100f, 70f),
            bottomRight = CardPoint(100f, 10f),
            bottomLeft = CardPoint(10f, 70f)
        )
        assertFalse(selfIntersecting.isConvex())
    }

    @Test
    fun testAspectRatio() {
        val quad = CardQuad(
            topLeft = CardPoint(0f, 0f),
            topRight = CardPoint(158f, 0f),
            bottomRight = CardPoint(158f, 100f),
            bottomLeft = CardPoint(0f, 100f)
        )
        assertEquals(1.58f, quad.aspectRatio(), 0.01f)
    }

    @Test
    fun testScaling() {
        val quad = CardQuad(
            topLeft = CardPoint(10f, 20f),
            topRight = CardPoint(30f, 20f),
            bottomRight = CardPoint(30f, 40f),
            bottomLeft = CardPoint(10f, 40f)
        )
        val scaled = quad.scaled(2f, 3f)
        assertEquals(20f, scaled.topLeft.x, 0.01f)
        assertEquals(60f, scaled.topLeft.y, 0.01f)
        assertEquals(60f, scaled.topRight.x, 0.01f)
        assertEquals(60f, scaled.topRight.y, 0.01f)
    }
}
