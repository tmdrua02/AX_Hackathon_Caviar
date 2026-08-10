package com.haneul.medassist.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.haneul.medassist.AppUiState
import com.haneul.medassist.AppViewModel
import com.haneul.medassist.data.InteractionAnalysisPhase
import com.haneul.medassist.data.InteractionResult
import com.haneul.medassist.data.InteractionSavePhase
import com.haneul.medassist.data.LoadState
import com.haneul.medassist.data.Medication
import com.haneul.medassist.data.ProductType
import com.haneul.medassist.data.Severity
import com.haneul.medassist.ui.theme.Muted
import com.haneul.medassist.ui.theme.Primary
import java.net.URI
import kotlinx.coroutines.delay

private const val ANALYSIS_COMPLETION_DISPLAY_MS = 650L
private val ProhibitedColor = Color(0xFFB82E46)
private val ProhibitedBackground = Color(0xFFFFF0F2)
private val CautionColor = Color(0xFFBE8100)
private val CautionBackground = Color(0xFFFFF8E5)
private val SimilarColor = Color(0xFF2C79B8)
private val SimilarBackground = Color(0xFFF0F7FF)
private val NoIssueColor = Color(0xFF238A57)
private val UnknownColor = Color(0xFF646B75)

@Composable
fun AnalyzingScreen(state: AppUiState, viewModel: AppViewModel, nav: NavHostController) {
    val runId = state.analysisRunId
    var navigatedRunId by rememberSaveable { mutableStateOf<String?>(null) }
    val completed = state.analysisPhase == InteractionAnalysisPhase.SUCCEEDED ||
        state.analysisPhase == InteractionAnalysisPhase.PARTIAL
    val leaveAnalysis: () -> Unit = {
        viewModel.cancelAnalysis()
        nav.popBackStack()
    }

    BackHandler(onBack = leaveAnalysis)
    LaunchedEffect(runId, completed) {
        if (completed && runId != null && navigatedRunId != runId) {
            navigatedRunId = runId
            delay(ANALYSIS_COMPLETION_DISPLAY_MS)
            nav.navigate(Routes.RESULT) {
                popUpTo(Routes.ANALYZING) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().background(Color.White)) {
        AppPageHeader(title = "동시 복용 확인", onBack = leaveAnalysis) {
            ProfileCircle()
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                when (state.analysisPhase) {
                    InteractionAnalysisPhase.RUNNING -> {
                        IngredientAnalysisProgress()
                        Text("잠시만 기다려 주세요", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("성분 조합 점검중...", style = MaterialTheme.typography.titleMedium)
                    }
                    InteractionAnalysisPhase.SUCCEEDED, InteractionAnalysisPhase.PARTIAL -> {
                        Box(Modifier.size(100.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, contentDescription = "분석 완료", tint = Color.White, modifier = Modifier.size(62.dp))
                        }
                        Text(
                            if (state.analysisPhase == InteractionAnalysisPhase.PARTIAL) "확인 가능한 결과를 정리했습니다" else "성분 조합 확인이 완료되었습니다",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    InteractionAnalysisPhase.EMPTY -> AnalysisFailureContent(
                        message = "서버에서 표시할 분석 결과를 받지 못했습니다.",
                        onRetry = viewModel::retryAnalysis,
                        onBack = leaveAnalysis,
                    )
                    InteractionAnalysisPhase.FAILED -> AnalysisFailureContent(
                        message = (state.interaction as? LoadState.Error)?.message
                            ?: "공식 성분·DUR 분석을 완료하지 못했습니다.",
                        onRetry = viewModel::retryAnalysis,
                        onBack = leaveAnalysis,
                    )
                    InteractionAnalysisPhase.IDLE -> AnalysisFailureContent(
                        message = "진행 중인 분석 요청이 없습니다.",
                        onRetry = viewModel::retryAnalysis,
                        onBack = leaveAnalysis,
                    )
                }
            }
        }
    }
}

@Composable
private fun IngredientAnalysisProgress() {
    val transition = rememberInfiniteTransition(label = "ingredient-analysis")
    val startAngle by transition.animateFloat(
        initialValue = -90f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(animation = tween(1_150, easing = LinearEasing)),
        label = "analysis-arc",
    )
    Box(
        Modifier.size(190.dp).semantics { contentDescription = "성분 조합 분석 진행 중" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            drawCircle(color = Color(0xFFF2F2F2), style = Stroke(width = stroke))
            drawArc(
                color = Primary,
                startAngle = startAngle,
                sweepAngle = 82f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Box(Modifier.size(100.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.BarChart, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dp))
        }
    }
}

@Composable
private fun AnalysisFailureContent(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(68.dp))
    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 28.dp))
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(.62f).height(54.dp)) { Text("다시 시도") }
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth(.62f).height(54.dp)) { Text("제품 선택으로 돌아가기") }
}

@Composable
fun ResultScreen(state: AppUiState, viewModel: AppViewModel, nav: NavHostController) {
    val check = (state.interaction as? LoadState.Content)?.value
    var detail by remember { mutableStateOf<InteractionResult?>(null) }
    val results = check?.results.orEmpty().distinctBy {
        listOf(it.newMedication.id, it.existingMedication.id).sorted().joinToString("|") + ":" + it.severity
    }
    val selected = check?.analyzedMedications.orEmpty().ifEmpty { state.analysisSelection }

    Column(Modifier.fillMaxSize().statusBarsPadding().background(Color.White)) {
        AppPageHeader(title = "동시 복용 확인", onBack = { nav.popBackStack() }) { ProfileCircle() }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text("복용중인 약", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { SelectedMedicationPanel(selected) }
            if (check == null) {
                item { ErrorCard("분석 결과를 찾을 수 없습니다.") { nav.popBackStack() } }
            } else if (results.isEmpty()) {
                item { EmptyCard("표시할 분석 결과가 없습니다. 제품을 다시 선택해 분석해 주세요.") }
            } else {
                severityOrder.forEach { severity ->
                    val sectionResults = results.filter { it.severity == severity }
                    if (sectionResults.isNotEmpty()) {
                        item(key = "title-$severity") {
                            Text(severitySectionTitle(severity), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        items(sectionResults, key = { it.id }) { result ->
                            InteractionResultCard(result) { detail = result }
                        }
                    }
                }
                item { SafetyNotice(check.disclaimer) }
                item {
                    Button(
                        onClick = viewModel::saveInteraction,
                        enabled = !check.saved && state.interactionSavePhase != InteractionSavePhase.SAVING,
                        modifier = Modifier.fillMaxWidth().height(62.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        when (state.interactionSavePhase) {
                            InteractionSavePhase.SAVING -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                            InteractionSavePhase.SAVED -> Icon(Icons.Default.CheckCircle, contentDescription = null)
                            else -> Icon(Icons.Default.Save, contentDescription = null)
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(if (check.saved) "저장됨" else if (state.interactionSavePhase == InteractionSavePhase.SAVING) "저장 중" else "결과 저장")
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    detail?.let { InteractionResultDetailDialog(it) { detail = null } }
}

@Composable
private fun ProfileCircle() {
    Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFF4F4F4)), contentAlignment = Alignment.Center) {
        Text("김", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SelectedMedicationPanel(medications: List<Medication>) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("선택한 복용 제품 ${medications.size}", fontWeight = FontWeight.Bold)
            if (medications.isEmpty()) Text("선택 제품 정보가 없습니다.", color = Muted)
            medications.groupBy { it.productType }.forEach { (type, products) ->
                Text(productTypeLabel(type), color = Muted, style = MaterialTheme.typography.bodyMedium)
                Text(products.joinToString { it.name }, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun InteractionResultCard(result: InteractionResult, onClick: () -> Unit) {
    val color = severityColor(result.severity)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = severityBackground(result.severity)),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(severityIcon(result.severity), contentDescription = severitySectionTitle(result.severity), tint = color, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${result.newMedication.name} · ${result.existingMedication.name}")
                val ingredients = result.evidence.flatMap { listOfNotNull(it.ingredientA, it.ingredientB) }.distinct()
                if (ingredients.isNotEmpty()) Text("관련 성분 ${ingredients.joinToString()}", color = Muted)
                Text(result.easyExplanation, color = Muted, maxLines = 3)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "상세 정보 보기")
        }
    }
}

@Composable
private fun InteractionResultDetailDialog(result: InteractionResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sourceUrl = result.evidence.asSequence().map { it.sourceUrl }.firstOrNull(::isValidHttpUrl)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 12.dp) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(result.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "닫기") }
                }
                LazyColumn(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { DetailSection("결과 상세 설명", result.easyExplanation) }
                    item { DetailSection("관련 제품", "${result.newMedication.name} / ${result.existingMedication.name}") }
                    val ingredients = result.evidence.flatMap { listOfNotNull(it.ingredientA, it.ingredientB) }.distinct()
                    item { DetailSection("관련 성분", ingredients.takeIf { it.isNotEmpty() }?.joinToString(" ↔ ") ?: "확인된 성분 근거가 없습니다.") }
                    if (result.evidence.isEmpty()) {
                        item { DetailSection("근거 출처", "제공된 근거 출처가 없습니다. 이는 안전하다는 의미가 아닙니다.") }
                    } else {
                        items(result.evidence.distinctBy { "${it.sourceName}|${it.sourceRecordId}|${it.ingredientA}|${it.ingredientB}" }) { evidence ->
                            HorizontalDivider()
                            DetailSection("상호작용 또는 금기 사유", evidence.originalSummary ?: result.easyExplanation)
                            DetailSection("근거 출처", listOfNotNull(evidence.sourceName, evidence.sourceRecordId).joinToString(" · "))
                        }
                    }
                    item { DetailSection("권고 사항", "표시된 공식 근거의 범위를 확인하고 복용 변경 전 의사 또는 약사와 상담하세요.") }
                }
                Button(
                    onClick = {
                        sourceUrl?.let { url ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }
                    },
                    enabled = sourceUrl != null,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(if (sourceUrl == null) "제공된 근거 출처가 없습니다" else "근거 출처 보기") }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(value, color = Muted)
    }
}

internal fun isValidHttpUrl(value: String?): Boolean = runCatching {
    val uri = value?.let(::URI) ?: return false
    (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

private val severityOrder = listOf(
    Severity.PROHIBITED,
    Severity.CAUTION,
    Severity.DUPLICATE_OR_SIMILAR,
    Severity.NO_KNOWN_ISSUE,
    Severity.UNKNOWN,
)

private fun severitySectionTitle(severity: Severity): String = when (severity) {
    Severity.PROHIBITED -> "동시에 복용하면 안 돼요!"
    Severity.CAUTION -> "복용에 주의하세요!"
    Severity.DUPLICATE_OR_SIMILAR -> "효능 또는 성분이 비슷해요!"
    Severity.NO_KNOWN_ISSUE -> "확인된 상호작용 없음"
    Severity.UNKNOWN -> "판단 불가 또는 정보 부족"
}

private fun severityColor(severity: Severity): Color = when (severity) {
    Severity.PROHIBITED -> ProhibitedColor
    Severity.CAUTION -> CautionColor
    Severity.DUPLICATE_OR_SIMILAR -> SimilarColor
    Severity.NO_KNOWN_ISSUE -> NoIssueColor
    Severity.UNKNOWN -> UnknownColor
}

private fun severityBackground(severity: Severity): Color = when (severity) {
    Severity.PROHIBITED -> ProhibitedBackground
    Severity.CAUTION -> CautionBackground
    Severity.DUPLICATE_OR_SIMILAR -> SimilarBackground
    Severity.NO_KNOWN_ISSUE -> Color(0xFFF0FAF5)
    Severity.UNKNOWN -> Color(0xFFF4F4F4)
}

private fun severityIcon(severity: Severity) = when (severity) {
    Severity.PROHIBITED -> Icons.Default.HealthAndSafety
    Severity.CAUTION -> Icons.Default.WarningAmber
    Severity.DUPLICATE_OR_SIMILAR -> Icons.Default.HelpOutline
    Severity.NO_KNOWN_ISSUE -> Icons.Default.CheckCircle
    Severity.UNKNOWN -> Icons.Default.ErrorOutline
}

private fun productTypeLabel(type: ProductType): String = when (type) {
    ProductType.PRESCRIPTION_DRUG -> "처방약"
    ProductType.OTC_DRUG -> "일반의약품"
    ProductType.HEALTH_SUPPLEMENT -> "건강기능식품"
    ProductType.UNKNOWN -> "분류 미확인"
}
