package com.haneul.medassist.service

import com.haneul.medassist.config.MatchingProperties
import com.haneul.medassist.domain.medication.IngredientLookupStatus
import com.haneul.medassist.domain.medication.MatchConflict
import com.haneul.medassist.domain.medication.SourceMetadata
import com.haneul.medassist.domain.medication.VerifiedDrugProduct
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrugProductMatcherTest {
    private val normalizer = DrugNameNormalizer()
    private val matcher = DrugProductMatcher(normalizer, MatchingProperties())

    @Test
    fun `scores name strength form and manufacturer matches`() {
        val assessment = matcher.assess(
            normalizer.normalize("타이레놀정 500mg (한울제약)"),
            product("타이레놀정500밀리그램", "한울제약"),
        )

        assertTrue(assessment.score >= 95)
        assertTrue(assessment.conflicts.isEmpty())
    }

    @Test
    fun `penalizes strength mismatch`() {
        val assessment = matcher.assess(
            normalizer.normalize("타이레놀정 500mg"),
            product("타이레놀정325밀리그램", null),
        )

        assertTrue(MatchConflict.STRENGTH in assessment.conflicts)
        assertTrue(assessment.score < 95)
    }

    @Test
    fun `only one high resolved candidate can skip confirmation`() {
        val high = MatchAssessment(95, listOf("match"), emptySet())

        assertFalse(matcher.requiresUserConfirmation(listOf(high to IngredientLookupStatus.RESOLVED)))
        assertTrue(
            matcher.requiresUserConfirmation(
                listOf(
                    high to IngredientLookupStatus.RESOLVED,
                    MatchAssessment(80, emptyList(), emptySet()) to IngredientLookupStatus.RESOLVED,
                ),
            ),
        )
        assertTrue(matcher.requiresUserConfirmation(listOf(high to IngredientLookupStatus.PROVIDER_ERROR)))
    }

    private fun product(name: String, manufacturer: String?) = VerifiedDrugProduct(
        productCode = "official-code",
        productName = name,
        manufacturer = manufacturer,
        source = SourceMetadata("official", "official-code", Instant.EPOCH, "official-reference"),
    )
}
