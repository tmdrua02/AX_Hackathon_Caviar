package com.haneul.medassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import com.haneul.medassist.AppUiState
import com.haneul.medassist.AppViewModel
import com.haneul.medassist.data.Counts
import com.haneul.medassist.data.MedicationAlarm
import com.haneul.medassist.data.Medication
import com.haneul.medassist.data.ProductType
import com.haneul.medassist.ui.theme.Muted
import com.haneul.medassist.ui.theme.Primary
import com.haneul.medassist.ui.theme.SurfaceSoft
import java.time.LocalDate

enum class MedicationListFilter { ALL, PRESCRIPTION, OTC, SUPPLEMENT }

@Composable
fun HomeScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    padding: PaddingValues,
    onMedicationsClick: () -> Unit,
) {
    val today = LocalDate.now()
    val todayAlarms = alarmsForDate(state.medicationAlarms, today)
    val completedAlarmIds = state.medicationDoseRecords
        .filter { it.date == today && it.completed }
        .mapTo(hashSetOf()) { it.alarmId }
    val counts = medicationCounts(state.medications)
    var confirm by remember { mutableStateOf<Pair<MedicationAlarm, Boolean>?>(null) }

    Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding().background(Color.White)) {
        MainTopBar("홈")
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
        item {
            Column {
                Text("안녕하세요, 하늘님", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text("오늘 복용해야 할 약을 확인하세요", color = Muted)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("복용중인 약", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onMedicationsClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "복용약 확인으로 이동", modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            HomeCountsCard(counts)
            Spacer(Modifier.height(10.dp))
        }
        item { HomeSectionTitle("오늘의 복약") }
        if (todayAlarms.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SurfaceSoft)) {
                    Text(
                        "오늘 예정된 복약이 없습니다.",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        color = Muted,
                    )
                }
            }
        } else {
            items(todayAlarms, key = { it.id }) { alarm ->
                val completed = alarm.id in completedAlarmIds
                TodayMedicationAlarmCard(
                    alarm = alarm,
                    dose = state.medications.firstOrNull { it.id == alarm.medicationId }?.dose,
                    completed = completed,
                    onComplete = { confirm = alarm to completed },
                )
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
        }
    }

    confirm?.let { (alarm, completed) ->
        AppConfirmDialog(
            title = if (completed) "복용 완료를 취소하시겠습니까?" else "복용을 완료하셨나요?",
            message = "${alarm.medicationName}\n${formatAlarmTime(alarm.hour, alarm.minute)} · ${alarm.timing}",
            confirmText = if (completed) "완료 취소" else "복용 완료",
            danger = completed,
            onDismiss = { confirm = null },
            onConfirm = {
                    if (completed) viewModel.cancelMedicationDoseCompletion(alarm.id)
                    else viewModel.completeMedicationDose(alarm.id)
                    confirm = null
            },
        )
    }
}

@Composable
private fun HomeSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun HomeCountsCard(counts: Counts) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            Modifier.weight(.9f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF)), shape = RoundedCornerShape(22.dp),
        ) {
            HomeCountItem("전체", counts.total, Modifier.fillMaxSize(), emphasized = true)
        }
        Card(
            Modifier.weight(2.25f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(22.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                HomeCountItem("처방약", counts.prescriptions, Modifier.weight(1f))
                HomeCountItem("일반약", counts.otc, Modifier.weight(1f))
                HomeCountItem("건강기능식품", counts.supplements, Modifier.weight(1.25f))
            }
        }
    }
}

@Composable
private fun HomeCountItem(label: String, value: Int, modifier: Modifier, emphasized: Boolean = false) {
    Column(modifier.padding(vertical = 20.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = if (emphasized) Primary else Color(0xFF27272A), fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
private fun TodayMedicationAlarmCard(alarm: MedicationAlarm, dose: String?, completed: Boolean, onComplete: () -> Unit) {
    val completedGreen = Color(0xFF238A57)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (completed) Color(0xFFF4F4F4) else Primary),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(alarm.medicationName, style = MaterialTheme.typography.titleMedium, color = if (completed) Color(0xFF27272A) else Color.White)
                Text(listOfNotNull(formatAlarmTime(alarm.hour, alarm.minute), dose, alarm.timing).joinToString("  |  "), color = if (completed) Muted else Color.White)
                Text(
                    if (completed) "복용 완료" else "복용 전",
                    color = if (completed) completedGreen else Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(onClick = onComplete, modifier = Modifier.size(52.dp)) {
                Icon(
                    if (completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (completed) "복용 완료" else "복용 완료하기",
                    tint = if (completed) completedGreen else Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

private fun formatAlarmTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

internal fun alarmsForDate(alarms: List<MedicationAlarm>, date: LocalDate): List<MedicationAlarm> = alarms
    .filter { it.enabled && date.dayOfWeek in it.repeatDays }
    .sortedWith(compareBy({ it.hour }, { it.minute }, { it.medicationName }))

internal fun medicationCounts(medications: List<Medication>): Counts {
    val active = medications.filter { it.active }.distinctBy { it.id }
    return Counts(
        total = active.size,
        prescriptions = active.count { it.productType == ProductType.PRESCRIPTION_DRUG },
        supplements = active.count { it.productType == ProductType.HEALTH_SUPPLEMENT },
        otc = active.count { it.productType == ProductType.OTC_DRUG },
    )
}
