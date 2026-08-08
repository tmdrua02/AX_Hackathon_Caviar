package com.haneul.medassist.controller

import com.haneul.medassist.dto.supplement.SupplementInteractionCheckRequest
import com.haneul.medassist.dto.supplement.SupplementInteractionCheckResponse
import com.haneul.medassist.service.SupplementInteractionPresentationService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/supplement-interaction-checks")
class SupplementInteractionController(
    private val service: SupplementInteractionPresentationService,
) {
    @PostMapping
    fun analyze(
        @Valid @RequestBody request: SupplementInteractionCheckRequest,
    ): SupplementInteractionCheckResponse = SupplementInteractionCheckResponse.from(
        service.analyzeAndExplain(request.medicationProductCode, request.supplementStatementNo),
    )
}
