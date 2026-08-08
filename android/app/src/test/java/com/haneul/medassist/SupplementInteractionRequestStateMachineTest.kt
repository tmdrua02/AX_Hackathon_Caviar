package com.haneul.medassist

import com.haneul.medassist.data.LoadState
import com.haneul.medassist.data.SupplementInteractionSeverity
import com.haneul.medassist.data.supplementInteractionResponseJson
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplementInteractionRequestStateMachineTest {
    private val response = Json { ignoreUnknownKeys = true }
        .decodeFromString<com.haneul.medassist.data.SupplementInteractionCheckResponse>(
            supplementInteractionResponseJson(),
        )

    @Test
    fun loadingTransitionsToSuccessEvenWhenSeverityIsUnknown() {
        val machine = SupplementInteractionRequestStateMachine()
        val token = machine.begin(key())!!

        assertTrue(machine.state is LoadState.Loading)
        assertTrue(machine.succeed(token, response))
        assertTrue(machine.state is LoadState.Content)
        assertEquals(SupplementInteractionSeverity.UNKNOWN, (machine.state as LoadState.Content).value.severityValue)
    }

    @Test
    fun loadingTransitionsToErrorOnlyForTransportFailure() {
        val machine = SupplementInteractionRequestStateMachine()
        val token = machine.begin(key())!!

        assertTrue(machine.fail(token, "network error"))
        assertTrue(machine.state is LoadState.Error)
    }

    @Test
    fun duplicateRequestIsRejectedWhileLoading() {
        val machine = SupplementInteractionRequestStateMachine()

        assertTrue(machine.begin(key()) != null)
        assertNull(machine.begin(key()))
    }

    @Test
    fun staleResponseCannotOverwriteNewSelection() {
        val machine = SupplementInteractionRequestStateMachine()
        val oldToken = machine.begin(key())!!
        machine.reset()
        val newToken = machine.begin(key().copy(supplementStatementNo = "TEST_STTEMNT_NO_2"))!!

        assertFalse(machine.succeed(oldToken, response))
        assertTrue(machine.state is LoadState.Loading)
        assertTrue(machine.succeed(newToken, response))
    }

    private fun key() = SupplementInteractionRequestKey("TEST_ITEM_SEQ", "TEST_STTEMNT_NO")
}
