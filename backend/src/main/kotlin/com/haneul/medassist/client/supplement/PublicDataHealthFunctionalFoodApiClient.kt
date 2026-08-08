package com.haneul.medassist.client.supplement

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataLogSanitizer
import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.config.HealthFunctionalFoodApiProperties
import com.haneul.medassist.domain.medication.SourceMetadata
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodLookupStatus
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodSearchResult
import com.haneul.medassist.domain.supplement.SupplementProductCoverage
import com.haneul.medassist.domain.supplement.SupplementProductSnapshot
import com.haneul.medassist.domain.supplement.SupplementProductSnapshotResult
import com.haneul.medassist.domain.supplement.SupplementSearchCandidate
import com.haneul.medassist.domain.supplement.SupplementSearchMatch
import com.haneul.medassist.domain.supplement.SupplementSearchMatchType
import com.haneul.medassist.domain.supplement.SupplementSearchSourceType
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import com.haneul.medassist.service.SupplementNameNormalizer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.URI
import java.time.Instant

@Component
class PublicDataHealthFunctionalFoodApiClient(
    @Qualifier("healthFunctionalFoodRestClient") private val restClient: RestClient,
    private val properties: HealthFunctionalFoodApiProperties,
    private val uriFactory: HealthFunctionalFoodUriFactory,
    private val responseParser: RawPublicDataResponseParser,
    private val responseDecoder: PublicDataResponseDecoder,
    private val responseValidator: PublicDataApiResponseValidator,
    private val responseMapper: HealthFunctionalFoodProviderMapper,
    private val normalizer: SupplementNameNormalizer,
    @Qualifier("healthFunctionalFoodCallExecutor") private val callExecutor: PublicDataCallExecutor,
) : HealthFunctionalFoodApiClient {
    override fun search(productName: String, manufacturer: String?): HealthFunctionalFoodSearchResult {
        val retrievedAt = Instant.now()
        val normalizedQuery = normalizer.normalize(productName)
        if (normalizedQuery.isBlank()) return failedSearch(retrievedAt, ApiErrorCode.VALIDATION_FAILED)

        val outcome = fetchAll(
            detail = false,
            listQuery = HealthFunctionalFoodListQuery(
                productName = productName.trim(),
                manufacturer = manufacturer?.trim()?.takeIf(String::isNotEmpty),
            ),
        )
        val invalidRecord = outcome.items.any { it.statementNo.isNullOrBlank() || it.productName.isNullOrBlank() }
        if (invalidRecord) return failedSearch(retrievedAt, ApiErrorCode.PUBLIC_API_INVALID_RESPONSE, outcome)

        val normalizedManufacturer = manufacturer?.let(normalizer::normalize)?.takeIf(String::isNotEmpty)
        val matches = outcome.items.asSequence()
            .filter { item ->
                normalizedManufacturer == null || normalizer.normalize(item.manufacturer.orEmpty()) == normalizedManufacturer
            }
            .mapNotNull { item -> toMatch(item, normalizedQuery, retrievedAt) }
            .distinctBy { it.candidate.sttemntNo }
            .sortedWith(
                compareByDescending<SupplementSearchMatch> { it.score }
                    .thenBy { it.candidate.productName }
                    .thenBy { it.candidate.sttemntNo },
            )
            .toList()

        if (!outcome.complete) {
            return HealthFunctionalFoodSearchResult(
                status = if (outcome.items.isEmpty()) {
                    HealthFunctionalFoodLookupStatus.FAILED
                } else {
                    HealthFunctionalFoodLookupStatus.PARTIAL
                },
                candidates = matches,
                totalCount = outcome.totalCount,
                completedPages = outcome.completedPages,
                failedPages = outcome.failedPages,
                complete = false,
                sourceType = SupplementSearchSourceType.PROVIDER,
                retrievedAt = retrievedAt,
                providerResultCode = outcome.resultCode,
                providerResultMessage = outcome.resultMessage,
                errorCode = outcome.errorCode?.name,
            )
        }

        return HealthFunctionalFoodSearchResult(
            status = if (matches.isEmpty()) {
                HealthFunctionalFoodLookupStatus.NOT_FOUND
            } else {
                HealthFunctionalFoodLookupStatus.RESOLVED
            },
            candidates = matches,
            totalCount = outcome.totalCount,
            completedPages = outcome.completedPages,
            failedPages = emptyList(),
            complete = true,
            sourceType = SupplementSearchSourceType.PROVIDER,
            retrievedAt = retrievedAt,
            providerResultCode = outcome.resultCode,
            providerResultMessage = outcome.resultMessage,
        )
    }

    override fun findByStatementNo(statementNo: String): SupplementProductSnapshotResult {
        val retrievedAt = Instant.now()
        if (statementNo.isBlank()) return failedDetail(retrievedAt, ApiErrorCode.VALIDATION_FAILED)

        val requested = statementNo.trim()
        val outcome = fetchAll(
            detail = true,
            listQuery = HealthFunctionalFoodListQuery(statementNo = requested),
        )
        val exact = outcome.items.filter { it.statementNo == requested }.distinct()
        if (outcome.items.any { it.statementNo != requested } || exact.size > 1) {
            return failedDetail(retrievedAt, ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH, outcome)
        }
        val snapshot = exact.singleOrNull()?.let { toSnapshot(it, retrievedAt, outcome.complete) }
        if (snapshot == null && outcome.items.isNotEmpty()) {
            return failedDetail(retrievedAt, ApiErrorCode.PUBLIC_API_INVALID_RESPONSE, outcome)
        }

        if (!outcome.complete) {
            return SupplementProductSnapshotResult(
                status = if (snapshot == null) {
                    HealthFunctionalFoodLookupStatus.FAILED
                } else {
                    HealthFunctionalFoodLookupStatus.PARTIAL
                },
                snapshot = snapshot,
                totalCount = outcome.totalCount,
                completedPages = outcome.completedPages,
                failedPages = outcome.failedPages,
                complete = false,
                retrievedAt = retrievedAt,
                providerResultCode = outcome.resultCode,
                providerResultMessage = outcome.resultMessage,
                errorCode = outcome.errorCode?.name,
            )
        }

        return SupplementProductSnapshotResult(
            status = if (snapshot == null) {
                HealthFunctionalFoodLookupStatus.NOT_FOUND
            } else {
                HealthFunctionalFoodLookupStatus.RESOLVED
            },
            snapshot = snapshot,
            totalCount = outcome.totalCount,
            completedPages = outcome.completedPages,
            failedPages = emptyList(),
            complete = true,
            retrievedAt = retrievedAt,
            providerResultCode = outcome.resultCode,
            providerResultMessage = outcome.resultMessage,
        )
    }

    private fun fetchAll(
        detail: Boolean,
        listQuery: HealthFunctionalFoodListQuery,
    ): ProviderQueryOutcome {
        val completedPages = mutableListOf<Int>()
        val items = mutableListOf<HealthFunctionalFoodProviderItem>()
        val first = try {
            fetchPage(detail, listQuery, 1)
        } catch (exception: PublicDataApiException) {
            return ProviderQueryOutcome.failure(1, exception.errorCode)
        }
        if (first.body.pageNo != 1) {
            return ProviderQueryOutcome.from(first, items, completedPages, listOf(1), ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH)
        }
        if (first.body.totalCount == 0) {
            return ProviderQueryOutcome.complete(first, emptyList(), listOf(1))
        }

        items += first.body.items
        completedPages += 1
        val totalPages = totalPages(first.body.totalCount, first.body.numOfRows)
        if (first.body.totalCount > properties.maxRecords || totalPages > properties.maxPages) {
            return ProviderQueryOutcome.from(
                first,
                items,
                completedPages,
                emptyList(),
                ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
            )
        }

        for (pageNumber in 2..totalPages) {
            val page = try {
                fetchPage(detail, listQuery, pageNumber)
            } catch (exception: PublicDataApiException) {
                return ProviderQueryOutcome.from(first, items, completedPages, listOf(pageNumber), exception.errorCode)
            }
            if (
                page.body.pageNo != pageNumber || page.body.totalCount != first.body.totalCount ||
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

    private fun fetchPage(
        detail: Boolean,
        query: HealthFunctionalFoodListQuery,
        pageNumber: Int,
    ): HealthFunctionalFoodProviderResponse {
        val uri = if (detail) {
            uriFactory.detailUri(requireNotNull(query.statementNo), pageNumber)
        } else {
            uriFactory.listUri(query, pageNumber)
        }
        val root = responseParser.parse(fetch(uri))
        responseValidator.validate(root)
        return responseMapper.map(root)
    }

    private fun fetch(uri: URI): String = callExecutor.execute {
        try {
            val response = restClient.get().uri(uri).retrieve().toEntity(ByteArray::class.java)
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

    private fun toMatch(
        item: HealthFunctionalFoodProviderItem,
        normalizedQuery: String,
        retrievedAt: Instant,
    ): SupplementSearchMatch? {
        val productName = requireNotNull(item.productName)
        val normalizedName = normalizer.normalize(productName)
        val matchType = when {
            normalizedName == normalizedQuery -> SupplementSearchMatchType.EXACT
            normalizedName.startsWith(normalizedQuery) -> SupplementSearchMatchType.PREFIX
            normalizedName.contains(normalizedQuery) -> SupplementSearchMatchType.CONTAINS
            else -> return null
        }
        val statementNo = requireNotNull(item.statementNo)
        return SupplementSearchMatch(
            candidate = SupplementSearchCandidate(
                sttemntNo = statementNo,
                productName = productName,
                manufacturer = item.manufacturer,
                normalizedName = normalizedName,
                aliases = emptySet(),
                source = source(statementNo, retrievedAt),
            ),
            score = SCORE_BY_TYPE.getValue(matchType),
            matchType = matchType,
        )
    }

    private fun toSnapshot(
        item: HealthFunctionalFoodProviderItem,
        retrievedAt: Instant,
        complete: Boolean,
    ): SupplementProductSnapshot? {
        val statementNo = item.statementNo ?: return null
        val productName = item.productName ?: return null
        val coverage = SupplementProductCoverage(
            statementResolved = true,
            detailResolved = true,
            complete = complete,
        )
        return SupplementProductSnapshot(
            statementNo = statementNo,
            productName = productName,
            manufacturer = item.manufacturer,
            registerDate = item.registerDate,
            distributionPeriod = item.distributionPeriod,
            appearance = item.appearance,
            usage = item.usage,
            storage = item.storage,
            intakeHint = item.intakeHint,
            mainFunction = item.mainFunction,
            baseStandard = item.baseStandard,
            coverage = coverage,
            retrievedAt = retrievedAt,
            source = source(statementNo, retrievedAt),
            rawProviderRecord = item.rawProviderRecord,
        )
    }

    private fun source(statementNo: String, retrievedAt: Instant) = SourceMetadata(
        name = "식품의약품안전처 건강기능식품정보",
        recordId = statementNo,
        retrievedAt = retrievedAt,
        providerReference = PROVIDER_REFERENCE,
    )

    private fun failedSearch(
        retrievedAt: Instant,
        errorCode: ApiErrorCode,
        outcome: ProviderQueryOutcome? = null,
    ) = HealthFunctionalFoodSearchResult(
        status = HealthFunctionalFoodLookupStatus.FAILED,
        candidates = emptyList(),
        totalCount = outcome?.totalCount,
        completedPages = outcome?.completedPages.orEmpty(),
        failedPages = outcome?.failedPages.orEmpty(),
        complete = false,
        sourceType = SupplementSearchSourceType.PROVIDER,
        retrievedAt = retrievedAt,
        providerResultCode = outcome?.resultCode,
        providerResultMessage = outcome?.resultMessage,
        errorCode = errorCode.name,
    )

    private fun failedDetail(
        retrievedAt: Instant,
        errorCode: ApiErrorCode,
        outcome: ProviderQueryOutcome? = null,
    ) = SupplementProductSnapshotResult(
        status = HealthFunctionalFoodLookupStatus.FAILED,
        snapshot = null,
        totalCount = outcome?.totalCount,
        completedPages = outcome?.completedPages.orEmpty(),
        failedPages = outcome?.failedPages.orEmpty(),
        complete = false,
        retrievedAt = retrievedAt,
        providerResultCode = outcome?.resultCode,
        providerResultMessage = outcome?.resultMessage,
        errorCode = errorCode.name,
    )

    private fun totalPages(totalCount: Int, pageSize: Int): Int =
        ((totalCount.toLong() + pageSize - 1) / pageSize).toInt()

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
        val items: List<HealthFunctionalFoodProviderItem>,
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
                response: HealthFunctionalFoodProviderResponse,
                items: List<HealthFunctionalFoodProviderItem>,
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
                response: HealthFunctionalFoodProviderResponse,
                items: List<HealthFunctionalFoodProviderItem>,
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
                emptyList(), null, emptyList(), listOf(failedPage), false, null, null, errorCode,
            )
        }
    }

    companion object {
        private const val PROVIDER_REFERENCE = "MFDS_HTFS_INFO_SERVICE_03"
        private val SCORE_BY_TYPE = mapOf(
            SupplementSearchMatchType.EXACT to 100,
            SupplementSearchMatchType.PREFIX to 80,
            SupplementSearchMatchType.CONTAINS to 60,
        )
    }
}
