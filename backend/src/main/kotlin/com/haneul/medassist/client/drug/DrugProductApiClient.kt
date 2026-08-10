package com.haneul.medassist.client.drug

import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.domain.medication.ProductSearchResult
import com.haneul.medassist.domain.medication.VerifiedDrugProduct

interface DrugProductApiClient {
    fun searchProducts(productName: String): ProductSearchResult.Success

    fun findProduct(productCode: String): VerifiedDrugProduct? = null

    fun findIngredients(productCode: String, productName: String): IngredientSearchResult
}
