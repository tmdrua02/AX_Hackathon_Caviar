package com.haneul.medassist.support

import com.haneul.medassist.domain.evidence.EvidenceAuthority
import com.haneul.medassist.domain.evidence.EvidenceVerificationStatus
import com.haneul.medassist.domain.evidence.VerifiedSourceReference
import com.haneul.medassist.domain.medication.Ingredient
import com.haneul.medassist.domain.medication.SourceMetadata
import com.haneul.medassist.domain.medication.VerifiedDrugProduct
import com.haneul.medassist.domain.supplement.InteractionType
import com.haneul.medassist.domain.supplement.MappingType
import com.haneul.medassist.domain.supplement.SupplementIngredientCanonical
import com.haneul.medassist.domain.supplement.SupplementInteractionRule
import com.haneul.medassist.domain.supplement.SupplementInteractionSeverity
import com.haneul.medassist.domain.supplement.SupplementProductCoverage
import com.haneul.medassist.domain.supplement.SupplementProductIngredientMapping
import com.haneul.medassist.domain.supplement.SupplementProductSnapshot
import com.haneul.medassist.repository.JsonSupplementRuleCatalog
import com.haneul.medassist.repository.SupplementRuleCatalogDocument
import java.time.Instant

val FIXED_TIME: Instant = Instant.parse("2026-08-06T00:00:00Z")

fun verifiedSource(
    id: String = "SRC-1",
    status: EvidenceVerificationStatus = EvidenceVerificationStatus.VERIFIED,
    originalText: String = "검수된 공식 근거 원문",
) = VerifiedSourceReference(
    id = id,
    authority = EvidenceAuthority.OTHER_OFFICIAL,
    title = "검수 근거",
    sourceUrl = "https://official.example/evidence/$id",
    originalText = originalText,
    retrievedAt = FIXED_TIME,
    verificationStatus = status,
    reviewedBy = if (status == EvidenceVerificationStatus.VERIFIED) "pharmacist-reviewer" else null,
    reviewedAt = if (status == EvidenceVerificationStatus.VERIFIED) FIXED_TIME else null,
)

fun canonicalIngredient(
    id: String = "CAN-1",
    sourceId: String = "SRC-1",
    status: EvidenceVerificationStatus = EvidenceVerificationStatus.VERIFIED,
    aliases: Set<String> = setOf("검수 별칭"),
) = SupplementIngredientCanonical(
    id = id,
    canonicalName = "canonical-$id",
    displayName = "기능성 원료 $id",
    aliases = aliases,
    providerCode = null,
    category = "CURATED_TEST_CATEGORY",
    sourceReferenceId = sourceId,
    verificationStatus = status,
    createdAt = FIXED_TIME,
    updatedAt = FIXED_TIME,
)

fun verifiedMapping(
    id: String = "MAP-1",
    statementNo: String = "S-1",
    canonicalId: String = "CAN-1",
    sourceId: String = "SRC-1",
    status: EvidenceVerificationStatus = EvidenceVerificationStatus.VERIFIED,
    mappingType: MappingType = MappingType.MANUAL_VERIFIED,
) = SupplementProductIngredientMapping(
    id = id,
    statementNo = statementNo,
    productName = "검수 건강기능식품",
    supplementIngredientCanonicalId = canonicalId,
    ingredientDisplayName = "기능성 원료 $canonicalId",
    mappingType = mappingType,
    sourceField = "HUMAN_REVIEWED_OFFICIAL_SOURCE",
    sourceReferenceId = sourceId,
    verificationStatus = status,
    createdAt = FIXED_TIME,
    updatedAt = FIXED_TIME,
)

fun verifiedRule(
    id: String = "RULE-1",
    drugCode: String = "D-1",
    canonicalId: String = "CAN-1",
    sourceId: String = "SRC-1",
    severity: SupplementInteractionSeverity = SupplementInteractionSeverity.CAUTION,
    status: EvidenceVerificationStatus = EvidenceVerificationStatus.VERIFIED,
    validFrom: Instant? = null,
    validTo: Instant? = null,
) = SupplementInteractionRule(
    id = id,
    drugIngredientCode = drugCode,
    drugIngredientName = "공식 의약품 성분 $drugCode",
    supplementIngredientCanonicalId = canonicalId,
    severity = severity,
    interactionType = InteractionType.OTHER,
    mechanismSummary = null,
    userMessage = "검수된 주의 문구",
    recommendation = "의사 또는 약사와 상담하세요.",
    sourceReferenceIds = setOf(sourceId),
    verificationStatus = status,
    validFrom = validFrom,
    validTo = validTo,
    createdAt = FIXED_TIME,
    updatedAt = FIXED_TIME,
)

fun testCatalog(
    sources: List<VerifiedSourceReference> = listOf(verifiedSource()),
    ingredients: List<SupplementIngredientCanonical> = listOf(canonicalIngredient()),
    mappings: List<SupplementProductIngredientMapping> = listOf(verifiedMapping()),
    rules: List<SupplementInteractionRule> = emptyList(),
) = JsonSupplementRuleCatalog(SupplementRuleCatalogDocument(sources, ingredients, mappings, rules))

fun verifiedDrugProduct(code: String = "P-1") = VerifiedDrugProduct(
    productCode = code,
    productName = "공식 처방약",
    manufacturer = "공식 제약사",
    source = sourceMetadata(code),
)

fun officialDrugIngredient(code: String? = "D-1", name: String = "공식 의약품 성분") = Ingredient(
    providerCode = code,
    displayName = name,
    koreanName = name,
    englishName = null,
    normalizedName = name,
    amount = null,
    unit = null,
    source = sourceMetadata(code ?: "P-1"),
)

fun supplementSnapshot(statementNo: String = "S-1") = SupplementProductSnapshot(
    statementNo = statementNo,
    productName = "공식 건강기능식품",
    manufacturer = "공식 업체",
    registerDate = null,
    distributionPeriod = null,
    appearance = null,
    usage = null,
    storage = null,
    intakeHint = null,
    mainFunction = null,
    baseStandard = null,
    coverage = SupplementProductCoverage(true, true, true),
    retrievedAt = FIXED_TIME,
    source = sourceMetadata(statementNo),
    rawProviderRecord = emptyMap(),
)

private fun sourceMetadata(recordId: String) = SourceMetadata(
    name = "공식 테스트 provider",
    recordId = recordId,
    retrievedAt = FIXED_TIME,
    providerReference = "https://official.example/provider",
)
