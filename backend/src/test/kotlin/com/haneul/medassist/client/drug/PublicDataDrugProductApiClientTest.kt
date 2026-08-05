package com.haneul.medassist.client.drug

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.config.DrugProductApiProperties
import com.haneul.medassist.config.RestClientConfig
import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import com.haneul.medassist.service.IngredientNormalizer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class PublicDataDrugProductApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var properties: DrugProductApiProperties

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        properties = properties(server.url("/").toString().removeSuffix("/"))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps only records returned by official http provider`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """
                {
                  "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                  "body": {"items": [
                    {"CODE": "P-1", "NAME": "공식제품정500밀리그램", "MAKER": "공식제조사"}
                  ]}
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """
                {
                  "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                  "body": {"items": [
                    {"INGR_CODE": "I-1", "INGR_NAME": "공식성분", "AMOUNT": "500", "UNIT": "mg"}
                  ]}
                }
                """.trimIndent(),
            ),
        )
        val client = client(properties)

        val search = client.searchProducts("공식제품")
        val ingredients = assertIs<IngredientSearchResult.Success>(client.findIngredients("P-1"))

        assertEquals(listOf("P-1"), search.products.map { it.productCode })
        assertEquals("공식성분", ingredients.ingredients.single().displayName)
        assertEquals("500", ingredients.ingredients.single().amount.toString())
    }

    @Test
    fun `successful empty response is not treated as provider failure`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"header":{"resultCode":"00"},"body":{"items":[]}}""",
            ),
        )

        val result = client(properties).searchProducts("없는제품")

        assertTrue(result.products.isEmpty())
    }

    @Test
    fun `http 200 with provider error code is an error`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"header":{"resultCode":"99","resultMsg":"INVALID REQUEST"},"body":{"items":[]}}""",
            ),
        )

        val exception = try {
            client(properties).searchProducts("제품")
            fail("expected provider error")
        } catch (exception: PublicDataApiException) {
            exception
        }

        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE, exception.errorCode)
    }

    @Test
    fun `encoded service key is encoded exactly once`() {
        val uri = PublicDataUriFactory(properties).searchUri("타이레놀")
        val ascii = uri.toASCIIString()

        assertFalse(ascii.contains("%252F", ignoreCase = true))
        assertTrue(
            ascii.contains("serviceKey=abc%2Fdef%2Bghi%3D", ignoreCase = true),
            "unexpected URI encoding: $ascii",
        )
    }

    @Test
    fun `xml response structure can be validated without medical field assumptions`() {
        val parser = RawPublicDataResponseParser(ObjectMapper())
        val root = parser.parse(
            """
            <response>
              <header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>
              <body><items /></body>
            </response>
            """.trimIndent(),
        )

        PublicDataApiResponseValidator().validate(root)
    }

    private fun client(properties: DrugProductApiProperties): PublicDataDrugProductApiClient {
        val restClient = RestClientConfig().drugProductRestClient(properties)
        return PublicDataDrugProductApiClient(
            restClient = restClient,
            properties = properties,
            uriFactory = PublicDataUriFactory(properties),
            responseParser = RawPublicDataResponseParser(ObjectMapper()),
            responseValidator = PublicDataApiResponseValidator(),
            mapper = DrugProductApiMapper(properties, IngredientNormalizer()),
            callExecutor = PublicDataCallExecutor(properties),
        )
    }

    private fun properties(baseUrl: String) = DrugProductApiProperties(
        baseUrl = baseUrl,
        serviceKey = "abc%2Fdef%2Bghi%3D",
        serviceKeyEncoded = true,
        policy = DrugProductApiProperties.Policy(
            connectTimeout = Duration.ofSeconds(1),
            readTimeout = Duration.ofSeconds(1),
            maxAttempts = 1,
            requestsPerSecond = 100,
        ),
        mapping = DrugProductApiProperties.Mapping(
            searchItemsJsonPointer = "/body/items",
            ingredientItemsJsonPointer = "/body/items",
            productCodeField = "CODE",
            productNameField = "NAME",
            manufacturerField = "MAKER",
            ingredientProductCodeParameter = "PRODUCT_CODE",
            ingredientCodeField = "INGR_CODE",
            ingredientDisplayNameField = "INGR_NAME",
            ingredientAmountField = "AMOUNT",
            ingredientUnitField = "UNIT",
        ),
    )
}
