package com.haneul.medassist.client.supplement

import com.haneul.medassist.client.common.PublicDataApiResponseValidator
import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.HealthFunctionalFoodApiProperties
import com.haneul.medassist.config.PublicDataClientPolicy
import com.haneul.medassist.config.PublicDataCredentialsProperties
import com.haneul.medassist.config.RestClientConfig
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodLookupStatus
import com.haneul.medassist.domain.supplement.SupplementSearchMatchType
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.service.SupplementNameNormalizer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicDataHealthFunctionalFoodApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var properties: HealthFunctionalFoodApiProperties

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
    fun `search resolves exact prefix and contains matches across all pages`() {
        properties.pageSize = 2
        server.enqueue(jsonResponse(page(3, 1, 2, item("S-1", "루테인"), item("S-2", "루테인지아잔틴"))))
        server.enqueue(jsonResponse(page(3, 2, 2, item("S-3", "프리미엄루테인"))))

        val result = client().search("루테인")

        assertEquals(HealthFunctionalFoodLookupStatus.RESOLVED, result.status)
        assertTrue(result.complete)
        assertEquals(listOf(1, 2), result.completedPages)
        assertEquals(
            listOf(SupplementSearchMatchType.EXACT, SupplementSearchMatchType.PREFIX, SupplementSearchMatchType.CONTAINS),
            result.candidates.map { it.matchType },
        )
        assertEquals(listOf("S-1", "S-2", "S-3"), result.candidates.map { it.candidate.sttemntNo })
    }

    @Test
    fun `detail maps only confirmed fields and preserves raw provider record`() {
        server.enqueue(jsonResponse(page(1, 1, 2, detailItem())))

        val result = client().findByStatementNo("S-1")

        assertEquals(HealthFunctionalFoodLookupStatus.RESOLVED, result.status)
        val snapshot = requireNotNull(result.snapshot)
        assertEquals("공식제품", snapshot.productName)
        assertEquals("공식업체", snapshot.manufacturer)
        assertEquals("20260101", snapshot.registerDate)
        assertEquals("소비기한", snapshot.distributionPeriod)
        assertEquals("성상", snapshot.appearance)
        assertEquals("용도용법", snapshot.usage)
        assertEquals("보관기준", snapshot.storage)
        assertEquals("섭취주의", snapshot.intakeHint)
        assertEquals("주된기능성", snapshot.mainFunction)
        assertEquals("기준규격", snapshot.baseStandard)
        assertEquals("주된기능성", snapshot.rawProviderRecord["MAIN_FNCTN"])
        assertTrue(snapshot.coverage.complete)
    }

    @Test
    fun `normal empty list and detail responses are not found`() {
        server.enqueue(jsonResponse(page(0, 1, 2)))
        assertEquals(HealthFunctionalFoodLookupStatus.NOT_FOUND, client().search("없는제품").status)

        server.enqueue(jsonResponse(page(0, 1, 2)))
        val detail = client().findByStatementNo("NO-SUCH")
        assertEquals(HealthFunctionalFoodLookupStatus.NOT_FOUND, detail.status)
        assertNull(detail.snapshot)
        assertTrue(detail.complete)
    }

    @Test
    fun `middle page failure is partial and never not found`() {
        properties.pageSize = 1
        server.enqueue(jsonResponse(page(2, 1, 1, item("S-1", "루테인"))))
        server.enqueue(MockResponse().setResponseCode(503))

        val result = client().search("루테인")

        assertEquals(HealthFunctionalFoodLookupStatus.PARTIAL, result.status)
        assertFalse(result.complete)
        assertEquals(listOf(2), result.failedPages)
        assertEquals(ApiErrorCode.PUBLIC_API_UNAVAILABLE.name, result.errorCode)
    }

    @Test
    fun `provider result code error is failed`() {
        server.enqueue(
            jsonResponse(
                """{"header":{"resultCode":"99","resultMsg":"INVALID REQUEST"},"body":{"pageNo":1,"numOfRows":2,"totalCount":0}}""",
            ),
        )

        val result = client().search("루테인")

        assertEquals(HealthFunctionalFoodLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, result.errorCode)
    }

    @Test
    fun `HTTP authentication quota and unavailable statuses remain failed`() {
        listOf(
            401 to ApiErrorCode.PUBLIC_API_AUTH_FAILED,
            403 to ApiErrorCode.PUBLIC_API_AUTH_FAILED,
            429 to ApiErrorCode.PUBLIC_API_QUOTA_EXCEEDED,
            502 to ApiErrorCode.PUBLIC_API_UNAVAILABLE,
            503 to ApiErrorCode.PUBLIC_API_UNAVAILABLE,
            504 to ApiErrorCode.PUBLIC_API_UNAVAILABLE,
        ).forEach { (status, expected) ->
            server.enqueue(MockResponse().setResponseCode(status))

            val result = client().search("루테인")

            assertEquals(HealthFunctionalFoodLookupStatus.FAILED, result.status)
            assertEquals(expected.name, result.errorCode)
        }
    }

    @Test
    fun `timeout remains failed`() {
        properties.client.readTimeout = Duration.ofMillis(100)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val result = client().search("루테인")

        assertEquals(HealthFunctionalFoodLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_TIMEOUT.name, result.errorCode)
    }

    @Test
    fun `malformed JSON malformed UTF-8 and replacement character are rejected`() {
        server.enqueue(jsonResponse("{not-json"))
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, client().search("루테인").errorCode)

        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(Buffer().write(byteArrayOf('{'.code.toByte(), 0xC3.toByte(), 0x28, '}'.code.toByte()))),
        )
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, client().search("루테인").errorCode)

        server.enqueue(jsonResponse(page(1, 1, 2, item("S-1", "깨진�제품"))))
        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE.name, client().search("루테인").errorCode)
    }

    @Test
    fun `response statement mismatch fails detail`() {
        server.enqueue(jsonResponse(page(1, 1, 2, detailItem(statementNo = "OTHER"))))

        val result = client().findByStatementNo("S-1")

        assertEquals(HealthFunctionalFoodLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_RESPONSE_MISMATCH.name, result.errorCode)
    }

    private fun client(): PublicDataHealthFunctionalFoodApiClient {
        val parser = RawPublicDataResponseParser(ObjectMapper())
        return PublicDataHealthFunctionalFoodApiClient(
            restClient = RestClientConfig().healthFunctionalFoodRestClient(properties),
            properties = properties,
            uriFactory = HealthFunctionalFoodUriFactory(
                properties,
                ServiceKeyEncoder(PublicDataCredentialsProperties("dummy%2Fsegment%2Bvalue%3D", true)),
            ),
            responseParser = parser,
            responseDecoder = PublicDataResponseDecoder(),
            responseValidator = PublicDataApiResponseValidator(),
            responseMapper = HealthFunctionalFoodProviderMapper(parser),
            normalizer = SupplementNameNormalizer(),
            callExecutor = PublicDataCallExecutor(properties.client),
        )
    }

    private fun properties(baseUrl: String) = HealthFunctionalFoodApiProperties(
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
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun page(
        totalCount: Int,
        pageNo: Int,
        pageSize: Int,
        vararg items: String,
    ): String {
        val itemsJson = if (items.isEmpty()) "" else ",\"items\":[${items.joinToString(",")}]"
        return """{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"pageNo":$pageNo,"totalCount":$totalCount,"numOfRows":$pageSize$itemsJson}}"""
    }

    private fun item(statementNo: String, productName: String) =
        """{"item":{"ENTRPS":"공식업체","PRDUCT":"$productName","STTEMNT_NO":"$statementNo","REGIST_DT":"20260101"}}"""

    private fun detailItem(statementNo: String = "S-1") =
        """{"item":{"ENTRPS":"공식업체","PRDUCT":"공식제품","STTEMNT_NO":"$statementNo","REGIST_DT":"20260101","DISTB_PD":"소비기한","SUNGSANG":"성상","SRV_USE":"용도용법","PRSRV_PD":"보관기준","INTAKE_HINT1":"섭취주의","MAIN_FNCTN":"주된기능성","BASE_STANDARD":"기준규격"}}"""
}
