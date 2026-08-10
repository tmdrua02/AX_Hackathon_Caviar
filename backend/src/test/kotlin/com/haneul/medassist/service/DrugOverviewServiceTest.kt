package com.haneul.medassist.service

import com.haneul.medassist.client.drug.overview.DrugOverviewApiClient
import com.haneul.medassist.config.PublicDataCacheProperties
import com.haneul.medassist.domain.medication.DrugOverviewCoverage
import com.haneul.medassist.domain.medication.DrugOverviewLookupResult
import com.haneul.medassist.domain.medication.DrugOverviewLookupStatus
import com.haneul.medassist.domain.medication.Medication
import com.haneul.medassist.domain.medication.ProductResolutionStatus
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class DrugOverviewServiceTest {
    @Test
    fun `resolved and not-found results use separate cache policies`() {
        listOf(DrugOverviewLookupStatus.RESOLVED, DrugOverviewLookupStatus.NOT_FOUND).forEach { status ->
            val calls = AtomicInteger()
            val service = service(calls) { result(status) }

            service.findOverview(medication())
            service.findOverview(medication())

            assertEquals(1, calls.get())
        }
    }

    @Test
    fun `provider failures are never cached as normal results`() {
        val calls = AtomicInteger()
        val service = service(calls) { result(DrugOverviewLookupStatus.FAILED) }

        service.findOverview(medication())
        service.findOverview(medication())

        assertEquals(2, calls.get())
    }

    private fun service(
        calls: AtomicInteger,
        answer: () -> DrugOverviewLookupResult,
    ) = DrugOverviewService(
        client = object : DrugOverviewApiClient {
            override fun findOverview(
                productCode: String,
                productName: String,
                manufacturer: String?,
            ): DrugOverviewLookupResult {
                calls.incrementAndGet()
                return answer()
            }
        },
        cache = DrugOverviewCache(
            PublicDataCacheProperties(
                positiveSearchTtl = Duration.ofMinutes(1),
                negativeSearchTtl = Duration.ofMinutes(1),
                ingredientTtl = Duration.ofMinutes(1),
                overviewTtl = Duration.ofMinutes(1),
                maximumSize = 10,
            ),
        ),
    )

    private fun result(status: DrugOverviewLookupStatus) = DrugOverviewLookupResult(
        status = status,
        overview = null,
        coverage = DrugOverviewCoverage(
            productResolved = true,
            overviewResolved = status == DrugOverviewLookupStatus.RESOLVED,
            complete = status == DrugOverviewLookupStatus.RESOLVED || status == DrugOverviewLookupStatus.NOT_FOUND,
        ),
        totalCount = if (status == DrugOverviewLookupStatus.NOT_FOUND) 0 else null,
        completedPages = emptyList(),
        failedPages = emptyList(),
        retrievedAt = Instant.EPOCH,
    )

    private fun medication() = Medication(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        productCode = "202106092",
        productName = "공식제품",
        manufacturer = "공식업체",
        ingredients = emptyList(),
        source = null,
        resolutionStatus = ProductResolutionStatus.RESOLVED,
    )
}
