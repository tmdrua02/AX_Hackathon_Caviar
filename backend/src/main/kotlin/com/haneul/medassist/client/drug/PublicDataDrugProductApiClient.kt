package com.haneul.medassist.client.drug

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataLogSanitizer
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.client.common.PublicDataResponseDecoder
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
import tools.jackson.databind.JsonNode
import java.net.URI
import java.time.Instant

@Component
class PublicDataDrugProductApiClient(
    @Qualifier("drugProductRestClient") private val restClient: RestClient,
    private val properties: DrugProductApiProperties,
    private val uriFactory: PublicDataUriFactory,
    private val responseParser: RawPublicDataResponseParser,
    private val responseDecoder: PublicDataResponseDecoder,
    private val responseValidator: PublicDataApiResponseValidator,
    private val mapper: DrugProductApiMapper,
    @Qualifier("drugProductCallExecutor") private val callExecutor: PublicDataCallExecutor,
) : DrugProductApiClient {
    override fun searchProducts(productName: String): ProductSearchResult.Success {
        mapper.requireSearchMapping()
        val retrievedAt = Instant.now()
        val root = responseParser.parse(fetch(uriFactory.searchUri(productName)))
        responseValidator.validate(root)
        val records = responseParser.records(root, properties.mapping.searchItemsJsonPointer)
        return ProductSearchResult.Success(records.map { mapper.toProduct(it, retrievedAt) })
    }

    override fun findIngredients(productCode: String, productName: String): IngredientSearchResult {
        if (!properties.mapping.ingredientsAreConfigured()) return IngredientSearchResult.SchemaUnverified
        return try {
            val retrievedAt = Instant.now()
            val records = fetchAllIngredientRecords(productName)
            val matchingRecords = records.filter { mapper.ingredientProductCode(it) == productCode }
            if (records.isNotEmpty() && matchingRecords.isEmpty()) {
                throw PublicDataApiException(
                    ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH,
                    "공공 API 주성분 응답에서 선택 제품의 품목기준코드를 확인할 수 없습니다.",
                )
            }
            IngredientSearchResult.Success(
                matchingRecords
                    .sortedWith(ingredientRecordComparator())
                    .distinctByOfficialIdentity()
                    .map { mapper.toIngredient(it, productCode, retrievedAt) },
            )
        } catch (exception: PublicDataApiException) {
            IngredientSearchResult.ProviderError(exception.errorCode.name)
        }
    }

    private fun fetchAllIngredientRecords(productName: String): List<JsonNode> {
        val allRecords = mutableListOf<JsonNode>()
        var expectedTotalCount: Int? = null
        var expectedPageSize: Int? = null
        var pageNumber = 1

        while (true) {
            val root = responseParser.parse(fetch(uriFactory.ingredientUri(productName, pageNumber)))
            responseValidator.validate(root)
            val metadata = responseParser.pageMetadata(
                root = root,
                totalCountJsonPointer = properties.mapping.totalCountJsonPointer,
                pageNumberJsonPointer = properties.mapping.pageNumberJsonPointer,
                pageSizeJsonPointer = properties.mapping.pageSizeJsonPointer,
            )
            if (metadata.pageNumber != pageNumber) throw paginationMismatch("pageNo")
            if (expectedTotalCount != null && expectedTotalCount != metadata.totalCount) {
                throw paginationMismatch("totalCount")
            }
            if (expectedPageSize != null && expectedPageSize != metadata.pageSize) {
                throw paginationMismatch("numOfRows")
            }
            expectedTotalCount = metadata.totalCount
            expectedPageSize = metadata.pageSize
            allRecords += responseParser.records(root, properties.mapping.ingredientItemsJsonPointer)

            val totalPages = totalPages(metadata.totalCount, metadata.pageSize)
            if (totalPages > properties.maximumPages.coerceAtLeast(1)) {
                throw PublicDataApiException(
                    ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
                    "공공 API 주성분 응답 페이지 수가 안전 한도를 초과했습니다.",
                )
            }
            if (pageNumber >= totalPages.coerceAtLeast(1)) break
            pageNumber++
        }

        if (allRecords.size != expectedTotalCount) {
            throw paginationMismatch("items")
        }
        return allRecords
    }

    private fun totalPages(totalCount: Int, pageSize: Int): Int =
        if (totalCount == 0) 0 else ((totalCount.toLong() + pageSize - 1) / pageSize).toInt()

    private fun paginationMismatch(field: String): PublicDataApiException = PublicDataApiException(
        ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH,
        "공공 API 주성분 페이지 응답의 $field 값이 요청 흐름과 일치하지 않습니다.",
    )

    private fun ingredientRecordComparator(): Comparator<JsonNode> = compareBy<JsonNode>(
        { mapper.ingredientOrder(it).first ?: Int.MAX_VALUE },
        { mapper.ingredientOrder(it).second ?: Int.MAX_VALUE },
    )

    private fun List<JsonNode>.distinctByOfficialIdentity(): List<JsonNode> {
        val seen = mutableSetOf<String>()
        return filter { record ->
            val identity = mapper.ingredientIdentity(record)
            identity == null || seen.add(identity)
        }
    }

    private fun fetch(uri: URI): String = callExecutor.execute {
        try {
            val response = restClient.get()
                .uri(uri)
                .retrieve()
                .toEntity(ByteArray::class.java)
            responseDecoder.decode(response.body, response.headers.contentType)
        } catch (exception: RestClientResponseException) {
            throw mapHttpError(exception.statusCode, exception)
        } catch (exception: ResourceAccessException) {
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_TIMEOUT,
                cause = PublicDataLogSanitizer.sanitizedCause(exception),
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
            cause = PublicDataLogSanitizer.sanitizedCause(cause),
        )

        status.value() == 429 -> PublicDataApiException(
            ApiErrorCode.PUBLIC_API_QUOTA_EXCEEDED,
            cause = PublicDataLogSanitizer.sanitizedCause(cause),
            retryable = true,
        )

        status.is5xxServerError -> PublicDataApiException(
            ApiErrorCode.PUBLIC_API_UNAVAILABLE,
            cause = PublicDataLogSanitizer.sanitizedCause(cause),
            retryable = true,
        )

        else -> PublicDataApiException(
            ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
            cause = PublicDataLogSanitizer.sanitizedCause(cause),
        )
    }
}
