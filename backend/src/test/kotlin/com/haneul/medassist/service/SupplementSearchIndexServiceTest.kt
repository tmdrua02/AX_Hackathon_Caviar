package com.haneul.medassist.service

import com.haneul.medassist.config.PublicDataCacheProperties
import com.haneul.medassist.domain.medication.SourceMetadata
import com.haneul.medassist.domain.supplement.SupplementSearchCandidate
import com.haneul.medassist.domain.supplement.SupplementSearchMatchType
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.MedAssistException
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SupplementSearchIndexServiceTest {
    @Test
    fun `exact match has the highest deterministic score`() {
        val result = service(candidate("A-1", "루테인")).search("루테인")

        assertEquals(1, result.size)
        assertEquals(SupplementSearchMatchType.EXACT, result.single().matchType)
        assertEquals(100, result.single().score)
    }

    @Test
    fun `prefix match only uses normalized product names`() {
        val result = service(candidate("A-1", "루테인지아잔틴")).search("루테인")

        assertEquals(SupplementSearchMatchType.PREFIX, result.single().matchType)
        assertEquals(80, result.single().score)
    }

    @Test
    fun `contains match does not use fuzzy matching`() {
        val result = service(candidate("A-1", "루테인지아잔틴")).search("지아잔틴")

        assertEquals(SupplementSearchMatchType.CONTAINS, result.single().matchType)
        assertEquals(60, result.single().score)
        assertTrue(service(candidate("A-1", "루테인")).search("루태인").isEmpty())
    }

    @Test
    fun `not found returns an empty candidate list`() {
        assertTrue(service(candidate("A-1", "루테인")).search("비타민").isEmpty())
    }

    @Test
    fun `normalize removes whitespace parentheses and symbols and lowercases English`() {
        val service = service()

        assertEquals("vitaminc1000", service.normalize("  Vitamin-C (1000)!! "))
    }

    @Test
    fun `alias participates in exact matching without changing official product name`() {
        val candidate = candidate("A-1", "공식 제품명", aliases = setOf("소비자 별칭"))

        val result = service(candidate).search("소비자별칭")

        assertEquals(SupplementSearchMatchType.EXACT, result.single().matchType)
        assertEquals("공식 제품명", result.single().candidate.productName)
    }

    @Test
    fun `empty normalized query is rejected`() {
        val exception = assertFailsWith<MedAssistException> { service().search(" ()-- ") }

        assertEquals(ApiErrorCode.VALIDATION_FAILED, exception.errorCode)
    }

    @Test
    fun `duplicate statement numbers are returned only once`() {
        val service = service(
            candidate("A-1", "루테인"),
            candidate("A-1", "루테인", manufacturer = "중복업체"),
        )

        assertEquals(1, service.search("루테인").size)
    }

    @Test
    fun `normalized query is the cache key`() {
        val service = service(candidate("A-1", "루테인"))

        val first = service.search("루테인")
        val cached = service.search("루 테 인")

        assertSame(first, cached)
    }

    private fun service(vararg candidates: SupplementSearchCandidate) = SupplementSearchIndexService(
        loader = SupplementSearchIndexLoader { candidates.toList() },
        normalizer = SupplementNameNormalizer(),
        cacheProperties = PublicDataCacheProperties(),
    )

    private fun candidate(
        sttemntNo: String,
        productName: String,
        manufacturer: String? = "공식업체",
        aliases: Set<String> = emptySet(),
    ) = SupplementSearchCandidate(
        sttemntNo = sttemntNo,
        productName = productName,
        manufacturer = manufacturer,
        normalizedName = SupplementNameNormalizer().normalize(productName),
        aliases = aliases,
        source = SourceMetadata(
            name = "테스트 인덱스",
            recordId = sttemntNo,
            retrievedAt = Instant.EPOCH,
            providerReference = "TEST_INDEX",
        ),
    )
}
