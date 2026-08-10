package com.haneul.medassist.domain.evidence

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

enum class EvidenceVerificationStatus {
    DRAFT,
    PENDING_REVIEW,
    VERIFIED,
    REJECTED,
    RETIRED,
}

enum class EvidenceAuthority {
    MFDS,
    FOOD_SAFETY_KOREA,
    DRUG_LABEL,
    PEER_REVIEWED_RESEARCH,
    OTHER_OFFICIAL,
}

data class VerifiedSourceReference(
    val id: String,
    val authority: EvidenceAuthority,
    val title: String,
    val sourceUrl: String? = null,
    val documentIdentifier: String? = null,
    val originalText: String,
    val publishedAt: Instant? = null,
    val retrievedAt: Instant,
    val verificationStatus: EvidenceVerificationStatus,
    val reviewedBy: String? = null,
    val reviewedAt: Instant? = null,
    val notes: String? = null,
    val sourceVersion: String? = null,
) {
    init {
        require(id.isNotBlank()) { "source id must not be blank" }
        require(title.isNotBlank()) { "source title must not be blank" }
        require(!sourceUrl.isNullOrBlank() || !documentIdentifier.isNullOrBlank()) {
            "sourceUrl or documentIdentifier is required"
        }
        require(originalText.isNotBlank()) { "source originalText is required" }
        if (verificationStatus == EvidenceVerificationStatus.VERIFIED) {
            require(!reviewedBy.isNullOrBlank()) { "VERIFIED source requires reviewedBy" }
            require(reviewedAt != null) { "VERIFIED source requires reviewedAt" }
        }
    }

    @JsonIgnore
    fun isProductionEligible(): Boolean = verificationStatus == EvidenceVerificationStatus.VERIFIED
}

data class SupplementRuleCatalogAuditMetadata(
    val available: Boolean,
    val verified: Boolean,
    val catalogVersion: String?,
    val schemaVersion: String?,
    val catalogChecksum: String?,
    val loadedAt: Instant,
    val sourceCount: Int,
    val canonicalIngredientCount: Int,
    val productMappingCount: Int,
    val interactionRuleCount: Int,
    val validationErrorCodes: List<String>,
)
