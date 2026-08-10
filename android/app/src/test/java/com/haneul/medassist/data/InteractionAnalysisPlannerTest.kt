package com.haneul.medassist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionAnalysisPlannerTest {
    @Test
    fun `analysis requires at least two active selected products`() {
        val first = medication("first", "CODE-1")
        val second = medication("second", "CODE-2")
        val inactive = medication("inactive", "CODE-3", active = false)

        assertFalse(InteractionAnalysisPlanner.canAnalyze(listOf(first, second), emptySet()))
        assertFalse(InteractionAnalysisPlanner.canAnalyze(listOf(first, second), setOf(first.id)))
        assertTrue(InteractionAnalysisPlanner.canAnalyze(listOf(first, second), setOf(first.id, second.id)))
        assertFalse(InteractionAnalysisPlanner.canAnalyze(listOf(first, inactive), setOf(first.id, inactive.id)))
    }

    @Test
    fun `reconcile keeps previous valid selection without auto selecting newly scanned product`() {
        val selected = medication("selected", "CODE-1")
        val scanned = medication("scanned", "CODE-2")
        val deletedId = "deleted"

        val reconciled = InteractionAnalysisPlanner.reconcileSelection(
            medications = listOf(selected, scanned),
            selectedIds = setOf(selected.id, deletedId),
        )

        assertEquals(setOf(selected.id), reconciled)
        assertFalse(scanned.id in reconciled)
    }

    @Test
    fun `three selected products produce every unordered pair once`() {
        val products = listOf(
            medication("first", "CODE-1"),
            medication("second", "CODE-2"),
            medication("third", "CODE-3"),
        )

        val pairKeys = InteractionAnalysisPlanner.allPairs(products)
            .map { setOf(it.left.id, it.right.id) }

        assertEquals(3, pairKeys.size)
        assertEquals(3, pairKeys.distinct().size)
    }

    @Test
    fun `official requests cover every pair and respect server batch limit`() {
        val products = (1..23).map { medication("id-$it", "CODE-$it") }
        val batches = InteractionAnalysisPlanner.officialDrugBatches(products)
        val requestedPairs = batches.flatMap { (reference, comparisons) ->
            comparisons.map { comparison -> setOf(reference.id, comparison.id) }
        }

        assertTrue(batches.all { it.second.size <= 20 })
        assertEquals(23 * 22 / 2, requestedPairs.size)
        assertEquals(requestedPairs.size, requestedPairs.distinct().size)
        assertEquals((1..23).map { "CODE-$it" }.toSet(), batches
            .flatMap { (reference, comparisons) -> listOfNotNull(reference.productCode) + comparisons.mapNotNull { it.productCode } }
            .toSet())
    }

    @Test
    fun `demo and mock identities are never sent as official drug requests`() {
        val official = medication("official", "202106092")
        val demo = medication("demo", "DEMO-1111")
        val mockIngredient = medication("mock", "198601920").copy(
            ingredients = listOf(Ingredient("이부프로펜", "ibuprofen", "MOCK")),
        )

        assertTrue(InteractionAnalysisPlanner.hasOfficialDrugIdentity(official))
        assertFalse(InteractionAnalysisPlanner.hasOfficialDrugIdentity(demo))
        assertFalse(InteractionAnalysisPlanner.hasOfficialDrugIdentity(mockIngredient))
        assertTrue(InteractionAnalysisPlanner.officialDrugBatches(listOf(official, demo, mockIngredient)).isEmpty())
    }

    @Test
    fun `run guard rejects duplicate start until matching run finishes`() {
        val guard = InteractionAnalysisRunGuard()

        assertTrue(guard.begin("run-1"))
        assertFalse(guard.begin("run-2"))
        guard.finish("another-run")
        assertFalse(guard.begin("run-2"))
        guard.finish("run-1")
        assertTrue(guard.begin("run-2"))
    }

    private fun medication(id: String, code: String?, active: Boolean = true) = Medication(
        id = id,
        name = "제품 $id",
        productType = ProductType.OTC_DRUG,
        productCode = code,
        active = active,
    )
}
