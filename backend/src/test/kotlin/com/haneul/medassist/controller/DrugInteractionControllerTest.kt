package com.haneul.medassist.controller

import com.haneul.medassist.client.drug.DrugProductApiClient
import com.haneul.medassist.client.dur.DurIngredientApiClient
import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.domain.medication.ProductSearchResult
import com.haneul.medassist.exception.GlobalExceptionHandler
import com.haneul.medassist.service.CoverageCalculator
import com.haneul.medassist.service.DrugInteractionAnalysisService
import com.haneul.medassist.service.IngredientComparisonService
import com.haneul.medassist.support.officialDrugIngredient
import com.haneul.medassist.support.verifiedDrugProduct
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DrugInteractionControllerTest {
    @Test
    fun `batch endpoint returns official duplicate ingredient result`() {
        val ingredients = mapOf(
            "NEW-1" to listOf(officialDrugIngredient("ING-1")),
            "OLD-1" to listOf(officialDrugIngredient("ING-1")),
        )

        mvc(ingredients).perform(
            post("/api/v1/drug-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "newMedicationProductCode":"NEW-1",
                      "existingMedicationProductCodes":["OLD-1"]
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processingStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.results[0].severity").value("DUPLICATE_OR_SIMILAR"))
            .andExpect(jsonPath("$.results[0].evidence[0].evidenceType").value("DUPLICATE"))
            .andExpect(jsonPath("$.coverage.completedComparisons").value(1))
    }

    @Test
    fun `batch endpoint preserves failed official resolution instead of reporting safe`() {
        val ingredients = mapOf("NEW-1" to listOf(officialDrugIngredient("ING-1")))

        mvc(ingredients).perform(
            post("/api/v1/drug-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "newMedicationProductCode":"NEW-1",
                      "existingMedicationProductCodes":["MISSING"]
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processingStatus").value("FAILED"))
            .andExpect(jsonPath("$.results[0].severity").doesNotExist())
            .andExpect(jsonPath("$.results[0].failedSteps[0]").value("RIGHT_PRODUCT_NOT_FOUND"))
    }

    @Test
    fun `batch endpoint rejects empty comparison list`() {
        mvc(emptyMap()).perform(
            post("/api/v1/drug-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "newMedicationProductCode":"NEW-1",
                      "existingMedicationProductCodes":[]
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    private fun mvc(ingredientsByProduct: Map<String, List<com.haneul.medassist.domain.medication.Ingredient>>) =
        MockMvcBuilders.standaloneSetup(
            DrugInteractionController(
                DrugInteractionAnalysisService(
                    object : DrugProductApiClient {
                        override fun searchProducts(productName: String) = ProductSearchResult.Success(emptyList())
                        override fun findProduct(productCode: String) =
                            ingredientsByProduct[productCode]?.let { verifiedDrugProduct(productCode) }

                        override fun findIngredients(productCode: String, productName: String) =
                            IngredientSearchResult.Success(ingredientsByProduct[productCode].orEmpty())
                    },
                    IngredientComparisonService(object : DurIngredientApiClient {}, CoverageCalculator()),
                ),
            ),
        )
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
}
