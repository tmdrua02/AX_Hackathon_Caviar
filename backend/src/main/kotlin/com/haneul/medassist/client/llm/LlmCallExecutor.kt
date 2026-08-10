package com.haneul.medassist.client.llm

import com.haneul.medassist.config.OpenAiExplanationProperties
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class LlmFailureCode {
    NOT_CONFIGURED,
    AUTH_FAILED,
    RATE_LIMITED,
    TIMEOUT,
    UNAVAILABLE,
    INVALID_RESPONSE,
    EMPTY_RESPONSE,
    CIRCUIT_OPEN,
}

class LlmProviderException(
    val failureCode: LlmFailureCode,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : RuntimeException(failureCode.name, cause)

class LlmCallExecutor(
    private val properties: OpenAiExplanationProperties,
) {
    private val bulkhead = Semaphore(properties.maxConcurrentCalls.coerceAtLeast(1))
    private val consecutiveFailures = AtomicInteger(0)
    private val circuitOpenUntilNanos = AtomicLong(0)

    fun <T> execute(call: () -> T): T {
        if (circuitOpenUntilNanos.get() > System.nanoTime()) {
            throw LlmProviderException(LlmFailureCode.CIRCUIT_OPEN, retryable = true)
        }
        if (!bulkhead.tryAcquire(properties.readTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw LlmProviderException(LlmFailureCode.UNAVAILABLE, retryable = true)
        }
        try {
            var last: LlmProviderException? = null
            repeat(properties.maxRetries.coerceAtLeast(0) + 1) { attempt ->
                try {
                    return call().also { consecutiveFailures.set(0) }
                } catch (exception: LlmProviderException) {
                    last = exception
                    if (!exception.retryable || attempt == properties.maxRetries.coerceAtLeast(0)) {
                        recordFailure(exception)
                        throw exception
                    }
                    sleep(properties.retryBackoff.multipliedBy(1L shl attempt.coerceAtMost(10)))
                }
            }
            throw checkNotNull(last)
        } finally {
            bulkhead.release()
        }
    }

    private fun recordFailure(exception: LlmProviderException) {
        if (!exception.retryable) return
        if (consecutiveFailures.incrementAndGet() >= properties.circuitFailureThreshold.coerceAtLeast(1)) {
            circuitOpenUntilNanos.set(System.nanoTime() + properties.circuitOpenDuration.toNanos())
            consecutiveFailures.set(0)
        }
    }

    private fun sleep(duration: java.time.Duration) {
        try {
            TimeUnit.NANOSECONDS.sleep(duration.toNanos())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw LlmProviderException(LlmFailureCode.UNAVAILABLE, cause = exception)
        }
    }
}
