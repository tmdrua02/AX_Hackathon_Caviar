package com.haneul.medassist.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class SupplementInteractionRemoteDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var json: Json

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun successfulUnknownResponseIsNotTransportFailure() = runBlocking {
        server.enqueue(jsonResponse(supplementInteractionResponseJson()))

        val result = dataSource().check("TEST_ITEM_SEQ", "TEST_STTEMNT_NO")

        assertTrue(result.isSuccess)
        assertEquals(SupplementInteractionSeverity.UNKNOWN, result.getOrThrow().severityValue)
        val recorded = server.takeRequest()
        assertEquals("/api/v1/supplement-interaction-checks", recorded.path)
        val requestBody = recorded.body.readUtf8()
        assertTrue(requestBody.contains("\"medicationProductCode\":\"TEST_ITEM_SEQ\""))
        assertTrue(requestBody.contains("\"supplementStatementNo\":\"TEST_STTEMNT_NO\""))
    }

    @Test
    fun supplementSearchPreservesOfficialStatementNumber() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"query":"TEST_SUPPLEMENT","normalizedQuery":"testsupplement","status":"RESOLVED","sourceType":"PROVIDER","complete":true,"candidates":[{"sttemntNo":"TEST_STTEMNT_NO","productName":"TEST_SUPPLEMENT","manufacturer":"TEST_MANUFACTURER","matchScore":100,"matchType":"EXACT","source":{"name":"TEST_SOURCE","recordId":"TEST_STTEMNT_NO","retrievedAt":"2026-08-08T00:00:00Z","providerReference":"TEST_REFERENCE"}}]}""",
            ),
        )

        val result = dataSource().searchSupplements("TEST_SUPPLEMENT")

        assertEquals("TEST_STTEMNT_NO", result.getOrThrow().candidates.single().sttemntNo)
        assertEquals("/api/v1/supplement-products/search", server.takeRequest().path)
    }

    @Test
    fun problemDetailsRemainHttpFailure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(
                    """{"type":"test","title":"Unavailable","status":503,"detail":"provider unavailable","instance":"/test","code":"PUBLIC_API_UNAVAILABLE","timestamp":"2026-08-08T00:00:00Z"}""",
                ),
        )

        val error = dataSource().check("TEST_ITEM_SEQ", "TEST_STTEMNT_NO").exceptionOrNull()
            as SupplementInteractionRequestException

        assertEquals(SupplementInteractionTransportFailure.HTTP, error.failure)
        assertEquals("PUBLIC_API_UNAVAILABLE", error.problemCode)
        assertEquals(503, error.httpStatus)
    }

    @Test
    fun timeoutIsDistinctFromHttpFailure() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val error = dataSource(readTimeoutMillis = 100)
            .check("TEST_ITEM_SEQ", "TEST_STTEMNT_NO")
            .exceptionOrNull() as SupplementInteractionRequestException

        assertEquals(SupplementInteractionTransportFailure.TIMEOUT, error.failure)
    }

    @Test
    fun malformedResponseIsDistinctFailure() = runBlocking {
        server.enqueue(jsonResponse("{not-json"))

        val error = dataSource().check("TEST_ITEM_SEQ", "TEST_STTEMNT_NO").exceptionOrNull()
            as SupplementInteractionRequestException

        assertEquals(SupplementInteractionTransportFailure.MALFORMED_RESPONSE, error.failure)
    }

    @Test
    fun disconnectedNetworkIsDistinctFailure() = runBlocking {
        server.shutdown()

        val error = dataSource(readTimeoutMillis = 100)
            .check("TEST_ITEM_SEQ", "TEST_STTEMNT_NO")
            .exceptionOrNull() as SupplementInteractionRequestException

        assertEquals(SupplementInteractionTransportFailure.NETWORK, error.failure)
    }

    private fun dataSource(readTimeoutMillis: Long = 2_000): SupplementInteractionRemoteDataSource {
        val client = OkHttpClient.Builder()
            .connectTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SupplementApiService::class.java)
        return SupplementInteractionRemoteDataSource(api, json)
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
