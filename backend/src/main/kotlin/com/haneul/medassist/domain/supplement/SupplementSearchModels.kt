package com.haneul.medassist.domain.supplement

import com.haneul.medassist.domain.medication.SourceMetadata

data class SupplementSearchCandidate(
    val sttemntNo: String,
    val productName: String,
    val manufacturer: String?,
    val normalizedName: String,
    val aliases: Set<String>,
    val source: SourceMetadata,
)

enum class SupplementSearchMatchType {
    EXACT,
    PREFIX,
    CONTAINS,
}

data class SupplementSearchMatch(
    val candidate: SupplementSearchCandidate,
    val score: Int,
    val matchType: SupplementSearchMatchType,
)
