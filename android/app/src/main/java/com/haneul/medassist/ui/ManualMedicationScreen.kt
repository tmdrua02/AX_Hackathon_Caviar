package com.haneul.medassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.haneul.medassist.AppViewModel
import com.haneul.medassist.data.PrescriptionDraft
import com.haneul.medassist.data.ProductType
import com.haneul.medassist.ui.theme.Primary
import com.haneul.medassist.ui.theme.SurfaceSoft

@Composable
fun ManualMedicationScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    title: String = "직접 약 추가",
    initialDraft: PrescriptionDraft? = null,
    onSaved: () -> Unit = onBack,
) {
    val parsedDose = remember(initialDraft?.id) { initialDraft?.dose?.let(::parseClearDose) }
    var name by remember(initialDraft?.id) { mutableStateOf(initialDraft?.productName.orEmpty()) }
    var productType by remember { mutableStateOf(ProductType.PRESCRIPTION_DRUG) }
    var description by remember(initialDraft?.id) { mutableStateOf(initialDraft?.ingredients.orEmpty().joinToString { it.displayName }) }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var timing by remember(initialDraft?.id) {
        mutableStateOf(initialDraft?.timing?.takeIf { it in standardTimings } ?: if (initialDraft?.timing.isNullOrBlank()) "식후 30분" else CUSTOM_TIMING)
    }
    var customTiming by remember(initialDraft?.id) { mutableStateOf(initialDraft?.timing?.takeUnless { it in standardTimings }.orEmpty()) }
    var timesPerDay by remember(initialDraft?.id) { mutableStateOf(initialDraft?.timesPerDay?.toString() ?: "1") }
    var doseValue by remember(initialDraft?.id) { mutableStateOf(parsedDose?.first.orEmpty()) }
    var doseUnit by remember(initialDraft?.id) { mutableStateOf(parsedDose?.second ?: "mL") }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val savedTiming = if (timing == CUSTOM_TIMING) customTiming.trim() else timing

    Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F8)).statusBarsPadding().imePadding()) {
        AppPageHeader(title, onBack = onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("약 이름") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Text("복용 기간", style = MaterialTheme.typography.titleMedium)
            DateRangeSelectionField(startDate, endDate) { showDateRangePicker = true }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("약 분류", style = MaterialTheme.typography.titleMedium)
                listOf(
                    ProductType.PRESCRIPTION_DRUG to "처방약",
                    ProductType.OTC_DRUG to "일반약",
                    ProductType.HEALTH_SUPPLEMENT to "건강기능식품",
                ).forEach { (type, label) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { productType = type },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (productType == type) Color(0xFFEAF6FF) else SurfaceSoft),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = productType == type, onClick = { productType = type })
                            Text(label, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("복용 시점", style = MaterialTheme.typography.titleMedium)
                standardTimings.chunked(2).forEach { options ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option -> MedicationChoiceChip(option, timing == option, Modifier.weight(1f)) { timing = option } }
                    }
                }
                if (timing == CUSTOM_TIMING) {
                    OutlinedTextField(
                        value = customTiming,
                        onValueChange = { customTiming = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("예: 운동 후", color = Color(0xFFB9BDC6)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
            Text("복용 횟수", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(timesPerDay, { timesPerDay = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp))
            Text("약 용량", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(doseValue, { doseValue = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), placeholder = { Text(if (doseUnit == "알") "1" else "12") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(16.dp))
                Row(Modifier.weight(.7f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("mL", "알").forEach { unit ->
                        MedicationChoiceChip(unit, doseUnit == unit, Modifier.weight(1f)) { doseUnit = unit }
                    }
                }
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().height(130.dp),
                label = { Text("성분명 또는 설명 (선택)") },
                shape = RoundedCornerShape(16.dp),
            )
            Button(
                onClick = { viewModel.addManualMedication(name, productType, description, startDate, endDate, savedTiming, timesPerDay.toIntOrNull() ?: 1, doseValue.toDoubleOrNull() ?: 0.0, doseUnit, onSaved) },
                enabled = name.isNotBlank() && startDate.isNotBlank() && endDate.isNotBlank() && savedTiming.isNotBlank() && (timesPerDay.toIntOrNull() ?: 0) > 0 && (doseValue.toDoubleOrNull() ?: 0.0) > 0,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) { Text("저장하기", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showDateRangePicker) {
        MedicationDateRangeDialog(startDate, endDate, { showDateRangePicker = false }) { start, end ->
            startDate = start.format(dateFormatter)
            endDate = end.format(dateFormatter)
            showDateRangePicker = false
        }
    }
}

@Composable
private fun MedicationChoiceChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(58.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFFE4F2FF) else SurfaceSoft,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Primary) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                textAlign = TextAlign.Center,
                color = if (selected) Color(0xFF175F9C) else Color(0xFF4F555E),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun DateSelectionField(value: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.height(60.dp).clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = SurfaceSoft) {
        Box(contentAlignment = Alignment.Center) {
            Text(value.ifBlank { "YYYY.MM.DD" }, color = if (value.isBlank()) Color(0xFFB9BDC6) else Color(0xFF27272A))
        }
    }
}

@Composable
private fun AppDateInputDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val today = LocalDate.now()
    val initialParts = initial.split('.')
    var year by remember { mutableStateOf(initialParts.getOrNull(0) ?: today.year.toString()) }
    var month by remember { mutableStateOf(initialParts.getOrNull(1) ?: today.monthValue.toString()) }
    var day by remember { mutableStateOf(initialParts.getOrNull(2) ?: today.dayOfMonth.toString()) }
    val date = runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 10.dp) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("날짜 선택", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("년" to year, "월" to month, "일" to day).forEach { (label, value) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { changed ->
                                val digits = changed.filter(Char::isDigit)
                                when (label) { "년" -> year = digits.take(4); "월" -> month = digits.take(2); else -> day = digits.take(2) }
                            },
                            modifier = Modifier.weight(if (label == "년") 1.35f else 1f),
                            label = { Text(label) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(15.dp)) { Text("취소") }
                    Button(
                        onClick = { date?.let { onConfirm(it.format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"))) } },
                        enabled = date != null, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(15.dp),
                    ) { Text("선택") }
                }
            }
        }
    }
}

private const val CUSTOM_TIMING = "직접 입력"
private val standardTimings = listOf("식전 30분", "식후 30분", "취침 전", CUSTOM_TIMING)
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

@Composable
private fun DateRangeSelectionField(startDate: String, endDate: String, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().height(60.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceSoft,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (startDate.isBlank() || endDate.isBlank()) "YYYY.MM.DD  ~  YYYY.MM.DD" else "$startDate  ~  $endDate",
                color = if (startDate.isBlank() || endDate.isBlank()) Color(0xFFB9BDC6) else Color(0xFF27272A),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationDateRangeDialog(
    initialStart: String,
    initialEnd: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart.toUtcMillis(),
        initialSelectedEndDateMillis = initialEnd.toUtcMillis(),
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 12.dp,
        ) {
            Column {
                DateRangePicker(
                    state = state,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    title = {
                        Text(
                            "복용 기간",
                            Modifier.padding(start = 24.dp, top = 20.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    headline = {
                        Text(
                            "시작일과 종료일을 선택하세요",
                            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    showModeToggle = false,
                )
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(15.dp)) {
                        Text("취소")
                    }
                    Button(
                        onClick = {
                            val start = state.selectedStartDateMillis?.toLocalDate() ?: return@Button
                            val end = state.selectedEndDateMillis?.toLocalDate() ?: return@Button
                            onConfirm(start, end)
                        },
                        enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) { Text("선택") }
                }
            }
        }
    }
}

private fun parseClearDose(raw: String): Pair<String, String>? {
    val match = Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*(mL|ml|ML|알|정)\\s*$").matchEntire(raw) ?: return null
    return match.groupValues[1] to if (match.groupValues[2].equals("ml", ignoreCase = true)) "mL" else "알"
}

private fun String.toUtcMillis(): Long? = runCatching {
    LocalDate.parse(this, dateFormatter).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrNull()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
