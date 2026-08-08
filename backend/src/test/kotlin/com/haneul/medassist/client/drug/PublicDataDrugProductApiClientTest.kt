package com.haneul.medassist.client.drug

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.DrugProductApiProperties
import com.haneul.medassist.config.PublicDataClientPolicy
import com.haneul.medassist.config.PublicDataCredentialsProperties
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
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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
    fun `maps verified product and ingredient fields without calling product detail`() {
        server.enqueue(jsonResponse(productSearchBody()))
        server.enqueue(
            jsonResponse(
                ingredientPage(
                    totalCount = 2,
                    pageNumber = 1,
                    pageSize = 20,
                    items = """
                        {"ITEM_SEQ":"P-1","PRDUCT":"타이레놀정500밀리그람","MTRAL_SN":"1","MTRAL_CODE":"I-1","MTRAL_NM":"아세트아미노펜","MAIN_INGR_ENG":"Acetaminophen","QNT":"500","INGD_UNIT_CD":"밀리그램","TAMT_SEQ":"1"},
                        {"ITEM_SEQ":"P-1","PRDUCT":"타이레놀정500밀리그람","MTRAL_SN":"2","MTRAL_NM":"공식보조성분","MAIN_INGR_ENG":"Official ingredient","QNT":null,"INGD_UNIT_CD":null,"TAMT_SEQ":"1"}
                    """.trimIndent(),
                ),
            ),
        )
        val client = client(properties)

        val product = client.searchProducts("타이레놀").products.single()
        val ingredients = assertIs<IngredientSearchResult.Success>(
            client.findIngredients(product.productCode, product.productName),
        )

        assertEquals("P-1", product.productCode)
        assertEquals("타이레놀정500밀리그람", product.productName)
        assertEquals("켄뷰코리아", product.manufacturer)
        assertEquals(listOf("I-1", null), ingredients.ingredients.map { it.providerCode })
        assertEquals("아세트아미노펜", ingredients.ingredients.first().displayName)
        assertEquals("500", ingredients.ingredients.first().amount.toString())
        assertEquals("밀리그램", ingredients.ingredients.first().unit)
        assertNull(ingredients.ingredients.last().amount)

        val searchRequest = server.takeRequest()
        val ingredientRequest = server.takeRequest()
        assertEquals("타이레놀", searchRequest.requestUrl?.queryParameter("item_name"))
        assertEquals("타이레놀정500밀리그람", ingredientRequest.requestUrl?.queryParameter("Prduct"))
        assertNull(ingredientRequest.requestUrl?.queryParameter("item_seq"))
        assertEquals(2, server.requestCount, "제품 상세 operation은 현재 검색 흐름에서 호출하지 않는다")
    }

    @Test
    fun `successful empty product response is not treated as provider failure`() {
        server.enqueue(
            jsonResponse(
                """{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"pageNo":1,"numOfRows":20,"totalCount":0}}""",
            ),
        )

        val result = client(properties).searchProducts("없는제품")

        assertTrue(result.products.isEmpty())
    }

    @Test
    fun `findProduct uses exact item sequence detail lookup`() {
        server.enqueue(jsonResponse(productSearchBody()))

        val product = client(properties).findProduct("P-1")

        assertEquals("P-1", product?.productCode)
        assertEquals("타이레놀정500밀리그람", product?.productName)
        val request = server.takeRequest()
        assertEquals(properties.detailOperationPath, request.requestUrl?.encodedPath)
        assertEquals("P-1", request.requestUrl?.queryParameter("item_seq"))
    }

    @Test
    fun `findProduct returns null only for normal empty detail response`() {
        server.enqueue(
            jsonResponse(
                """{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"pageNo":1,"numOfRows":20,"totalCount":0}}""",
            ),
        )

        assertNull(client(properties).findProduct("P-404"))
    }

    @Test
    fun `findProduct rejects detail record for another product code`() {
        server.enqueue(
            jsonResponse(
                """{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":[{"ITEM_SEQ":"OTHER","ITEM_NAME":"다른제품","ENTP_NAME":"다른업체"}]}}""",
            ),
        )

        val failure = capturePublicDataFailure { client(properties).findProduct("P-1") }

        assertEquals(ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH, failure.errorCode)
    }

    @Test
    fun `successful empty ingredient response is distinct from provider failure`() {
        server.enqueue(jsonResponse(ingredientPage(0, 1, 20, "")))

        val result = assertIs<IngredientSearchResult.Success>(client(properties).findIngredients("P-1", "제품명"))

        assertTrue(result.ingredients.isEmpty())
    }

    @Test
    fun `http 200 with provider error code is an error`() {
        server.enqueue(
            jsonResponse("""{"header":{"resultCode":"99","resultMsg":"INVALID REQUEST"},"body":{"items":[]}}"""),
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
    fun `http 401 maps to authentication failure without retry or request URI exposure`() {
        assertHttpFailure(
            statusCode = 401,
            expectedError = ApiErrorCode.PUBLIC_API_AUTH_FAILED,
            maxRetries = 2,
            expectedRequestCount = 1,
        )
    }

    @Test
    fun `http 403 maps to authentication failure without retry or request URI exposure`() {
        assertHttpFailure(
            statusCode = 403,
            expectedError = ApiErrorCode.PUBLIC_API_AUTH_FAILED,
            maxRetries = 2,
            expectedRequestCount = 1,
        )
    }

    @Test
    fun `http 429 retries only to configured limit and remains a provider error`() {
        assertHttpFailure(
            statusCode = 429,
            expectedError = ApiErrorCode.PUBLIC_API_QUOTA_EXCEEDED,
            maxRetries = 2,
            expectedRequestCount = 3,
            retryAfterSeconds = 1,
        )
    }

    @Test
    fun `read timeout retries only to configured limit and opens circuit after final failure`() {
        properties.client.readTimeout = Duration.ofMillis(50)
        properties.client.maxRetries = 1
        properties.client.retryBackoff = Duration.ZERO
        properties.client.circuitFailureThreshold = 1
        repeat(2) {
            server.enqueue(
                jsonResponse(productSearchBody()).setHeadersDelay(300, TimeUnit.MILLISECONDS),
            )
        }
        val client = client(properties)

        val timeout = capturePublicDataFailure { client.searchProducts("제품") }

        assertEquals(ApiErrorCode.PUBLIC_API_TIMEOUT, timeout.errorCode)
        assertEquals(2, server.requestCount)
        assertNoRequestDetails(timeout)

        val circuitOpen = capturePublicDataFailure { client.searchProducts("제품") }

        assertEquals(ApiErrorCode.PUBLIC_API_CIRCUIT_OPEN, circuitOpen.errorCode)
        assertEquals(2, server.requestCount)
        assertNoRequestDetails(circuitOpen)
    }

    @Test
    fun `http 502 retries only to configured limit and remains unavailable`() {
        assertHttpFailure(
            statusCode = 502,
            expectedError = ApiErrorCode.PUBLIC_API_UNAVAILABLE,
            maxRetries = 2,
            expectedRequestCount = 3,
        )
    }

    @Test
    fun `http 503 retries only to configured limit and remains unavailable`() {
        assertHttpFailure(
            statusCode = 503,
            expectedError = ApiErrorCode.PUBLIC_API_UNAVAILABLE,
            maxRetries = 2,
            expectedRequestCount = 3,
        )
    }

    @Test
    fun `http 504 retries only to configured limit and remains unavailable`() {
        assertHttpFailure(
            statusCode = 504,
            expectedError = ApiErrorCode.PUBLIC_API_UNAVAILABLE,
            maxRetries = 2,
            expectedRequestCount = 3,
        )
    }

    @Test
    fun `ingredient lookup follows all pages filters ITEM_SEQ and preserves official order`() {
        properties.pageSize = 2
        server.enqueue(
            jsonResponse(
                ingredientPage(
                    3,
                    1,
                    2,
                    """
                        {"ITEM_SEQ":"P-1","PRDUCT":"선택제품","MTRAL_SN":"2","MTRAL_CODE":"I-2","MTRAL_NM":"두번째성분","QNT":"2","INGD_UNIT_CD":"mg","TAMT_SEQ":"1"},
                        {"ITEM_SEQ":"P-2","PRDUCT":"다른제품","MTRAL_SN":"1","MTRAL_CODE":"OTHER","MTRAL_NM":"제외성분","QNT":"1","INGD_UNIT_CD":"mg","TAMT_SEQ":"1"}
                    """.trimIndent(),
                ),
            ),
        )
        server.enqueue(
            jsonResponse(
                ingredientPage(
                    3,
                    2,
                    2,
                    """{"ITEM_SEQ":"P-1","PRDUCT":"선택제품","MTRAL_SN":"1","MTRAL_CODE":"I-1","MTRAL_NM":"첫번째성분","QNT":"1","INGD_UNIT_CD":"mg","TAMT_SEQ":"1"}""",
                ),
            ),
        )

        val result = assertIs<IngredientSearchResult.Success>(client(properties).findIngredients("P-1", "선택제품"))

        assertEquals(listOf("I-1", "I-2"), result.ingredients.map { it.providerCode })
        val firstRequest = server.takeRequest()
        val secondRequest = server.takeRequest()
        assertEquals("1", firstRequest.requestUrl?.queryParameter("pageNo"))
        assertEquals("2", secondRequest.requestUrl?.queryParameter("pageNo"))
        assertEquals("선택제품", secondRequest.requestUrl?.queryParameter("Prduct"))
    }

    @Test
    fun `ingredient records for another product are a response mismatch not an empty success`() {
        server.enqueue(
            jsonResponse(
                ingredientPage(
                    1,
                    1,
                    20,
                    """{"ITEM_SEQ":"P-2","PRDUCT":"다른제품","MTRAL_SN":"1","MTRAL_CODE":"I-2","MTRAL_NM":"다른성분","QNT":"1","INGD_UNIT_CD":"mg","TAMT_SEQ":"1"}""",
                ),
            ),
        )

        val result = assertIs<IngredientSearchResult.ProviderError>(
            client(properties).findIngredients("P-1", "선택제품"),
        )

        assertEquals(ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH.name, result.safeErrorCode)
    }

    @Test
    fun `failure on a later ingredient page is not returned as partial success`() {
        properties.pageSize = 1
        server.enqueue(
            jsonResponse(
                ingredientPage(
                    2,
                    1,
                    1,
                    """{"ITEM_SEQ":"P-1","PRDUCT":"선택제품","MTRAL_SN":"1","MTRAL_CODE":"I-1","MTRAL_NM":"첫성분","QNT":"1","INGD_UNIT_CD":"mg","TAMT_SEQ":"1"}""",
                ),
            ),
        )
        server.enqueue(jsonResponse("""{"header":{"resultCode":"99","resultMsg":"INVALID REQUEST"}}"""))

        val result = assertIs<IngredientSearchResult.ProviderError>(
            client(properties).findIngredients("P-1", "선택제품"),
        )

        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, result.safeErrorCode)
    }

    @Test
    fun `invalid numeric amount is a provider error and is never converted to zero`() {
        server.enqueue(
            jsonResponse(
                ingredientPage(
                    1,
                    1,
                    20,
                    """{"ITEM_SEQ":"P-1","PRDUCT":"선택제품","MTRAL_SN":"1","MTRAL_CODE":"I-1","MTRAL_NM":"성분","QNT":"not-a-number","INGD_UNIT_CD":"mg","TAMT_SEQ":"1"}""",
                ),
            ),
        )

        val result = assertIs<IngredientSearchResult.ProviderError>(
            client(properties).findIngredients("P-1", "선택제품"),
        )

        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, result.safeErrorCode)
    }

    @Test
    fun `slash separated MAIN_INGR_ENG is preserved and never split into invented ingredients`() {
        server.enqueue(
            jsonResponse(
                ingredientPage(
                    1,
                    1,
                    20,
                    """{"ITEM_SEQ":"P-1","PRDUCT":"복합제","MTRAL_SN":"1","MTRAL_CODE":"I-1","MTRAL_NM":"구조화성분","MAIN_INGR_ENG":"Ingredient A/Ingredient B","QNT":"1","INGD_UNIT_CD":"mg","TAMT_SEQ":"1"}""",
                ),
            ),
        )

        val result = assertIs<IngredientSearchResult.Success>(client(properties).findIngredients("P-1", "복합제"))

        assertEquals(1, result.ingredients.size)
        assertEquals("Ingredient A/Ingredient B", result.ingredients.single().englishName)
    }

    @Test
    fun `encoded service key is encoded exactly once`() {
        val uri = PublicDataUriFactory(properties, serviceKeyEncoder()).searchUri("타이레놀")
        val ascii = uri.toASCIIString()

        assertFalse(ascii.contains("%252F", ignoreCase = true))
        assertTrue(ascii.contains("serviceKey=abc%2Fdef%2Bghi%3D", ignoreCase = true))
    }

    @Test
    fun `product detail URI uses verified item_seq without adding a public endpoint`() {
        val uri = PublicDataUriFactory(properties, serviceKeyEncoder()).detailUri("202106092")

        assertEquals("/getDrugPrdtPrmsnDtlInq06", uri.path)
        assertTrue(uri.rawQuery.contains("item_seq=202106092"))
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

    private fun client(properties: DrugProductApiProperties): PublicDataDrugProductApiClient =
        PublicDataDrugProductApiClient(
            restClient = RestClientConfig().drugProductRestClient(properties),
            properties = properties,
            uriFactory = PublicDataUriFactory(properties, serviceKeyEncoder()),
            responseParser = RawPublicDataResponseParser(ObjectMapper()),
            responseDecoder = PublicDataResponseDecoder(),
            responseValidator = PublicDataApiResponseValidator(),
            mapper = DrugProductApiMapper(properties, IngredientNormalizer()),
            callExecutor = PublicDataCallExecutor(properties.client),
        )

    private fun assertHttpFailure(
        statusCode: Int,
        expectedError: ApiErrorCode,
        maxRetries: Int,
        expectedRequestCount: Int,
        retryAfterSeconds: Long? = null,
    ) {
        properties.client.maxRetries = maxRetries
        properties.client.retryBackoff = Duration.ZERO
        repeat(expectedRequestCount) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(statusCode)
                    .setHeader("Content-Type", "application/json")
                    .apply {
                        retryAfterSeconds?.let { setHeader("Retry-After", it) }
                    },
            )
        }

        val exception = capturePublicDataFailure { client(properties).searchProducts("제품") }

        assertEquals(expectedError, exception.errorCode)
        assertEquals(expectedRequestCount, server.requestCount)
        assertNoRequestDetails(exception)
    }

    private fun capturePublicDataFailure(call: () -> Unit): PublicDataApiException = try {
        call()
        fail("expected public data API failure")
    } catch (exception: PublicDataApiException) {
        exception
    }

    private fun assertNoRequestDetails(exception: Throwable) {
        val messages = generateSequence(exception) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")

        assertFalse(messages.contains("serviceKey", ignoreCase = true))
        assertFalse(messages.contains(properties.baseUrl, ignoreCase = true))
        assertFalse(messages.contains(properties.searchOperationPath, ignoreCase = true))
        assertFalse(messages.contains("abc%2Fdef", ignoreCase = true))
    }

    private fun properties(baseUrl: String) = DrugProductApiProperties(
        baseUrl = baseUrl,
        client = PublicDataClientPolicy(
            connectTimeout = Duration.ofSeconds(1),
            readTimeout = Duration.ofSeconds(1),
            maxRetries = 0,
            permitsPerSecond = 100,
        ),
    )

    private fun serviceKeyEncoder() = ServiceKeyEncoder(
        PublicDataCredentialsProperties(
            serviceKey = "abc%2Fdef%2Bghi%3D",
            serviceKeyEncoded = true,
        ),
    )

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun productSearchBody(): String = """
        {
          "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
          "body": {
            "totalCount": 1,
            "pageNo": 1,
            "numOfRows": 20,
            "items": [
              {"ITEM_SEQ": "P-1", "ITEM_NAME": "타이레놀정500밀리그람", "ENTP_NAME": "켄뷰코리아", "ITEM_ENG_NAME": "Tylenol", "ENTP_ENG_NAME": "Kenvue Korea"}
            ]
          }
        }
    """.trimIndent()

    private fun ingredientPage(
        totalCount: Int,
        pageNumber: Int,
        pageSize: Int,
        items: String,
    ): String = """
        {
          "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
          "body": {
            "totalCount": $totalCount,
            "pageNo": $pageNumber,
            "numOfRows": $pageSize,
            "items": [${items.trim()}]
          }
        }
    """.trimIndent()
}
