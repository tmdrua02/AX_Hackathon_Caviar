package com.haneul.medassist.client.drug.overview

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataLogSanitizer
import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.config.DrugOverviewApiProperties
import com.haneul.medassist.domain.medication.DrugOverview
import com.haneul.medassist.domain.medication.DrugOverviewCoverage
import com.haneul.medassist.domain.medication.DrugOverviewLookupResult
import com.haneul.medassist.domain.medication.DrugOverviewLookupStatus
import com.haneul.medassist.domain.medication.OfficialMedicalText
import com.haneul.medassist.domain.medication.SourceMetadata
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.HtmlUtils
import java.net.URI
import java.time.Instant

@Component
class PublicDataDrugOverviewApiClient(
    @Qualifier("drugOverviewRestClient") private val restClient: RestClient,
    private val properties: DrugOverviewApiProperties,
    private val uriFactory: DrugOverviewUriFactory,
    private val responseParser: RawPublicDataResponseParser,
    private val responseDecoder: PublicDataResponseDecoder,
    private val responseValidator: PublicDataApiResponseValidator,
    private val responseMapper: DrugOverviewResponseMapper,
    @Qualifier("drugOverviewCallExecutor") private val callExecutor: PublicDataCallExecutor,
) : DrugOverviewApiClient {
    override fun findOverview(
        productCode: String,
        productName: String,
        manufacturer: String?,
    ): DrugOverviewLookupResult {
        val retrievedAt = Instant.now()
        if (productCode.isBlank() || productName.isBlank()) {
            return failure(retrievedAt, ApiErrorCode.VALIDATION_FAILED)
        }

        val exactQuery = fetchAll(DrugOverviewProviderQuery(itemSeq = productCode.trim()))
        if (!exactQuery.complete) return incompleteResult(retrievedAt, exactQuery)
        val exactSelection = selectExact(exactQuery.items, productCode, productName, manufacturer)
        exactSelection?.let { selected ->
            return resolved(selected, exactQuery, retrievedAt)
        }
        if (codeMatchCount(exactQuery.items, productCode) > 1) {
            return failure(retrievedAt, ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH)
        }

        val nameQuery = fetchAll(
            DrugOverviewProviderQuery(
                itemName = productName.trim(),
                manufacturer = manufacturer?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        if (!nameQuery.complete) return incompleteResult(retrievedAt, nameQuery)
        val selected = selectExact(nameQuery.items, productCode, productName, manufacturer)
        if (selected == null && codeMatchCount(nameQuery.items, productCode) > 1) {
            return failure(retrievedAt, ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH)
        }
        selected ?: return notFound(retrievedAt, nameQuery)
        return resolved(selected, nameQuery, retrievedAt)
    }

    private fun selectExact(
        items: List<DrugOverviewProviderItem>,
        productCode: String,
        productName: String,
        manufacturer: String?,
    ): DrugOverviewProviderItem? {
        val codeMatches = items.filter { it.productCode == productCode.trim() }.distinct()
        if (codeMatches.size <= 1) return codeMatches.singleOrNull()

        val nameMatches = codeMatches.filter { normalized(it.productName) == normalized(productName) }
        if (nameMatches.size == 1) return nameMatches.single()
        if (!manufacturer.isNullOrBlank()) {
            val manufacturerMatches = nameMatches.filter {
                normalized(it.manufacturer) == normalized(manufacturer)
            }
            if (manufacturerMatches.size == 1) return manufacturerMatches.single()
        }
        return null
    }

    private fun codeMatchCount(items: List<DrugOverviewProviderItem>, productCode: String): Int =
        items.filter { it.productCode == productCode.trim() }.distinct().size

    private fun fetchAll(query: DrugOverviewProviderQuery): ProviderQueryOutcome {
        val completedPages = mutableListOf<Int>()
        val items = mutableListOf<DrugOverviewProviderItem>()
        val first = try {
            fetchPage(query, 1)
        } catch (exception: PublicDataApiException) {
            return ProviderQueryOutcome.failure(1, exception.errorCode)
        }
        if (first.body.pageNo != 1) {
            return ProviderQueryOutcome.from(first, items, completedPages, listOf(1), ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH)
        }
        if (first.body.totalCount == 0) {
            if (first.body.items.isNotEmpty()) {
                return ProviderQueryOutcome.from(first, items, completedPages, listOf(1), ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH)
            }
            return ProviderQueryOutcome.complete(first, emptyList(), listOf(1))
        }

        items += first.body.items
        completedPages += 1
        val totalPages = totalPages(first.body.totalCount, first.body.numOfRows)
        if (
            first.body.totalCount > properties.maxRecords.coerceAtLeast(1) ||
            totalPages > properties.maxPages.coerceAtLeast(1)
        ) {
            return ProviderQueryOutcome.from(first, items, completedPages, emptyList(), ApiErrorCode.PUBLIC_API_INVALID_RESPONSE)
        }

        for (pageNumber in 2..totalPages) {
            val page = try {
                fetchPage(query, pageNumber)
            } catch (exception: PublicDataApiException) {
                return ProviderQueryOutcome.from(first, items, completedPages, listOf(pageNumber), exception.errorCode)
            }
            if (
                page.body.pageNo != pageNumber ||
                page.body.totalCount != first.body.totalCount ||
                page.body.numOfRows != first.body.numOfRows
            ) {
                return ProviderQueryOutcome.from(
                    first,
                    items,
                    completedPages,
                    listOf(pageNumber),
                    ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH,
                )
            }
            items += page.body.items
            completedPages += pageNumber
        }
        if (items.size != first.body.totalCount) {
            return ProviderQueryOutcome.from(
                first,
                items,
                completedPages,
                emptyList(),
                ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH,
            )
        }
        return ProviderQueryOutcome.complete(first, items, completedPages)
    }

    private fun fetchPage(query: DrugOverviewProviderQuery, pageNumber: Int): DrugOverviewProviderResponse {
        val root = responseParser.parse(fetch(uriFactory.lookupUri(query, pageNumber)))
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

    private fun resolved(
        item: DrugOverviewProviderItem,
        outcome: ProviderQueryOutcome,
        retrievedAt: Instant,
    ): DrugOverviewLookupResult {
        val productCode = item.productCode ?: return failure(retrievedAt, ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH)
        val productName = item.productName ?: return failure(retrievedAt, ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH)
        val coverage = DrugOverviewCoverage(productResolved = true, overviewResolved = true, complete = true)
        val overview = DrugOverview(
            productCode = productCode,
            productName = productName,
            manufacturer = item.manufacturer,
            efficacy = medicalText(item.efficacy),
            usageMethod = medicalText(item.usageMethod),
            warning = medicalText(item.warning),
            precautions = medicalText(item.precautions),
            interactions = medicalText(item.interactions),
            sideEffects = medicalText(item.sideEffects),
            storageMethod = medicalText(item.storageMethod),
            imageUrl = item.imageUrl,
            openDate = item.openDate,
            updateDate = item.updateDate,
            source = SourceMetadata(
                name = "식품의약품안전처 의약품개요정보(e약은요)",
                recordId = productCode,
                retrievedAt = retrievedAt,
                providerReference = PROVIDER_REFERENCE,
            ),
            coverage = coverage,
        )
        return DrugOverviewLookupResult(
            status = DrugOverviewLookupStatus.RESOLVED,
            overview = overview,
            coverage = coverage,
            totalCount = outcome.totalCount,
            completedPages = outcome.completedPages,
            failedPages = emptyList(),
            retrievedAt = retrievedAt,
            providerResultCode = outcome.resultCode,
            providerResultMessage = outcome.resultMessage,
        )
    }

    private fun notFound(retrievedAt: Instant, outcome: ProviderQueryOutcome): DrugOverviewLookupResult =
        DrugOverviewLookupResult(
            status = DrugOverviewLookupStatus.NOT_FOUND,
            overview = null,
            coverage = DrugOverviewCoverage(productResolved = true, overviewResolved = false, complete = true),
            totalCount = outcome.totalCount,
            completedPages = outcome.completedPages,
            failedPages = emptyList(),
            retrievedAt = retrievedAt,
            providerResultCode = outcome.resultCode,
            providerResultMessage = outcome.resultMessage,
        )

    private fun incompleteResult(retrievedAt: Instant, outcome: ProviderQueryOutcome) = DrugOverviewLookupResult(
        status = if (outcome.items.isEmpty()) DrugOverviewLookupStatus.FAILED else DrugOverviewLookupStatus.PARTIAL,
        overview = null,
        coverage = DrugOverviewCoverage(productResolved = true, overviewResolved = false, complete = false),
        totalCount = outcome.totalCount,
        completedPages = outcome.completedPages,
        failedPages = outcome.failedPages,
        retrievedAt = retrievedAt,
        providerResultCode = outcome.resultCode,
        providerResultMessage = outcome.resultMessage,
        errorCode = outcome.errorCode?.name,
    )

    private fun failure(retrievedAt: Instant, errorCode: ApiErrorCode) = DrugOverviewLookupResult(
        status = DrugOverviewLookupStatus.FAILED,
        overview = null,
        coverage = DrugOverviewCoverage(productResolved = false, overviewResolved = false, complete = false),
        totalCount = null,
        completedPages = emptyList(),
        failedPages = emptyList(),
        retrievedAt = retrievedAt,
        errorCode = errorCode.name,
    )

    private fun medicalText(raw: String?): OfficialMedicalText? = raw?.let {
        OfficialMedicalText(raw = it, display = displayText(it))
    }

    private fun displayText(raw: String): String = HtmlUtils.htmlUnescape(
        raw
            .replace(BREAK_TAG, "\n")
            .replace(BLOCK_END_TAG, "\n")
            .replace(HTML_TAG, ""),
    )
        .replace('\u00A0', ' ')
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    private fun normalized(value: String?): String = value.orEmpty().trim().replace(WHITESPACE, "").lowercase()

    private fun totalPages(totalCount: Int, pageSize: Int): Int =
        if (totalCount == 0) 0 else ((totalCount.toLong() + pageSize - 1) / pageSize).toInt()

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

    private data class ProviderQueryOutcome(
        val items: List<DrugOverviewProviderItem>,
        val totalCount: Int?,
        val completedPages: List<Int>,
        val failedPages: List<Int>,
        val complete: Boolean,
        val resultCode: String?,
        val resultMessage: String?,
        val errorCode: ApiErrorCode?,
    ) {
        companion object {
            fun complete(
                response: DrugOverviewProviderResponse,
                items: List<DrugOverviewProviderItem>,
                completedPages: List<Int>,
            ) = ProviderQueryOutcome(
                items,
                response.body.totalCount,
                completedPages.toList(),
                emptyList(),
                true,
                response.header.resultCode,
                response.header.resultMsg,
                null,
            )

            fun from(
                response: DrugOverviewProviderResponse,
                items: List<DrugOverviewProviderItem>,
                completedPages: List<Int>,
                failedPages: List<Int>,
                errorCode: ApiErrorCode,
            ) = ProviderQueryOutcome(
                items.toList(),
                response.body.totalCount,
                completedPages.toList(),
                failedPages.toList(),
                false,
                response.header.resultCode,
                response.header.resultMsg,
                errorCode,
            )

            fun failure(failedPage: Int, errorCode: ApiErrorCode) = ProviderQueryOutcome(
                emptyList(),
                null,
                emptyList(),
                listOf(failedPage),
                false,
                null,
                null,
                errorCode,
            )
        }
    }

    companion object {
        private const val PROVIDER_REFERENCE = "MFDS_DRB_EASY_DRUG_INFO"
        private val BREAK_TAG = Regex("(?i)<br\\s*/?>")
        private val BLOCK_END_TAG = Regex("(?i)</(?:p|div|li|tr|h[1-6])\\s*>")
        private val HTML_TAG = Regex("<[^>]+>")
        private val WHITESPACE = Regex("\\s+")
    }
}
