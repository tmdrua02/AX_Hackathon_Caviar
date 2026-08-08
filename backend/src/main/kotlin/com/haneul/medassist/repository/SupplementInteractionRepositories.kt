package com.haneul.medassist.repository

import com.haneul.medassist.domain.evidence.VerifiedSourceReference
import com.haneul.medassist.domain.supplement.SupplementIngredientCanonical
import com.haneul.medassist.domain.supplement.SupplementInteractionRule
import com.haneul.medassist.domain.supplement.SupplementProductIngredientMapping
import java.time.Instant

interface VerifiedSourceReferenceRepository {
    fun findById(id: String): VerifiedSourceReference?

    fun findVerifiedByIds(ids: Set<String>): List<VerifiedSourceReference>

    fun isAvailable(): Boolean
}

interface SupplementIngredientCanonicalRepository {
    fun findVerifiedById(id: String): SupplementIngredientCanonical?

    fun isAvailable(): Boolean
}

interface SupplementProductIngredientMappingRepository {
    fun findVerifiedByStatementNo(statementNo: String, at: Instant): List<SupplementProductIngredientMapping>

    fun isAvailable(): Boolean
}

interface SupplementInteractionRuleRepository {
    fun findVerified(
        drugIngredientCode: String,
        supplementIngredientCanonicalId: String,
        at: Instant,
    ): List<SupplementInteractionRule>

    fun isAvailable(): Boolean
}

class SupplementRuleRepositoryUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
