package com.haneul.medassist.domain.evidence

import com.haneul.medassist.domain.interaction.Evidence
import com.haneul.medassist.domain.medication.DrugOverviewLookupResult
import com.haneul.medassist.domain.medication.Ingredient
import com.haneul.medassist.domain.medication.Medication
import com.haneul.medassist.domain.supplement.SupplementProduct
import com.haneul.medassist.domain.supplement.SupplementRawMaterialStatus
import com.haneul.medassist.domain.supplement.SupplementRawMaterials
import com.haneul.medassist.domain.supplement.SupplementRuleEvidenceStatus

data class EvidenceBundleCoverage(
    val complete: Boolean,
    val failedStages: Set<String>,
)

data class MedicationEvidenceBundle(
    val medication: Medication,
    val ingredients: List<Ingredient>,
    val overview: DrugOverviewLookupResult?,
    val durEvidence: List<Evidence>,
    val coverage: EvidenceBundleCoverage,
)

data class SupplementEvidenceBundle(
    val product: SupplementProduct?,
    val rawMaterialStatus: SupplementRawMaterialStatus = SupplementRawMaterialStatus.NOT_IMPLEMENTED,
    val rawMaterials: SupplementRawMaterials = SupplementRawMaterials.NotRequested,
    val ruleEvidence: SupplementRuleEvidenceStatus = SupplementRuleEvidenceStatus.NOT_EVALUATED,
    val coverage: EvidenceBundleCoverage = EvidenceBundleCoverage(
        complete = false,
        failedStages = setOf("RAW_MATERIALS", "SUPPLEMENT_RULES"),
    ),
)
