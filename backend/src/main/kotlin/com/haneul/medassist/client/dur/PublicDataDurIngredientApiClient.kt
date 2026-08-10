package com.haneul.medassist.client.dur

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataLogSanitizer
import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.config.DurApiProperties
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
class PublicDataDurIngredientApiClient(
    @Qualifier("durRestClient") private val restClient: RestClient,
    private val properties: DurApiProperties,
    private val uriFactory: DurPublicDataUriFactory,
    private val responseParser: RawPublicDataResponseParser,
    private val responseDecoder: PublicDataResponseDecoder,
    private val responseValidator: PublicDataApiResponseValidator,
    private val responseMapper: DurProviderResponseMapper,
    @Qualifier("durCallExecutor") private val callExecutor: PublicDataCallExecutor,
) : DurIngredientApiClient {
    override fun lookup(request: DurLookupRequest): DurLookupResult {
        val retrievedAt = Instant.now()
        if (request.ingredientCode.isBlank()) {
            return failed(retrievedAt, 1, ApiErrorCode.VALIDATION_FAILED)
        }
        if (request.ingredientKoreanName.isNullOrBlank()) {
            return failed(retrievedAt, 1, ApiErrorCode.PUBLIC_API_MAPPING_UNVERIFIED)
        }

        val completedPages = mutableListOf<Int>()
        val records = mutableListOf<DurProviderRecord>()
        var rawRecordCount = 0
        val first = try {
            fetchPage(request, 1)
        } catch (exception: PublicDataApiException) {
            return failed(retrievedAt, 1, exception.errorCode)
        }

        if (first.body.pageNo != 1) {
            return incomplete(
                retrievedAt = retrievedAt,
                response = first,
                completedPages = completedPages,
                records = records,
                errorCode = ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH,
                failedPages = listOf(1),
            )
        }
        if (first.body.totalCount == 0) {
            if (first.body.items.isNotEmpty()) {
                return incomplete(
                    retrievedAt,
                    first,
                    completedPages,
                    records,
                    ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH,
                    failedPages = listOf(1),
                )
            }
            completedPages += 1
            return successResult(
                status = DurLookupStatus.NO_MATCH,
                records = emptyList(),
                response = first,
                completedPages = completedPages,
                retrievedAt = retrievedAt,
            )
        }

        try {
            records += first.body.items.map { toRecord(it.item, request) }
            rawRecordCount += first.body.items.size
        } catch (exception: PublicDataApiException) {
            return incomplete(
                retrievedAt,
                first,
                completedPages,
                records,
                exception.errorCode,
                failedPages = listOf(1),
            )
        }
        completedPages += 1

        val totalPages = totalPages(first.body.totalCount, first.body.numOfRows)
        if (
            first.body.totalCount > properties.maxRecords.coerceAtLeast(1) ||
            totalPages > properties.maxPages.coerceAtLeast(1)
        ) {
            return incomplete(
                retrievedAt,
                first,
                completedPages,
                records,
                ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
            )
        }

        for (pageNumber in 2..totalPages) {
            val page = try {
                fetchPage(request, pageNumber)
            } catch (exception: PublicDataApiException) {
                return incomplete(
                    retrievedAt = retrievedAt,
                    response = first,
                    completedPages = completedPages,
                    records = records,
                    errorCode = exception.errorCode,
                    failedPages = listOf(pageNumber),
                )
            }
            if (
                page.body.pageNo != pageNumber ||
                page.body.totalCount != first.body.totalCount ||
                page.body.numOfRows != first.body.numOfRows
            ) {
                return incomplete(
                    retrievedAt = retrievedAt,
                    response = first,
                    completedPages = completedPages,
                    records = records,
                    errorCode = ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH,
                    failedPages = listOf(pageNumber),
                )
            }
            val pageRecords = try {
                page.body.items.map { toRecord(it.item, request) }
            } catch (exception: PublicDataApiException) {
                return incomplete(
                    retrievedAt = retrievedAt,
                    response = first,
                    completedPages = completedPages,
                    records = records,
                    errorCode = exception.errorCode,
                    failedPages = listOf(pageNumber),
                )
            }
            completedPages += pageNumber
            records += pageRecords
            rawRecordCount += page.body.items.size
        }

        if (rawRecordCount != first.body.totalCount) {
            return incomplete(
                retrievedAt,
                first,
                completedPages,
                records,
                ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH,
            )
        }
        return successResult(
            status = DurLookupStatus.MATCHED,
            records = records.distinctBy(::dedupKey),
            response = first,
            completedPages = completedPages,
            retrievedAt = retrievedAt,
        )
    }

    private fun fetchPage(request: DurLookupRequest, pageNumber: Int): DurProviderResponse {
        val uri = uriFactory.lookupUri(request, pageNumber)
        val root = responseParser.parse(fetch(uri))
        responseValidator.validate(root)
        return responseMapper.map(root)
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

    private fun toRecord(item: DurProviderItem, request: DurLookupRequest): DurProviderRecord {
        val typeName = item.typeName ?: throw mismatch("DUR 응답 TYPE_NAME이 없습니다.")
        if (typeName != properties.typeName) {
            throw mismatch("DUR 응답 유형이 요청한 병용금기 유형과 일치하지 않습니다.")
        }
        val ingredientCode = item.ingredientCode ?: throw mismatch("DUR 응답 INGR_CODE가 없습니다.")
        if (ingredientCode != request.ingredientCode.trim()) {
            throw mismatch("DUR 응답 기준 성분코드가 요청과 일치하지 않습니다.")
        }
        val ingredientKoreanName = item.ingredientKoreanName
            ?: throw mismatch("DUR 응답 INGR_KOR_NAME이 없습니다.")
        val relatedIngredientCode = item.relatedIngredientCode
            ?: throw mismatch("DUR 응답 MIXTURE_INGR_CODE가 없습니다.")
        val relatedIngredientKoreanName = item.relatedIngredientKoreanName
            ?: throw mismatch("DUR 응답 MIXTURE_INGR_KOR_NAME이 없습니다.")
        return DurProviderRecord(
            providerRecordId = null,
            typeName = typeName,
            ingredientCode = ingredientCode,
            ingredientEnglishName = item.ingredientEnglishName,
            ingredientKoreanName = ingredientKoreanName,
            relatedIngredientCode = relatedIngredientCode,
            relatedIngredientEnglishName = item.relatedIngredientEnglishName,
            relatedIngredientKoreanName = relatedIngredientKoreanName,
            prohibitionContent = item.prohibitionContent,
            notificationDate = item.notificationDate,
            remark = item.remark,
            providerStatus = when (item.deletionStatus?.uppercase()) {
                "N" -> DurProviderStatus.ACTIVE
                "Y" -> DurProviderStatus.DELETED
                else -> DurProviderStatus.UNKNOWN
            },
            rawFields = item.rawFields,
        )
    }

    private fun dedupKey(record: DurProviderRecord): List<String?> = listOf(
        record.ingredientCode,
        record.relatedIngredientCode,
        record.notificationDate,
        record.typeName,
        record.prohibitionContent,
    )

    private fun successResult(
        status: DurLookupStatus,
        records: List<DurProviderRecord>,
        response: DurProviderResponse,
        completedPages: List<Int>,
        retrievedAt: Instant,
    ): DurLookupResult = DurLookupResult(
        status = status,
        records = records,
        totalCount = response.body.totalCount,
        completedPages = completedPages.toList(),
        failedPages = emptyList(),
        complete = true,
        retrievedAt = retrievedAt,
        providerResultCode = response.header.resultCode,
        providerResultMessage = response.header.resultMsg,
    )

    private fun failed(
        retrievedAt: Instant,
        failedPage: Int,
        errorCode: ApiErrorCode,
    ): DurLookupResult = DurLookupResult(
        status = DurLookupStatus.FAILED,
        records = emptyList(),
        totalCount = null,
        completedPages = emptyList(),
        failedPages = listOf(failedPage),
        complete = false,
        retrievedAt = retrievedAt,
        errorCode = errorCode.name,
    )

    private fun incomplete(
        retrievedAt: Instant,
        response: DurProviderResponse,
        completedPages: List<Int>,
        records: List<DurProviderRecord>,
        errorCode: ApiErrorCode,
        failedPages: List<Int> = emptyList(),
    ): DurLookupResult = DurLookupResult(
        status = if (records.isEmpty()) DurLookupStatus.FAILED else DurLookupStatus.PARTIAL,
        records = records.distinctBy(::dedupKey),
        totalCount = response.body.totalCount,
        completedPages = completedPages.toList(),
        failedPages = failedPages,
        complete = false,
        retrievedAt = retrievedAt,
        providerResultCode = response.header.resultCode,
        providerResultMessage = response.header.resultMsg,
        errorCode = errorCode.name,
    )

    private fun totalPages(totalCount: Int, pageSize: Int): Int =
        if (totalCount == 0) 0 else ((totalCount.toLong() + pageSize - 1) / pageSize).toInt()

    private fun mismatch(message: String): PublicDataApiException =
        PublicDataApiException(ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH, message)

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

        status.value() in setOf(502, 503, 504) || status.is5xxServerError -> PublicDataApiException(
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
