package com.haneul.medassist.service

import com.haneul.medassist.client.supplement.HealthFunctionalFoodApiClient
import com.haneul.medassist.config.PublicDataCacheProperties
import com.haneul.medassist.domain.medication.SourceMetadata
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodLookupStatus
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodSearchResult
import com.haneul.medassist.domain.supplement.SupplementProductSnapshotResult
import com.haneul.medassist.domain.supplement.SupplementSearchCandidate
import com.haneul.medassist.domain.supplement.SupplementSearchMatch
import com.haneul.medassist.domain.supplement.SupplementSearchMatchType
import com.haneul.medassist.domain.supplement.SupplementSearchSourceType
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class HealthFunctionalFoodServiceTest {
    @Test
    fun `provider candidates take priority over search index`() {
        val providerCandidate = match("P-1", "공식제품")
        val service = service(searchResult(HealthFunctionalFoodLookupStatus.RESOLVED, listOf(providerCandidate)))

        val result = service.search("공식제품")

        assertEquals(SupplementSearchSourceType.PROVIDER, result.sourceType)
        assertEquals("P-1", result.candidates.single().candidate.sttemntNo)
    }

    @Test
    fun `normal provider not found uses index fallback`() {
        val service = service(searchResult(HealthFunctionalFoodLookupStatus.NOT_FOUND))

        val result = service.search("인덱스제품")

        assertEquals(HealthFunctionalFoodLookupStatus.RESOLVED, result.status)
        assertEquals(SupplementSearchSourceType.INDEX_FALLBACK, result.sourceType)
        assertEquals("I-1", result.candidates.single().candidate.sttemntNo)
    }

    @Test
    fun `provider failure is not replaced by index fallback or cached`() {
        val calls = AtomicInteger()
        val failed = searchResult(HealthFunctionalFoodLookupStatus.FAILED).copy(errorCode = "PUBLIC_API_UNAVAILABLE")
        val service = service(failed, calls)

        repeat(2) { assertEquals(HealthFunctionalFoodLookupStatus.FAILED, service.search("인덱스제품").status) }

        assertEquals(2, calls.get())
    }

    @Test
    fun `resolved and not found search and detail results are cached`() {
        val searchCalls = AtomicInteger()
        val detailCalls = AtomicInteger()
        val api = object : HealthFunctionalFoodApiClient {
            override fun search(productName: String, manufacturer: String?): HealthFunctionalFoodSearchResult {
                searchCalls.incrementAndGet()
                return searchResult(HealthFunctionalFoodLookupStatus.RESOLVED, listOf(match("P-1", productName)))
            }

            override fun findByStatementNo(statementNo: String): SupplementProductSnapshotResult {
                detailCalls.incrementAndGet()
                return detailResult(HealthFunctionalFoodLookupStatus.NOT_FOUND)
            }
        }
        val service = service(api)

        repeat(2) { service.search("공식 제품") }
        repeat(2) { service.findByStatementNo("NO-SUCH") }

        assertEquals(1, searchCalls.get())
        assertEquals(1, detailCalls.get())
    }

    @Test
    fun `failed detail result is never cached`() {
        val detailCalls = AtomicInteger()
        val api = object : HealthFunctionalFoodApiClient {
            override fun search(productName: String, manufacturer: String?) =
                searchResult(HealthFunctionalFoodLookupStatus.NOT_FOUND)

            override fun findByStatementNo(statementNo: String): SupplementProductSnapshotResult {
                detailCalls.incrementAndGet()
                return detailResult(HealthFunctionalFoodLookupStatus.FAILED).copy(
                    errorCode = "PUBLIC_API_UNAVAILABLE",
                )
            }
        }
        val service = service(api)

        repeat(2) { service.findByStatementNo("S-1") }

        assertEquals(2, detailCalls.get())
    }

    private fun service(
        result: HealthFunctionalFoodSearchResult,
        calls: AtomicInteger = AtomicInteger(),
    ) = service(
        object : HealthFunctionalFoodApiClient {
            override fun search(productName: String, manufacturer: String?): HealthFunctionalFoodSearchResult {
                calls.incrementAndGet()
                return result
            }

            override fun findByStatementNo(statementNo: String) = detailResult(HealthFunctionalFoodLookupStatus.NOT_FOUND)
        },
    )

    private fun service(api: HealthFunctionalFoodApiClient): HealthFunctionalFoodService {
        val normalizer = SupplementNameNormalizer()
        val index = SupplementSearchIndexService(
            loader = SupplementSearchIndexLoader { listOf(candidate("I-1", "인덱스제품")) },
            normalizer = normalizer,
            cacheProperties = PublicDataCacheProperties(),
        )
        return HealthFunctionalFoodService(
            apiClient = api,
            searchIndexService = index,
            normalizer = normalizer,
            cache = HealthFunctionalFoodCache(PublicDataCacheProperties()),
        )
    }

    private fun searchResult(
        status: HealthFunctionalFoodLookupStatus,
        candidates: List<SupplementSearchMatch> = emptyList(),
    ) = HealthFunctionalFoodSearchResult(
        status = status,
        candidates = candidates,
        totalCount = candidates.size,
        completedPages = listOf(1),
        failedPages = emptyList(),
        complete = status == HealthFunctionalFoodLookupStatus.RESOLVED || status == HealthFunctionalFoodLookupStatus.NOT_FOUND,
        sourceType = SupplementSearchSourceType.PROVIDER,
        retrievedAt = Instant.EPOCH,
        providerResultCode = "00",
        providerResultMessage = "NORMAL SERVICE.",
    )

    private fun detailResult(status: HealthFunctionalFoodLookupStatus) = SupplementProductSnapshotResult(
        status = status,
        snapshot = null,
        totalCount = 0,
        completedPages = listOf(1),
        failedPages = emptyList(),
        complete = status == HealthFunctionalFoodLookupStatus.NOT_FOUND,
        retrievedAt = Instant.EPOCH,
    )

    private fun match(statementNo: String, productName: String) = SupplementSearchMatch(
        candidate(statementNo, productName),
        score = 100,
        matchType = SupplementSearchMatchType.EXACT,
    )

    private fun candidate(statementNo: String, productName: String) = SupplementSearchCandidate(
        sttemntNo = statementNo,
        productName = productName,
        manufacturer = "공식업체",
        normalizedName = SupplementNameNormalizer().normalize(productName),
        aliases = emptySet(),
        source = SourceMetadata("테스트", statementNo, Instant.EPOCH, "TEST"),
    )
}
