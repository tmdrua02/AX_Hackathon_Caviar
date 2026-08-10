package com.haneul.medassist.domain.supplement

import com.haneul.medassist.domain.evidence.EvidenceAuthority
import com.haneul.medassist.domain.evidence.EvidenceVerificationStatus
import com.haneul.medassist.domain.evidence.VerifiedSourceReference
import com.haneul.medassist.repository.JsonSupplementRuleCatalog
import com.haneul.medassist.repository.SupplementRuleCatalogDocument
import com.haneul.medassist.support.FIXED_TIME
import com.haneul.medassist.support.canonicalIngredient
import com.haneul.medassist.support.verifiedMapping
import com.haneul.medassist.support.verifiedRule
import com.haneul.medassist.support.verifiedSource
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupplementRuleDomainTest {
    @Test
    fun `VERIFIED source requires reviewer identity and review time`() {
        assertFailsWith<IllegalArgumentException> {
            VerifiedSourceReference(
                id = "SRC",
                authority = EvidenceAuthority.MFDS,
                title = "근거",
                documentIdentifier = "DOC-1",
                originalText = "공식 원문",
                retrievedAt = FIXED_TIME,
                verificationStatus = EvidenceVerificationStatus.VERIFIED,
            )
        }
    }

    @Test
    fun `source requires URL or document identifier and original text`() {
        assertFailsWith<IllegalArgumentException> {
            VerifiedSourceReference(
                id = "SRC",
                authority = EvidenceAuthority.MFDS,
                title = "근거",
                originalText = "공식 원문",
                retrievedAt = FIXED_TIME,
                verificationStatus = EvidenceVerificationStatus.DRAFT,
            )
        }
    }

    @Test
    fun `canonical aliases are curated values and remain unchanged`() {
        val ingredient = canonicalIngredient(aliases = setOf("Alias A", "별칭 B"))

        assertEquals(setOf("Alias A", "별칭 B"), ingredient.aliases)
    }

    @Test
    fun `VERIFIED rule based on DRAFT source is rejected at import`() {
        val canonicalSource = verifiedSource(id = "SRC-CANONICAL")
        val ruleSource = verifiedSource(id = "SRC-RULE", status = EvidenceVerificationStatus.DRAFT)

        assertFailsWith<IllegalArgumentException> {
            JsonSupplementRuleCatalog(
                SupplementRuleCatalogDocument(
                    sources = listOf(canonicalSource, ruleSource),
                    ingredients = listOf(canonicalIngredient(sourceId = "SRC-CANONICAL")),
                    mappings = emptyList(),
                    rules = listOf(verifiedRule(sourceId = "SRC-RULE")),
                ),
            )
        }
    }

    @Test
    fun `VERIFIED mapping cannot use unverified candidate type`() {
        assertFailsWith<IllegalArgumentException> {
            JsonSupplementRuleCatalog(
                SupplementRuleCatalogDocument(
                    sources = listOf(verifiedSource()),
                    ingredients = listOf(canonicalIngredient()),
                    mappings = listOf(verifiedMapping(mappingType = MappingType.UNVERIFIED_CANDIDATE)),
                    rules = emptyList(),
                ),
            )
        }
    }

    @Test
    fun `DRAFT mapping is excluded from production lookup`() {
        val catalog = JsonSupplementRuleCatalog(
            SupplementRuleCatalogDocument(
                sources = listOf(verifiedSource()),
                ingredients = listOf(canonicalIngredient()),
                mappings = listOf(verifiedMapping(status = EvidenceVerificationStatus.DRAFT)),
                rules = emptyList(),
            ),
        )

        assertTrue(catalog.findVerifiedByStatementNo("S-1", FIXED_TIME).isEmpty())
    }

    @Test
    fun `duplicate overlapping active VERIFIED rules are rejected`() {
        val first = verifiedRule(id = "R-1", validFrom = Instant.parse("2026-01-01T00:00:00Z"))
        val second = verifiedRule(id = "R-2", validFrom = Instant.parse("2026-02-01T00:00:00Z"))

        assertFailsWith<IllegalArgumentException> {
            JsonSupplementRuleCatalog(
                SupplementRuleCatalogDocument(
                    sources = listOf(verifiedSource()),
                    ingredients = listOf(canonicalIngredient()),
                    mappings = listOf(verifiedMapping()),
                    rules = listOf(first, second),
                ),
            )
        }
    }

    @Test
    fun `non-overlapping rules and validity filtering are supported`() {
        val expired = verifiedRule(
            id = "R-OLD",
            validFrom = Instant.parse("2025-01-01T00:00:00Z"),
            validTo = Instant.parse("2025-12-31T23:59:59Z"),
        )
        val current = verifiedRule(
            id = "R-NEW",
            validFrom = Instant.parse("2026-01-01T00:00:00Z"),
        )
        val catalog = JsonSupplementRuleCatalog(
            SupplementRuleCatalogDocument(
                sources = listOf(verifiedSource()),
                ingredients = listOf(canonicalIngredient()),
                mappings = listOf(verifiedMapping()),
                rules = listOf(expired, current),
            ),
        )

        assertEquals(listOf("R-NEW"), catalog.findVerified("D-1", "CAN-1", FIXED_TIME).map { it.id })
        assertNull(catalog.findVerifiedById("missing"))
    }

    @Test
    fun `rule requires at least one source and valid period order`() {
        assertFailsWith<IllegalArgumentException> {
            verifiedRule().copy(sourceReferenceIds = emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            verifiedRule().copy(
                validFrom = Instant.parse("2026-12-31T00:00:00Z"),
                validTo = Instant.parse("2026-01-01T00:00:00Z"),
            )
        }
    }
}
