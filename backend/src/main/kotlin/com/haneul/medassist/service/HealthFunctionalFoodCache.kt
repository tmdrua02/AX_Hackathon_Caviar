package com.haneul.medassist.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.haneul.medassist.config.PublicDataCacheProperties
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodLookupStatus
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodSearchResult
import com.haneul.medassist.domain.supplement.SupplementProductSnapshotResult
import org.springframework.stereotype.Component

@Component
class HealthFunctionalFoodCache(
    properties: PublicDataCacheProperties,
) {
    private val search: Cache<String, HealthFunctionalFoodSearchResult> = Caffeine.newBuilder()
        .maximumSize(properties.maximumSize)
        .expireAfterWrite(properties.positiveSearchTtl)
        .build()
    private val negativeSearch: Cache<String, HealthFunctionalFoodSearchResult> = Caffeine.newBuilder()
        .maximumSize(properties.maximumSize)
        .expireAfterWrite(properties.negativeSearchTtl)
        .build()
    private val detail: Cache<String, SupplementProductSnapshotResult> = Caffeine.newBuilder()
        .maximumSize(properties.maximumSize)
        .expireAfterWrite(properties.overviewTtl)
        .build()
    private val negativeDetail: Cache<String, SupplementProductSnapshotResult> = Caffeine.newBuilder()
        .maximumSize(properties.maximumSize)
        .expireAfterWrite(properties.negativeSearchTtl)
        .build()

    fun search(key: String): HealthFunctionalFoodSearchResult? =
        search.getIfPresent(key) ?: negativeSearch.getIfPresent(key)

    fun putSearch(key: String, result: HealthFunctionalFoodSearchResult) {
        when (result.status) {
            HealthFunctionalFoodLookupStatus.RESOLVED -> search.put(key, result)
            HealthFunctionalFoodLookupStatus.NOT_FOUND -> negativeSearch.put(key, result)
            HealthFunctionalFoodLookupStatus.FAILED,
            HealthFunctionalFoodLookupStatus.PARTIAL,
            -> Unit
        }
    }

    fun detail(statementNo: String): SupplementProductSnapshotResult? =
        detail.getIfPresent(statementNo) ?: negativeDetail.getIfPresent(statementNo)

    fun putDetail(statementNo: String, result: SupplementProductSnapshotResult) {
        when (result.status) {
            HealthFunctionalFoodLookupStatus.RESOLVED -> detail.put(statementNo, result)
            HealthFunctionalFoodLookupStatus.NOT_FOUND -> negativeDetail.put(statementNo, result)
            HealthFunctionalFoodLookupStatus.FAILED,
            HealthFunctionalFoodLookupStatus.PARTIAL,
            -> Unit
        }
    }
}
