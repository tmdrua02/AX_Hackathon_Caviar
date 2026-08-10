package com.haneul.medassist.repository

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplementRuleCatalogCliTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `validate command writes a successful report`() {
        val source = copyFixture("valid.json")
        val report = temporaryDirectory.resolve("report.json")

        SupplementRuleCatalogCli.main(
            arrayOf("--mode=validate", "--catalog=$source", "--report=$report"),
        )

        val validation = objectMapper.readValue(report.toFile(), CatalogValidationReport::class.java)
        assertTrue(validation.valid)
        assertTrue(Files.exists(report))
    }

    @Test
    fun `invalid command writes report and refuses verified artifact`() {
        val source = temporaryDirectory.resolve("invalid.json")
        val report = temporaryDirectory.resolve("invalid-report.json")
        val output = temporaryDirectory.resolve("must-not-exist.json")
        Files.writeString(source, """{"sources":[],"ingredients":[],"mappings":[],"rules":[{}]}""")

        assertFailsWith<IllegalStateException> {
            SupplementRuleCatalogCli.main(
                arrayOf(
                    "--mode=build-verified",
                    "--catalog=$source",
                    "--report=$report",
                    "--output=$output",
                    "--reviewer=test-reviewer",
                    "--catalog-version=test-v1",
                ),
            )
        }

        val validation = objectMapper.readValue(report.toFile(), CatalogValidationReport::class.java)
        assertFalse(validation.valid)
        assertFalse(Files.exists(output))
    }

    @Test
    fun `verified build requires reviewer and never mutates source`() {
        val source = copyFixture("source.json")
        val sourceBefore = Files.readAllBytes(source)
        val report = temporaryDirectory.resolve("report.json")
        val output = temporaryDirectory.resolve("verified.json")

        assertFailsWith<IllegalStateException> {
            SupplementRuleCatalogCli.main(
                arrayOf(
                    "--mode=build-verified",
                    "--catalog=$source",
                    "--report=$report",
                    "--output=$output",
                    "--catalog-version=test-v1",
                ),
            )
        }

        SupplementRuleCatalogCli.main(
            arrayOf(
                "--mode=build-verified",
                "--catalog=$source",
                "--report=$report",
                "--output=$output",
                "--reviewer=test-reviewer",
                "--catalog-version=test-v1",
                "--generated-by=test-author",
            ),
        )

        assertContentEquals(sourceBefore, Files.readAllBytes(source))
        val artifactBytes = Files.readAllBytes(output)
        val validation = SupplementRuleCatalogValidator(objectMapper).validate(artifactBytes, true)
        assertTrue(validation.report.valid, validation.report.errors.toString())
        assertTrue(validation.document?.manifest?.status == SupplementRuleCatalogStatus.VERIFIED)
    }

    private fun copyFixture(name: String): Path {
        val target = temporaryDirectory.resolve(name)
        requireNotNull(javaClass.getResourceAsStream("/supplement-interaction-rules-valid.json")).use {
            Files.copy(it, target)
        }
        return target
    }
}
