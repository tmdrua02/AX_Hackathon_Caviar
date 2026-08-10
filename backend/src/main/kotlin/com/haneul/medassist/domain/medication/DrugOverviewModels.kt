package com.haneul.medassist.domain.medication

import java.time.Instant

data class OfficialMedicalText(
    val raw: String,
    val display: String,
)

data class DrugOverviewCoverage(
    val productResolved: Boolean,
    val overviewResolved: Boolean,
    val complete: Boolean,
)

data class DrugOverview(
    val productCode: String,
    val productName: String,
    val manufacturer: String?,
    val efficacy: OfficialMedicalText?,
    val usageMethod: OfficialMedicalText?,
    val warning: OfficialMedicalText?,
    val precautions: OfficialMedicalText?,
    val interactions: OfficialMedicalText?,
    val sideEffects: OfficialMedicalText?,
    val storageMethod: OfficialMedicalText?,
    val imageUrl: String?,
    val openDate: String?,
    val updateDate: String?,
    val source: SourceMetadata,
    val coverage: DrugOverviewCoverage,
)

enum class DrugOverviewLookupStatus {
    RESOLVED,
    NOT_FOUND,
    FAILED,
    PARTIAL,
}

data class DrugOverviewLookupResult(
    val status: DrugOverviewLookupStatus,
    val overview: DrugOverview?,
    val coverage: DrugOverviewCoverage,
    val totalCount: Int?,
    val completedPages: List<Int>,
    val failedPages: List<Int>,
    val retrievedAt: Instant,
    val providerResultCode: String? = null,
    val providerResultMessage: String? = null,
    val errorCode: String? = null,
)
