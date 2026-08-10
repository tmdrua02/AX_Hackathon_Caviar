package com.haneul.medassist.controller

import com.haneul.medassist.dto.supplement.SupplementProductSearchRequest
import com.haneul.medassist.dto.supplement.SupplementProductSearchResponse
import com.haneul.medassist.dto.supplement.SupplementSearchCandidateResponse
import com.haneul.medassist.dto.supplement.SupplementSearchSourceResponse
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodLookupStatus
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import com.haneul.medassist.service.HealthFunctionalFoodService
import com.haneul.medassist.service.SupplementNameNormalizer
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/supplement-products")
class SupplementProductController(
    private val service: HealthFunctionalFoodService,
    private val normalizer: SupplementNameNormalizer,
) {
    @PostMapping("/search")
    fun search(
        @Valid @RequestBody request: SupplementProductSearchRequest,
    ): SupplementProductSearchResponse {
        val result = service.search(request.query, request.manufacturer)
        if (result.status == HealthFunctionalFoodLookupStatus.FAILED) {
            val errorCode = result.errorCode?.let { runCatching { ApiErrorCode.valueOf(it) }.getOrNull() }
                ?: ApiErrorCode.PUBLIC_API_UNAVAILABLE
            throw PublicDataApiException(errorCode)
        }
        return SupplementProductSearchResponse(
            query = request.query,
            normalizedQuery = normalizer.normalize(request.query),
            status = result.status.name,
            sourceType = result.sourceType.name,
            complete = result.complete,
            candidates = result.candidates.map { match ->
                val candidate = match.candidate
                SupplementSearchCandidateResponse(
                    sttemntNo = candidate.sttemntNo,
                    productName = candidate.productName,
                    manufacturer = candidate.manufacturer,
                    matchScore = match.score,
                    matchType = match.matchType.name,
                    source = SupplementSearchSourceResponse(
                        name = candidate.source.name,
                        recordId = candidate.source.recordId,
                        retrievedAt = candidate.source.retrievedAt.toString(),
                        providerReference = candidate.source.providerReference,
                    ),
                )
            },
        )
    }
}
