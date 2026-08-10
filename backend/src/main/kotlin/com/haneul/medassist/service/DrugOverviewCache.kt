package com.haneul.medassist.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.haneul.medassist.config.PublicDataCacheProperties
import com.haneul.medassist.domain.medication.DrugOverviewLookupResult
import com.haneul.medassist.domain.medication.DrugOverviewLookupStatus
import org.springframework.stereotype.Component

@Component
class DrugOverviewCache(
    properties: PublicDataCacheProperties,
) {
    private val resolved: Cache<String, DrugOverviewLookupResult> = Caffeine.newBuilder()
        .maximumSize(properties.maximumSize)
        .expireAfterWrite(properties.overviewTtl)
        .build()
    private val notFound: Cache<String, DrugOverviewLookupResult> = Caffeine.newBuilder()
        .maximumSize(properties.maximumSize)
        .expireAfterWrite(properties.negativeSearchTtl)
        .build()

    fun get(productCode: String): DrugOverviewLookupResult? =
        resolved.getIfPresent(productCode) ?: notFound.getIfPresent(productCode)

    fun put(productCode: String, result: DrugOverviewLookupResult) {
        when (result.status) {
            DrugOverviewLookupStatus.RESOLVED -> resolved.put(productCode, result)
            DrugOverviewLookupStatus.NOT_FOUND -> notFound.put(productCode, result)
            DrugOverviewLookupStatus.FAILED,
            DrugOverviewLookupStatus.PARTIAL,
            -> Unit
        }
    }
}
