package com.haneul.medassist.repository

import com.haneul.medassist.domain.evidence.EvidenceVerificationStatus
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SupplementRuleCatalogValidatorTest {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()
    private val validator = SupplementRuleCatalogValidator(objectMapper)

    @Test
    fun `empty legacy catalog is valid for authoring but not for verified startup`() {
        val bytes = """{"sources":[],"ingredients":[],"mappings":[],"rules":[]}""".toByteArray()

        assertTrue(validator.validate(bytes).report.valid)
        val production = validator.validate(bytes, requireVerifiedManifest = true).report

        assertFalse(production.valid)
        assertTrue(production.errors.any { it.code == "MANIFEST_REQUIRED" })
    }

    @Test
    fun `verified manifest with matching counts and checksum is accepted`() {
        val artifact = verifiedDocument()

        val report = validator.validate(objectMapper.writeValueAsBytes(artifact), true).report

        assertTrue(report.valid, report.errors.toString())
        assertEquals("test-v1", report.catalogVersion)
        assertNotNull(report.checksum)
    }

    @Test
    fun `missing source and canonical references are reported without inventing records`() {
        val document = fixtureDocument().copy(sources = emptyList(), ingredients = emptyList())

        val report = validator.validate(objectMapper.writeValueAsBytes(document)).report

        assertFalse(report.valid)
        assertTrue(report.missingReferences.any { it.contains("mapping") }, report.toString())
        assertTrue(report.missingReferences.any { it.contains("rule") }, report.toString())
    }

    @Test
    fun `VERIFIED rule cannot rely on a DRAFT source`() {
        val document = fixtureDocument().let { catalog ->
            catalog.copy(sources = catalog.sources.map { it.copy(verificationStatus = EvidenceVerificationStatus.DRAFT) })
        }

        val report = validator.validate(objectMapper.writeValueAsBytes(document)).report

        assertFalse(report.valid)
        assertTrue(report.invalidVerificationStates.any { it.startsWith("rule:") }, report.toString())
    }

    @Test
    fun `VERIFIED source without reviewer is a schema failure`() {
        val json = fixtureJson()
            .replace("\n      \"reviewedBy\": \"test-reviewer\",", "")

        val report = validator.validate(json.toByteArray()).report

        assertFalse(report.valid)
        assertTrue(report.errors.any { it.code == "SCHEMA_VERIFIED_REVIEWER_REQUIRED" })
    }

    @Test
    fun `global duplicate id and normalized alias duplication are rejected`() {
        val json = fixtureJson()
            .replace("\"id\": \"TEST-CANONICAL-1\"", "\"id\": \"TEST-SOURCE-1\"")
            .replace("\"aliases\": []", "\"aliases\": [\"Alias\", \" alias \" ]")

        val report = validator.validate(json.toByteArray()).report

        assertFalse(report.valid)
        assertTrue("TEST-SOURCE-1" in report.duplicateIds)
        assertTrue(report.errors.any { it.code == "DUPLICATE_ALIAS" })
    }

    @Test
    fun `reversed date range is classified`() {
        val json = fixtureJson().replace(
            "\"createdAt\": \"2026-08-06T00:00:00Z\",\n      \"updatedAt\": \"2026-08-06T00:00:00Z\"\n    }\n  ],\n  \"rules\"",
            "\"validFrom\": \"2026-08-07T00:00:00Z\",\n      \"validTo\": \"2026-08-06T00:00:00Z\",\n      \"createdAt\": \"2026-08-06T00:00:00Z\",\n      \"updatedAt\": \"2026-08-06T00:00:00Z\"\n    }\n  ],\n  \"rules\"",
        )

        val report = validator.validate(json.toByteArray()).report

        assertFalse(report.valid)
        assertEquals(listOf("TEST-MAPPING-1"), report.invalidDateRanges)
    }

    @Test
    fun `overlapping VERIFIED rules for the same pair are rejected`() {
        val document = fixtureDocument().let { it.copy(rules = it.rules + it.rules.single().copy(id = "TEST-RULE-2")) }

        val report = validator.validate(objectMapper.writeValueAsBytes(document)).report

        assertFalse(report.valid)
        assertEquals(1, report.duplicateActiveRules.size, report.toString())
        assertTrue(report.errors.any { it.code == "DUPLICATE_ACTIVE_RULE" })
    }

    @Test
    fun `manifest count checksum and schema mismatches are independently reported`() {
        val artifact = verifiedDocument().let { document ->
            document.copy(
                manifest = requireNotNull(document.manifest).copy(
                    schemaVersion = "0.9",
                    contentChecksum = "0".repeat(64),
                    recordCounts = SupplementRuleCatalogRecordCounts(0, 0, 0, 0),
                ),
            )
        }

        val report = validator.validate(objectMapper.writeValueAsBytes(artifact), true).report

        assertFalse(report.valid)
        assertTrue(report.errors.any { it.code == "SCHEMA_VERSION_MISMATCH" })
        assertTrue(report.errors.any { it.code == "CHECKSUM_MISMATCH" })
        assertTrue(report.errors.any { it.code == "MANIFEST_COUNT_MISMATCH" })
    }

    @Test
    fun `non VERIFIED manifest is rejected by production policy`() {
        val artifact = verifiedDocument().let { document ->
            document.copy(manifest = requireNotNull(document.manifest).copy(status = SupplementRuleCatalogStatus.READY_FOR_REVIEW))
        }

        val report = validator.validate(objectMapper.writeValueAsBytes(artifact), true).report

        assertFalse(report.valid)
        assertTrue(report.errors.any { it.code == "MANIFEST_NOT_VERIFIED" })
    }

    @Test
    fun `VERIFIED manifest requires reviewer metadata`() {
        val artifact = verifiedDocument().let { document ->
            document.copy(manifest = requireNotNull(document.manifest).copy(reviewer = null, reviewedAt = null))
        }

        val report = validator.validate(objectMapper.writeValueAsBytes(artifact), true).report

        assertFalse(report.valid)
        assertTrue(report.errors.any { it.code == "VERIFIED_MANIFEST_REVIEW_REQUIRED" })
    }

    private fun verifiedDocument(): SupplementRuleCatalogDocument {
        val document = fixtureDocument()
        val checksum = validator.contentChecksum(document)
        return document.copy(
            manifest = SupplementRuleCatalogManifest(
                catalogVersion = "test-v1",
                schemaVersion = SupplementRuleCatalogValidator.SCHEMA_VERSION,
                generatedAt = Instant.parse("2026-08-06T00:00:00Z"),
                generatedBy = "test-author",
                reviewer = "test-reviewer",
                reviewedAt = Instant.parse("2026-08-06T00:00:00Z"),
                sourceFileChecksums = mapOf("fixture.json" to "a".repeat(64)),
                recordCounts = SupplementRuleCatalogRecordCounts(1, 1, 1, 1),
                status = SupplementRuleCatalogStatus.VERIFIED,
                contentChecksum = checksum,
            ),
        )
    }

    private fun fixtureDocument(): SupplementRuleCatalogDocument =
        objectMapper.readValue(fixtureJson(), SupplementRuleCatalogDocument::class.java)

    private fun fixtureJson(): String = requireNotNull(
        javaClass.getResource("/supplement-interaction-rules-valid.json"),
    ).readText()
}
