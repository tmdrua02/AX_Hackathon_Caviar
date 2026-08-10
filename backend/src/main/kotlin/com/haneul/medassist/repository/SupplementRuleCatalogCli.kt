package com.haneul.medassist.repository

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.io.path.absolute
import kotlin.io.path.name

object SupplementRuleCatalogCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val arguments = args.mapNotNull { argument ->
            argument.removePrefix("--").split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()
        val mode = arguments.required("mode")
        val catalogPath = Path.of(arguments.required("catalog")).absolute().normalize()
        val reportPath = Path.of(arguments.required("report")).absolute().normalize()
        val objectMapper = JsonMapper.builder().findAndAddModules().build()
        when (mode) {
            "validate" -> validate(objectMapper, catalogPath, reportPath)
            "build-verified" -> buildVerified(objectMapper, catalogPath, reportPath, arguments)
            else -> error("unsupported catalog command mode")
        }
    }

    private fun validate(objectMapper: ObjectMapper, catalogPath: Path, reportPath: Path) {
        val bytes = Files.readAllBytes(catalogPath)
        val result = SupplementRuleCatalogValidator(objectMapper).validate(bytes)
        writeJsonAtomically(objectMapper, reportPath, result.report)
        check(result.report.valid) { "catalog validation failed; see validation report" }
    }

    private fun buildVerified(
        objectMapper: ObjectMapper,
        catalogPath: Path,
        reportPath: Path,
        arguments: Map<String, String>,
    ) {
        val outputPath = Path.of(arguments.required("output")).absolute().normalize()
        require(outputPath != catalogPath) { "verified output path must differ from source catalog path" }
        val reviewer = arguments.required("reviewer").trim()
        require(reviewer.isNotBlank()) { "reviewer is required" }
        val catalogVersion = arguments.required("catalog-version").trim()
        require(catalogVersion.isNotBlank()) { "catalog version is required" }
        val generatedBy = arguments["generated-by"]?.trim().takeUnless { it.isNullOrBlank() } ?: reviewer
        val sourceBytes = Files.readAllBytes(catalogPath)
        val validator = SupplementRuleCatalogValidator(objectMapper)
        val validation = validator.validate(sourceBytes)
        writeJsonAtomically(objectMapper, reportPath, validation.report)
        check(validation.report.valid && validation.document != null) {
            "catalog validation failed; verified artifact was not created"
        }
        val document = requireNotNull(validation.document)
        val generatedAt = Instant.now()
        val verifiedDocument = document.copy(
            manifest = SupplementRuleCatalogManifest(
                catalogVersion = catalogVersion,
                schemaVersion = SupplementRuleCatalogValidator.SCHEMA_VERSION,
                generatedAt = generatedAt,
                generatedBy = generatedBy,
                reviewer = reviewer,
                reviewedAt = generatedAt,
                sourceFileChecksums = mapOf(catalogPath.name to validator.rawChecksum(sourceBytes)),
                recordCounts = SupplementRuleCatalogRecordCounts(
                    sources = document.sources.size,
                    canonicalIngredients = document.ingredients.size,
                    productMappings = document.mappings.size,
                    interactionRules = document.rules.size,
                ),
                status = SupplementRuleCatalogStatus.VERIFIED,
                contentChecksum = validator.contentChecksum(document),
            ),
        )
        val artifactBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(verifiedDocument)
        val artifactValidation = validator.validate(artifactBytes, requireVerifiedManifest = true)
        check(artifactValidation.report.valid) { "generated artifact failed verification" }
        writeBytesAtomically(outputPath, artifactBytes)
        writeJsonAtomically(objectMapper, reportPath, artifactValidation.report)
    }

    private fun writeJsonAtomically(objectMapper: ObjectMapper, output: Path, value: Any) {
        writeBytesAtomically(output, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value))
    }

    private fun writeBytesAtomically(output: Path, bytes: ByteArray) {
        output.parent?.let(Files::createDirectories)
        val temporary = Files.createTempFile(output.parent, ".${output.fileName}.", ".tmp")
        try {
            Files.write(temporary, bytes)
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun Map<String, String>.required(name: String): String =
        get(name)?.takeIf(String::isNotBlank) ?: error("--$name is required")
}
