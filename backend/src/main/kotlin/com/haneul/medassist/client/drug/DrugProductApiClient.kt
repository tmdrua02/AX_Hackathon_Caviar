package com.haneul.medassist.client.drug

import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.domain.medication.ProductSearchResult

interface DrugProductApiClient {
    fun searchProducts(productName: String): ProductSearchResult.Success

    fun findIngredients(productCode: String, productName: String): IngredientSearchResult
}
