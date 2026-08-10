package com.haneul.medassist.data

import kotlinx.serialization.Serializable

@Serializable
data class DrugProductSearchRequest(val query: String)

@Serializable
data class DrugProductSearchResponse(
    val query: String,
    val normalizedQuery: String,
    val candidates: List<DrugProductSearchCandidate>,
    val requiresUserConfirmation: Boolean,
    val coverage: ProductSearchCoverage,
    val disclaimer: String,
)

@Serializable
data class DrugProductSearchCandidate(
    val productCode: String,
    val productName: String,
    val manufacturer: String? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val matchConfidence: Int,
    val matchReasons: List<String> = emptyList(),
    val ingredientLookupStatus: String,
    val source: ProductSource,
)

@Serializable
data class ProductSource(
    val name: String,
    val recordId: String,
    val retrievedAt: String,
    val providerReference: String,
)

@Serializable
data class ProductSearchCoverage(
    val productCandidates: Int,
    val ingredientLookupsCompleted: Int,
    val ingredientLookupsFailed: Int,
    val complete: Boolean,
)
