package com.haneul.medassist.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.haneul.medassist.config.PublicDataCacheProperties
import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.domain.medication.ProductSearchResult
import org.springframework.stereotype.Component

@Component
class DrugProductCache(
    properties: PublicDataCacheProperties,
) {
    private val positiveSearch: Cache<String, ProductSearchResult.Success> = Caffeine.newBuilder()
        .maximumSize(properties.maximumSize)
        .expireAfterWrite(properties.positiveSearchTtl)
        .build()
    private val negativeSearch: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(properties.maximumSize)
        .expireAfterWrite(properties.negativeSearchTtl)
        .build()
    private val ingredients: Cache<String, IngredientSearchResult.Success> = Caffeine.newBuilder()
        .maximumSize(properties.maximumSize)
        .expireAfterWrite(properties.ingredientTtl)
        .build()

    fun search(key: String): ProductSearchResult.Success? =
        positiveSearch.getIfPresent(key)
            ?: negativeSearch.getIfPresent(key)?.let { ProductSearchResult.Success(emptyList()) }

    fun putSearch(key: String, result: ProductSearchResult.Success) {
        if (result.products.isEmpty()) negativeSearch.put(key, true) else positiveSearch.put(key, result)
    }

    fun ingredients(productCode: String): IngredientSearchResult.Success? =
        ingredients.getIfPresent(productCode)

    fun putIngredients(productCode: String, result: IngredientSearchResult.Success) {
        ingredients.put(productCode, result)
    }
}
