package com.haneul.medassist.service

import com.haneul.medassist.domain.evidence.VerifiedSourceReference
import com.haneul.medassist.domain.medication.DrugOverview
import com.haneul.medassist.domain.medication.Ingredient
import com.haneul.medassist.domain.medication.VerifiedDrugProduct
import com.haneul.medassist.domain.supplement.SupplementIngredientCanonical
import com.haneul.medassist.domain.supplement.SupplementInteractionAnalysisResult
import com.haneul.medassist.domain.supplement.SupplementInteractionCoverage
import com.haneul.medassist.domain.supplement.SupplementInteractionEvidence
import com.haneul.medassist.domain.supplement.SupplementInteractionEvidenceBundle
import com.haneul.medassist.domain.supplement.SupplementInteractionPairEvaluation
import com.haneul.medassist.domain.supplement.SupplementInteractionProcessingStatus
import com.haneul.medassist.domain.supplement.SupplementInteractionRule
import com.haneul.medassist.domain.supplement.SupplementInteractionSeverity
import com.haneul.medassist.domain.supplement.SupplementProductIngredientMapping
import com.haneul.medassist.domain.supplement.SupplementProductSnapshot
import com.haneul.medassist.repository.SupplementIngredientCanonicalRepository
import com.haneul.medassist.repository.SupplementInteractionRuleRepository
import com.haneul.medassist.repository.SupplementProductIngredientMappingRepository
import com.haneul.medassist.repository.VerifiedSourceReferenceRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class SupplementInteractionAnalysisService(
    private val medicationProvider: MedicationEvidenceProvider,
    private val supplementProvider: SupplementProductEvidenceProvider,
    private val sourceRepository: VerifiedSourceReferenceRepository,
    private val canonicalRepository: SupplementIngredientCanonicalRepository,
    private val mappingRepository: SupplementProductIngredientMappingRepository,
    private val ruleRepository: SupplementInteractionRuleRepository,
) {
    fun analyze(
        medicationProductCode: String,
        supplementStatementNo: String,
    ): SupplementInteractionAnalysisResult {
        val analyzedAt = Instant.now()
        val failedSteps = linkedSetOf<String>()
        val blockingFailures = linkedSetOf<String>()

        var medicationProduct: VerifiedDrugProduct? = null
        var medicationIngredients = emptyList<Ingredient>()
        var medicationIngredientsComplete = false
        var medicationOverview: DrugOverview? = null
        when (val medication = medicationProvider.resolve(medicationProductCode.trim())) {
            is MedicationEvidenceResolution.Resolved -> {
                medicationProduct = medication.product
                medicationIngredients = medication.ingredients
                medicationIngredientsComplete = medication.ingredientsComplete && medication.ingredients.isNotEmpty()
                medicationOverview = medication.overview
                failedSteps += medication.optionalFailedSteps
                if (!medicationIngredientsComplete) blockingFailures += "MEDICATION_INGREDIENTS"
            }

            MedicationEvidenceResolution.NotFound -> blockingFailures += "MEDICATION_NOT_FOUND"
            is MedicationEvidenceResolution.Failed -> blockingFailures += "MEDICATION_PROVIDER:${medication.errorCode}"
        }

        var supplementProduct: SupplementProductSnapshot? = null
        when (val supplement = supplementProvider.resolve(supplementStatementNo.trim())) {
            is SupplementProductEvidenceResolution.Resolved -> supplementProduct = supplement.product
            SupplementProductEvidenceResolution.NotFound -> blockingFailures += "SUPPLEMENT_NOT_FOUND"
            is SupplementProductEvidenceResolution.Failed -> blockingFailures += "SUPPLEMENT_PROVIDER:${supplement.errorCode}"
        }

        failedSteps += blockingFailures
        val repositoryAvailability = repositoryAvailability()
        if (!repositoryAvailability.mapping || !repositoryAvailability.canonical || !repositoryAvailability.source) {
            blockingFailures += "SUPPLEMENT_EVIDENCE_REPOSITORY"
        }
        if (!repositoryAvailability.rules) blockingFailures += "RULE_REPOSITORY"

        val mappings = if (supplementProduct != null && repositoryAvailability.mapping) {
            runCatching {
                mappingRepository.findVerifiedByStatementNo(supplementProduct.statementNo, analyzedAt)
            }.getOrElse {
                blockingFailures += "SUPPLEMENT_MAPPING_REPOSITORY"
                emptyList()
            }
        } else {
            emptyList()
        }
        if (supplementProduct != null && mappings.isEmpty()) blockingFailures += "VERIFIED_SUPPLEMENT_MAPPING_NOT_FOUND"

        val supplementIngredients = resolveCanonicalIngredients(mappings, blockingFailures)
        val expectedSupplementIngredients = mappings.map(SupplementProductIngredientMapping::supplementIngredientCanonicalId)
            .distinct()
            .size
        if (expectedSupplementIngredients > supplementIngredients.size) {
            blockingFailures += "VERIFIED_SUPPLEMENT_INGREDIENT_NOT_FOUND"
        }

        val pairEvaluations = mutableListOf<SupplementInteractionPairEvaluation>()
        val matchedRules = mutableListOf<SupplementInteractionRule>()
        medicationIngredients.forEach { drugIngredient ->
            supplementIngredients.forEach { supplementIngredient ->
                val drugCode = drugIngredient.providerCode
                if (drugCode.isNullOrBlank()) {
                    pairEvaluations += failedPair(drugIngredient, supplementIngredient, "DRUG_INGREDIENT_CODE_MISSING")
                    blockingFailures += "DRUG_INGREDIENT_CODE_MISSING"
                } else if (!repositoryAvailability.rules) {
                    pairEvaluations += failedPair(drugIngredient, supplementIngredient, "RULE_REPOSITORY_UNAVAILABLE")
                } else {
                    val rules = runCatching {
                        ruleRepository.findVerified(drugCode, supplementIngredient.id, analyzedAt)
                    }.getOrElse {
                        blockingFailures += "RULE_REPOSITORY"
                        pairEvaluations += failedPair(drugIngredient, supplementIngredient, "RULE_REPOSITORY_FAILED")
                        null
                    }
                    if (rules != null) {
                        matchedRules += rules
                        pairEvaluations += SupplementInteractionPairEvaluation(
                            drugIngredientCode = drugCode,
                            drugIngredientName = drugIngredient.displayName,
                            supplementIngredientCanonicalId = supplementIngredient.id,
                            supplementIngredientName = supplementIngredient.displayName,
                            evaluated = true,
                            matchedRuleIds = rules.map(SupplementInteractionRule::id),
                        )
                    }
                }
            }
        }

        val distinctRules = matchedRules.distinctBy(SupplementInteractionRule::id)
        val sources = resolveSources(distinctRules, blockingFailures)
        val evidence = buildEvidence(distinctRules, supplementIngredients, sources)
        if (distinctRules.isNotEmpty() && evidence.isEmpty()) blockingFailures += "VERIFIED_RULE_SOURCE_NOT_FOUND"

        failedSteps += blockingFailures
        val coverage = coverage(
            medicationResolved = medicationProduct != null,
            medicationIngredients = medicationIngredients,
            medicationIngredientsComplete = medicationIngredientsComplete,
            supplementResolved = supplementProduct != null,
            mappings = mappings,
            supplementIngredients = supplementIngredients,
            pairs = pairEvaluations,
            ruleRepositoryAvailable = repositoryAvailability.rules,
            evidenceRepositoriesAvailable = repositoryAvailability.source && repositoryAvailability.canonical &&
                repositoryAvailability.mapping && repositoryAvailability.rules,
        )
        val severity = severity(distinctRules, coverage)
        val processingStatus = when {
            coverage.complete -> SupplementInteractionProcessingStatus.COMPLETED
            medicationProduct == null || supplementProduct == null -> SupplementInteractionProcessingStatus.FAILED
            else -> SupplementInteractionProcessingStatus.PARTIAL
        }
        val disclaimer = DISCLAIMER
        val bundle = SupplementInteractionEvidenceBundle(
            officialMedicationProduct = medicationProduct,
            officialMedicationIngredients = medicationIngredients,
            medicationOverview = medicationOverview,
            officialSupplementProduct = supplementProduct,
            verifiedSupplementIngredients = supplementIngredients,
            matchedInteractionRules = distinctRules,
            sourceReferences = sources,
            immutableDecision = severity,
            coverage = coverage,
            failedSteps = failedSteps,
            analyzedAt = analyzedAt,
            disclaimer = disclaimer,
        )
        return SupplementInteractionAnalysisResult(
            processingStatus = processingStatus,
            severity = severity,
            medication = medicationProduct,
            medicationOverview = medicationOverview,
            supplement = supplementProduct,
            drugIngredients = medicationIngredients,
            supplementIngredients = supplementIngredients,
            evaluatedPairs = pairEvaluations,
            matchedRules = distinctRules,
            evidence = evidence,
            coverage = coverage,
            failedSteps = failedSteps,
            message = message(severity, blockingFailures),
            disclaimer = disclaimer,
            analyzedAt = analyzedAt,
            evidenceBundle = bundle,
        )
    }

    private fun repositoryAvailability(): RepositoryAvailability = RepositoryAvailability(
        source = runCatching(sourceRepository::isAvailable).getOrDefault(false),
        canonical = runCatching(canonicalRepository::isAvailable).getOrDefault(false),
        mapping = runCatching(mappingRepository::isAvailable).getOrDefault(false),
        rules = runCatching(ruleRepository::isAvailable).getOrDefault(false),
    )

    private fun resolveCanonicalIngredients(
        mappings: List<SupplementProductIngredientMapping>,
        blockingFailures: MutableSet<String>,
    ): List<SupplementIngredientCanonical> {
        val resolved = mutableListOf<SupplementIngredientCanonical>()
        mappings.map(SupplementProductIngredientMapping::supplementIngredientCanonicalId)
            .distinct()
            .forEach { canonicalId ->
                val ingredient = runCatching { canonicalRepository.findVerifiedById(canonicalId) }
                    .getOrElse {
                        blockingFailures += "CANONICAL_INGREDIENT_REPOSITORY"
                        null
                    }
                if (ingredient != null) resolved += ingredient
            }
        return resolved
    }

    private fun resolveSources(
        rules: List<SupplementInteractionRule>,
        blockingFailures: MutableSet<String>,
    ): List<VerifiedSourceReference> {
        val expectedIds = rules.flatMap { it.sourceReferenceIds }.toSet()
        if (expectedIds.isEmpty()) return emptyList()
        val sources = runCatching { sourceRepository.findVerifiedByIds(expectedIds) }
            .getOrElse {
                blockingFailures += "SOURCE_REPOSITORY"
                emptyList()
            }
        if (sources.map(VerifiedSourceReference::id).toSet() != expectedIds) {
            blockingFailures += "VERIFIED_RULE_SOURCE_NOT_FOUND"
        }
        return sources
    }

    private fun buildEvidence(
        rules: List<SupplementInteractionRule>,
        ingredients: List<SupplementIngredientCanonical>,
        sources: List<VerifiedSourceReference>,
    ): List<SupplementInteractionEvidence> {
        val ingredientById = ingredients.associateBy(SupplementIngredientCanonical::id)
        val sourceById = sources.associateBy(VerifiedSourceReference::id)
        return rules.flatMap { rule ->
            val ingredient = ingredientById[rule.supplementIngredientCanonicalId] ?: return@flatMap emptyList()
            rule.sourceReferenceIds.mapNotNull { sourceId ->
                val source = sourceById[sourceId] ?: return@mapNotNull null
                SupplementInteractionEvidence(
                    evidenceType = "SUPPLEMENT_INTERACTION_RULE",
                    sourceAuthority = source.authority,
                    sourceReferenceId = source.id,
                    title = source.title,
                    originalText = source.originalText,
                    drugIngredientCode = rule.drugIngredientCode,
                    drugIngredientName = rule.drugIngredientName,
                    supplementIngredientCanonicalId = ingredient.id,
                    supplementIngredientName = ingredient.displayName,
                    severity = rule.severity,
                    verificationStatus = rule.verificationStatus,
                    validFrom = rule.validFrom,
                    validTo = rule.validTo,
                    retrievedAt = source.retrievedAt,
                )
            }
        }
    }

    private fun coverage(
        medicationResolved: Boolean,
        medicationIngredients: List<Ingredient>,
        medicationIngredientsComplete: Boolean,
        supplementResolved: Boolean,
        mappings: List<SupplementProductIngredientMapping>,
        supplementIngredients: List<SupplementIngredientCanonical>,
        pairs: List<SupplementInteractionPairEvaluation>,
        ruleRepositoryAvailable: Boolean,
        evidenceRepositoriesAvailable: Boolean,
    ): SupplementInteractionCoverage {
        val mappingIds = mappings.map(SupplementProductIngredientMapping::supplementIngredientCanonicalId).distinct()
        val expectedSupplementIngredients = mappingIds.size
        val verifiedSupplementIngredients = supplementIngredients.count(SupplementIngredientCanonical::isProductionEligible)
        val totalPairs = medicationIngredients.size * verifiedSupplementIngredients
        val evaluatedPairs = pairs.count(SupplementInteractionPairEvaluation::evaluated)
        val failedPairs = pairs.count { !it.evaluated }
        val matchedPairs = pairs.count { it.evaluated && it.matchedRuleIds.isNotEmpty() }
        val allSupplementIngredientsVerified = expectedSupplementIngredients > 0 &&
            expectedSupplementIngredients == verifiedSupplementIngredients
        val complete = medicationResolved && medicationIngredientsComplete &&
            medicationIngredients.isNotEmpty() && medicationIngredients.all { !it.providerCode.isNullOrBlank() } &&
            supplementResolved && mappings.isNotEmpty() && allSupplementIngredientsVerified &&
            totalPairs > 0 && evaluatedPairs == totalPairs && failedPairs == 0 && evidenceRepositoriesAvailable
        val checkpoints = listOf(
            medicationResolved,
            medicationIngredientsComplete,
            supplementResolved,
            mappings.isNotEmpty(),
            allSupplementIngredientsVerified,
            evidenceRepositoriesAvailable,
        )
        val denominator = checkpoints.size + totalPairs
        val numerator = checkpoints.count { it } + evaluatedPairs.coerceAtMost(totalPairs)
        val percentage = if (denominator == 0) 0 else (numerator * 100 / denominator).coerceIn(0, 100)
        return SupplementInteractionCoverage(
            medicationResolved = medicationResolved,
            medicationIngredientsExpected = medicationIngredients.size,
            medicationIngredientsResolved = medicationIngredients.count { !it.providerCode.isNullOrBlank() },
            medicationIngredientsComplete = medicationIngredientsComplete &&
                medicationIngredients.isNotEmpty() && medicationIngredients.all { !it.providerCode.isNullOrBlank() },
            supplementResolved = supplementResolved,
            supplementIngredientMappingAvailable = mappings.isNotEmpty(),
            supplementIngredientsExpected = expectedSupplementIngredients,
            supplementIngredientsVerified = verifiedSupplementIngredients,
            totalPairs = totalPairs,
            evaluatedPairs = evaluatedPairs,
            matchedPairs = matchedPairs,
            failedPairs = failedPairs,
            ruleRepositoryAvailable = ruleRepositoryAvailable,
            complete = complete,
            percentage = percentage,
        )
    }

    private fun severity(
        rules: List<SupplementInteractionRule>,
        coverage: SupplementInteractionCoverage,
    ): SupplementInteractionSeverity = when {
        rules.any { it.severity == SupplementInteractionSeverity.AVOID_COMBINATION } ->
            SupplementInteractionSeverity.AVOID_COMBINATION

        rules.any { it.severity == SupplementInteractionSeverity.CAUTION } -> SupplementInteractionSeverity.CAUTION
        coverage.complete -> SupplementInteractionSeverity.NO_VERIFIED_RULE_FOUND
        else -> SupplementInteractionSeverity.UNKNOWN
    }

    private fun message(
        severity: SupplementInteractionSeverity,
        blockingFailures: Set<String>,
    ): String = when (severity) {
        SupplementInteractionSeverity.AVOID_COMBINATION -> "검수된 병용 회피 근거가 확인되었습니다."
        SupplementInteractionSeverity.CAUTION -> "검수된 병용섭취 주의 근거가 확인되었습니다."
        SupplementInteractionSeverity.NO_VERIFIED_RULE_FOUND -> NO_VERIFIED_RULE_MESSAGE
        SupplementInteractionSeverity.UNKNOWN -> {
            val steps = blockingFailures.joinToString(", ").ifBlank { "COVERAGE_INCOMPLETE" }
            "분석 근거를 완전히 확인하지 못했습니다($steps). 안전하다는 의미가 아닙니다."
        }
    }

    private fun failedPair(
        drugIngredient: Ingredient,
        supplementIngredient: SupplementIngredientCanonical,
        errorCode: String,
    ) = SupplementInteractionPairEvaluation(
        drugIngredientCode = drugIngredient.providerCode.orEmpty(),
        drugIngredientName = drugIngredient.displayName,
        supplementIngredientCanonicalId = supplementIngredient.id,
        supplementIngredientName = supplementIngredient.displayName,
        evaluated = false,
        matchedRuleIds = emptyList(),
        errorCode = errorCode,
    )

    private data class RepositoryAvailability(
        val source: Boolean,
        val canonical: Boolean,
        val mapping: Boolean,
        val rules: Boolean,
    )

    companion object {
        const val NO_VERIFIED_RULE_MESSAGE =
            "현재 검수된 병용섭취 규칙에서 일치하는 주의 정보를 찾지 못했습니다. 이는 함께 복용해도 안전하다는 의미가 아닙니다."
        const val DISCLAIMER = "이 결과는 정보 제공용이며 복용 전 의사 또는 약사와 상담하세요."
    }
}
