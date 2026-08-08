package com.haneul.medassist.service

import com.haneul.medassist.client.drug.DrugProductApiClient
import com.haneul.medassist.client.dur.DurIngredientApiClient
import com.haneul.medassist.client.dur.DurPairLookupResult
import com.haneul.medassist.domain.interaction.DrugInteractionFailureCode
import com.haneul.medassist.domain.interaction.InteractionCheckStatus
import com.haneul.medassist.domain.interaction.InteractionSeverity
import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.domain.medication.ProductSearchResult
import com.haneul.medassist.support.officialDrugIngredient
import com.haneul.medassist.support.verifiedDrugProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrugInteractionAnalysisServiceTest {
    @Test
    fun `official complex products evaluate every ingredient Cartesian pair`() {
        val client = fakeDrugClient(
            mapOf(
                "P-A" to listOf(officialDrugIngredient("D-A1"), officialDrugIngredient("D-A2")),
                "P-B" to listOf(
                    officialDrugIngredient("D-B1"),
                    officialDrugIngredient("D-B2"),
                    officialDrugIngredient("D-B3"),
                ),
            ),
        )
        val comparison = IngredientComparisonService(
            object : DurIngredientApiClient {
                override fun check(
                    left: com.haneul.medassist.domain.medication.Ingredient,
                    right: com.haneul.medassist.domain.medication.Ingredient,
                ) = DurPairLookupResult.NoMatch
            },
            CoverageCalculator(),
        )

        val result = DrugInteractionAnalysisService(client, comparison).analyze("P-A", "P-B")

        assertEquals(InteractionCheckStatus.COMPLETED, result.processingStatus)
        assertEquals(InteractionSeverity.NO_KNOWN_ISSUE, result.interaction?.status)
        assertEquals(6, result.interaction?.coverage?.totalPairs)
        assertEquals(6, result.interaction?.coverage?.completedPairs)
    }

    @Test
    fun `missing official product fails before pair evaluation`() {
        val client = fakeDrugClient(mapOf("P-A" to listOf(officialDrugIngredient("D-A1"))))
        val comparison = IngredientComparisonService(object : DurIngredientApiClient {}, CoverageCalculator())

        val result = DrugInteractionAnalysisService(client, comparison).analyze("P-A", "P-404")

        assertEquals(InteractionCheckStatus.FAILED, result.processingStatus)
        assertTrue(DrugInteractionFailureCode.RIGHT_PRODUCT_NOT_FOUND in result.failedSteps)
        assertEquals(null, result.interaction)
    }

    private fun fakeDrugClient(ingredientsByProduct: Map<String, List<com.haneul.medassist.domain.medication.Ingredient>>) =
        object : DrugProductApiClient {
            override fun searchProducts(productName: String) = ProductSearchResult.Success(emptyList())

            override fun findProduct(productCode: String) =
                ingredientsByProduct[productCode]?.let { verifiedDrugProduct(productCode) }

            override fun findIngredients(productCode: String, productName: String) =
                IngredientSearchResult.Success(ingredientsByProduct[productCode].orEmpty())
        }
}
