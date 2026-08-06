package com.haneul.medassist.client.supplement

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.HealthFunctionalFoodApiProperties
import com.haneul.medassist.config.PublicDataCredentialsProperties
import com.haneul.medassist.config.RestClientConfig
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodLookupStatus
import com.haneul.medassist.service.SupplementNameNormalizer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("external-api")
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class HealthFunctionalFoodExternalApiTest {
    @Test
    fun `actual list and detail responses preserve confirmed Korean fields`() {
        val client = client()

        val search = client.search("11종 혼합유산균")
        val detail = client.findByStatementNo("20140017002183")

        assertEquals(HealthFunctionalFoodLookupStatus.RESOLVED, search.status)
        assertEquals("00", search.providerResultCode)
        assertTrue(search.totalCount != null && search.totalCount > 0)
        assertEquals(HealthFunctionalFoodLookupStatus.RESOLVED, detail.status)
        assertEquals("00", detail.providerResultCode)
        val snapshot = requireNotNull(detail.snapshot)
        val requiredKoreanFields = listOf(
            snapshot.productName,
            requireNotNull(snapshot.manufacturer),
            requireNotNull(snapshot.mainFunction),
            requireNotNull(snapshot.intakeHint),
            requireNotNull(snapshot.baseStandard),
        )
        requiredKoreanFields.forEach { value ->
            assertFalse(value.contains('\uFFFD'))
            assertTrue(HANGUL.containsMatchIn(value))
        }
        println(
            "external_htfs_result_code=${detail.providerResultCode} " +
                "search_status=${search.status} search_total=${search.totalCount} " +
                "detail_status=${detail.status} required_korean_fields=${requiredKoreanFields.size}",
        )
    }

    private fun client(): PublicDataHealthFunctionalFoodApiClient {
        val properties = HealthFunctionalFoodApiProperties().apply { client.maxRetries = 0 }
        val credentials = PublicDataCredentialsProperties(
            serviceKey = requireNotNull(System.getenv("DATA_GO_KR_SERVICE_KEY")),
            serviceKeyEncoded = System.getenv("DATA_GO_KR_SERVICE_KEY_ENCODED")?.toBooleanStrictOrNull() ?: true,
        )
        val parser = RawPublicDataResponseParser(ObjectMapper())
        return PublicDataHealthFunctionalFoodApiClient(
            restClient = RestClientConfig().healthFunctionalFoodRestClient(properties),
            properties = properties,
            uriFactory = HealthFunctionalFoodUriFactory(properties, ServiceKeyEncoder(credentials)),
            responseParser = parser,
            responseDecoder = PublicDataResponseDecoder(),
            responseValidator = PublicDataApiResponseValidator(),
            responseMapper = HealthFunctionalFoodProviderMapper(parser),
            normalizer = SupplementNameNormalizer(),
            callExecutor = PublicDataCallExecutor(properties.client),
        )
    }

    companion object {
        private val HANGUL = Regex("[가-힣]")
    }
}
