package com.haneul.medassist.domain.medication

sealed interface ProductSearchResult {
    data class Success(
        val products: List<VerifiedDrugProduct>,
    ) : ProductSearchResult
}

sealed interface IngredientSearchResult {
    data class Success(
        val ingredients: List<Ingredient>,
    ) : IngredientSearchResult

    data object SchemaUnverified : IngredientSearchResult

    data class ProviderError(
        val safeErrorCode: String,
    ) : IngredientSearchResult
}
