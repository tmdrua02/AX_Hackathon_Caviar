package com.haneul.medassist.service

import com.haneul.medassist.client.dur.DurIngredientApiClient
import com.haneul.medassist.client.dur.DurLookupRequest
import com.haneul.medassist.client.dur.DurLookupResult
import com.haneul.medassist.client.dur.DurLookupStatus
import com.haneul.medassist.exception.ApiErrorCode
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DurLookupService(
    private val durClient: DurIngredientApiClient,
) {
    fun lookup(request: DurLookupRequest): DurLookupResult {
        if (request.ingredientCode.isBlank() || request.ingredientKoreanName.isNullOrBlank()) {
            return DurLookupResult(
                status = DurLookupStatus.FAILED,
                records = emptyList(),
                totalCount = null,
                completedPages = emptyList(),
                failedPages = emptyList(),
                complete = false,
                retrievedAt = Instant.now(),
                errorCode = ApiErrorCode.PUBLIC_API_MAPPING_UNVERIFIED.name,
            )
        }
        return durClient.lookup(request)
    }
}
