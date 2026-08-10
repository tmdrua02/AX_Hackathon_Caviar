package com.haneul.medassist.client.common

import com.haneul.medassist.config.PublicDataClientPolicy
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class PublicDataCallExecutor(
    private val policy: PublicDataClientPolicy,
) {
    private val bulkhead = Semaphore(policy.maxConcurrentCalls.coerceAtLeast(1))
    private val consecutiveFailures = AtomicInteger(0)
    private val circuitOpenUntilNanos = AtomicLong(0)
    private val rateLock = Any()
    private var rateWindowStartNanos: Long = System.nanoTime()
    private var callsInWindow: Int = 0

    fun <T> execute(call: () -> T): T {
        rejectIfCircuitOpen()
        val acquired = bulkhead.tryAcquire(policy.readTimeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!acquired) {
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_UNAVAILABLE,
                "공공 API 동시 호출 제한을 초과했습니다.",
                retryable = true,
            )
        }

        try {
            awaitRatePermit()
            var last: PublicDataApiException? = null
            val maxAttempts = policy.maxRetries.coerceAtLeast(0) + 1
            repeat(maxAttempts) { attempt ->
                try {
                    val result = call()
                    consecutiveFailures.set(0)
                    return result
                } catch (exception: PublicDataApiException) {
                    last = exception
                    if (!exception.retryable || attempt == maxAttempts - 1) {
                        recordFailure(exception)
                        throw exception
                    }
                    sleep(backoffFor(attempt))
                }
            }
            throw checkNotNull(last)
        } finally {
            bulkhead.release()
        }
    }

    private fun rejectIfCircuitOpen() {
        val remaining = circuitOpenUntilNanos.get() - System.nanoTime()
        if (remaining > 0) {
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_CIRCUIT_OPEN,
                retryAfterSeconds = Duration.ofNanos(remaining).seconds.coerceAtLeast(1),
            )
        }
    }

    private fun recordFailure(exception: PublicDataApiException) {
        if (!exception.retryable) return
        if (consecutiveFailures.incrementAndGet() >= policy.circuitFailureThreshold.coerceAtLeast(1)) {
            circuitOpenUntilNanos.set(System.nanoTime() + policy.circuitOpenDuration.toNanos())
            consecutiveFailures.set(0)
        }
    }

    private fun awaitRatePermit() {
        synchronized(rateLock) {
            val oneSecond = Duration.ofSeconds(1).toNanos()
            var now = System.nanoTime()
            if (now - rateWindowStartNanos >= oneSecond) {
                rateWindowStartNanos = now
                callsInWindow = 0
            }
            if (callsInWindow >= policy.permitsPerSecond.coerceAtLeast(1)) {
                val waitNanos = oneSecond - (now - rateWindowStartNanos)
                if (waitNanos > 0) sleep(Duration.ofNanos(waitNanos))
                now = System.nanoTime()
                rateWindowStartNanos = now
                callsInWindow = 0
            }
            callsInWindow++
        }
    }

    private fun backoffFor(attempt: Int): Duration =
        policy.retryBackoff.multipliedBy(1L shl attempt.coerceAtMost(10))

    private fun sleep(duration: Duration) {
        try {
            TimeUnit.NANOSECONDS.sleep(duration.toNanos())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_UNAVAILABLE,
                "공공 API 호출 대기가 중단되었습니다.",
                exception,
            )
        }
    }
}
