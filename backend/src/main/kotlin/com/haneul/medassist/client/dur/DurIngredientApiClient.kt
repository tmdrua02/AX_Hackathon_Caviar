package com.haneul.medassist.client.dur

import com.haneul.medassist.domain.interaction.Evidence
import com.haneul.medassist.domain.medication.Ingredient
import org.springframework.stereotype.Component

interface DurIngredientApiClient {
    fun check(left: Ingredient, right: Ingredient): DurLookupResult
}

sealed interface DurLookupResult {
    data class Prohibited(val evidence: List<Evidence>) : DurLookupResult
    data class Caution(val evidence: List<Evidence>) : DurLookupResult
    data object NoMatch : DurLookupResult
    data class Failure(val safeErrorCode: String) : DurLookupResult
}

@Component
class SwaggerUnverifiedDurIngredientApiClient : DurIngredientApiClient {
    override fun check(left: Ingredient, right: Ingredient): DurLookupResult =
        // TODO: 공공데이터포털 Swagger 명세 확인 필요.
        // operation path, 성분코드 요청변수, 관계성분 방향성을 확인하기 전에는 외부 결과를 만들지 않는다.
        DurLookupResult.Failure("DUR_SCHEMA_UNVERIFIED")
}
