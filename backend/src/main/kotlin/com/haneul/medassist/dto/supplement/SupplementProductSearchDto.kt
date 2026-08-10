package com.haneul.medassist.dto.supplement

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SupplementProductSearchRequest(
    @field:NotBlank(message = "건강기능식품 제품명을 입력해 주세요.")
    @field:Size(max = 100, message = "제품명은 100자 이하로 입력해 주세요.")
    val query: String,
    @field:Size(max = 100, message = "업체명은 100자 이하로 입력해 주세요.")
    val manufacturer: String? = null,
)

data class SupplementProductSearchResponse(
    val query: String,
    val normalizedQuery: String,
    val status: String,
    val sourceType: String,
    val complete: Boolean,
    val candidates: List<SupplementSearchCandidateResponse>,
)

data class SupplementSearchCandidateResponse(
    val sttemntNo: String,
    val productName: String,
    val manufacturer: String?,
    val matchScore: Int,
    val matchType: String,
    val source: SupplementSearchSourceResponse,
)

data class SupplementSearchSourceResponse(
    val name: String,
    val recordId: String,
    val retrievedAt: String,
    val providerReference: String,
)
