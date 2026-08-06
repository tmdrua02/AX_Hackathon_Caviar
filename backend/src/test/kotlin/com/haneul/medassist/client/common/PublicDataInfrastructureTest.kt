package com.haneul.medassist.client.common

import com.haneul.medassist.config.PublicDataClientPolicy
import com.haneul.medassist.config.PublicDataCredentialsProperties
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class PublicDataInfrastructureTest {
    @Test
    fun `credentials never expose service key through toString`() {
        val credentials = PublicDataCredentialsProperties("top-secret", true)

        assertFalse(credentials.toString().contains("top-secret"))
    }

    @Test
    fun `missing common key fails only when request key is needed`() {
        val encoder = ServiceKeyEncoder(PublicDataCredentialsProperties())

        val exception = try {
            encoder.encodedQueryValue()
            fail("expected missing configuration")
        } catch (exception: PublicDataApiException) {
            exception
        }
        assertEquals(ApiErrorCode.PUBLIC_API_NOT_CONFIGURED, exception.errorCode)
    }

    @Test
    fun `encoded key is decoded then encoded exactly once without converting plus to space`() {
        val encoded = ServiceKeyEncoder(
            PublicDataCredentialsProperties("abc%2Fdef+ghi%3D", true),
        ).encodedQueryValue()

        assertEquals("abc%2Fdef%2Bghi%3D", encoded)
        assertFalse(encoded.contains("%252F", ignoreCase = true))
        assertFalse(encoded.contains("%20"))
    }

    @Test
    fun `service key query parameter is masked`() {
        val message = "GET https://apis.data.go.kr/path?serviceKey=abc%2Fdef%2Bghi%3D&pageNo=1 failed"

        val masked = PublicDataLogSanitizer.mask(message)

        assertEquals("GET https://apis.data.go.kr/path?serviceKey=***&pageNo=1 failed", masked)
        assertFalse(masked.contains("abc"))

        val safeCause = PublicDataLogSanitizer.sanitizedCause(IllegalStateException(message))
        assertFalse(safeCause.message.orEmpty().contains("abc"))
        assertEquals(null, safeCause.cause)
    }

    @Test
    fun `circuit breaker state is independent between executor instances`() {
        val policy = policy(circuitFailureThreshold = 1)
        val first = PublicDataCallExecutor(policy)
        val second = PublicDataCallExecutor(policy)
        try {
            first.execute<Unit> {
                throw PublicDataApiException(ApiErrorCode.PUBLIC_API_UNAVAILABLE, retryable = true)
            }
        } catch (_: PublicDataApiException) {
        }

        assertEquals("available", second.execute { "available" })
        val exception = try {
            first.execute { "unreachable" }
            fail("expected open circuit")
        } catch (exception: PublicDataApiException) {
            exception
        }
        assertEquals(ApiErrorCode.PUBLIC_API_CIRCUIT_OPEN, exception.errorCode)
    }

    @Test
    fun `bulkhead semaphore is independent between executor instances`() {
        val policy = policy(maxConcurrentCalls = 1, readTimeout = Duration.ofMillis(100))
        val first = PublicDataCallExecutor(policy)
        val second = PublicDataCallExecutor(policy)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        val held = pool.submit {
            first.execute {
                entered.countDown()
                release.await(1, TimeUnit.SECONDS)
            }
        }
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        try {
            assertEquals("independent", second.execute { "independent" })
        } finally {
            release.countDown()
            held.get(1, TimeUnit.SECONDS)
            pool.shutdownNow()
        }
    }

    private fun policy(
        circuitFailureThreshold: Int = 5,
        maxConcurrentCalls: Int = 8,
        readTimeout: Duration = Duration.ofSeconds(1),
    ) = PublicDataClientPolicy(
        readTimeout = readTimeout,
        maxRetries = 0,
        permitsPerSecond = 100,
        maxConcurrentCalls = maxConcurrentCalls,
        circuitFailureThreshold = circuitFailureThreshold,
        circuitOpenDuration = Duration.ofSeconds(5),
    )
}
