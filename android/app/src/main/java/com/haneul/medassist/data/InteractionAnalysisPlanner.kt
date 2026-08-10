package com.haneul.medassist.data

internal data class MedicationPair(val left: Medication, val right: Medication)

/** Pure selection and pair planning rules shared by the UI and analysis request path. */
internal object InteractionAnalysisPlanner {
    fun selectedActiveMedications(
        medications: List<Medication>,
        selectedIds: Set<String>,
    ): List<Medication> = medications
        .asSequence()
        .filter { it.active && it.id in selectedIds }
        .distinctBy { it.id }
        .toList()

    fun reconcileSelection(
        medications: List<Medication>,
        selectedIds: Set<String>,
    ): Set<String> = selectedActiveMedications(medications, selectedIds).mapTo(linkedSetOf()) { it.id }

    fun canAnalyze(medications: List<Medication>, selectedIds: Set<String>): Boolean =
        selectedActiveMedications(medications, selectedIds).size >= 2

    fun allPairs(selected: List<Medication>): List<MedicationPair> {
        val unique = selected.filter { it.active }.distinctBy { it.id }
        return buildList {
            for (leftIndex in 0 until unique.lastIndex) {
                for (rightIndex in leftIndex + 1 until unique.size) {
                    add(MedicationPair(unique[leftIndex], unique[rightIndex]))
                }
            }
        }
    }

    /**
     * The server accepts one reference product and up to 20 comparison products. N selected drugs therefore
     * need N-1 triangular batch calls instead of N*(N-1)/2 individual calls.
     */
    fun officialDrugBatches(selected: List<Medication>): List<Pair<Medication, List<Medication>>> {
        val officialDrugs = selected
            .filter { it.active && it.productType != ProductType.HEALTH_SUPPLEMENT && !it.productCode.isNullOrBlank() }
            .distinctBy { it.id }
        return buildList {
            for (leftIndex in 0 until officialDrugs.lastIndex) {
                officialDrugs.drop(leftIndex + 1).chunked(20).forEach { comparisons ->
                    add(officialDrugs[leftIndex] to comparisons)
                }
            }
        }.filter { it.second.isNotEmpty() }
    }
}

internal class InteractionAnalysisRunGuard {
    private var activeRunId: String? = null

    @Synchronized
    fun begin(runId: String): Boolean {
        if (activeRunId != null) return false
        activeRunId = runId
        return true
    }

    @Synchronized
    fun finish(runId: String) {
        if (activeRunId == runId) activeRunId = null
    }

    @Synchronized
    fun cancel() {
        activeRunId = null
    }
}
