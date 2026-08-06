package com.haneul.medassist.controller

import com.haneul.medassist.client.supplement.HealthFunctionalFoodApiClient
import com.haneul.medassist.config.PublicDataCacheProperties
import com.haneul.medassist.domain.medication.SourceMetadata
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodLookupStatus
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodSearchResult
import com.haneul.medassist.domain.supplement.SupplementProductSnapshotResult
import com.haneul.medassist.domain.supplement.SupplementSearchCandidate
import com.haneul.medassist.domain.supplement.SupplementSearchSourceType
import com.haneul.medassist.exception.GlobalExceptionHandler
import com.haneul.medassist.service.HealthFunctionalFoodCache
import com.haneul.medassist.service.HealthFunctionalFoodService
import com.haneul.medassist.service.SupplementNameNormalizer
import com.haneul.medassist.service.SupplementSearchIndexLoader
import com.haneul.medassist.service.SupplementSearchIndexService
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class SupplementProductControllerTest {
    @Test
    fun `normal provider not found returns ranked index fallback candidates`() {
        mvc().perform(
            post("/api/v1/supplement-products/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query":"루테인"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.query").value("루테인"))
            .andExpect(jsonPath("$.normalizedQuery").value("루테인"))
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.sourceType").value("INDEX_FALLBACK"))
            .andExpect(jsonPath("$.complete").value(true))
            .andExpect(jsonPath("$.candidates[0].sttemntNo").value("S-1"))
            .andExpect(jsonPath("$.candidates[0].productName").value("루테인"))
            .andExpect(jsonPath("$.candidates[0].matchScore").value(100))
            .andExpect(jsonPath("$.candidates[0].matchType").value("EXACT"))
            .andExpect(jsonPath("$.candidates[0].source.recordId").value("S-1"))
    }

    @Test
    fun `not found is a normal empty response`() {
        mvc().perform(
            post("/api/v1/supplement-products/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query":"없는제품"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("NOT_FOUND"))
            .andExpect(jsonPath("$.candidates").isEmpty)
    }

    @Test
    fun `blank query returns validation problem detail`() {
        mvc().perform(
            post("/api/v1/supplement-products/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query":"   "}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `provider failure returns problem detail and does not look like empty success`() {
        mvc(HealthFunctionalFoodLookupStatus.FAILED).perform(
            post("/api/v1/supplement-products/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query":"루테인"}"""),
        )
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("PUBLIC_API_UNAVAILABLE"))
    }

    private fun mvc(providerStatus: HealthFunctionalFoodLookupStatus = HealthFunctionalFoodLookupStatus.NOT_FOUND): MockMvc {
        val candidate = SupplementSearchCandidate(
            sttemntNo = "S-1",
            productName = "루테인",
            manufacturer = "공식업체",
            normalizedName = "루테인",
            aliases = emptySet(),
            source = SourceMetadata(
                name = "테스트 인덱스",
                recordId = "S-1",
                retrievedAt = Instant.EPOCH,
                providerReference = "TEST_INDEX",
            ),
        )
        val normalizer = SupplementNameNormalizer()
        val index = SupplementSearchIndexService(
            loader = SupplementSearchIndexLoader { listOf(candidate) },
            normalizer = normalizer,
            cacheProperties = PublicDataCacheProperties(),
        )
        val provider = object : HealthFunctionalFoodApiClient {
            override fun search(productName: String, manufacturer: String?) = HealthFunctionalFoodSearchResult(
                status = providerStatus,
                candidates = emptyList(),
                totalCount = if (providerStatus == HealthFunctionalFoodLookupStatus.NOT_FOUND) 0 else null,
                completedPages = if (providerStatus == HealthFunctionalFoodLookupStatus.NOT_FOUND) listOf(1) else emptyList(),
                failedPages = if (providerStatus == HealthFunctionalFoodLookupStatus.FAILED) listOf(1) else emptyList(),
                complete = providerStatus == HealthFunctionalFoodLookupStatus.NOT_FOUND,
                sourceType = SupplementSearchSourceType.PROVIDER,
                retrievedAt = Instant.EPOCH,
                providerResultCode = if (providerStatus == HealthFunctionalFoodLookupStatus.NOT_FOUND) "00" else null,
                errorCode = if (providerStatus == HealthFunctionalFoodLookupStatus.FAILED) {
                    "PUBLIC_API_UNAVAILABLE"
                } else {
                    null
                },
            )

            override fun findByStatementNo(statementNo: String) = SupplementProductSnapshotResult(
                status = HealthFunctionalFoodLookupStatus.NOT_FOUND,
                snapshot = null,
                totalCount = 0,
                completedPages = listOf(1),
                failedPages = emptyList(),
                complete = true,
                retrievedAt = Instant.EPOCH,
            )
        }
        val service = HealthFunctionalFoodService(
            apiClient = provider,
            searchIndexService = index,
            normalizer = normalizer,
            cache = HealthFunctionalFoodCache(PublicDataCacheProperties()),
        )
        return MockMvcBuilders
            .standaloneSetup(SupplementProductController(service, normalizer))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }
}
