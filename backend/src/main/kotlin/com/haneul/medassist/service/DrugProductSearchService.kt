package com.haneul.medassist.service

import com.haneul.medassist.client.drug.DrugProductApiClient
import com.haneul.medassist.config.MatchingProperties
import com.haneul.medassist.domain.medication.DrugProductCandidate
import com.haneul.medassist.domain.medication.IngredientLookupStatus
import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.domain.medication.ProductSearchResult
import com.haneul.medassist.dto.drug.DrugProductCandidateResponse
import com.haneul.medassist.dto.drug.DrugProductSearchRequest
import com.haneul.medassist.dto.drug.DrugProductSearchResponse
import com.haneul.medassist.dto.drug.IngredientResponse
import com.haneul.medassist.dto.drug.ProductSearchCoverage
import com.haneul.medassist.dto.drug.SourceResponse
import org.springframework.stereotype.Service

@Service
class DrugProductSearchService(
    private val apiClient: DrugProductApiClient,
    private val normalizer: DrugNameNormalizer,
    private val matcher: DrugProductMatcher,
    private val cache: DrugProductCache,
    private val matchingProperties: MatchingProperties,
) {
    fun search(request: DrugProductSearchRequest): DrugProductSearchResponse {
        val normalized = normalizer.normalize(request.query)
        val providerQuery = normalized.baseName.ifBlank { normalized.normalizedQuery }
        val cacheKey = normalizer.compact(providerQuery)
        val officialResult = cache.search(cacheKey) ?: apiClient.searchProducts(providerQuery).also {
            cache.putSearch(cacheKey, it)
        }

        if (officialResult.products.isEmpty()) {
            return DrugProductSearchResponse(
                query = request.query,
                normalizedQuery = normalized.normalizedQuery,
                candidates = emptyList(),
                requiresUserConfirmation = false,
                coverage = ProductSearchCoverage(0, 0, 0, complete = true),
            )
        }

        val rankedProducts = officialResult.products
            .distinctBy { it.productCode }
            .map { it to matcher.assess(normalized, it) }
            .sortedByDescending { it.second.score }
            .take(matchingProperties.maximumCandidates)

        val candidates = rankedProducts.map { (product, assessment) ->
            val ingredientResult = cache.ingredients(product.productCode)
                ?: apiClient.findIngredients(product.productCode).also { result ->
                    if (result is IngredientSearchResult.Success) cache.putIngredients(product.productCode, result)
                }
            val (ingredients, status) = ingredientResult.toCandidateIngredients()
            DrugProductCandidate(
                product = product,
                ingredients = ingredients,
                matchConfidence = assessment.score,
                matchReasons = assessment.reasons,
                ingredientLookupStatus = status,
                conflicts = assessment.conflicts,
            )
        }

        val requiresConfirmation = matcher.requiresUserConfirmation(
            candidates.map { candidate ->
                matcher.assess(normalized, candidate.product) to candidate.ingredientLookupStatus
            },
        )
        val resolvedLookups = candidates.count { it.ingredientLookupStatus == IngredientLookupStatus.RESOLVED }
        val failedLookups = candidates.size - resolvedLookups

        return DrugProductSearchResponse(
            query = request.query,
            normalizedQuery = normalized.normalizedQuery,
            candidates = candidates.map(::toResponse),
            requiresUserConfirmation = requiresConfirmation,
            coverage = ProductSearchCoverage(
                productCandidates = candidates.size,
                ingredientLookupsCompleted = resolvedLookups,
                ingredientLookupsFailed = failedLookups,
                complete = failedLookups == 0,
            ),
        )
    }

    private fun IngredientSearchResult.toCandidateIngredients() = when (this) {
        is IngredientSearchResult.Success ->
            ingredients to if (ingredients.isEmpty()) IngredientLookupStatus.NOT_FOUND else IngredientLookupStatus.RESOLVED

        IngredientSearchResult.SchemaUnverified -> emptyList<com.haneul.medassist.domain.medication.Ingredient>() to
            IngredientLookupStatus.SCHEMA_UNVERIFIED

        is IngredientSearchResult.ProviderError -> emptyList<com.haneul.medassist.domain.medication.Ingredient>() to
            IngredientLookupStatus.PROVIDER_ERROR
    }

    private fun toResponse(candidate: DrugProductCandidate): DrugProductCandidateResponse =
        DrugProductCandidateResponse(
            productCode = candidate.product.productCode,
            productName = candidate.product.productName,
            manufacturer = candidate.product.manufacturer,
            ingredients = candidate.ingredients.map {
                IngredientResponse(
                    displayName = it.displayName,
                    normalizedName = it.normalizedName,
                    providerCode = it.providerCode,
                    amount = it.amount,
                    unit = it.unit,
                )
            },
            matchConfidence = candidate.matchConfidence,
            matchReasons = candidate.matchReasons,
            ingredientLookupStatus = candidate.ingredientLookupStatus.name,
            source = SourceResponse(
                name = candidate.product.source.name,
                recordId = candidate.product.source.recordId,
                retrievedAt = candidate.product.source.retrievedAt.toString(),
                providerReference = candidate.product.source.providerReference,
            ),
        )
}
