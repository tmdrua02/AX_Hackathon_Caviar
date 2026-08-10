package com.haneul.medassist.data

import kotlinx.serialization.Serializable

@Serializable
data class Counts(val total: Int, val prescriptions: Int, val supplements: Int, val otc: Int = 0)

@Serializable
data class Ingredient(
    val displayName: String,
    val normalizedName: String,
    val providerCode: String? = null,
    val amount: Double? = null,
    val unit: String? = null,
)

@Serializable
enum class ProductType { PRESCRIPTION_DRUG, OTC_DRUG, HEALTH_SUPPLEMENT, UNKNOWN }

@Serializable
data class Medication(
    val id: String,
    val name: String,
    val productType: ProductType,
    val productCode: String? = null,
    val manufacturer: String? = null,
    val active: Boolean = true,
    val ingredients: List<Ingredient> = emptyList(),
    val dose: String? = null,
    val time: String? = null,
    val timing: String? = null,
    val taken: Boolean = false,
    val version: Long = 0,
    val startDate: String? = null,
    val endDate: String? = null,
    val timesPerDay: Int? = null,
    val doseValue: Double? = null,
    val doseUnit: String? = null,
)

@Serializable
data class HomeResponse(
    val greeting: String,
    val subtitle: String,
    val counts: Counts,
    val todayMedications: List<Medication>,
    val disclaimer: String,
)

@Serializable
data class DoseLogRequest(
    val scheduledAt: String,
    val status: String,
    val takenAt: String? = null,
    val expectedVersion: Long,
)

@Serializable
data class ProductCandidate(
    val name: String,
    val productCode: String,
    val manufacturer: String,
    val confidence: Int,
    val source: String,
)

@Serializable
data class PrescriptionDraft(
    val id: String,
    val status: String,
    val productName: String,
    val dose: String,
    val timesPerDay: Int,
    val days: Int,
    val timing: String,
    val manufacturer: String? = null,
    val productCode: String? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val efficacy: String? = null,
    val matchConfidence: Int,
    val source: String,
    val candidates: List<ProductCandidate> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class DraftUpdate(
    val productName: String,
    val dose: String,
    val timesPerDay: Int,
    val days: Int,
    val timing: String,
    val productCode: String?,
    val manufacturer: String?,
    val ingredients: List<Ingredient>,
)

@Serializable
data class InteractionRequest(val newMedicationId: String, val existingMedicationIds: List<String>)

@Serializable
data class Accepted(val resourceId: String, val jobId: String, val status: String)

@Serializable
data class Evidence(
    val ingredientA: String? = null,
    val ingredientB: String? = null,
    val evidenceType: String? = null,
    val sourceName: String,
    val sourceUrl: String,
    val sourceRecordId: String? = null,
    val sourceDate: String? = null,
    val retrievedAt: String,
    val originalSummary: String? = null,
    val sourceType: String,
)

@Serializable
enum class Severity { PROHIBITED, CAUTION, DUPLICATE_OR_SIMILAR, NO_KNOWN_ISSUE, UNKNOWN }

@Serializable
data class InteractionResult(
    val id: String,
    val newMedication: Medication,
    val existingMedication: Medication,
    val severity: Severity,
    val title: String,
    val easyExplanation: String,
    val evidence: List<Evidence>,
)

@Serializable
data class Coverage(
    val identifiedIngredients: Int,
    val successfulQueries: Int,
    val unidentifiedIngredients: Int,
    val providerError: Boolean,
)

@Serializable
data class InteractionCheck(
    val id: String,
    val jobId: String,
    val status: String,
    val results: List<InteractionResult>,
    val coverage: Coverage,
    val saved: Boolean,
    val disclaimer: String,
)

@Serializable
data class TranscriptSegment(
    val id: String,
    val speaker: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

@Serializable
data class SummaryItem(val text: String, val evidenceSegmentIds: List<String>)

@Serializable
data class ConsultationSummary(
    val overallSummary: String,
    val symptoms: List<SummaryItem>,
    val testsAndAssessment: List<SummaryItem>,
    val prescriptionAndInstructions: List<SummaryItem>,
    val followUps: List<SummaryItem>,
    val uncertainties: List<SummaryItem>,
)

@Serializable
data class Consultation(
    val id: String,
    val title: String,
    val hospitalName: String? = null,
    val consultedAt: String,
    val durationMs: Long,
    val status: String,
    val transcript: List<TranscriptSegment> = emptyList(),
    val summary: ConsultationSummary? = null,
    val failureCode: String? = null,
    val failureMessage: String? = null,
)

@Serializable
data class ChatSession(val id: String, val createdAt: String)

@Serializable
data class ChatMessageRequest(val message: String)

@Serializable
data class ChatMessageAccepted(val messageId: String, val streamUrl: String)

sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Content<T>(val value: T, val offline: Boolean = false) : LoadState<T>
    data object Empty : LoadState<Nothing>
    data class Error(val message: String, val retryable: Boolean = true) : LoadState<Nothing>
}

fun demoHome() = HomeResponse(
    greeting = "안녕하세요, 하늘님",
    subtitle = "오늘 복용해야 할 약을 확인하세요.",
    counts = Counts(3, 1, 1),
    todayMedications = listOf(
        Medication(
            "11111111-1111-1111-1111-111111111111", "타이레놀", ProductType.OTC_DRUG,
            ingredients = listOf(Ingredient("아세트아미노펜", "acetaminophen")),
            dose = "1정", time = "09:00", timing = "식후",
        ),
        Medication(
            "22222222-2222-2222-2222-222222222222", "해열 시럽 A", ProductType.PRESCRIPTION_DRUG,
            ingredients = listOf(Ingredient("이부프로펜", "ibuprofen")),
            dose = "10mL", time = "20:00", timing = "식후", taken = true,
        ),
    ),
    disclaimer = "정보 제공용이며 복용 변경 전 의사·약사와 상담하세요.",
)

fun demoDraft() = PrescriptionDraft(
    id = "local-draft",
    status = "NEEDS_CONFIRMATION",
    productName = "종합감기약 데모",
    dose = "1정",
    timesPerDay = 3,
    days = 3,
    timing = "식후 30분",
    manufacturer = "데모제약",
    productCode = "DEMO-COLD-01",
    ingredients = listOf(Ingredient("아세트아미노펜", "acetaminophen", amount = 325.0, unit = "mg")),
    efficacy = "감기 증상의 완화(데모)",
    matchConfidence = 86,
    source = "제품 허가정보 mock snapshot",
    candidates = listOf(
        ProductCandidate("종합감기약 데모", "DEMO-COLD-01", "데모제약", 86, "공공데이터 mock"),
        ProductCandidate("종합감기약 데모 정", "DEMO-COLD-02", "데모제약", 81, "공공데이터 mock"),
    ),
    warnings = listOf("OCR 결과를 확인하고 수정해 주세요.", "제품 후보가 둘 이상입니다."),
)
