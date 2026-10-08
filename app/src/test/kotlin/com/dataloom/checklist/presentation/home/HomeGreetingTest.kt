package com.dataloom.checklist.presentation.home

import com.dataloom.checklist.domain.model.Checklist
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeGreetingTest {

    @Test
    fun `the greeting uses the first word of the name`() {
        assertEquals("Kamala", greetingName("Kamala Hiremath"))
        assertEquals("ಕಮಲಾ", greetingName("  ಕಮಲಾ  ಹಿರೇಮಠ "))
        assertEquals("Kamala", greetingName("Kamala"))
    }

    @Test
    fun `no name means no greeting`() {
        assertNull(greetingName(null))
        assertNull(greetingName(""))
        assertNull(greetingName("   "))
    }

    @Test
    fun `only active lists with unfinished items are in progress`() {
        val lists = listOf(
            summary("a", total = 12, done = 3),
            summary("b", total = 24, done = 10),
            summary("c", total = 6, done = 0),
            summary("finished", total = 4, done = 4),
            summary("empty", total = 0, done = 0),
            summary("archived", total = 5, done = 1, archived = true),
        )

        assertEquals(3, countInProgress(lists))
        assertEquals(0, countInProgress(emptyList()))
    }

    private fun summary(id: String, total: Int, done: Int, archived: Boolean = false) = ChecklistSummary(
        checklist = Checklist(
            id = ChecklistId(id),
            title = id,
            description = null,
            isArchived = archived,
            createdAt = 0L,
            updatedAt = 0L,
        ),
        totalItems = total,
        completedItems = done,
    )
}
