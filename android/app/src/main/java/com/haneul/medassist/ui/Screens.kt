package com.haneul.medassist.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.haneul.medassist.AppUiState
import com.haneul.medassist.AppViewModel
import com.haneul.medassist.R
import com.haneul.medassist.data.*
import com.haneul.medassist.recording.AmplitudeProcessor
import com.haneul.medassist.recording.WaveformBar
import com.haneul.medassist.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale
import androidx.compose.ui.window.Dialog

@Composable
fun HomeScreen(state: AppUiState, viewModel: AppViewModel, padding: PaddingValues) {
    var confirm by remember { mutableStateOf<Medication?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val home = (state.home as? LoadState.Content)?.value
                    Text(home?.greeting ?: "안녕하세요, 하늘님", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(home?.subtitle ?: "오늘 복용해야 할 약을 확인하세요.", color = Muted)
                }
                Box(Modifier.size(48.dp).clip(CircleShape).background(Primary).semantics { contentDescription = "김 님 프로필" }, contentAlignment = Alignment.Center) {
                    Text("김", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        when (val home = state.home) {
            LoadState.Loading, LoadState.Idle -> item { LoadingCard("복약 정보를 불러오는 중입니다.") }
            is LoadState.Error -> item { ErrorCard(home.message, viewModel::refreshHome) }
            LoadState.Empty -> item { EmptyCard("등록된 복용약이 없습니다.") }
            is LoadState.Content -> {
                if (home.offline) item { AssistChip(onClick = {}, label = { Text("오프라인 캐시/데모 데이터") }, leadingIcon = { Icon(Icons.Default.CloudOff, null) }) }
                item {
                    SectionTitle("복용중인 약")
                    CountsCard(home.value.counts)
                }
                item { SectionTitle("오늘의 복약") }
                items(home.value.todayMedications, key = { it.id }) { medication ->
                    MedicationCard(medication) { confirm = medication }
                }
                item { SafetyNotice(home.value.disclaimer) }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
    confirm?.let { medication ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (medication.taken) "복용 완료를 취소할까요?" else "복용을 완료했나요?") },
            text = { Text("${medication.name} · ${medication.dose.orEmpty()}\n변경 내용은 서버와 동기화됩니다.") },
            confirmButton = { TextButton(onClick = { viewModel.toggleDose(medication); confirm = null }) { Text("확인") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun CountsCard(counts: Counts) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
            CountItem("전체", counts.total, Modifier.weight(1f))
            VerticalDivider(Modifier.height(44.dp))
            CountItem("처방약", counts.prescriptions, Modifier.weight(1f))
            VerticalDivider(Modifier.height(44.dp))
            CountItem("건강기능식품", counts.supplements, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CountItem(label: String, value: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = Primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
private fun MedicationCard(medication: Medication, onToggle: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = if (medication.taken) Color(0xFFF2FAF6) else SurfaceSoft)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(medication.name, style = MaterialTheme.typography.titleMedium)
                Text("${medication.time.orEmpty()} · ${medication.dose.orEmpty()} · ${medication.timing.orEmpty()}", color = Muted)
                Text(if (medication.taken) "복용 완료" else "복용 전", color = if (medication.taken) Color(0xFF238A57) else Primary, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(52.dp)) {
                Icon(if (medication.taken) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (medication.taken) "복용 완료 취소" else "복용 완료로 표시",
                    tint = if (medication.taken) Color(0xFF238A57) else Muted, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun AlarmScreen(state: AppUiState, padding: PaddingValues) {
    val context = LocalContext.current
    var notificationGranted by remember {
        mutableStateOf(android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationGranted = it }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !notificationGranted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    ScreenColumn(padding) {
        Text("복용알람", style = MaterialTheme.typography.headlineMedium)
        Text("일반 복약 알림은 WorkManager와 알림을 사용하며, 정확한 시각이 꼭 필요한 경우만 exact alarm을 검토합니다.", color = Muted)
        if (!notificationGranted) SafetyNotice("알림 권한이 꺼져 있습니다. 시스템 설정에서 알림을 허용해야 복약 알림을 받을 수 있습니다.")
        if (state.medications.isEmpty()) EmptyCard("알림을 설정할 복용약이 없습니다.")
        state.medications.take(3).forEach { med ->
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SurfaceSoft)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, null, tint = Primary)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(med.name, fontWeight = FontWeight.SemiBold)
                        Text("매일 ${med.time ?: "09:00"} · ${med.dose ?: "설정 필요"}", color = Muted)
                    }
                    Switch(checked = med.active, onCheckedChange = {})
                }
            }
        }
        SafetyNotice("알림은 복약을 돕는 보조 기능입니다. 처방 지시를 우선하세요.")
    }
}

@Composable
fun InteractionListScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    padding: PaddingValues,
    onAdd: () -> Unit,
    onStart: () -> Unit,
    onSupplementResult: () -> Unit,
) {
    var supplementQuery by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("동시 복용 확인", style = MaterialTheme.typography.headlineMedium) }
        item { Text("새로 추가한 약", style = MaterialTheme.typography.titleMedium) }
        item {
            val added = state.newMedication
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = if (added == null) Color.White else Color(0xFFEAF6FF)),
                border = androidx.compose.foundation.BorderStroke(2.dp, if (added == null) Color(0xFFCBD1D8) else Primary),
            ) {
                Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (added == null) Icons.Default.AddAPhoto else Icons.Default.Medication, null, tint = Primary)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(added?.name ?: "비교할 약을 추가해주세요", fontWeight = FontWeight.SemiBold)
                        Text(if (added == null) "제품·처방전 앞면과 뒷면 촬영" else added.ingredients.joinToString { it.displayName }, color = Muted)
                    }
                }
            }
        }
        item { Text("기존 복용 제품 ${state.medications.count { it.active }}", style = MaterialTheme.typography.titleMedium) }
        items(state.medications.filter { it.active }, key = { it.id }) { medication ->
            val selected = medication.id in state.selectedExisting
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SurfaceSoft), modifier = Modifier.clickable { viewModel.toggleExisting(medication.id) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selected, onCheckedChange = { viewModel.toggleExisting(medication.id) })
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(medication.name, fontWeight = FontWeight.SemiBold)
                        Text(medication.ingredients.joinToString { it.displayName }, color = Muted)
                    }
                }
            }
        }
        item { InteractionStartButton(state.newMedication != null && state.selectedExisting.isNotEmpty(), onStart) }
        item { SafetyNotice("검색 결과 없음은 안전함을 뜻하지 않습니다. 근거가 부족하면 확인 불가로 표시합니다.") }
        item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
        item { Text("건강기능식품 병용 확인", style = MaterialTheme.typography.titleLarge) }
        item { Text("공식 건강기능식품 후보를 검색하고 품목제조관리번호를 확정해 주세요.", color = Muted) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = supplementQuery,
                    onValueChange = { supplementQuery = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("건강기능식품 제품명") },
                    singleLine = true,
                )
                Button(
                    onClick = { viewModel.searchSupplementProducts(supplementQuery) },
                    enabled = supplementQuery.isNotBlank() && state.supplementSearch !is LoadState.Loading,
                ) { Text("검색") }
            }
        }
        when (val search = state.supplementSearch) {
            LoadState.Idle -> item { Text("제품명을 입력해 공식 후보를 검색하세요.", color = Muted) }
            LoadState.Loading -> item { LoadingCard("건강기능식품 후보를 검색하는 중입니다.") }
            LoadState.Empty -> item { EmptyCard("일치하는 공식 건강기능식품 후보가 없습니다.") }
            is LoadState.Error -> item { ErrorCard(search.message) { viewModel.searchSupplementProducts(supplementQuery) } }
            is LoadState.Content -> items(search.value.candidates, key = { it.sttemntNo }) { candidate ->
                val selected = state.selectedSupplementStatementNo == candidate.sttemntNo
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.selectSupplementCandidate(candidate) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFEAF6FF) else SurfaceSoft),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { viewModel.selectSupplementCandidate(candidate) })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(candidate.productName, fontWeight = FontWeight.SemiBold)
                            Text(candidate.manufacturer ?: "업체명 확인 필요", color = Muted)
                            Text("품목제조관리번호 ${candidate.sttemntNo}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        state.selectedSupplement?.let { selected ->
            item {
                InfoCard(
                    "선택한 건강기능식품",
                    listOf(
                        "제품명" to selected.productName,
                        "업체명" to selected.manufacturer.orEmpty(),
                        "품목번호" to selected.sttemntNo,
                    ),
                )
            }
        }
        item {
            val medicationCode = state.newMedication?.productCode
            val supplementCode = state.selectedSupplementStatementNo
            val loading = state.supplementInteraction is LoadState.Loading
            Button(
                onClick = {
                    if (viewModel.checkSupplementInteraction(medicationCode.orEmpty(), supplementCode.orEmpty())) {
                        onSupplementResult()
                    }
                },
                enabled = !medicationCode.isNullOrBlank() && !supplementCode.isNullOrBlank() && !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("약–건강기능식품 병용 정보 확인") }
        }
        if (state.newMedication != null && state.newMedication.productCode.isNullOrBlank()) {
            item { SafetyNotice("의약품의 공식 품목기준코드가 확인되어야 건강기능식품 병용 분석을 시작할 수 있습니다.") }
        }
    }
}

@Composable
fun InteractionStartButton(enabled: Boolean, onStart: () -> Unit) {
    Button(
        onClick = onStart,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
    ) { Text("동시 복용 확인 시작") }
}

@Composable
fun ReviewScreen(state: AppUiState, viewModel: AppViewModel, nav: NavHostController) {
    val draft = state.draft
    if (draft == null) {
        ScreenColumn { BackTitle("OCR 결과 검토", nav); ErrorCard("검토할 OCR 결과가 없습니다.") { nav.popBackStack() } }
        return
    }
    var productName by remember(draft.id) { mutableStateOf(draft.productName) }
    var dose by remember(draft.id) { mutableStateOf(draft.dose) }
    var times by remember(draft.id) { mutableStateOf(draft.timesPerDay.toString()) }
    var days by remember(draft.id) { mutableStateOf(draft.days.toString()) }
    var timing by remember(draft.id) { mutableStateOf(draft.timing) }
    var candidate by remember(draft.id) { mutableStateOf(draft.candidates.firstOrNull()?.productCode) }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BackTitle("OCR 결과 검토", nav) }
        item { Text("자동 인식 결과를 확인·수정한 뒤 확정해 주세요.", color = Muted) }
        if (draft.warnings.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E5)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) { draft.warnings.forEach { Text("• $it", color = Color(0xFF835C00)) } }
            }
        }
        if (draft.candidates.size > 1) {
            item { Text("제품명 후보", style = MaterialTheme.typography.titleMedium) }
            items(draft.candidates) { item ->
                Row(Modifier.fillMaxWidth().clickable { candidate = item.productCode; productName = item.name }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = candidate == item.productCode, onClick = { candidate = item.productCode; productName = item.name })
                    Column { Text(item.name); Text("일치도 ${item.confidence}% · ${item.source}", color = Muted, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        item { FormField("약 제품명 *", productName) { productName = it } }
        item { FormField("1회 투약량 *", dose) { dose = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { FormField("1일 횟수 *", times) { times = it.filter(Char::isDigit) } }
                Box(Modifier.weight(1f)) { FormField("복용 일수 *", days) { days = it.filter(Char::isDigit) } }
            }
        }
        item { FormField("복용 시점 *", timing) { timing = it } }
        item {
            InfoCard("확인 정보", listOf(
                "제조사" to draft.manufacturer.orEmpty(), "품목기준코드" to draft.productCode.orEmpty(),
                "주성분" to draft.ingredients.joinToString { "${it.displayName} ${it.amount?.toInt() ?: ""}${it.unit.orEmpty()}" },
                "효능·효과" to draft.efficacy.orEmpty(), "OCR 매칭" to "${draft.matchConfidence}% · ${draft.source}",
            ))
        }
        item {
            Button(
                enabled = productName.isNotBlank() && dose.isNotBlank() && times.toIntOrNull() != null && days.toIntOrNull() != null && timing.isNotBlank() && !state.draftLoading,
                onClick = {
                    viewModel.updateDraft(draft.copy(productName = productName, dose = dose, timesPerDay = times.toInt(), days = days.toInt(), timing = timing,
                        productCode = candidate ?: draft.productCode))
                    viewModel.confirmDraft { nav.popBackStack(Routes.INTERACTION, false) }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp),
            ) { if (state.draftLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("확인하고 새 약 추가") }
        }
        item { SafetyNotice("제품이 확정되기 전에는 동시복용 분석을 시작하지 않습니다.") }
    }
}

@Composable
fun AnalyzingScreen(state: AppUiState, viewModel: AppViewModel, nav: NavHostController) {
    var completed by remember { mutableStateOf(false) }
    LaunchedEffect(state.interactionAccepted?.jobId) {
        viewModel.finishAnalysis {
            completed = true
        }
    }
    LaunchedEffect(completed) {
        if (completed) { delay(650); nav.navigate(Routes.RESULT) { popUpTo(Routes.ANALYZING) { inclusive = true } } }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            if (completed) {
                Icon(Icons.Default.CheckCircle, contentDescription = "분석 완료", tint = Primary, modifier = Modifier.size(112.dp))
                Text("분석이 완료되었습니다", style = MaterialTheme.typography.titleLarge)
            } else if (state.interaction is LoadState.Error) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(72.dp))
                Text(state.interaction.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.finishAnalysis { completed = true } }) { Text("다시 시도") }
                TextButton(onClick = { nav.popBackStack(Routes.INTERACTION, false) }) { Text("나중에 확인하기") }
            } else {
                CircularProgressIndicator(Modifier.size(92.dp), strokeWidth = 8.dp)
                Text("잠시만 기다려 주세요", style = MaterialTheme.typography.titleLarge)
                Text("성분 조합 점검중...", color = Muted)
                Text("화면을 나가도 jobId로 분석 상태가 복원됩니다.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ResultScreen(state: AppUiState, viewModel: AppViewModel, nav: NavHostController) {
    val check = (state.interaction as? LoadState.Content)?.value
    var detail by remember { mutableStateOf<InteractionResult?>(null) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackTitle("동시 복용 확인 결과", nav) }
        if (check == null) item { ErrorCard("결과를 찾을 수 없습니다.") { nav.popBackStack() } }
        else {
            item {
                InfoCard("비교 대상", listOf(
                    "새로 추가한 약" to (state.newMedication?.name ?: "-"),
                    "기존 복용 제품" to check.results.joinToString { it.existingMedication.name },
                    "조회 범위" to "성분 ${check.coverage.identifiedIngredients}개 · 성공 ${check.coverage.successfulQueries} · 미확인 ${check.coverage.unidentifiedIngredients}",
                ))
            }
            items(check.results, key = { it.id }) { result -> ResultCard(result) { detail = result } }
            item { SafetyNotice(check.disclaimer) }
            item {
                Button(onClick = viewModel::saveInteraction, enabled = !check.saved, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(if (check.saved) Icons.Default.Check else Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp)); Text(if (check.saved) "저장됨" else "결과 저장")
                }
            }
        }
    }
    detail?.let { ResultDetailDialog(it) { detail = null } }
}

@Composable
private fun ResultCard(result: InteractionResult, onClick: () -> Unit) {
    val color = when (result.severity) {
        Severity.PROHIBITED -> Color(0xFFD94343)
        Severity.CAUTION -> Color(0xFFF0A91D)
        Severity.DUPLICATE_OR_SIMILAR -> Primary
        Severity.NO_KNOWN_ISSUE -> Color(0xFF238A57)
        Severity.UNKNOWN -> Color(0xFF7D858E)
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.09f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.45f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (result.severity == Severity.UNKNOWN) Icons.Default.HelpOutline else Icons.Default.HealthAndSafety, null, tint = color)
                Text(result.title, Modifier.padding(start = 8.dp).weight(1f), fontWeight = FontWeight.Bold, color = color)
                Icon(Icons.Default.ChevronRight, contentDescription = "상세 근거 보기")
            }
            Text("${result.newMedication.name} × ${result.existingMedication.name}", style = MaterialTheme.typography.titleMedium)
            Text(result.easyExplanation, color = Muted)
            if (result.evidence.isNotEmpty()) {
                Text("관련 성분: ${result.evidence.joinToString { "${it.ingredientA} / ${it.ingredientB}" }}", style = MaterialTheme.typography.bodyMedium)
                Text("출처: ${result.evidence.first().sourceName} · 기준일 ${result.evidence.first().sourceDate ?: "확인 필요"}", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ResultDetailDialog(result: InteractionResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.FactCheck, null, tint = Primary); Text("  판정 근거 상세") } },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(result.easyExplanation) }
                item { Text("관련 제품", fontWeight = FontWeight.Bold); Text("${result.newMedication.name} / ${result.existingMedication.name}") }
                if (result.evidence.isEmpty()) item { Text("공식 관계 데이터가 없어 원문 근거를 표시할 수 없습니다. 이는 안전하다는 의미가 아닙니다.", color = Muted) }
                items(result.evidence) { evidence ->
                    HorizontalDivider()
                    Text("${evidence.ingredientA} / ${evidence.ingredientB}", fontWeight = FontWeight.Bold)
                    Text(evidence.originalSummary ?: "원문 요약 없음")
                    Text("${evidence.sourceType} · ${evidence.sourceName}", color = Primary)
                    Text("고시/수정일 ${evidence.sourceDate ?: "확인 필요"}\n${evidence.sourceUrl}", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
fun RecordingHomeScreen(state: AppUiState, viewModel: AppViewModel, padding: PaddingValues, nav: NavHostController) {
    val context = LocalContext.current
    var consent by remember { mutableStateOf(false) }
    val latest = (state.consultations as? LoadState.Content)?.value?.maxByOrNull { it.consultedAt }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) consent = true
        else viewModel.recordingPermissionDenied()
    }
    Column(
        Modifier.fillMaxSize().padding(padding).statusBarsPadding().padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SimpleProfileHeader("진료 녹음")
        Spacer(Modifier.height(4.dp))
        Text("녹음 파일", style = MaterialTheme.typography.titleLarge)
        if (latest == null) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SurfaceSoft)) {
                Text("아직 저장된 진료 녹음이 없습니다.", Modifier.fillMaxWidth().padding(22.dp), color = Muted)
            }
        } else {
            RecordingFileCard(latest, onClick = { nav.navigate("recording/detail/${latest.id}") })
        }
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) consent = true
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Mic, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("녹음하기", fontWeight = FontWeight.Bold)
        }
        state.recording.error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodyMedium) }
        if (((state.consultations as? LoadState.Content)?.value?.size ?: 0) > 1) {
            TextButton(onClick = { nav.navigate(Routes.RECORDING_FILES) }, modifier = Modifier.align(Alignment.End)) {
                Text("전체 녹음 보기")
                Icon(Icons.Default.ChevronRight, null)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("녹음 전 의료진에게 알리고 동의를 확인해 주세요.", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
    if (consent) {
        AlertDialog(
            onDismissRequest = { consent = false },
            title = { Text("민감정보 처리 및 분석 동의") },
            text = { Text("진료 음성에는 민감한 의료정보가 포함될 수 있습니다. 저장과 전사·요약 분석을 위해 서버 및 설정한 모델 제공자에 전송됩니다. 의료진의 녹음 동의도 확인했습니다.") },
            confirmButton = { Button(onClick = { consent = false; if (viewModel.startRecording()) nav.navigate(Routes.ACTIVE_RECORDING) }) { Text("동의하고 녹음") } },
            dismissButton = { TextButton(onClick = { consent = false }) { Text("취소") } },
        )
    }
}

@Composable
fun ActiveRecordingScreen(state: AppUiState, viewModel: AppViewModel, nav: NavHostController) {
    val recording = state.recording
    var saveDialog by remember { mutableStateOf(false) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.stopRecordingIfActive() }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BackTitle("진료 녹음", nav)
        Spacer(Modifier.height(48.dp))
        Text(formatElapsed(recording.elapsedMs), style = MaterialTheme.typography.headlineLarge, color = Primary)
        Spacer(Modifier.height(34.dp))
        Box(
            Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFF5F5F5)).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            RecordingWaveform(viewModel, Modifier.fillMaxWidth().height(150.dp))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFFFF9C9C)))
        }
        val statusText = when {
            recording.finalizing -> "녹음 파일 마무리 중"
            recording.paused -> "일시정지됨"
            recording.active -> "녹음 중"
            recording.readyToSave -> "녹음 완료"
            else -> "녹음 종료"
        }
        Spacer(Modifier.height(12.dp))
        Text(
            statusText,
            color = if (recording.paused || recording.finalizing) Warning else Muted,
            fontWeight = FontWeight.SemiBold,
        )
        recording.error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodyMedium) }
        recording.inputWarning?.let { Text(it, color = Warning, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.weight(1f))
        if (recording.active) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}, modifier = Modifier.size(58.dp)) { Icon(Icons.Default.Menu, contentDescription = "녹음 메뉴", tint = Ink) }
                FilledTonalIconButton(onClick = viewModel::pauseResumeRecording, modifier = Modifier.size(68.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFFF2F2F2))) {
                    Icon(if (recording.paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = if (recording.paused) "녹음 재개" else "녹음 일시정지", modifier = Modifier.size(32.dp), tint = Ink)
                }
                IconButton(onClick = { viewModel.stopRecording(); saveDialog = true }, modifier = Modifier.size(58.dp)) {
                    Icon(Icons.Default.Stop, contentDescription = "녹음 정지", modifier = Modifier.size(28.dp), tint = Ink)
                }
            }
        } else if (recording.readyToSave) {
            Button(
                onClick = { saveDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("녹음 파일 저장")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (saveDialog) RecordingSaveDialog(
        duration = recording.elapsedMs,
        finalizing = recording.finalizing,
        canSave = recording.readyToSave,
        error = recording.error,
        onDismiss = { saveDialog = false },
    ) { title, hospital ->
        viewModel.saveRecording(title, hospital); saveDialog = false; nav.navigate(Routes.RECORDING_FILES) { popUpTo(Routes.RECORDING) }
    }
}

@Composable
private fun RecordingWaveform(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val values by viewModel.waveform.collectAsState()
    Waveform(values, modifier)
}

private val BAR_HEIGHT_VARIATIONS = floatArrayOf(0.94f, 1.03f, 0.97f, 1.06f, 0.95f, 1.02f, 0.96f, 1.05f)

@Composable
private fun Waveform(values: List<WaveformBar>, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "실시간 음성 진폭 파형" }) {
        val gap = size.width / values.size.coerceAtLeast(1)
        values.forEachIndexed { index, value ->
            val variation = BAR_HEIGHT_VARIATIONS[index % BAR_HEIGHT_VARIATIONS.size]
            val heightFraction = when {
                value.clipped -> 1f
                value.heightFraction <= AmplitudeProcessor.MIN_BAR_HEIGHT -> AmplitudeProcessor.MIN_BAR_HEIGHT
                else -> (value.heightFraction * variation).coerceIn(AmplitudeProcessor.MIN_BAR_HEIGHT, 1f)
            }
            val height = size.height * heightFraction
            val centerX = (index + 0.5f) * gap
            drawLine(if (value.clipped) Danger else Primary, start = androidx.compose.ui.geometry.Offset(centerX, (size.height - height) / 2),
                end = androidx.compose.ui.geometry.Offset(centerX, (size.height + height) / 2), strokeWidth = gap * .55f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun RecordingSaveDialog(
    duration: Long,
    finalizing: Boolean,
    canSave: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var hospital by remember { mutableStateOf("") }
    var isConsultation by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("녹음 파일 저장", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "닫기") }
                }
                Text("파일 이름", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("파일 이름을 입력해 주세요") }, singleLine = true, shape = RoundedCornerShape(10.dp))
                Text("병원/약국 이름", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(hospital, { hospital = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("병원, 약국 이름을 입력해 주세요") }, singleLine = true, shape = RoundedCornerShape(10.dp))
                Text("기록 유형 선택", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RecordTypeCard("진료 기록", "병원에서 녹음", isConsultation, Modifier.weight(1f)) { isConsultation = true }
                    RecordTypeCard("복약 기록", "약국에서 녹음", !isConsultation, Modifier.weight(1f)) { isConsultation = false }
                }
                if (finalizing) Text("녹음 파일을 마무리하는 중입니다.", color = Muted)
                else Text("녹음 길이 ${formatDuration(duration)}", color = Muted)
                error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodyMedium) }
                Button(
                    onClick = { onSave(title, hospital) },
                    enabled = title.isNotBlank() && canSave,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                ) {
                    if (finalizing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("저장하기")
                }
            }
        }
    }
}

@Composable
private fun RecordTypeCard(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) Primary else Color.Transparent),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F6F6)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.Circle, null, tint = if (selected) Primary else Color(0xFFD8D8D8), modifier = Modifier.size(20.dp))
                Text(title, Modifier.padding(start = 7.dp), fontWeight = FontWeight.SemiBold)
            }
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RecordingFilesScreen(state: AppUiState, viewModel: AppViewModel, nav: NavHostController) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BackTitle("진료 녹음", nav) }
        item { Text("녹음 파일", style = MaterialTheme.typography.titleLarge) }
        when (val consultations = state.consultations) {
            LoadState.Loading, LoadState.Idle -> item { LoadingCard("녹음 목록을 불러오는 중입니다.") }
            LoadState.Empty -> item { EmptyCard("저장된 녹음이 없습니다.") }
            is LoadState.Error -> item { ErrorCard(consultations.message, viewModel::loadConsultations) }
            is LoadState.Content -> items(consultations.value, key = { it.id }) { consultation ->
                RecordingFileCard(consultation, onClick = { nav.navigate("recording/detail/${consultation.id}") })
            }
        }
        item { Button(onClick = { nav.navigate(Routes.RECORDING) }, Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("녹음하기") } }
    }
}

@Composable
private fun RecordingFileCard(consultation: Consultation, onClick: () -> Unit) {
    val status = when (consultation.status) {
        "SUCCEEDED" -> "AI 기록 완료"
        "RUNNING", "QUEUED" -> "AI 기록 생성 중"
        else -> "분석 실패 · 다시 시도 가능"
    }
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3F3))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(consultation.title, fontWeight = FontWeight.Bold)
                Text("${consultation.consultedAt.take(16).replace('T', ' ')} · ${consultation.hospitalName.orEmpty()}", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Text(status, color = if (consultation.status == "FAILED") Danger else Primary, style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayCircle, contentDescription = "녹음 재생", tint = Ink, modifier = Modifier.size(34.dp))
                Text(formatDuration(consultation.durationMs), color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun RecordingDetailScreen(state: AppUiState, id: String, viewModel: AppViewModel, nav: NavHostController) {
    val consultation = (state.consultations as? LoadState.Content)?.value?.firstOrNull { it.id == id }
    var tab by remember { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackTitle(consultation?.title ?: "음성 기록", nav) }
        if (consultation == null) item { EmptyCard("기록을 찾을 수 없습니다.") } else {
            item { AssistChip(onClick = {}, label = { Text("진료 기록") }, colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF0F7FF))) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${consultation.consultedAt.take(16).replace('T', ' ')}   ${formatDuration(consultation.durationMs)}", fontWeight = FontWeight.SemiBold)
                    Text(consultation.hospitalName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                }
            }
            item { TabRow(selectedTabIndex = tab, containerColor = Color.White, contentColor = Primary) {
                Tab(tab == 0, { tab = 0 }, text = { Text("음성 기록", color = if (tab == 0) Primary else Muted) })
                Tab(tab == 1, { tab = 1 }, text = { Text("요약 메모", color = if (tab == 1) Primary else Muted) })
            } }
            if (tab == 0) {
                if (consultation.status == "RUNNING" || consultation.status == "QUEUED") item { LoadingCard("AI가 대화 내용을 기록하는 중입니다.") }
                if (consultation.status == "FAILED") item {
                    ErrorCard(
                        consultation.failureMessage ?: "AI 기록 생성에 실패했습니다.",
                    ) { viewModel.retryConsultation(consultation.id) }
                }
                items(consultation.transcript, key = { it.id }) { segment ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFF2F2F2)), contentAlignment = Alignment.Center) {
                            Text(if (segment.speaker == "의사") "의" else if (segment.speaker == "환자") "나" else "?", fontWeight = FontWeight.Bold, color = Muted)
                        }
                        Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(segment.speaker, fontWeight = FontWeight.Bold)
                                Text(" ${formatDuration(segment.startMs)}", color = Color(0xFFB9BEC5), style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(segment.text, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight)
                        }
                    }
                }
            } else {
                val summary = consultation.summary
                if (summary == null) item { EmptyCard("요약 생성에 실패했거나 분석 중입니다.") }
                else {
                    item { SummaryCard("전체 요약", summary.overallSummary, Color(0xFFF0F7FF)) }
                    item { SummaryCard("주요 증상", summary.symptoms.joinToString("\n") { "• ${it.text}" }.ifBlank { "기록 없음" }) }
                    item { SummaryCard("검사·진단", summary.testsAndAssessment.joinToString("\n") { "• ${it.text}" }.ifBlank { "확인된 내용 없음" }) }
                    item { SummaryCard("처방 및 복용 안내", summary.prescriptionAndInstructions.joinToString("\n") { "• ${it.text}" }.ifBlank { "확인된 내용 없음" }) }
                    item { SummaryCard("추후 일정 및 주의사항", summary.followUps.joinToString("\n") { "• ${it.text}" }.ifBlank { "확인된 내용 없음" }) }
                    if (summary.uncertainties.isNotEmpty()) item { SummaryCard("확인 필요", summary.uncertainties.joinToString("\n") { "• ${it.text}" }, Warning.copy(alpha = .13f)) }
                    item { Text("AI 요약은 참고용입니다. 의료진의 실제 설명과 처방을 우선해 주세요.", color = Muted, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
fun RecordsScreen(state: AppUiState, padding: PaddingValues, nav: NavHostController) {
    ScreenColumn(padding) {
        Text("기록", style = MaterialTheme.typography.headlineMedium)
        Text("저장한 동시복용 결과와 진료 녹음을 한곳에서 확인합니다.", color = Muted)
        Card(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.RECORDING_FILES) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SurfaceSoft)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, tint = Primary); Column(Modifier.weight(1f).padding(start = 14.dp)) { Text("진료 녹음 기록", fontWeight = FontWeight.Bold); Text("전사, 요약, 분석 상태", color = Muted) }; Icon(Icons.Default.ChevronRight, null)
            }
        }
        val saved = (state.interaction as? LoadState.Content)?.value?.saved == true
        if (saved) InfoCard("저장된 동시복용 결과", listOf("상태" to "최근 분석 결과 저장됨", "주의" to "근거와 데이터 기준일을 함께 보관"))
        else EmptyCard("아직 저장된 동시복용 결과가 없습니다.")
    }
}

@Composable
fun ChatScreen(state: AppUiState, viewModel: AppViewModel, nav: NavHostController) {
    var input by remember { mutableStateOf("") }
    val welcome = listOf(
        false to "안녕하세요, 메디봇 입니다!",
        false to "약에 대해 궁금한 점을 물어봐 주세요!",
    )
    val visibleMessages = welcome + state.chatMessages
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().background(Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ArrowBack, "뒤로가기", modifier = Modifier.size(30.dp)) }
            Text("메디봇", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 6.dp))
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(visibleMessages) { (isUser, message) ->
                if (isUser) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Text(
                            message,
                            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFB9DCFA)).padding(horizontal = 18.dp, vertical = 14.dp),
                            color = Ink,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text("방금", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp))
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Image(
                            painter = painterResource(R.drawable.medibot_pill_button),
                            contentDescription = "메디봇",
                            modifier = Modifier.size(58.dp),
                        )
                        Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("메디봇", style = MaterialTheme.typography.titleMedium)
                            Text(
                                message.ifBlank { "답변을 작성하는 중..." },
                                Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFF0F6FC)).padding(horizontal = 18.dp, vertical = 14.dp),
                                color = Ink,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text("방금", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Text("의료진을 대신하지 않으며, 복용 변경 전 의사·약사와 확인해 주세요.", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp))
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                input,
                { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("무엇이든 물어보세요", color = Color(0xFFB8BDC6)) },
                maxLines = 3,
                shape = RoundedCornerShape(30.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF5F5F5),
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )
            FilledIconButton(
                onClick = { val message = input.trim(); input = ""; viewModel.sendChat(message) },
                enabled = input.isNotBlank() && !state.chatLoading,
                modifier = Modifier.size(58.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (input.isBlank()) Color(0xFFC6C9D1) else Primary),
            ) {
                Icon(Icons.Default.Send, contentDescription = "메시지 보내기", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun SimpleProfileHeader(title: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFF4F4F4)), contentAlignment = Alignment.Center) {
            Text("김", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ScreenColumn(padding: PaddingValues = PaddingValues(0.dp), horizontal: Alignment.Horizontal = Alignment.Start, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding().padding(20.dp), horizontalAlignment = horizontal, verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
private fun BackTitle(title: String, nav: NavHostController) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleLarge) }

@Composable
private fun SafetyNotice(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFEAF6FF)).padding(14.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Info, null, tint = Primary, modifier = Modifier.size(20.dp)); Text(text, Modifier.padding(start = 8.dp), color = PrimaryDark, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LoadingCard(text: String) { Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp); Text(text, Modifier.padding(start = 14.dp)) } } }

@Composable
private fun EmptyCard(text: String) { Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Inbox, null, tint = Muted); Spacer(Modifier.height(8.dp)); Text(text, color = Muted) } } }

@Composable
private fun ErrorCard(text: String, retry: () -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEEE)), shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp)) { Text(text, color = Danger); TextButton(onClick = retry) { Text("다시 시도") } } } }

@Composable
private fun FormField(label: String, value: String, onValueChange: (String) -> Unit) { OutlinedTextField(value, onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(14.dp)) }

@Composable
private fun InfoCard(title: String, rows: List<Pair<String, String>>) { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SurfaceSoft)) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text(title, fontWeight = FontWeight.Bold); rows.forEach { (label, value) -> Row { Text(label, Modifier.width(110.dp), color = Muted); Text(value.ifBlank { "확인 필요" }, Modifier.weight(1f)) } } } } }

@Composable
private fun SummaryCard(title: String, text: String, color: Color = SurfaceSoft) { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = color)) { Column(Modifier.fillMaxWidth().padding(18.dp)) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(text) } } }

private fun formatDuration(ms: Long): String = String.format(Locale.KOREA, "%02d:%02d", ms / 60_000, (ms / 1_000) % 60)
private fun formatElapsed(ms: Long): String = String.format(Locale.KOREA, "%02d:%02d.%02d", ms / 60_000, (ms / 1_000) % 60, (ms / 10) % 100)
