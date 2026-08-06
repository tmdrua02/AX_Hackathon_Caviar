package com.haneul.medassist.client.drug.overview

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.DrugOverviewApiProperties
import com.haneul.medassist.config.PublicDataClientPolicy
import com.haneul.medassist.config.PublicDataCredentialsProperties
import com.haneul.medassist.config.RestClientConfig
import com.haneul.medassist.domain.medication.DrugOverviewLookupStatus
import com.haneul.medassist.exception.ApiErrorCode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicDataDrugOverviewApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var properties: DrugOverviewApiProperties

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
    fun `itemSeq exact lookup maps every official overview field`() {
        server.enqueue(jsonResponse(page(1, 1, 2, item())))

        val result = client().findOverview(CODE, NAME, MANUFACTURER)

        assertEquals(DrugOverviewLookupStatus.RESOLVED, result.status)
        assertTrue(result.coverage.complete)
        val overview = requireNotNull(result.overview)
        assertEquals(CODE, overview.productCode)
        assertEquals(NAME, overview.productName)
        assertEquals("효능 원문", overview.efficacy?.raw)
        assertEquals("사용법 원문", overview.usageMethod?.raw)
        assertEquals("경고 원문", overview.warning?.raw)
        assertEquals("주의사항 원문", overview.precautions?.raw)
        assertEquals("상호작용 원문", overview.interactions?.raw)
        assertEquals("부작용 원문", overview.sideEffects?.raw)
        assertEquals("보관법 원문", overview.storageMethod?.raw)
        assertEquals("https://example.invalid/image.png", overview.imageUrl)
        assertEquals("20230531", overview.openDate)
        assertEquals("2026-06-29", overview.updateDate)
        assertEquals(CODE, overview.source.recordId)

        val request = server.takeRequest()
        assertEquals(CODE, request.requestUrl?.queryParameter("itemSeq"))
        assertNull(request.requestUrl?.queryParameter("itemName"))
        assertEquals("dummy/segment+value=", request.requestUrl?.queryParameter("ServiceKey"))
        assertNull(request.requestUrl?.queryParameter("serviceKey"))
    }

    @Test
    fun `itemName fallback selects only the exact product code from multiple results`() {
        server.enqueue(jsonResponse(page(0, 1, 2, null)))
        server.enqueue(
            jsonResponse(
                page(
                    2,
                    1,
                    2,
                    listOf(item(code = "OTHER", name = NAME), item()).joinToString(","),
                ),
            ),
        )

        val result = client().findOverview(CODE, NAME, MANUFACTURER)

        assertEquals(DrugOverviewLookupStatus.RESOLVED, result.status)
        assertEquals(CODE, result.overview?.productCode)
        server.takeRequest()
        val fallback = server.takeRequest()
        assertEquals(NAME, fallback.requestUrl?.queryParameter("itemName"))
        assertEquals(MANUFACTURER, fallback.requestUrl?.queryParameter("entpName"))
    }

    @Test
    fun `different itemSeq records are excluded and never attached`() {
        server.enqueue(jsonResponse(page(1, 1, 2, item(code = "OTHER"))))
        server.enqueue(jsonResponse(page(1, 1, 2, item(code = "OTHER"))))

        val result = client().findOverview(CODE, NAME, MANUFACTURER)

        assertEquals(DrugOverviewLookupStatus.NOT_FOUND, result.status)
        assertNull(result.overview)
        assertTrue(result.coverage.complete)
    }

    @Test
    fun `normal empty code and name responses are not found`() {
        server.enqueue(jsonResponse(page(0, 1, 2, null)))
        server.enqueue(jsonResponse(page(0, 1, 2, null)))

        val result = client().findOverview(CODE, "미제공 전문의약품", MANUFACTURER)

        assertEquals(DrugOverviewLookupStatus.NOT_FOUND, result.status)
        assertTrue(result.coverage.productResolved)
        assertFalse(result.coverage.overviewResolved)
        assertTrue(result.coverage.complete)
    }

    @Test
    fun `fallback pagination must finish before exact product is resolved`() {
        properties.pageSize = 1
        server.enqueue(jsonResponse(page(0, 1, 1, null)))
        server.enqueue(jsonResponse(page(2, 1, 1, item(code = "OTHER"))))
        server.enqueue(jsonResponse(page(2, 2, 1, item())))

        val result = client().findOverview(CODE, NAME, MANUFACTURER)

        assertEquals(DrugOverviewLookupStatus.RESOLVED, result.status)
        assertEquals(listOf(1, 2), result.completedPages)
        assertEquals("1", server.takeRequest().requestUrl?.queryParameter("pageNo"))
        assertEquals("1", server.takeRequest().requestUrl?.queryParameter("pageNo"))
        assertEquals("2", server.takeRequest().requestUrl?.queryParameter("pageNo"))
    }

    @Test
    fun `middle page failure is partial and not not-found`() {
        properties.pageSize = 1
        server.enqueue(jsonResponse(page(0, 1, 1, null)))
        server.enqueue(jsonResponse(page(2, 1, 1, item(code = "OTHER"))))
        server.enqueue(MockResponse().setResponseCode(503))

        val result = client().findOverview(CODE, NAME, MANUFACTURER)

        assertEquals(DrugOverviewLookupStatus.PARTIAL, result.status)
        assertFalse(result.coverage.complete)
        assertEquals(listOf(2), result.failedPages)
        assertEquals(ApiErrorCode.PUBLIC_API_UNAVAILABLE.name, result.errorCode)
    }

    @Test
    fun `HTML medical text keeps raw source and creates non-summarized display text`() {
        val html = "<p>첫 문장&amp;근거</p><p>둘째<br/>문장</p>"
        server.enqueue(jsonResponse(page(1, 1, 2, item(efficacy = html))))

        val result = client().findOverview(CODE, NAME, MANUFACTURER)

        assertEquals(html, result.overview?.efficacy?.raw)
        assertEquals("첫 문장&근거\n둘째\n문장", result.overview?.efficacy?.display)
    }

    @Test
    fun `provider resultCode error is failed`() {
        server.enqueue(
            jsonResponse(
                """{"header":{"resultCode":"99","resultMsg":"INVALID REQUEST"},"body":{"pageNo":1,"numOfRows":2,"totalCount":0}}""",
            ),
        )

        val result = client().findOverview(CODE, NAME, MANUFACTURER)

        assertEquals(DrugOverviewLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, result.errorCode)
    }

    @Test
    fun `HTTP authentication quota and provider failures stay failed`() {
        listOf(
            401 to ApiErrorCode.PUBLIC_API_AUTH_FAILED,
            403 to ApiErrorCode.PUBLIC_API_AUTH_FAILED,
            429 to ApiErrorCode.PUBLIC_API_QUOTA_EXCEEDED,
            502 to ApiErrorCode.PUBLIC_API_UNAVAILABLE,
            503 to ApiErrorCode.PUBLIC_API_UNAVAILABLE,
            504 to ApiErrorCode.PUBLIC_API_UNAVAILABLE,
        ).forEach { (status, expected) ->
            server.enqueue(MockResponse().setResponseCode(status))

            val result = client().findOverview(CODE, NAME, MANUFACTURER)

            assertEquals(DrugOverviewLookupStatus.FAILED, result.status)
            assertEquals(expected.name, result.errorCode)
        }
    }

    @Test
    fun `timeout is failed`() {
        properties.client.readTimeout = Duration.ofMillis(100)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val result = client().findOverview(CODE, NAME, MANUFACTURER)

        assertEquals(DrugOverviewLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_TIMEOUT.name, result.errorCode)
    }

    @Test
    fun `malformed JSON malformed UTF-8 and replacement character are rejected`() {
        server.enqueue(jsonResponse("{not-json"))
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, client().findOverview(CODE, NAME, MANUFACTURER).errorCode)

        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(Buffer().write(byteArrayOf('{'.code.toByte(), 0xC3.toByte(), 0x28, '}'.code.toByte()))),
        )
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, client().findOverview(CODE, NAME, MANUFACTURER).errorCode)

        server.enqueue(jsonResponse(page(1, 1, 2, item(efficacy = "깨진�효능"))))
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, client().findOverview(CODE, NAME, MANUFACTURER).errorCode)
    }

    @Test
    fun `missing service key fails lazily without sending request`() {
        val parser = RawPublicDataResponseParser(ObjectMapper())
        val client = PublicDataDrugOverviewApiClient(
            restClient = RestClientConfig().drugOverviewRestClient(properties),
            properties = properties,
            uriFactory = DrugOverviewUriFactory(properties, ServiceKeyEncoder(PublicDataCredentialsProperties())),
            responseParser = parser,
            responseDecoder = PublicDataResponseDecoder(),
            responseValidator = PublicDataApiResponseValidator(),
            responseMapper = DrugOverviewResponseMapper(parser),
            callExecutor = PublicDataCallExecutor(properties.client),
        )

        val result = client.findOverview(CODE, NAME, MANUFACTURER)

        assertEquals(ApiErrorCode.PUBLIC_API_NOT_CONFIGURED.name, result.errorCode)
        assertEquals(0, server.requestCount)
    }

    private fun client(): PublicDataDrugOverviewApiClient {
        val parser = RawPublicDataResponseParser(ObjectMapper())
        return PublicDataDrugOverviewApiClient(
            restClient = RestClientConfig().drugOverviewRestClient(properties),
            properties = properties,
            uriFactory = DrugOverviewUriFactory(
                properties,
                ServiceKeyEncoder(PublicDataCredentialsProperties(DUMMY_KEY, true)),
            ),
            responseParser = parser,
            responseDecoder = PublicDataResponseDecoder(),
            responseValidator = PublicDataApiResponseValidator(),
            responseMapper = DrugOverviewResponseMapper(parser),
            callExecutor = PublicDataCallExecutor(properties.client),
        )
    }

    private fun properties(baseUrl: String) = DrugOverviewApiProperties(
        baseUrl = baseUrl,
        pageSize = 2,
        maxPages = 10,
        maxRecords = 100,
        client = PublicDataClientPolicy(
            connectTimeout = Duration.ofSeconds(1),
            readTimeout = Duration.ofSeconds(1),
            maxRetries = 0,
            retryBackoff = Duration.ZERO,
            permitsPerSecond = 100,
            maxConcurrentCalls = 2,
            circuitFailureThreshold = 20,
        ),
    )

    private fun jsonResponse(body: String) = MockResponse()
        .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .setBody(body)

    private fun page(totalCount: Int, pageNo: Int, pageSize: Int, items: String?): String {
        val itemsJson = items?.let { ",\"items\":[$it]" }.orEmpty()
        return """{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"pageNo":$pageNo,"totalCount":$totalCount,"numOfRows":$pageSize$itemsJson}}"""
    }

    private fun item(
        code: String = CODE,
        name: String = NAME,
        manufacturer: String = MANUFACTURER,
        efficacy: String = "효능 원문",
    ) = """
        {"entpName":"$manufacturer","itemName":"$name","itemSeq":"$code",
         "efcyQesitm":"$efficacy","useMethodQesitm":"사용법 원문","atpnWarnQesitm":"경고 원문",
         "atpnQesitm":"주의사항 원문","intrcQesitm":"상호작용 원문","seQesitm":"부작용 원문",
         "depositMethodQesitm":"보관법 원문","openDe":"20230531","updateDe":"2026-06-29",
         "itemImage":"https://example.invalid/image.png"}
    """.trimIndent()

    companion object {
        private const val CODE = "202106092"
        private const val NAME = "타이레놀정500밀리그람(아세트아미노펜)"
        private const val MANUFACTURER = "켄뷰코리아판매유한회사"
        private const val DUMMY_KEY = "dummy%2Fsegment%2Bvalue%3D"
    }
}
