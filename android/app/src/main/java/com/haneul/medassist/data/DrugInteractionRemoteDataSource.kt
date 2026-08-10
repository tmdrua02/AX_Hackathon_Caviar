package com.haneul.medassist.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrugInteractionRemoteDataSource @Inject constructor(
    private val api: SupplementApiService,
    private val json: Json,
) {
    suspend fun check(
        newMedicationProductCode: String,
        existingMedicationProductCodes: List<String>,
    ): Result<DrugInteractionBatchResponse> = try {
        Result.success(
            api.checkDrugInteractions(
                DrugInteractionBatchRequest(
                    newMedicationProductCode = newMedicationProductCode.trim(),
                    existingMedicationProductCodes = existingMedicationProductCodes.map(String::trim).distinct(),
                ),
            ),
        )
    } catch (error: HttpException) {
        val problem = error.response()?.errorBody()?.string()?.let(::decodeProblem)
        Result.failure(
            SupplementInteractionRequestException(
                failure = SupplementInteractionTransportFailure.HTTP,
                problemCode = problem?.code,
                httpStatus = error.code(),
                message = problem?.detail?.takeIf(String::isNotBlank) ?: "공식 약물 상호작용 서버가 요청을 처리하지 못했습니다.",
                cause = error,
            ),
        )
    } catch (error: SocketTimeoutException) {
        Result.failure(
            SupplementInteractionRequestException(
                failure = SupplementInteractionTransportFailure.TIMEOUT,
                message = "공식 약물 상호작용 조회 시간이 초과되었습니다.",
                cause = error,
            ),
        )
    } catch (error: SerializationException) {
        Result.failure(
            SupplementInteractionRequestException(
                failure = SupplementInteractionTransportFailure.MALFORMED_RESPONSE,
                message = "공식 약물 상호작용 응답을 확인할 수 없습니다.",
                cause = error,
            ),
        )
    } catch (error: IOException) {
        Result.failure(
            SupplementInteractionRequestException(
                failure = SupplementInteractionTransportFailure.NETWORK,
                message = "공식 약물 상호작용 서버에 연결할 수 없습니다.",
                cause = error,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: RuntimeException) {
        Result.failure(
            SupplementInteractionRequestException(
                failure = SupplementInteractionTransportFailure.MALFORMED_RESPONSE,
                message = "공식 약물 상호작용 응답을 처리할 수 없습니다.",
                cause = error,
            ),
        )
    }

    private fun decodeProblem(raw: String): ProblemDetailsDto? = runCatching {
        json.decodeFromString<ProblemDetailsDto>(raw)
    }.getOrNull()
}
