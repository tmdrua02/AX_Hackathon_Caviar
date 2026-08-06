package com.haneul.medassist.service

import com.haneul.medassist.client.drug.overview.DrugOverviewApiClient
import com.haneul.medassist.domain.medication.DrugOverviewCoverage
import com.haneul.medassist.domain.medication.DrugOverviewLookupResult
import com.haneul.medassist.domain.medication.DrugOverviewLookupStatus
import com.haneul.medassist.domain.medication.Medication
import com.haneul.medassist.exception.ApiErrorCode
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DrugOverviewService(
    private val client: DrugOverviewApiClient,
    private val cache: DrugOverviewCache,
) {
    fun findOverview(medication: Medication): DrugOverviewLookupResult {
        val productCode = medication.productCode
        if (productCode.isNullOrBlank()) {
            return DrugOverviewLookupResult(
                status = DrugOverviewLookupStatus.FAILED,
                overview = null,
                coverage = DrugOverviewCoverage(false, false, false),
                totalCount = null,
                completedPages = emptyList(),
                failedPages = emptyList(),
                retrievedAt = Instant.now(),
                errorCode = ApiErrorCode.VALIDATION_FAILED.name,
            )
        }
        cache.get(productCode)?.let { return it }
        return client.findOverview(productCode, medication.productName, medication.manufacturer)
            .also { cache.put(productCode, it) }
    }
}
