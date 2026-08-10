package com.haneul.medassist.controller

import com.haneul.medassist.dto.interaction.DrugInteractionBatchRequest
import com.haneul.medassist.dto.interaction.DrugInteractionBatchResponse
import com.haneul.medassist.service.DrugInteractionAnalysisService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/drug-interaction-checks")
class DrugInteractionController(
    private val analysisService: DrugInteractionAnalysisService,
) {
    @PostMapping
    fun analyze(
        @Valid @RequestBody request: DrugInteractionBatchRequest,
    ): DrugInteractionBatchResponse {
        val newProductCode = request.newMedicationProductCode.trim()
        val existingProductCodes = request.existingMedicationProductCodes.map(String::trim).distinct()
        val analyses = existingProductCodes.map { code -> code to analysisService.analyze(newProductCode, code) }
        return DrugInteractionBatchResponse.from(newProductCode, analyses)
    }
}
