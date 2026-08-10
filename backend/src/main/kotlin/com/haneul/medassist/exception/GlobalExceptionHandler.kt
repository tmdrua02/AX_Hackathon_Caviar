package com.haneul.medassist.exception

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(MedAssistException::class)
    fun handleMedAssistException(
        exception: MedAssistException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = problem(
            exception.errorCode,
            exception.errorCode.title,
            request,
        )
        val headers = HttpHeaders()
        exception.retryAfterSeconds?.let { headers.set(HttpHeaders.RETRY_AFTER, it.toString()) }
        return ResponseEntity(problem, headers, exception.errorCode.status)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val detail = exception.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage ?: "invalid"}" }
            .ifBlank { ApiErrorCode.VALIDATION_FAILED.title }
        val problem = problem(ApiErrorCode.VALIDATION_FAILED, detail, request)
        return ResponseEntity.badRequest().body(problem)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(
        @Suppress("UNUSED_PARAMETER") exception: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = problem(
            ApiErrorCode.VALIDATION_FAILED,
            "요청 본문을 읽을 수 없습니다.",
            request,
        )
        return ResponseEntity.badRequest().body(problem)
    }

    private fun problem(
        code: ApiErrorCode,
        detail: String,
        request: HttpServletRequest,
    ): ProblemDetail = ProblemDetail.forStatusAndDetail(code.status, detail).apply {
        title = code.title
        type = URI.create("https://medassist.local/problems/${code.name.lowercase().replace('_', '-')}")
        instance = URI.create(request.requestURI)
        setProperty("code", code.name)
        setProperty("timestamp", Instant.now().toString())
    }
}
