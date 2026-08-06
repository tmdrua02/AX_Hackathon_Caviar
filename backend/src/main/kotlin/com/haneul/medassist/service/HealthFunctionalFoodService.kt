package com.haneul.medassist.service

import com.haneul.medassist.client.supplement.HealthFunctionalFoodApiClient
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodLookupStatus
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodSearchResult
import com.haneul.medassist.domain.supplement.SupplementProductSnapshotResult
import com.haneul.medassist.domain.supplement.SupplementSearchSourceType
import org.springframework.stereotype.Service

@Service
class HealthFunctionalFoodService(
    private val apiClient: HealthFunctionalFoodApiClient,
    private val searchIndexService: SupplementSearchIndexService,
    private val normalizer: SupplementNameNormalizer,
    private val cache: HealthFunctionalFoodCache,
) {
    fun search(productName: String, manufacturer: String? = null): HealthFunctionalFoodSearchResult {
        val normalizedName = normalizer.normalize(productName)
        val normalizedManufacturer = manufacturer?.let(normalizer::normalize).orEmpty()
        val cacheKey = "$normalizedName|$normalizedManufacturer"
        cache.search(cacheKey)?.let { return it }

        val provider = apiClient.search(productName, manufacturer)
        val result = if (provider.status == HealthFunctionalFoodLookupStatus.NOT_FOUND) {
            val indexMatches = searchIndexService.search(productName)
                .filter { match ->
                    normalizedManufacturer.isBlank() ||
                        normalizer.normalize(match.candidate.manufacturer.orEmpty()) == normalizedManufacturer
                }
            provider.copy(
                status = if (indexMatches.isEmpty()) {
                    HealthFunctionalFoodLookupStatus.NOT_FOUND
                } else {
                    HealthFunctionalFoodLookupStatus.RESOLVED
                },
                candidates = indexMatches,
                complete = true,
                sourceType = SupplementSearchSourceType.INDEX_FALLBACK,
            )
        } else {
            provider
        }
        cache.putSearch(cacheKey, result)
        return result
    }

    fun findByStatementNo(statementNo: String): SupplementProductSnapshotResult {
        val key = statementNo.trim()
        cache.detail(key)?.let { return it }
        return apiClient.findByStatementNo(key).also { result -> cache.putDetail(key, result) }
    }
}
