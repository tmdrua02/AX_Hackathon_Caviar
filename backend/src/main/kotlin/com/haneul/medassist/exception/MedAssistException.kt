package com.haneul.medassist.exception

open class MedAssistException(
    val errorCode: ApiErrorCode,
    message: String = errorCode.title,
    cause: Throwable? = null,
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message, cause)

class PublicDataApiException(
    errorCode: ApiErrorCode,
    message: String = errorCode.title,
    cause: Throwable? = null,
    retryAfterSeconds: Long? = null,
    val retryable: Boolean = false,
) : MedAssistException(errorCode, message, cause, retryAfterSeconds)
