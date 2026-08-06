package com.haneul.medassist.repository

import com.haneul.medassist.config.SupplementInteractionRuleProperties
import com.haneul.medassist.domain.evidence.EvidenceVerificationStatus
import com.haneul.medassist.domain.evidence.VerifiedSourceReference
import com.haneul.medassist.domain.supplement.MappingType
import com.haneul.medassist.domain.supplement.SupplementIngredientCanonical
import com.haneul.medassist.domain.supplement.SupplementInteractionRule
import com.haneul.medassist.domain.supplement.SupplementProductIngredientMapping
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import tools.jackson.databind.ObjectMapper
import java.time.Instant

data class SupplementRuleCatalogDocument(
    val sources: List<VerifiedSourceReference> = emptyList(),
    val ingredients: List<SupplementIngredientCanonical> = emptyList(),
    val mappings: List<SupplementProductIngredientMapping> = emptyList(),
    val rules: List<SupplementInteractionRule> = emptyList(),
)

class JsonSupplementRuleCatalog(
    document: SupplementRuleCatalogDocument,
) : VerifiedSourceReferenceRepository,
    SupplementIngredientCanonicalRepository,
    SupplementProductIngredientMappingRepository,
    SupplementInteractionRuleRepository {
    private val sources = document.sources.associateUniqueBy("source", VerifiedSourceReference::id)
    private val ingredients = document.ingredients.associateUniqueBy("canonical ingredient", SupplementIngredientCanonical::id)
    private val mappings = document.mappings.associateUniqueBy("product ingredient mapping", SupplementProductIngredientMapping::id)
    private val rules = document.rules.associateUniqueBy("interaction rule", SupplementInteractionRule::id)

    init {
        validateReferences()
        validateDuplicateActiveRules()
    }

    override fun findById(id: String): VerifiedSourceReference? = sources[id]

    override fun findVerifiedByIds(ids: Set<String>): List<VerifiedSourceReference> =
        ids.mapNotNull(sources::get).filter(VerifiedSourceReference::isProductionEligible)

    override fun findVerifiedById(id: String): SupplementIngredientCanonical? =
        ingredients[id]?.takeIf(SupplementIngredientCanonical::isProductionEligible)

    override fun findVerifiedByStatementNo(
        statementNo: String,
        at: Instant,
    ): List<SupplementProductIngredientMapping> = mappings.values
        .filter { it.statementNo == statementNo && it.isProductionEligible(at) }
        .sortedBy(SupplementProductIngredientMapping::id)

    override fun findVerified(
        drugIngredientCode: String,
        supplementIngredientCanonicalId: String,
        at: Instant,
    ): List<SupplementInteractionRule> = rules.values
        .filter {
            it.drugIngredientCode == drugIngredientCode &&
                it.supplementIngredientCanonicalId == supplementIngredientCanonicalId &&
                it.isProductionEligible(at)
        }
        .sortedBy(SupplementInteractionRule::id)

    override fun isAvailable(): Boolean = true

    private fun validateReferences() {
        ingredients.values.forEach { ingredient ->
            val source = requireSource(ingredient.sourceReferenceId, "canonical ingredient ${ingredient.id}")
            if (ingredient.verificationStatus == EvidenceVerificationStatus.VERIFIED) {
                require(source.isProductionEligible()) {
                    "VERIFIED canonical ingredient ${ingredient.id} requires a VERIFIED source"
                }
            }
        }
        mappings.values.forEach { mapping ->
            val source = requireSource(mapping.sourceReferenceId, "mapping ${mapping.id}")
            val ingredient = requireNotNull(ingredients[mapping.supplementIngredientCanonicalId]) {
                "mapping ${mapping.id} references a missing canonical ingredient"
            }
            if (mapping.verificationStatus == EvidenceVerificationStatus.VERIFIED) {
                require(mapping.mappingType != MappingType.UNVERIFIED_CANDIDATE) {
                    "VERIFIED mapping ${mapping.id} cannot be UNVERIFIED_CANDIDATE"
                }
                require(source.isProductionEligible()) { "VERIFIED mapping ${mapping.id} requires a VERIFIED source" }
                require(ingredient.isProductionEligible()) {
                    "VERIFIED mapping ${mapping.id} requires a VERIFIED canonical ingredient"
                }
            }
        }
        rules.values.forEach { rule ->
            val ingredient = requireNotNull(ingredients[rule.supplementIngredientCanonicalId]) {
                "rule ${rule.id} references a missing canonical ingredient"
            }
            val referencedSources = rule.sourceReferenceIds.map { requireSource(it, "rule ${rule.id}") }
            if (rule.verificationStatus == EvidenceVerificationStatus.VERIFIED) {
                require(ingredient.isProductionEligible()) {
                    "VERIFIED rule ${rule.id} requires a VERIFIED canonical ingredient"
                }
                require(referencedSources.all(VerifiedSourceReference::isProductionEligible)) {
                    "VERIFIED rule ${rule.id} requires only VERIFIED sources"
                }
            }
        }
    }

    private fun requireSource(id: String, owner: String): VerifiedSourceReference =
        requireNotNull(sources[id]) { "$owner references a missing source" }

    private fun validateDuplicateActiveRules() {
        rules.values
            .filter { it.verificationStatus == EvidenceVerificationStatus.VERIFIED }
            .groupBy { it.drugIngredientCode to it.supplementIngredientCanonicalId }
            .values
            .forEach { samePair ->
                samePair.forEachIndexed { index, left ->
                    samePair.drop(index + 1).forEach { right ->
                        require(!periodsOverlap(left.validFrom, left.validTo, right.validFrom, right.validTo)) {
                            "duplicate active VERIFIED rules for ${left.drugIngredientCode} and " +
                                left.supplementIngredientCanonicalId
                        }
                    }
                }
            }
    }

    private fun periodsOverlap(
        leftFrom: Instant?,
        leftTo: Instant?,
        rightFrom: Instant?,
        rightTo: Instant?,
    ): Boolean {
        val leftStartsBeforeRightEnds = rightTo == null || leftFrom == null || !leftFrom.isAfter(rightTo)
        val rightStartsBeforeLeftEnds = leftTo == null || rightFrom == null || !rightFrom.isAfter(leftTo)
        return leftStartsBeforeRightEnds && rightStartsBeforeLeftEnds
    }

    private fun <T> List<T>.associateUniqueBy(label: String, key: (T) -> String): Map<String, T> {
        val result = linkedMapOf<String, T>()
        forEach { value ->
            val id = key(value)
            require(result.putIfAbsent(id, value) == null) { "duplicate $label id: $id" }
        }
        return result
    }
}

@Configuration
class SupplementInteractionRuleCatalogConfiguration {
    @Bean
    fun supplementRuleCatalog(
        properties: SupplementInteractionRuleProperties,
        resourceLoader: ResourceLoader,
        objectMapper: ObjectMapper,
    ): JsonSupplementRuleCatalog {
        val resource = resourceLoader.getResource(properties.resource)
        require(resource.exists()) { "supplement interaction rule catalog does not exist" }
        val document = resource.inputStream.use {
            objectMapper.readValue(it, SupplementRuleCatalogDocument::class.java)
        }
        return JsonSupplementRuleCatalog(document)
    }
}
