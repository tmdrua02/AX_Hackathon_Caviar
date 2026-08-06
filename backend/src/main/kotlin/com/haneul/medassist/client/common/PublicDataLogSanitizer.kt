package com.haneul.medassist.client.common

object PublicDataLogSanitizer {
    private val serviceKeyPattern = Regex("(?i)([?&]serviceKey=)[^&#\\s]*")

    fun mask(message: String): String = message.replace(serviceKeyPattern, "\$1***")

    fun sanitizedCause(cause: Throwable): RuntimeException = RuntimeException(
        "Public data API request failed (${cause.javaClass.simpleName})",
    )
}
