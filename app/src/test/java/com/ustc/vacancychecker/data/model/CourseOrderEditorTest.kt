package com.ustc.vacancychecker.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseOrderEditorTest {
    @Test
    fun `held classroom can repeatedly swap forward and backward`() {
        var order = listOf("A", "B", "C")

        order = CourseOrderEditor.move(order, order.indexOf("A"), 1)
        assertEquals(listOf("B", "A", "C"), order)

        order = CourseOrderEditor.move(order, order.indexOf("A"), 1)
        assertEquals(listOf("B", "C", "A"), order)

        order = CourseOrderEditor.move(order, order.indexOf("A"), -1)
        assertEquals(listOf("B", "A", "C"), order)

        order = CourseOrderEditor.move(order, order.indexOf("A"), -1)
        assertEquals(listOf("A", "B", "C"), order)
    }

    @Test
    fun `moving past group boundary keeps current order instance`() {
        val order = listOf("A", "B")

        val result = CourseOrderEditor.move(order, 0, -1)

        assertEquals(order, result)
        assert(result === order)
    }
}
