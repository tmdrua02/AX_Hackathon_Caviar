package com.haneul.medassist.client.drug.overview

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.DrugOverviewApiProperties
import com.haneul.medassist.config.PublicDataCredentialsProperties
import com.haneul.medassist.config.RestClientConfig
import com.haneul.medassist.domain.medication.DrugOverviewLookupStatus
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("external-api")
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class DrugOverviewExternalApiTest {
    @Test
    fun `verified e약은요 exact product and normal not-found responses are distinct`() {
        val client = client()

        val resolved = client.findOverview(
            productCode = "202106092",
            productName = "타이레놀정500밀리그람(아세트아미노펜)",
            manufacturer = "켄뷰코리아판매유한회사",
        )
        val notFound = client.findOverview(
            productCode = "NO-SUCH-PRODUCT-CODE",
            productName = "존재하지않는공식제품명",
            manufacturer = null,
        )

        assertEquals(DrugOverviewLookupStatus.RESOLVED, resolved.status)
        assertEquals("00", resolved.providerResultCode)
        assertTrue(resolved.coverage.complete)
        assertEquals(DrugOverviewLookupStatus.NOT_FOUND, notFound.status)
        assertEquals("00", notFound.providerResultCode)
        assertTrue(notFound.coverage.complete)
        assertTrue(resolved.overview?.efficacy?.raw?.contains('\uFFFD') == false)
        println(
            "external_overview_result_code=${resolved.providerResultCode} " +
                "external_overview_status=${resolved.status} " +
                "external_overview_not_found_status=${notFound.status}",
        )
    }

    private fun client(): PublicDataDrugOverviewApiClient {
        val properties = DrugOverviewApiProperties().apply { client.maxRetries = 0 }
        val credentials = PublicDataCredentialsProperties(
            serviceKey = requireNotNull(System.getenv("DATA_GO_KR_SERVICE_KEY")),
            serviceKeyEncoded = System.getenv("DATA_GO_KR_SERVICE_KEY_ENCODED")?.toBooleanStrictOrNull() ?: true,
        )
        val parser = RawPublicDataResponseParser(ObjectMapper())
        return PublicDataDrugOverviewApiClient(
            restClient = RestClientConfig().drugOverviewRestClient(properties),
            properties = properties,
            uriFactory = DrugOverviewUriFactory(properties, ServiceKeyEncoder(credentials)),
            responseParser = parser,
            responseDecoder = PublicDataResponseDecoder(),
            responseValidator = PublicDataApiResponseValidator(),
            responseMapper = DrugOverviewResponseMapper(parser),
            callExecutor = PublicDataCallExecutor(properties.client),
        )
    }
}
