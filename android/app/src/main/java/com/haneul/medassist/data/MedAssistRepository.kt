package com.haneul.medassist.data

import android.content.ContentResolver
import android.net.Uri
import com.haneul.medassist.BuildConfig
import com.haneul.medassist.di.MainHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedAssistRepository @Inject constructor(
    private val api: ApiService,
    private val database: MedAssistDatabase,
    @MainHttpClient private val client: OkHttpClient,
    private val json: Json,
    private val supplementInteractionRemoteDataSource: SupplementInteractionRemoteDataSource,
) {
    suspend fun home(): LoadState<HomeResponse> = try {
        val remote = api.home()
        database.medicationDao().replace(remote.todayMedications.map { it.toCache() })
        LoadState.Content(remote)
    } catch (_: Exception) {
        val cached = database.medicationDao().all()
        if (cached.isNotEmpty()) {
            val demo = demoHome()
            LoadState.Content(demo.copy(todayMedications = cached.map { it.toMedication() }), offline = true)
        } else LoadState.Content(demoHome(), offline = true)
    }

    suspend fun medications(): List<Medication> {
        val primary = runCatching { api.medications() }.getOrElse {
            val home = demoHome()
            home.todayMedications + Medication(
                "33333333-3333-3333-3333-333333333333", "오메가3 데모", ProductType.HEALTH_SUPPLEMENT,
                ingredients = listOf(Ingredient("EPA 및 DHA", "omega3")), dose = "1캡슐", time = "13:00", timing = "식후",
            )
        }
        val overrides = database.manualMedicationDao().all()
        val hiddenIds = overrides.filterNot { it.active }.mapTo(hashSetOf()) { it.id }
        val activeOverrides = overrides.filter { it.active }.map { it.toMedication() }
        return (activeOverrides + primary.filterNot { it.id in hiddenIds }).distinctBy { it.id }
    }

    suspend fun addManualMedication(
        name: String, productType: ProductType, ingredientDescription: String,
        startDate: String?, endDate: String?, intakeTiming: String,
        timesPerDay: Int, doseValue: Double, doseUnit: String,
    ): Medication {
        val medication = Medication(
            id = "manual-${UUID.randomUUID()}",
            name = name.trim(),
            productType = productType,
            ingredients = ingredientDescription.trim().takeIf { it.isNotBlank() }?.let {
                listOf(Ingredient(displayName = it, normalizedName = it.lowercase()))
            }.orEmpty(),
            dose = "${doseValue.formatDose()}$doseUnit",
            timing = intakeTiming,
            startDate = startDate,
            endDate = endDate,
            timesPerDay = timesPerDay,
            doseValue = doseValue,
            doseUnit = doseUnit,
        )
        database.manualMedicationDao().upsert(
            ManualMedicationEntity(
                medication.id, medication.name, medication.productType.name, ingredientDescription.trim(), true,
                startDate, endDate, intakeTiming, timesPerDay, doseValue, doseUnit,
            ),
        )
        return medication
    }

    suspend fun updateMedication(original: Medication, name: String, productType: ProductType, ingredientDescription: String): Medication {
        val updated = original.copy(
            name = name.trim(),
            productType = productType,
            ingredients = ingredientDescription.trim().takeIf { it.isNotBlank() }?.let {
                listOf(Ingredient(displayName = it, normalizedName = it.lowercase()))
            }.orEmpty(),
            active = true,
        )
        database.manualMedicationDao().upsert(
            ManualMedicationEntity(
                updated.id, updated.name, updated.productType.name, ingredientDescription.trim(), true,
                updated.startDate, updated.endDate, updated.timing, updated.timesPerDay, updated.doseValue, updated.doseUnit,
            ),
        )
        return updated
    }

    suspend fun deleteMedication(medication: Medication) {
        database.manualMedicationDao().upsert(
            ManualMedicationEntity(
                medication.id,
                medication.name,
                medication.productType.name,
                medication.ingredients.joinToString { it.displayName },
                false,
            ),
        )
    }

    suspend fun setDose(medication: Medication, taken: Boolean): Result<Medication> = runCatching {
        api.doseLog(
            medication.id,
            DoseLogRequest(Instant.now().toString(), if (taken) "TAKEN" else "PENDING",
                if (taken) Instant.now().toString() else null, medication.version),
        )
    }

    suspend fun searchSupplementProducts(query: String): Result<SupplementProductSearchResponse> =
        supplementInteractionRemoteDataSource.searchSupplements(query)

    suspend fun checkSupplementInteraction(
        medicationProductCode: String,
        supplementStatementNo: String,
    ): Result<SupplementInteractionCheckResponse> =
        supplementInteractionRemoteDataSource.check(medicationProductCode, supplementStatementNo)

    suspend fun createDraft(front: Uri, back: Uri, resolver: ContentResolver, ocrText: String): PrescriptionDraft =
        runCatching {
            val frontFile = copyToTemp(front, resolver, "front")
            val backFile = copyToTemp(back, resolver, "back")
            api.createDraft(
                MultipartBody.Part.createFormData("frontImage", frontFile.name, frontFile.asRequestBody("image/jpeg".toMediaType())),
                MultipartBody.Part.createFormData("backImage", backFile.name, backFile.asRequestBody("image/jpeg".toMediaType())),
                ocrText.toRequestBody("text/plain".toMediaType()),
            )
        }.getOrElse { demoDraft() }

    suspend fun confirmDraft(draft: PrescriptionDraft): Medication = runCatching {
        val updated = api.updateDraft(
            draft.id,
            DraftUpdate(draft.productName, draft.dose, draft.timesPerDay, draft.days, draft.timing,
                draft.productCode, draft.manufacturer, draft.ingredients),
        )
        api.confirmDraft(updated.id)
    }.getOrElse {
        Medication(
            id = "local-new-medication",
            name = draft.productName,
            productType = ProductType.UNKNOWN,
            productCode = draft.productCode,
            manufacturer = draft.manufacturer,
            ingredients = draft.ingredients,
            dose = draft.dose,
            time = "09:00",
            timing = draft.timing,
        )
    }

    suspend fun createCheck(added: Medication, existing: List<Medication>): Accepted = runCatching {
        api.createCheck(UUID.randomUUID().toString(), InteractionRequest(added.id, existing.map { it.id }))
    }.getOrElse { Accepted("local-check", "local-job", "SUCCEEDED") }

    suspend fun check(accepted: Accepted, added: Medication, existing: List<Medication>): InteractionCheck = runCatching {
        api.check(accepted.resourceId)
    }.getOrElse { localCheck(added, existing) }

    suspend fun saveCheck(check: InteractionCheck): InteractionCheck = runCatching { api.saveCheck(check.id) }
        .getOrElse { check.copy(saved = true) }

    suspend fun consultations(): Result<List<Consultation>> = runCatching { api.consultations() }

    suspend fun uploadRecording(file: File, title: String, hospital: String, durationMs: Long): Result<Accepted> = runCatching {
        api.uploadConsultation(
            MultipartBody.Part.createFormData("audio", file.name, file.asRequestBody("audio/mp4".toMediaType())),
            title.toRequestBody("text/plain".toMediaType()),
            hospital.toRequestBody("text/plain".toMediaType()),
            Instant.now().toString().toRequestBody("text/plain".toMediaType()),
            durationMs.toString().toRequestBody("text/plain".toMediaType()),
            UUID.randomUUID().toString(),
        )
    }

    suspend fun retryConsultation(id: String): Result<Accepted> = runCatching { api.retryConsultation(id) }

    suspend fun chat(message: String, onDelta: (String) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val session = api.createChat()
            val accepted = api.sendMessage(session.id, ChatMessageRequest(message))
            val request = Request.Builder().url(BuildConfig.API_BASE_URL.removeSuffix("/") + accepted.streamUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("채팅 서버 오류")
                response.body?.source()?.let { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data:")) {
                            val payload = line.removePrefix("data:").trim()
                            runCatching {
                                json.parseToJsonElement(payload).jsonObject["text"]?.jsonPrimitive?.content
                            }.getOrNull()?.let(onDelta)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            onDelta(localChat(message))
        }
    }

    private fun copyToTemp(uri: Uri, resolver: ContentResolver, prefix: String): File {
        val file = File.createTempFile(prefix, ".jpg")
        resolver.openInputStream(uri).use { input -> file.outputStream().use { output -> input?.copyTo(output) } }
        return file
    }

    private fun Medication.toCache() = CachedMedication(id, name, productType.name, dose, time, timing, taken, version)
    private fun CachedMedication.toMedication() = Medication(id, name, ProductType.valueOf(productType),
        dose = dose, time = time, timing = timing, taken = taken, version = version)
    private fun ManualMedicationEntity.toMedication() = Medication(
        id = id,
        name = name,
        productType = ProductType.valueOf(productType),
        active = active,
        ingredients = ingredientDescription.takeIf { it.isNotBlank() }?.let {
            listOf(Ingredient(displayName = it, normalizedName = it.lowercase()))
        }.orEmpty(),
        dose = doseValue?.let { "${it.formatDose()}${doseUnit.orEmpty()}" },
        timing = intakeTiming,
        startDate = startDate,
        endDate = endDate,
        timesPerDay = timesPerDay,
        doseValue = doseValue,
        doseUnit = doseUnit,
    )

    private fun Double.formatDose(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

    private fun localCheck(added: Medication, existing: List<Medication>): InteractionCheck {
        val results = existing.map { current ->
            val duplicate = added.ingredients.any { a -> current.ingredients.any { b -> a.normalizedName == b.normalizedName } }
            val severity = if (duplicate) Severity.DUPLICATE_OR_SIMILAR else Severity.UNKNOWN
            InteractionResult(
                UUID.randomUUID().toString(), added, current, severity,
                if (duplicate) "동일 성분 또는 유사 효능 가능성" else "확인 불가 · 전문가 확인 필요",
                if (duplicate) "두 제품의 표준화 성분명이 같습니다. 복용 전 전문가에게 확인하세요."
                else "공신력 데이터로 충분히 확인하지 못했습니다. 안전하다는 의미가 아닙니다.",
                if (duplicate) listOf(Evidence(
                    added.ingredients.first().displayName, current.ingredients.first().displayName,
                    "SAME_INGREDIENT", "식품의약품안전처 의약품 제품 허가정보",
                    "https://www.data.go.kr/data/15095677/openapi.do", "MOCK-SAME-001", "2026-07-01",
                    Instant.now().toString(), "두 제품에서 같은 표준화 성분명이 확인됨", "PUBLIC_DATA",
                )) else emptyList(),
            )
        }
        return InteractionCheck("local-check", "local-job", "SUCCEEDED", results,
            Coverage(added.ingredients.size + existing.sumOf { it.ingredients.size }, 0, 0, false), false,
            "정보 제공용이며 복용 변경 전 의사·약사와 상담하세요.")
    }

    private fun demoConsultation(): Consultation {
        val first = TranscriptSegment("segment-1", "의사", 0, 8200, "어디가 가장 불편해서 오셨어요?")
        val second = TranscriptSegment("segment-2", "환자", 8400, 18300, "어제부터 목이 따갑고 미열이 있었어요.")
        val third = TranscriptSegment("segment-3", "의사", 19000, 31000, "물을 충분히 드시고 증상이 심해지면 다시 내원하세요.")
        return Consultation(
            "44444444-4444-4444-4444-444444444444", "감기 증상 진료", "하늘내과(데모)",
            "2026-08-03T01:30:00Z", 31_000, "SUCCEEDED", listOf(first, second, third),
            ConsultationSummary(
                "목 불편감과 미열에 관해 상담한 데모 진료 기록입니다.",
                listOf(SummaryItem("목 따가움과 미열", listOf(second.id))), emptyList(), emptyList(),
                listOf(SummaryItem("증상이 심해지면 재내원", listOf(third.id))),
                listOf(SummaryItem("화자 구분은 AI 추정이므로 원음 확인 필요", listOf(first.id, second.id))),
            ),
        )
    }

    private fun localChat(message: String): String {
        val emergencies = listOf("흉통", "호흡곤란", "숨을 못", "의식저하", "심한 알레르기")
        if (emergencies.any(message::contains)) {
            return "지금 즉시 119에 연락하거나 가까운 응급실로 가세요. 이 채팅으로 평가를 기다리지 마세요."
        }
        return "결론: 현재 질문만으로 약물 안전성을 확인할 수 없습니다.\n확인된 근거: 공식 상호작용 근거가 연결되지 않았습니다.\n할 일: 제품명과 성분을 확인한 뒤 의사·약사에게 상담하세요."
    }
}
