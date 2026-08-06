package com.haneul.medassist.client.drug.overview

import com.haneul.medassist.domain.medication.DrugOverviewLookupResult

interface DrugOverviewApiClient {
    fun findOverview(
        productCode: String,
        productName: String,
        manufacturer: String?,
    ): DrugOverviewLookupResult
}

data class DrugOverviewProviderQuery(
    val itemSeq: String? = null,
    val itemName: String? = null,
    val manufacturer: String? = null,
)
