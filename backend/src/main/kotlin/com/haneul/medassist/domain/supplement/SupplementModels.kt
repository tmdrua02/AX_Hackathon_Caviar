package com.haneul.medassist.domain.supplement

import com.haneul.medassist.domain.medication.SourceMetadata

enum class SupplementProductLookupStatus {
    RESOLVED,
    MULTIPLE_CANDIDATES,
    NOT_FOUND,
    FAILED,
}

data class SupplementProduct(
    val providerProductCode: String,
    val productName: String,
    val manufacturer: String?,
    val appearance: String?,
    val usage: String?,
    val intakeMethod: String?,
    val expirationInformation: String?,
    val source: SourceMetadata,
    val resolutionStatus: SupplementProductLookupStatus,
)

data class SupplementProductCandidate(
    val providerProductCode: String,
    val productName: String,
    val manufacturer: String?,
    val matchConfidence: Int,
    val matchReasons: List<String>,
    val source: SourceMetadata,
)

enum class SupplementRawMaterialStatus {
    NOT_IMPLEMENTED,
}

sealed interface SupplementRawMaterials {
    data object NotRequested : SupplementRawMaterials
}

enum class SupplementRuleEvidenceStatus {
    NOT_EVALUATED,
}
