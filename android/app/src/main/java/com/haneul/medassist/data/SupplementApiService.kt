package com.haneul.medassist.data

import retrofit2.http.Body
import retrofit2.http.POST

/** API served by the standalone medication/supplement backend (port 8081 in local development). */
interface SupplementApiService {
    @POST("api/v1/drug-interaction-checks")
    suspend fun checkDrugInteractions(
        @Body request: DrugInteractionBatchRequest,
    ): DrugInteractionBatchResponse

    @POST("api/v1/drug-products/search")
    suspend fun searchDrugProducts(
        @Body request: DrugProductSearchRequest,
    ): DrugProductSearchResponse

    @POST("api/v1/supplement-products/search")
    suspend fun searchSupplementProducts(
        @Body request: SupplementProductSearchRequest,
    ): SupplementProductSearchResponse

    @POST("api/v1/supplement-interaction-checks")
    suspend fun checkSupplementInteraction(
        @Body request: SupplementInteractionCheckRequest,
    ): SupplementInteractionCheckResponse
}
