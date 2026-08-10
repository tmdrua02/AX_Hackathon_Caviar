package com.haneul.medassist.client.dur

import java.time.Instant

enum class DurLookupDirection {
    FORWARD,
    REVERSE,
}

data class DurLookupRequest(
    val ingredientCode: String,
    val ingredientKoreanName: String?,
    val lookupDirection: DurLookupDirection,
)

enum class DurLookupStatus {
    MATCHED,
    NO_MATCH,
    FAILED,
    PARTIAL,
}

enum class DurProviderStatus {
    ACTIVE,
    DELETED,
    UNKNOWN,
}

data class DurProviderRecord(
    val providerRecordId: String?,
    val typeName: String,
    val ingredientCode: String,
    val ingredientEnglishName: String?,
    val ingredientKoreanName: String,
    val relatedIngredientCode: String,
    val relatedIngredientEnglishName: String?,
    val relatedIngredientKoreanName: String,
    val prohibitionContent: String?,
    val notificationDate: String?,
    val remark: String?,
    val providerStatus: DurProviderStatus,
    val rawFields: Map<String, String?>,
)

data class DurLookupResult(
    val status: DurLookupStatus,
    val records: List<DurProviderRecord>,
    val totalCount: Int?,
    val completedPages: List<Int>,
    val failedPages: List<Int>,
    val complete: Boolean,
    val retrievedAt: Instant,
    val providerResultCode: String? = null,
    val providerResultMessage: String? = null,
    val errorCode: String? = null,
)
