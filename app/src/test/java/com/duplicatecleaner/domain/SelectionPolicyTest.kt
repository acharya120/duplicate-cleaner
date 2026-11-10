package com.duplicatecleaner.domain

import com.duplicatecleaner.domain.model.DuplicateGroup
import com.duplicatecleaner.domain.model.GroupType
import com.duplicatecleaner.domain.usecase.SelectionPolicy
import com.duplicatecleaner.testFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionPolicyTest {
    private val a = testFile("a", size = 10, modified = 5)
    private val b = testFile("b", size = 10, modified = 1)
    private val c = testFile("c", size = 10, modified = 9)
    private val exact = DuplicateGroup("g1", GroupType.EXACT_PHOTO, listOf(a, b, c))

    @Test fun `selecting every file of a group is rejected`() {
        assertFalse(SelectionPolicy.isSafe(listOf(exact), setOf("a", "b", "c")))
        assertEquals(listOf("g1"), SelectionPolicy.violatingGroups(listOf(exact), setOf("a", "b", "c")))
    }

    @Test fun `leaving one copy is allowed`() = assertTrue(SelectionPolicy.isSafe(listOf(exact), setOf("a", "b")))

    @Test fun `canSelect blocks the last remaining copy`() {
        assertTrue(SelectionPolicy.canSelect(listOf(exact), setOf("a"), "b"))
        assertFalse(SelectionPolicy.canSelect(listOf(exact), setOf("a", "b"), "c"))
    }

    @Test fun `protection applies across overlapping groups`() {
        val similar = DuplicateGroup("g2", GroupType.SIMILAR_PHOTO, listOf(a, testFile("d")))
        assertFalse(SelectionPolicy.isSafe(listOf(exact, similar), setOf("a", "d")))
        assertTrue(SelectionPolicy.isSafe(listOf(exact, similar), setOf("a")))
    }

    @Test fun `selectAllExcept keeps exactly the chosen original`() {
        assertEquals(setOf("a", "c"), SelectionPolicy.selectAllExcept(exact, "b"))
    }

    @Test fun `suggested keeper is a suggestion that prefers oldest among equal sizes`() {
        assertEquals("b", SelectionPolicy.suggestedKeeper(exact).id)
    }

    @Test fun `plan is null for empty or unsafe selections`() {
        assertNull(SelectionPolicy.buildPlan(listOf(exact), emptySet()))
        assertNull(SelectionPolicy.buildPlan(listOf(exact), setOf("a", "b", "c")))
    }

    @Test fun `plan counts distinct files and exact bytes`() {
        val plan = SelectionPolicy.buildPlan(listOf(exact, DuplicateGroup("g2", GroupType.SIMILAR_PHOTO, listOf(a, testFile("d")))), setOf("a", "b"))
        assertNotNull(plan)
        assertEquals(2, plan!!.files.size)
        assertEquals(20L, plan.estimatedBytes)
    }
}
