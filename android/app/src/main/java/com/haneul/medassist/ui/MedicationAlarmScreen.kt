package com.haneul.medassist.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.haneul.medassist.AppUiState
import com.haneul.medassist.AppViewModel
import com.haneul.medassist.data.Medication
import com.haneul.medassist.data.MedicationAlarm
import com.haneul.medassist.ui.theme.Muted
import com.haneul.medassist.ui.theme.Primary
import com.haneul.medassist.ui.theme.SurfaceSoft
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged

private val AlarmBlue = Color(0xFF329BFA)
private val AlarmCardBlue = Color(0xFFF0F7FF)
private val DisabledCard = Color(0xFFFAFAFA)
private val dayLabels = linkedMapOf(
    DayOfWeek.SUNDAY to "일",
    DayOfWeek.MONDAY to "월",
    DayOfWeek.TUESDAY to "화",
    DayOfWeek.WEDNESDAY to "수",
    DayOfWeek.THURSDAY to "목",
    DayOfWeek.FRIDAY to "금",
    DayOfWeek.SATURDAY to "토",
)

@Composable
fun AlarmScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    padding: PaddingValues,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onManage: () -> Unit,
) {
    val context = LocalContext.current
    var notificationGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationGranted = it
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(Modifier.fillMaxSize().padding(padding).background(Color.White)) {
        Box(Modifier.fillMaxWidth().statusBarsPadding()) {
            AlarmHeader()
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("알람", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RoundIconButton(Icons.Default.Add, "복용 알람 추가", onAdd)
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Default.MoreVert, "알람 선택 및 삭제", onManage)
            }
        }
        if (!notificationGranted) {
            item {
                PermissionCard("알림 권한이 꺼져 있어 복용 알림을 표시할 수 없습니다.") {
                    if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            item {
                PermissionCard("정확한 시간 알림 권한을 허용하면 설정한 시각에 더 정확히 알려드릴 수 있습니다.") {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
            }
        }
        if (state.medicationAlarms.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = AlarmCardBlue)) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("등록된 복용 알람이 없어요", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("+ 버튼을 눌러 첫 알람을 만들어보세요.", color = Muted)
                    }
                }
            }
        } else {
            items(state.medicationAlarms, key = { it.id }) { alarm ->
                val currentAlarm = alarm.copy(
                    medicationName = state.medications.firstOrNull { it.id == alarm.medicationId }?.name
                        ?: alarm.medicationName,
                )
                MedicationAlarmCard(
                    alarm = currentAlarm,
                    onToggle = { viewModel.toggleMedicationAlarm(currentAlarm, it) },
                    onEdit = { onEdit(alarm.id) },
                )
            }
        }
        }
    }

}

@Composable
private fun AlarmHeader() {
    MainTopBar(title = "복용 알람")
}

@Composable
private fun MedicationAlarmCard(
    alarm: MedicationAlarm,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val contentAlpha = if (alarm.enabled) 1f else .43f
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (alarm.enabled) 1f else .72f).clickable(onClick = onEdit),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = if (alarm.enabled) AlarmCardBlue else DisabledCard),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(contentAlpha), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatTime(alarm.hour, alarm.minute), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${alarm.medicationName}  |  ${alarm.timing}", style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(formatDays(alarm.repeatDays), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AlarmBlue),
            )
        }
    }
}

@Composable
fun AlarmSelectionScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(Color.White)) {
        AppPageHeader("알람 선택", onBack = onBack)
        Text("알람", Modifier.padding(horizontal = 26.dp, vertical = 14.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(state.medicationAlarms, key = { it.id }) { alarm ->
                val selected = alarm.id in selectedIds
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedIds = if (selected) selectedIds - alarm.id else selectedIds + alarm.id
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFD9EAFB) else Color(0xFFF6F6F6)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(formatTime(alarm.hour, alarm.minute), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("${alarm.medicationName}  |  ${alarm.timing}", maxLines = 1, softWrap = false)
                        }
                        Icon(
                            if (selected) Icons.Default.CheckCircle else Icons.Default.Circle,
                            contentDescription = if (selected) "선택됨" else "선택 안 됨",
                            tint = if (selected) AlarmBlue else Color(0xFFD7D7D7), modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
        }
        Button(
            onClick = { confirmDelete = true }, enabled = selectedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(26.dp).height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29292B)),
        ) {
            Icon(Icons.Default.Delete, null)
            Spacer(Modifier.width(10.dp))
            Text("삭제하기", style = MaterialTheme.typography.titleMedium)
        }
    }
    if (confirmDelete) {
        AppConfirmDialog(
            title = "선택한 ${selectedIds.size}개의 알람을 삭제하시겠습니까?",
            message = "삭제하면 예약된 Android 알림도 함께 취소됩니다.",
            confirmText = "삭제", danger = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                val selected = state.medicationAlarms.filter { it.id in selectedIds }
                viewModel.deleteMedicationAlarms(selected, onBack)
                confirmDelete = false
            },
        )
    }
}

@Composable
private fun PermissionCard(message: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E8))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClick) { Text("설정") }
        }
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Color(0xFFF6F6F6), modifier = Modifier.size(52.dp)) {
        IconButton(onClick = onClick) { Icon(icon, description) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationAlarmEditScreen(
    alarmId: String?,
    state: AppUiState,
    viewModel: AppViewModel,
    scaffoldPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val existing = state.medicationAlarms.firstOrNull { it.id == alarmId }
    val initialTime = remember { LocalTime.now() }
    var isPm by remember { mutableStateOf(initialTime.hour >= 12) }
    var hour12 by remember { mutableStateOf(initialTime.hour.to12Hour()) }
    var minute by remember { mutableStateOf(LocalTime.now().minute) }
    var selectedMedicationId by remember { mutableStateOf("") }
    var selectedMedicationName by remember { mutableStateOf("") }
    var repeatDays by remember { mutableStateOf(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)) }
    var timing by remember { mutableStateOf("식후 30분") }
    var soundEnabled by remember { mutableStateOf(true) }
    var soundName by remember { mutableStateOf("기본 알림음") }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var medicationMenu by remember { mutableStateOf(false) }
    var soundMenu by remember { mutableStateOf(false) }

    LaunchedEffect(existing?.id, state.medications) {
        if (existing != null) {
            isPm = existing.hour >= 12
            hour12 = existing.hour.to12Hour()
            minute = existing.minute
            val linkedMedication = state.medications.firstOrNull { it.active && it.id == existing.medicationId }
            selectedMedicationId = linkedMedication?.id.orEmpty()
            selectedMedicationName = linkedMedication?.name.orEmpty()
            repeatDays = existing.repeatDays
            timing = existing.timing
            soundEnabled = existing.soundEnabled
            soundName = existing.soundName
            vibrationEnabled = existing.vibrationEnabled
        } else if (selectedMedicationId.isBlank()) {
            state.medications.firstOrNull { it.active }?.let {
                selectedMedicationId = it.id
                selectedMedicationName = it.name
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF8F8F9))
            .padding(bottom = scaffoldPadding.calculateBottomPadding())
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Box(Modifier.fillMaxWidth().statusBarsPadding()) {
            AppPageHeader(title = if (existing == null) "복용 알람 추가" else "복용 알람 수정", onBack = onBack)
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
        item {
            WheelTimePicker(
                isPm = isPm,
                hour12 = hour12,
                minute = minute,
                onPeriodChanged = { isPm = it },
                onHourChanged = { hour12 = it },
                onMinuteChanged = { minute = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
            )
        }
        item {
            Surface(shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp), color = Color.White) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    SettingSection("복용 약 선택") {
                        Box {
                            SelectionBox(selectedMedicationName.ifBlank { "복용할 약을 선택하세요" }) { medicationMenu = true }
                            AppPopupMenu(expanded = medicationMenu, onDismissRequest = { medicationMenu = false }) {
                                state.medications.filter { it.active }.forEach { med ->
                                    AppPopupMenuItem(med.name) { selectedMedicationId = med.id; selectedMedicationName = med.name; medicationMenu = false }
                                }
                            }
                        }
                    }
                    SettingSection("반복") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            dayLabels.forEach { (day, label) ->
                                val selected = day in repeatDays
                                Surface(
                                    modifier = Modifier.size(42.dp).clickable {
                                        repeatDays = if (selected) repeatDays - day else repeatDays + day
                                    },
                                    shape = CircleShape,
                                    color = if (selected) AlarmBlue else Color.Transparent,
                                ) {
                                    Box(contentAlignment = Alignment.Center) { Text(label, color = if (selected) Color.White else Color(0xFF27272A), fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                    SettingSection("복용 시점") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("식전 30분", "식후 30분", "취침 전").forEach { option ->
                                val selected = timing == option
                                Surface(
                                    modifier = Modifier.weight(1f).height(58.dp).clickable { timing = option },
                                    shape = RoundedCornerShape(17.dp),
                                    color = if (selected) AlarmCardBlue else Color(0xFFF4F4F4),
                                    border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, AlarmBlue) else null,
                                ) {
                                    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                        Box(Modifier.size(26.dp).clip(CircleShape).background(if (selected) AlarmBlue else Color(0xFFD9D9D9)), contentAlignment = Alignment.Center) {
                                            if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                        Text(option, modifier = Modifier.padding(start = 5.dp), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                    SettingSection("알람음") {
                        Box {
                            ToggleSettingCard(soundName, soundEnabled, { soundEnabled = it }, onTextClick = { if (soundEnabled) soundMenu = true })
                            AppPopupMenu(expanded = soundMenu, onDismissRequest = { soundMenu = false }) {
                                listOf("기본 알림음", "활기찬 노래").forEach { option ->
                                    AppPopupMenuItem(option) { soundName = option; soundMenu = false }
                                }
                            }
                        }
                    }
                    SettingSection("진동") {
                        ToggleSettingCard(if (vibrationEnabled) "Basic Call" else "진동 끔", vibrationEnabled, { vibrationEnabled = it })
                    }
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD8D8D8), contentColor = Color.White),
                    ) { Text("취소하기", fontSize = 20.sp, fontWeight = FontWeight.Medium) }
                    Button(
                        onClick = {
                            viewModel.saveMedicationAlarm(
                                id = alarmId,
                                medicationId = selectedMedicationId,
                                medicationName = selectedMedicationName,
                                hour = to24Hour(hour12, isPm),
                                minute = minute,
                                repeatDays = repeatDays,
                                timing = timing,
                                soundEnabled = soundEnabled,
                                soundName = soundName,
                                vibrationEnabled = vibrationEnabled,
                                onSaved = onBack,
                            )
                        },
                        enabled = selectedMedicationId.isNotBlank() && repeatDays.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AlarmBlue, contentColor = Color.White),
                    ) { Text("저장하기", fontSize = 20.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
        }
    }
}

@Composable
private fun WheelTimePicker(
    isPm: Boolean,
    hour12: Int,
    minute: Int,
    onPeriodChanged: (Boolean) -> Unit,
    onHourChanged: (Int) -> Unit,
    onMinuteChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val periodIndex = if (isPm) 1 else 0
    val hourValues = remember { (1..12).map { "%02d".format(it) } }
    val minuteValues = remember { (0..59).map { "%02d".format(it) } }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FiniteWheelColumn(
            values = listOf("오전", "오후"),
            selectedIndex = periodIndex,
            onSelected = { selectedPeriod -> onPeriodChanged(selectedPeriod == 1) },
            modifier = Modifier.width(92.dp),
        )
        CircularWheelColumn(
            values = hourValues,
            selectedIndex = hour12 - 1,
            onSelected = { selectedHour -> onHourChanged(selectedHour + 1) },
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = ":",
            modifier = Modifier.width(30.dp),
            color = Color(0xFF29292B),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        CircularWheelColumn(
            values = minuteValues,
            selectedIndex = minute,
            onSelected = onMinuteChanged,
            modifier = Modifier.width(88.dp),
        )
    }
}

@Composable
private fun FiniteWheelColumn(
    values: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 58.dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState, values) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                abs(item.offset + item.size / 2 - center)
            }?.index
        }.distinctUntilChanged().collect { index ->
            index?.let(onSelected)
        }
    }
    LaunchedEffect(selectedIndex) {
        if (!listState.isScrollInProgress) listState.animateScrollToItem(selectedIndex)
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier.height(itemHeight * 3),
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count = values.size) { index ->
            val selected = index == selectedIndex
            Box(Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                Text(
                    text = values[index],
                    color = if (selected) Color(0xFF29292B) else Color(0xFFC7C9D1),
                    fontSize = if (selected) 34.sp else 28.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CircularWheelColumn(
    values: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 58.dp
    val middle = Int.MAX_VALUE / 2
    val initialIndex = remember(values) { middle - middle % values.size + selectedIndex }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState, values) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                abs(item.offset + item.size / 2 - center)
            }?.index
        }.distinctUntilChanged().collect { index ->
            index?.let { onSelected(Math.floorMod(it, values.size)) }
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier.height(itemHeight * 3),
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count = Int.MAX_VALUE) { index ->
            val valueIndex = Math.floorMod(index, values.size)
            val selected = valueIndex == selectedIndex
            Box(Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                Text(
                    text = values[valueIndex],
                    color = if (selected) Color(0xFF29292B) else Color(0xFFC7C9D1),
                    fontSize = if (selected) 34.sp else 28.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun SelectionBox(value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(17.dp)).background(AlarmCardBlue).clickable(onClick = onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Icon(Icons.Default.ExpandMore, "선택 목록")
    }
}

@Composable
private fun ToggleSettingCard(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, onTextClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFFF4F4F4)).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f).clickable(onClick = onTextClick), fontWeight = FontWeight.Bold)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AlarmBlue))
    }
}

private fun formatTime(hour: Int, minute: Int): String = LocalTime.of(hour, minute)
    .format(DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN))

private fun formatDays(days: Set<DayOfWeek>): String {
    if (days.size == 7) return "매일"
    return dayLabels.filterKeys { it in days }.values.joinToString(" · ")
}

internal fun Int.to12Hour(): Int = when (val value = this % 12) { 0 -> 12; else -> value }

internal fun to24Hour(hour12: Int, isPm: Boolean): Int =
    (hour12 % 12) + if (isPm) 12 else 0
