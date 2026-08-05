package com.haneul.medassist.client.drug

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.config.DrugProductApiProperties
import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.domain.medication.ProductSearchResult
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.URI
import java.time.Instant

@Component
class PublicDataDrugProductApiClient(
    @Qualifier("drugProductRestClient") private val restClient: RestClient,
    private val properties: DrugProductApiProperties,
    private val uriFactory: PublicDataUriFactory,
    private val responseParser: RawPublicDataResponseParser,
    private val responseValidator: PublicDataApiResponseValidator,
    private val mapper: DrugProductApiMapper,
    private val callExecutor: PublicDataCallExecutor,
) : DrugProductApiClient {
    override fun searchProducts(productName: String): ProductSearchResult.Success {
        mapper.requireSearchMapping()
        val retrievedAt = Instant.now()
        val root = responseParser.parse(fetch(uriFactory.searchUri(productName)))
        responseValidator.validate(root)
        val records = responseParser.records(root, properties.mapping.searchItemsJsonPointer)
        return ProductSearchResult.Success(records.map { mapper.toProduct(it, retrievedAt) })
    }

    override fun findIngredients(productCode: String): IngredientSearchResult {
        if (!properties.mapping.ingredientsAreConfigured()) return IngredientSearchResult.SchemaUnverified
        return try {
            val retrievedAt = Instant.now()
            val root = responseParser.parse(fetch(uriFactory.ingredientUri(productCode)))
            responseValidator.validate(root)
            val records = responseParser.records(root, properties.mapping.ingredientItemsJsonPointer)
            IngredientSearchResult.Success(records.map { mapper.toIngredient(it, productCode, retrievedAt) })
        } catch (exception: PublicDataApiException) {
            IngredientSearchResult.ProviderError(exception.errorCode.name)
        }
    }

    private fun fetch(uri: URI): String = callExecutor.execute {
        try {
            restClient.get()
                .uri(uri)
                .retrieve()
                .body(String::class.java)
                ?: throw PublicDataApiException(
                    ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
                    "공공 API 응답 본문이 비어 있습니다.",
                )
        } catch (exception: RestClientResponseException) {
            throw mapHttpError(exception.statusCode, exception)
        } catch (exception: ResourceAccessException) {
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_TIMEOUT,
                cause = exception,
                retryable = true,
            )
        }
    }

    private fun mapHttpError(
        status: HttpStatusCode,
        cause: RestClientResponseException,
    ): PublicDataApiException = when {
        status.value() == 401 || status.value() == 403 -> PublicDataApiException(
            ApiErrorCode.PUBLIC_API_AUTH_FAILED,
            cause = cause,
        )

        status.value() == 429 -> PublicDataApiException(
            ApiErrorCode.PUBLIC_API_QUOTA_EXCEEDED,
            cause = cause,
            retryable = true,
        )

        status.is5xxServerError -> PublicDataApiException(
            ApiErrorCode.PUBLIC_API_UNAVAILABLE,
            cause = cause,
            retryable = true,
        )

        else -> PublicDataApiException(
            ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
            cause = cause,
        )
    }
}
