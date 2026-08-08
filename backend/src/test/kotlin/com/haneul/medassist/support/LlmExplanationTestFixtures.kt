package com.haneul.medassist.support

import com.haneul.medassist.domain.evidence.SupplementRuleCatalogAuditMetadata
import com.haneul.medassist.domain.supplement.SupplementInteractionCoverage
import com.haneul.medassist.domain.supplement.SupplementInteractionExplanationRequest
import com.haneul.medassist.domain.supplement.SupplementInteractionFailureCode
import com.haneul.medassist.domain.supplement.SupplementInteractionSeverity

fun explanationRequest(
    severity: SupplementInteractionSeverity = SupplementInteractionSeverity.UNKNOWN,
    failedSteps: Set<SupplementInteractionFailureCode> = setOf(
        SupplementInteractionFailureCode.SUPPLEMENT_INGREDIENT_MAPPING_MISSING,
    ),
) = SupplementInteractionExplanationRequest(
    immutableDecision = severity,
    catalogMetadata = SupplementRuleCatalogAuditMetadata(
        available = true,
        verified = true,
        catalogVersion = "TEST-CATALOG-V1",
        schemaVersion = "1.0",
        catalogChecksum = "a".repeat(64),
        loadedAt = FIXED_TIME,
        sourceCount = 0,
        canonicalIngredientCount = 0,
        productMappingCount = 0,
        interactionRuleCount = 0,
        validationErrorCodes = emptyList(),
    ),
    medication = null,
    supplement = null,
    officialDrugIngredients = emptyList(),
    verifiedSupplementIngredients = emptyList(),
    matchedRules = emptyList(),
    evidence = emptyList(),
    coverage = SupplementInteractionCoverage(
        medicationResolved = true,
        medicationIngredientsExpected = 1,
        medicationIngredientsResolved = 1,
        medicationIngredientsComplete = true,
        supplementResolved = true,
        supplementIngredientMappingAvailable = false,
        supplementIngredientsExpected = 0,
        supplementIngredientsVerified = 0,
        totalPairs = 0,
        evaluatedPairs = 0,
        matchedPairs = 0,
        failedPairs = 0,
        ruleRepositoryAvailable = true,
        complete = false,
        percentage = 50,
    ),
    failedSteps = failedSteps,
    disclaimer = "TEST DISCLAIMER: 의사 또는 약사와 상담하세요.",
)
