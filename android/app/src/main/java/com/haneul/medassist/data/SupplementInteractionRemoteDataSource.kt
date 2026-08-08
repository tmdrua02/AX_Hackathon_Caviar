package com.haneul.medassist.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

enum class SupplementInteractionTransportFailure {
    HTTP,
    TIMEOUT,
    NETWORK,
    MALFORMED_RESPONSE,
}

class SupplementInteractionRequestException(
    val failure: SupplementInteractionTransportFailure,
    val problemCode: String? = null,
    val httpStatus: Int? = null,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

@Singleton
class SupplementInteractionRemoteDataSource @Inject constructor(
    private val api: ApiService,
    private val json: Json,
) {
    suspend fun check(
        medicationProductCode: String,
        supplementStatementNo: String,
    ): Result<SupplementInteractionCheckResponse> = remoteCall {
        api.checkSupplementInteraction(
            SupplementInteractionCheckRequest(
                medicationProductCode = medicationProductCode,
                supplementStatementNo = supplementStatementNo,
            ),
        )
    }

    suspend fun searchSupplements(query: String): Result<SupplementProductSearchResponse> = remoteCall {
        api.searchSupplementProducts(SupplementProductSearchRequest(query.trim()))
    }

    private suspend fun <T> remoteCall(call: suspend () -> T): Result<T> = try {
        Result.success(call())
    } catch (error: HttpException) {
        val problem = error.response()?.errorBody()?.string()?.let(::decodeProblem)
        Result.failure(
            SupplementInteractionRequestException(
                failure = SupplementInteractionTransportFailure.HTTP,
                problemCode = problem?.code,
                httpStatus = error.code(),
                message = problem?.detail?.takeIf(String::isNotBlank) ?: "서버가 요청을 처리하지 못했습니다.",
                cause = error,
            ),
        )
    } catch (error: SocketTimeoutException) {
        Result.failure(
            SupplementInteractionRequestException(
                failure = SupplementInteractionTransportFailure.TIMEOUT,
                message = "서버 응답 시간이 초과되었습니다.",
                cause = error,
            ),
        )
    } catch (error: SerializationException) {
        Result.failure(
            SupplementInteractionRequestException(
                failure = SupplementInteractionTransportFailure.MALFORMED_RESPONSE,
                message = "서버 응답을 확인할 수 없습니다.",
                cause = error,
            ),
        )
    } catch (error: IOException) {
        Result.failure(
            SupplementInteractionRequestException(
                failure = SupplementInteractionTransportFailure.NETWORK,
                message = "서버에 연결할 수 없습니다.",
                cause = error,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: RuntimeException) {
        Result.failure(
            SupplementInteractionRequestException(
                failure = SupplementInteractionTransportFailure.MALFORMED_RESPONSE,
                message = "서버 응답을 확인할 수 없습니다.",
                cause = error,
            ),
        )
    }

    private fun decodeProblem(raw: String): ProblemDetailsDto? = runCatching {
        json.decodeFromString<ProblemDetailsDto>(raw)
    }.getOrNull()
}
