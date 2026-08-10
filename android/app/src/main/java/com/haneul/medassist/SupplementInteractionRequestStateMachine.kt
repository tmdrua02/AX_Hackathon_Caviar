package com.haneul.medassist

import com.haneul.medassist.data.LoadState
import com.haneul.medassist.data.SupplementInteractionCheckResponse

internal data class SupplementInteractionRequestKey(
    val medicationProductCode: String,
    val supplementStatementNo: String,
)

internal class SupplementInteractionRequestStateMachine {
    private var generation: Long = 0
    private var activeGeneration: Long? = null

    var state: LoadState<SupplementInteractionCheckResponse> = LoadState.Idle
        private set

    fun begin(key: SupplementInteractionRequestKey): Long? {
        if (state is LoadState.Loading) return null
        require(key.medicationProductCode.isNotBlank() && key.supplementStatementNo.isNotBlank()) {
            "official product identifiers are required"
        }
        generation += 1
        activeGeneration = generation
        state = LoadState.Loading
        return generation
    }

    fun succeed(token: Long, response: SupplementInteractionCheckResponse): Boolean {
        if (activeGeneration != token) return false
        state = LoadState.Content(response)
        activeGeneration = null
        return true
    }

    fun fail(token: Long, message: String): Boolean {
        if (activeGeneration != token) return false
        state = LoadState.Error(message)
        activeGeneration = null
        return true
    }

    fun reset() {
        generation += 1
        activeGeneration = null
        state = LoadState.Idle
    }
}
