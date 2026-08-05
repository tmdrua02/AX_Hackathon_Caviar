package com.haneul.medassist.controller

import com.haneul.medassist.client.drug.DrugProductApiClient
import com.haneul.medassist.config.MatchingProperties
import com.haneul.medassist.config.PublicDataCacheProperties
import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.domain.medication.ProductSearchResult
import com.haneul.medassist.domain.medication.SourceMetadata
import com.haneul.medassist.domain.medication.VerifiedDrugProduct
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.GlobalExceptionHandler
import com.haneul.medassist.exception.PublicDataApiException
import com.haneul.medassist.service.DrugNameNormalizer
import com.haneul.medassist.service.DrugProductCache
import com.haneul.medassist.service.DrugProductMatcher
import com.haneul.medassist.service.DrugProductSearchService
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class DrugProductControllerTest {
    @Test
    fun `blank query returns validation problem detail`() {
        mvc(SuccessClient(emptyList())).perform(
            post("/api/v1/drug-products/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query":"   "}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `official no result returns 200 and empty candidates`() {
        mvc(SuccessClient(emptyList())).perform(
            post("/api/v1/drug-products/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query":"없는제품"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.candidates").isEmpty)
            .andExpect(jsonPath("$.requiresUserConfirmation").value(false))
    }

    @Test
    fun `provider failure returns problem detail instead of empty success`() {
        mvc(FailingClient()).perform(
            post("/api/v1/drug-products/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query":"타이레놀"}"""),
        )
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("PUBLIC_API_UNAVAILABLE"))
    }

    private fun mvc(client: DrugProductApiClient): MockMvc {
        val matching = MatchingProperties()
        val normalizer = DrugNameNormalizer()
        val service = DrugProductSearchService(
            apiClient = client,
            normalizer = normalizer,
            matcher = DrugProductMatcher(normalizer, matching),
            cache = DrugProductCache(PublicDataCacheProperties()),
            matchingProperties = matching,
        )
        return MockMvcBuilders
            .standaloneSetup(DrugProductController(service))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }
}

private class SuccessClient(
    private val products: List<VerifiedDrugProduct>,
) : DrugProductApiClient {
    override fun searchProducts(productName: String) = ProductSearchResult.Success(products)
    override fun findIngredients(productCode: String) = IngredientSearchResult.Success(emptyList())
}

private class FailingClient : DrugProductApiClient {
    override fun searchProducts(productName: String): ProductSearchResult.Success {
        throw PublicDataApiException(ApiErrorCode.PUBLIC_API_UNAVAILABLE, retryable = true)
    }

    override fun findIngredients(productCode: String) = IngredientSearchResult.ProviderError("not-called")
}

@Suppress("unused")
private fun officialProduct() = VerifiedDrugProduct(
    "P-1",
    "공식제품",
    "공식제조사",
    SourceMetadata("공식공급자", "P-1", Instant.EPOCH, "공식참조"),
)
