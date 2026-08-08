package com.haneul.medassist.service

import com.haneul.medassist.client.dur.DurIngredientApiClient
import com.haneul.medassist.client.dur.DurLookupDirection
import com.haneul.medassist.client.dur.DurLookupRequest
import com.haneul.medassist.client.dur.DurLookupResult
import com.haneul.medassist.client.dur.DurLookupStatus
import com.haneul.medassist.exception.ApiErrorCode
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DurLookupServiceTest {
    @Test
    fun `blank Korean name is rejected before unverified code-only lookup`() {
        val called = AtomicBoolean(false)
        val service = DurLookupService(
            object : DurIngredientApiClient {
                override fun lookup(request: DurLookupRequest): DurLookupResult {
                    called.set(true)
                    return success()
                }
            },
        )

        val result = service.lookup(DurLookupRequest("D000762", null, DurLookupDirection.FORWARD))

        assertEquals(DurLookupStatus.FAILED, result.status)
        assertEquals(ApiErrorCode.PUBLIC_API_MAPPING_UNVERIFIED.name, result.errorCode)
        assertFalse(called.get())
    }

    @Test
    fun `verified code and name lookup is delegated`() {
        val service = DurLookupService(
            object : DurIngredientApiClient {
                override fun lookup(request: DurLookupRequest): DurLookupResult = success()
            },
        )

        val result = service.lookup(DurLookupRequest("D000762", "이트라코나졸", DurLookupDirection.FORWARD))

        assertTrue(result.complete)
        assertEquals(DurLookupStatus.NO_MATCH, result.status)
    }

    private fun success() = DurLookupResult(
        status = DurLookupStatus.NO_MATCH,
        records = emptyList(),
        totalCount = 0,
        completedPages = listOf(1),
        failedPages = emptyList(),
        complete = true,
        retrievedAt = Instant.EPOCH,
        providerResultCode = "00",
        providerResultMessage = "NORMAL SERVICE.",
    )
}
