package com.haneul.medassist.client.dur

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.DurApiProperties
import com.haneul.medassist.config.PublicDataCredentialsProperties
import com.haneul.medassist.config.RestClientConfig
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("external-api")
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class DurExternalApiTest {
    @Test
    fun `verified itraconazole contraindication response is complete and valid UTF-8`() {
        val properties = DurApiProperties()
        properties.client.maxRetries = 0
        val credentials = PublicDataCredentialsProperties(
            serviceKey = requireNotNull(System.getenv("DATA_GO_KR_SERVICE_KEY")),
            serviceKeyEncoded = System.getenv("DATA_GO_KR_SERVICE_KEY_ENCODED")?.toBooleanStrictOrNull() ?: true,
        )
        val parser = RawPublicDataResponseParser(ObjectMapper())
        val client = PublicDataDurIngredientApiClient(
            restClient = RestClientConfig().durRestClient(properties),
            properties = properties,
            uriFactory = DurPublicDataUriFactory(properties, ServiceKeyEncoder(credentials)),
            responseParser = parser,
            responseDecoder = PublicDataResponseDecoder(),
            responseValidator = PublicDataApiResponseValidator(),
            responseMapper = DurProviderResponseMapper(parser),
            callExecutor = PublicDataCallExecutor(properties.client),
        )

        val result = client.findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.MATCHED, result.status)
        assertTrue(result.complete)
        assertEquals("00", result.providerResultCode)
        assertTrue(requireNotNull(result.totalCount) > 0)
        assertTrue(result.records.all { it.relatedIngredientCode.isNotBlank() })
        assertTrue(result.records.none { record ->
            record.rawFields.values.filterNotNull().any { it.contains('\uFFFD') }
        })
        val relationCodeCount = result.records.map { it.relatedIngredientCode }.distinct().size
        println(
            "external_result_code=${result.providerResultCode} " +
                "external_total_count=${result.totalCount} " +
                "external_relation_code_count=$relationCodeCount",
        )
    }
}
