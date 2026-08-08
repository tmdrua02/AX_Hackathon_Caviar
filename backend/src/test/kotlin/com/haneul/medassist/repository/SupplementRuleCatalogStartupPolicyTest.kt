package com.haneul.medassist.repository

import com.haneul.medassist.config.SupplementInteractionRuleProperties
import com.haneul.medassist.service.SupplementRuleCatalogStatusService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplementRuleCatalogStartupPolicyTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()
    private val configuration = SupplementInteractionRuleCatalogConfiguration()

    @Test
    fun `missing catalog keeps server dependencies constructible and marks catalog unavailable`() {
        val catalog = load(temporaryDirectory.resolve("missing.json"), requireVerifiedManifest = true)

        assertFalse(catalog.isAvailable())
        assertTrue("CATALOG_NOT_FOUND" in catalog.metadata().validationErrorCodes)
    }

    @Test
    fun `empty catalog without manifest is unavailable under production policy`() {
        val path = temporaryDirectory.resolve("empty.json")
        Files.writeString(path, """{"sources":[],"ingredients":[],"mappings":[],"rules":[]}""")

        val catalog = load(path, requireVerifiedManifest = true)

        assertFalse(catalog.isAvailable())
        assertTrue("MANIFEST_REQUIRED" in catalog.metadata().validationErrorCodes)
    }

    @Test
    fun `invalid catalog is isolated instead of throwing during startup`() {
        val path = temporaryDirectory.resolve("invalid.json")
        Files.writeString(path, "{" )

        val catalog = load(path, requireVerifiedManifest = true)

        assertFalse(catalog.isAvailable())
        assertTrue("SCHEMA_MALFORMED_JSON" in catalog.metadata().validationErrorCodes)
    }

    @Test
    fun `verified catalog exposes only operational metadata through internal service`() {
        val path = temporaryDirectory.resolve("verified.json")
        val document = fixtureDocument()
        val validator = SupplementRuleCatalogValidator(objectMapper)
        val artifact = document.copy(
            manifest = SupplementRuleCatalogManifest(
                catalogVersion = "test-v1",
                schemaVersion = SupplementRuleCatalogValidator.SCHEMA_VERSION,
                generatedAt = Instant.parse("2026-08-06T00:00:00Z"),
                generatedBy = "test-author",
                reviewer = "test-reviewer",
                reviewedAt = Instant.parse("2026-08-06T00:00:00Z"),
                sourceFileChecksums = mapOf("source.json" to "b".repeat(64)),
                recordCounts = SupplementRuleCatalogRecordCounts(1, 1, 1, 1),
                status = SupplementRuleCatalogStatus.VERIFIED,
                contentChecksum = validator.contentChecksum(document),
            ),
        )
        Files.write(path, objectMapper.writeValueAsBytes(artifact))

        val catalog = load(path, requireVerifiedManifest = true)
        val status = SupplementRuleCatalogStatusService(catalog).status()

        assertTrue(status.available)
        assertTrue(status.verified)
        assertTrue(status.validationErrorCodes.isEmpty())
        assertTrue(status.catalogChecksum?.length == 64)
    }

    private fun load(path: Path, requireVerifiedManifest: Boolean): JsonSupplementRuleCatalog =
        configuration.supplementRuleCatalog(
            properties = SupplementInteractionRuleProperties(
                resource = path.toUri().toString(),
                requireVerifiedManifest = requireVerifiedManifest,
            ),
            resourceLoader = DefaultResourceLoader(),
            objectMapper = objectMapper,
        )

    private fun fixtureDocument(): SupplementRuleCatalogDocument = requireNotNull(
        javaClass.getResourceAsStream("/supplement-interaction-rules-valid.json"),
    ).use { objectMapper.readValue(it, SupplementRuleCatalogDocument::class.java) }
}
