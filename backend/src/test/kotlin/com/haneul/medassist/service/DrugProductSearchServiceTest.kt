package com.haneul.medassist.service

import com.haneul.medassist.client.drug.DrugProductApiClient
import com.haneul.medassist.config.MatchingProperties
import com.haneul.medassist.config.PublicDataCacheProperties
import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.domain.medication.ProductSearchResult
import com.haneul.medassist.domain.medication.SourceMetadata
import com.haneul.medassist.domain.medication.VerifiedDrugProduct
import com.haneul.medassist.dto.drug.DrugProductSearchRequest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrugProductSearchServiceTest {
    @Test
    fun `returns only products supplied by official provider`() {
        val provider = FakeDrugProductApiClient(
            products = listOf(product("official-1", "타이레놀정500밀리그램")),
            ingredientResult = IngredientSearchResult.Success(emptyList()),
        )
        val service = service(provider)

        val response = service.search(DrugProductSearchRequest("타이레놀정 500mg"))

        assertEquals(listOf("official-1"), response.candidates.map { it.productCode })
        assertEquals("NOT_FOUND", response.candidates.single().ingredientLookupStatus)
        assertTrue(response.requiresUserConfirmation)
        assertFalse(response.coverage.complete)
    }

    @Test
    fun `successful official empty result is a normal empty response`() {
        val service = service(FakeDrugProductApiClient(emptyList(), IngredientSearchResult.SchemaUnverified))

        val response = service.search(DrugProductSearchRequest("없는약품"))

        assertTrue(response.candidates.isEmpty())
        assertFalse(response.requiresUserConfirmation)
        assertTrue(response.coverage.complete)
    }

    private fun service(client: DrugProductApiClient): DrugProductSearchService {
        val matching = MatchingProperties()
        val normalizer = DrugNameNormalizer()
        return DrugProductSearchService(
            apiClient = client,
            normalizer = normalizer,
            matcher = DrugProductMatcher(normalizer, matching),
            cache = DrugProductCache(PublicDataCacheProperties()),
            matchingProperties = matching,
        )
    }

    private fun product(code: String, name: String) = VerifiedDrugProduct(
        productCode = code,
        productName = name,
        manufacturer = "공식제조사",
        source = SourceMetadata("공식공급자", code, Instant.EPOCH, "공식참조"),
    )
}

private class FakeDrugProductApiClient(
    private val products: List<VerifiedDrugProduct>,
    private val ingredientResult: IngredientSearchResult,
) : DrugProductApiClient {
    override fun searchProducts(productName: String) = ProductSearchResult.Success(products)

    override fun findIngredients(productCode: String): IngredientSearchResult = ingredientResult
}
