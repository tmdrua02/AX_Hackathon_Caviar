package com.haneul.medassist.domain.evidence

import com.haneul.medassist.domain.supplement.SupplementRawMaterialStatus
import com.haneul.medassist.domain.supplement.SupplementRawMaterials
import com.haneul.medassist.domain.supplement.SupplementRuleEvidenceStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EvidenceBundlesTest {
    @Test
    fun `supplement evidence never represents unqueried raw materials as an empty official list`() {
        val bundle = SupplementEvidenceBundle(product = null)

        assertEquals(SupplementRawMaterialStatus.NOT_IMPLEMENTED, bundle.rawMaterialStatus)
        assertEquals(SupplementRawMaterials.NotRequested, bundle.rawMaterials)
        assertEquals(SupplementRuleEvidenceStatus.NOT_EVALUATED, bundle.ruleEvidence)
        assertFalse(bundle.coverage.complete)
    }
}
