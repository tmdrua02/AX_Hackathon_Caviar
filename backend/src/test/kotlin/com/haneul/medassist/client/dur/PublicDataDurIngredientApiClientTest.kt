package com.haneul.medassist.client.dur

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataLogSanitizer
import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.DurApiProperties
import com.haneul.medassist.config.PublicDataClientPolicy
import com.haneul.medassist.config.PublicDataCredentialsProperties
import com.haneul.medassist.config.RestClientConfig
import com.haneul.medassist.exception.ApiErrorCode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicDataDurIngredientApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var properties: DurApiProperties

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
    fun `normal one page response maps nested item and preserves nullable provider id`() {
        server.enqueue(jsonResponse(page(totalCount = 1, pageNo = 1, pageSize = 2, items = item())))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.MATCHED, result.status)
        assertTrue(result.complete)
        assertEquals(listOf(1), result.completedPages)
        assertEquals(1, result.totalCount)
        assertEquals("00", result.providerResultCode)
        val record = result.records.single()
        assertNull(record.providerRecordId)
        assertEquals("D000762", record.ingredientCode)
        assertEquals("D000027", record.relatedIngredientCode)
        assertEquals("심바스타틴", record.relatedIngredientKoreanName)
        assertEquals("병용투여하지 않는다.", record.prohibitionContent)
        assertEquals(DurProviderStatus.ACTIVE, record.providerStatus)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            listOf("serviceKey", "pageNo", "numOfRows", "type", "typeName", "ingrCode", "ingrKorName"),
            request.requestUrl!!.encodedQuery!!.split("&").map { it.substringBefore("=") },
        )
        assertEquals("dummy/segment+value=", request.requestUrl?.queryParameter("serviceKey"))
        assertEquals("병용금기", request.requestUrl?.queryParameter("typeName"))
        assertEquals("이트라코나졸", request.requestUrl?.queryParameter("ingrKorName"))
    }

    @Test
    fun `multiple pages are fully fetched and multiple relations are retained`() {
        server.enqueue(
            jsonResponse(
                page(
                    totalCount = 3,
                    pageNo = 1,
                    pageSize = 2,
                    items = listOf(item("D000027", "심바스타틴"), item("D000028", "트리아졸람")).joinToString(","),
                ),
            ),
        )
        server.enqueue(jsonResponse(page(3, 2, 2, item("D000029", "로바스타틴"))))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.MATCHED, result.status)
        assertEquals(listOf(1, 2), result.completedPages)
        assertTrue(result.failedPages.isEmpty())
        assertTrue(result.complete)
        assertEquals(listOf("D000027", "D000028", "D000029"), result.records.map { it.relatedIngredientCode })
        assertEquals("1", server.takeRequest().requestUrl?.queryParameter("pageNo"))
        assertEquals("2", server.takeRequest().requestUrl?.queryParameter("pageNo"))
    }

    @Test
    fun `normal zero count with omitted items is no match`() {
        server.enqueue(jsonResponse(page(totalCount = 0, pageNo = 1, pageSize = 2, items = null)))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.NO_MATCH, result.status)
        assertTrue(result.complete)
        assertTrue(result.records.isEmpty())
    }

    @Test
    fun `normal zero count with empty items is no match`() {
        server.enqueue(jsonResponse(page(totalCount = 0, pageNo = 1, pageSize = 2, items = "")))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.NO_MATCH, result.status)
        assertTrue(result.complete)
    }

    @Test
    fun `response ingredient code mismatch is failed and never no match`() {
        server.enqueue(jsonResponse(page(1, 1, 2, item(ingredientCode = "D999999"))))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertFalse(result.complete)
        assertEquals(ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH.name, result.errorCode)
    }

    @Test
    fun `missing related ingredient code is provider mismatch`() {
        server.enqueue(jsonResponse(page(1, 1, 2, item(relatedCode = null))))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH.name, result.errorCode)
    }

    @Test
    fun `internal dedup key removes duplicates without inventing provider id`() {
        val duplicate = item("D000027", "심바스타틴")
        server.enqueue(jsonResponse(page(2, 1, 2, "$duplicate,$duplicate")))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertTrue(result.complete)
        assertEquals(2, result.totalCount)
        assertEquals(1, result.records.size)
        assertNull(result.records.single().providerRecordId)
    }

    @Test
    fun `middle page failure returns partial and never complete`() {
        server.enqueue(jsonResponse(page(3, 1, 2, item())))
        server.enqueue(MockResponse().setResponseCode(503))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.PARTIAL, result.status)
        assertFalse(result.complete)
        assertEquals(listOf(1), result.completedPages)
        assertEquals(listOf(2), result.failedPages)
        assertEquals(ApiErrorCode.PUBLIC_API_UNAVAILABLE.name, result.errorCode)
    }

    @Test
    fun `pagination metadata mismatch is partial`() {
        server.enqueue(jsonResponse(page(3, 1, 2, item())))
        server.enqueue(jsonResponse(page(4, 2, 2, item("D000028", "트리아졸람"))))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.PARTIAL, result.status)
        assertFalse(result.complete)
        assertEquals(listOf(2), result.failedPages)
        assertEquals(ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH.name, result.errorCode)
    }

    @Test
    fun `configured page and record safety limits prevent incomplete success`() {
        properties.maxPages = 1
        properties.maxRecords = 2
        server.enqueue(jsonResponse(page(3, 1, 2, item())))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.PARTIAL, result.status)
        assertFalse(result.complete)
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, result.errorCode)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `provider resultCode error is failed and not empty success`() {
        server.enqueue(
            jsonResponse(
                """{"header":{"resultCode":"99","resultMsg":"INVALID REQUEST"},"body":{"pageNo":1,"numOfRows":2,"totalCount":0}}""",
            ),
        )

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, result.errorCode)
    }

    @Test
    fun `http 401 and 403 map to authentication failure`() {
        listOf(401, 403).forEach { status ->
            server.enqueue(MockResponse().setResponseCode(status))

            val result = client().findContraindications("D000762", "이트라코나졸")

            assertEquals(DurLookupStatus.FAILED, result.status)
            assertEquals(ApiErrorCode.PUBLIC_API_AUTH_FAILED.name, result.errorCode)
        }
    }

    @Test
    fun `http 429 maps to quota failure`() {
        server.enqueue(MockResponse().setResponseCode(429))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_QUOTA_EXCEEDED.name, result.errorCode)
    }

    @Test
    fun `gateway server errors map to unavailable`() {
        listOf(502, 503, 504).forEach { status ->
            server.enqueue(MockResponse().setResponseCode(status))

            val result = client().findContraindications("D000762", "이트라코나졸")

            assertEquals(DurLookupStatus.FAILED, result.status)
            assertEquals(ApiErrorCode.PUBLIC_API_UNAVAILABLE.name, result.errorCode)
        }
    }

    @Test
    fun `read timeout is failed and never no match`() {
        properties.client.readTimeout = Duration.ofMillis(100)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_TIMEOUT.name, result.errorCode)
    }

    @Test
    fun `malformed json is invalid response`() {
        server.enqueue(jsonResponse("{not-json"))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, result.errorCode)
    }

    @Test
    fun `malformed item wrapper is invalid response`() {
        server.enqueue(
            jsonResponse(
                """{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"pageNo":1,"numOfRows":2,"totalCount":1,"items":[{"wrong":{}}]}}""",
            ),
        )

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, result.errorCode)
    }

    @Test
    fun `malformed utf8 is invalid response`() {
        val bytes = byteArrayOf('{'.code.toByte(), '"'.code.toByte(), 0xC3.toByte(), 0x28, '"'.code.toByte(), '}'.code.toByte())
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(Buffer().write(bytes)),
        )

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, result.errorCode)
    }

    @Test
    fun `replacement character in Korean field is invalid response`() {
        server.enqueue(jsonResponse(page(1, 1, 2, item(relatedName = "심바�스타틴"))))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, result.errorCode)
    }

    @Test
    fun `non contraindication type is rejected`() {
        server.enqueue(jsonResponse(page(1, 1, 2, item(typeName = "특정연령대금기"))))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH.name, result.errorCode)
    }

    @Test
    fun `missing required Korean relationship name is rejected`() {
        val withoutName = item().replace("\"MIXTURE_INGR_KOR_NAME\":\"심바스타틴\"", "\"MIXTURE_INGR_KOR_NAME\":null")
        server.enqueue(jsonResponse(page(1, 1, 2, withoutName)))

        val result = client().findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH.name, result.errorCode)
    }

    @Test
    fun `missing service key fails lazily without exposing a request`() {
        val encoder = ServiceKeyEncoder(PublicDataCredentialsProperties())
        val client = client(uriFactory = DurPublicDataUriFactory(properties, encoder))

        val result = client.findContraindications("D000762", "이트라코나졸")

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_NOT_CONFIGURED.name, result.errorCode)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `RestClient receives the exact URI produced by factory`() {
        server.enqueue(jsonResponse(page(1, 1, 2, item())))
        val uriFactory = uriFactory()
        val expected = uriFactory.lookupUri(request(), 1)
        val unchanged = AtomicBoolean(false)
        val restClient = RestClientConfig().durRestClient(properties).mutate()
            .requestInterceptor { request, body, execution ->
                unchanged.set(request.uri == expected)
                execution.execute(request, body)
            }
            .build()

        val result = client(uriFactory = uriFactory, restClient = restClient).lookup(request())

        assertEquals(DurLookupStatus.MATCHED, result.status)
        assertTrue(unchanged.get())
    }

    @Test
    fun `service key is masked if an error message contains a DUR URI`() {
        val message = "GET https://example.invalid/dur?serviceKey=dummy%2Fsegment%2Bvalue%3D&pageNo=1 failed"

        val masked = PublicDataLogSanitizer.mask(message)

        assertTrue(masked.contains("serviceKey=***"))
        assertFalse(masked.contains("dummy"))
    }

    private fun client(
        uriFactory: DurPublicDataUriFactory = uriFactory(),
        restClient: RestClient = RestClientConfig().durRestClient(properties),
    ): PublicDataDurIngredientApiClient {
        val parser = RawPublicDataResponseParser(ObjectMapper())
        return PublicDataDurIngredientApiClient(
            restClient = restClient,
            properties = properties,
            uriFactory = uriFactory,
            responseParser = parser,
            responseDecoder = PublicDataResponseDecoder(),
            responseValidator = PublicDataApiResponseValidator(),
            responseMapper = DurProviderResponseMapper(parser),
            callExecutor = PublicDataCallExecutor(properties.client),
        )
    }

    private fun uriFactory(): DurPublicDataUriFactory = DurPublicDataUriFactory(
        properties,
        ServiceKeyEncoder(PublicDataCredentialsProperties(DUMMY_ENCODED_KEY, true)),
    )

    private fun request() = DurLookupRequest("D000762", "이트라코나졸", DurLookupDirection.FORWARD)

    private fun properties(baseUrl: String) = DurApiProperties(
        baseUrl = baseUrl,
        pageSize = 2,
        maxPages = 5,
        maxRecords = 20,
        client = PublicDataClientPolicy(
            connectTimeout = Duration.ofSeconds(1),
            readTimeout = Duration.ofSeconds(1),
            maxRetries = 0,
            retryBackoff = Duration.ZERO,
            permitsPerSecond = 100,
            maxConcurrentCalls = 2,
            circuitFailureThreshold = 10,
        ),
    )

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .setBody(body)

    private fun page(totalCount: Int, pageNo: Int, pageSize: Int, items: String?): String {
        val itemsJson = when (items) {
            null -> ""
            "" -> ",\"items\":[]"
            else -> ",\"items\":[$items]"
        }
        return """{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"pageNo":$pageNo,"totalCount":$totalCount,"numOfRows":$pageSize$itemsJson}}"""
    }

    private fun item(
        relatedCode: String? = "D000027",
        relatedName: String = "심바스타틴",
        ingredientCode: String = "D000762",
        typeName: String = "병용금기",
    ): String {
        val relatedCodeJson = relatedCode?.let { "\"$it\"" } ?: "null"
        return """
            {"item":{
              "TYPE_NAME":"$typeName","MIX_TYPE":"단일","INGR_CODE":"$ingredientCode",
              "INGR_ENG_NAME":"Itraconazole","INGR_KOR_NAME":"이트라코나졸","MIX":"N","ORI":"원문",
              "CLASS":"분류","MIXTURE_MIX_TYPE":"단일","MIXTURE_INGR_CODE":$relatedCodeJson,
              "MIXTURE_INGR_ENG_NAME":"Simvastatin","MIXTURE_INGR_KOR_NAME":"$relatedName",
              "MIXTURE_MIX":"N","MIXTURE_ORI":"관계 원문","MIXTURE_CLASS":"관계 분류",
              "NOTIFICATION_DATE":"20200101","PROHBT_CONTENT":"병용투여하지 않는다.","REMARK":"비고","DEL_YN":"N"
            }}
        """.trimIndent()
    }

    companion object {
        private const val DUMMY_ENCODED_KEY = "dummy%2Fsegment%2Bvalue%3D"
    }
}
