package com.haneul.medassist.domain.supplement

import com.haneul.medassist.domain.medication.SourceMetadata
import java.time.Instant

enum class HealthFunctionalFoodLookupStatus {
    RESOLVED,
    NOT_FOUND,
    FAILED,
    PARTIAL,
}

enum class SupplementSearchSourceType {
    PROVIDER,
    INDEX_FALLBACK,
}

data class SupplementProductCoverage(
    val statementResolved: Boolean,
    val detailResolved: Boolean,
    val complete: Boolean,
)

data class SupplementProductSnapshot(
    val statementNo: String,
    val productName: String,
    val manufacturer: String?,
    val registerDate: String?,
    val distributionPeriod: String?,
    val appearance: String?,
    val usage: String?,
    val storage: String?,
    val intakeHint: String?,
    val mainFunction: String?,
    val baseStandard: String?,
    val coverage: SupplementProductCoverage,
    val retrievedAt: Instant,
    val source: SourceMetadata,
    val rawProviderRecord: Map<String, String?>,
)

data class HealthFunctionalFoodSearchResult(
    val status: HealthFunctionalFoodLookupStatus,
    val candidates: List<SupplementSearchMatch>,
    val totalCount: Int?,
    val completedPages: List<Int>,
    val failedPages: List<Int>,
    val complete: Boolean,
    val sourceType: SupplementSearchSourceType,
    val retrievedAt: Instant,
    val providerResultCode: String? = null,
    val providerResultMessage: String? = null,
    val errorCode: String? = null,
)

data class SupplementProductSnapshotResult(
    val status: HealthFunctionalFoodLookupStatus,
    val snapshot: SupplementProductSnapshot?,
    val totalCount: Int?,
    val completedPages: List<Int>,
    val failedPages: List<Int>,
    val complete: Boolean,
    val retrievedAt: Instant,
    val providerResultCode: String? = null,
    val providerResultMessage: String? = null,
    val errorCode: String? = null,
)
