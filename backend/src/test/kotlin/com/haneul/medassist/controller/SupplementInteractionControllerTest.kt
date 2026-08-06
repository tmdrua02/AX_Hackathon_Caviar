package com.haneul.medassist.controller

import com.haneul.medassist.exception.GlobalExceptionHandler
import com.haneul.medassist.service.MedicationEvidenceProvider
import com.haneul.medassist.service.MedicationEvidenceResolution
import com.haneul.medassist.service.SupplementInteractionAnalysisService
import com.haneul.medassist.service.SupplementProductEvidenceProvider
import com.haneul.medassist.service.SupplementProductEvidenceResolution
import com.haneul.medassist.support.officialDrugIngredient
import com.haneul.medassist.support.supplementSnapshot
import com.haneul.medassist.support.testCatalog
import com.haneul.medassist.support.verifiedDrugProduct
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SupplementInteractionControllerTest {
    @Test
    fun `additive endpoint returns completed no-rule result without calling LLM`() {
        mvc().perform(
            post("/api/v1/supplement-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"medicationProductCode":"P-1","supplementStatementNo":"S-1"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processingStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.severity").value("NO_VERIFIED_RULE_FOUND"))
            .andExpect(jsonPath("$.coverage.complete").value(true))
            .andExpect(jsonPath("$.coverage.totalPairs").value(1))
            .andExpect(jsonPath("$.matchedRules.length()").value(0))
            .andExpect(jsonPath("$.disclaimer").isNotEmpty)
    }

    @Test
    fun `malformed official code returns validation Problem Details`() {
        mvc().perform(
            post("/api/v1/supplement-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"medicationProductCode":"bad code!","supplementStatementNo":"S-1"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.type").value("https://medassist.local/problems/validation-failed"))
    }

    @Test
    fun `empty request returns validation Problem Details`() {
        mvc().perform(
            post("/api/v1/supplement-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `syntactically valid but unknown product returns UNKNOWN evidence result`() {
        val medication = MedicationEvidenceProvider { MedicationEvidenceResolution.NotFound }

        mvc(medication).perform(
            post("/api/v1/supplement-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"medicationProductCode":"P-404","supplementStatementNo":"S-1"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processingStatus").value("FAILED"))
            .andExpect(jsonPath("$.severity").value("UNKNOWN"))
            .andExpect(jsonPath("$.failedSteps[0]").isNotEmpty)
            .andExpect(jsonPath("$.coverage.complete").value(false))
    }

    private fun mvc(
        medicationProvider: MedicationEvidenceProvider = MedicationEvidenceProvider {
            MedicationEvidenceResolution.Resolved(
                product = verifiedDrugProduct(),
                ingredients = listOf(officialDrugIngredient()),
                ingredientsComplete = true,
                overview = null,
            )
        },
    ): MockMvc {
        val catalog = testCatalog()
        val service = SupplementInteractionAnalysisService(
            medicationProvider = medicationProvider,
            supplementProvider = SupplementProductEvidenceProvider {
                SupplementProductEvidenceResolution.Resolved(supplementSnapshot())
            },
            sourceRepository = catalog,
            canonicalRepository = catalog,
            mappingRepository = catalog,
            ruleRepository = catalog,
        )
        return MockMvcBuilders.standaloneSetup(SupplementInteractionController(service))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }
}
