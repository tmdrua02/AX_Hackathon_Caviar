package com.haneul.medassist.client.supplement

import com.haneul.medassist.domain.supplement.HealthFunctionalFoodSearchResult
import com.haneul.medassist.domain.supplement.SupplementProductSnapshotResult

interface HealthFunctionalFoodApiClient {
    fun search(productName: String, manufacturer: String? = null): HealthFunctionalFoodSearchResult

    fun findByStatementNo(statementNo: String): SupplementProductSnapshotResult
}
