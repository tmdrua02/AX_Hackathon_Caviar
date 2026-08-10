package com.haneul.medassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.haneul.medassist.AppUiState
import com.haneul.medassist.AppViewModel
import com.haneul.medassist.data.LoadState
import com.haneul.medassist.data.SupplementInteractionCheckResponse
import com.haneul.medassist.data.SupplementInteractionExplanationStatus
import com.haneul.medassist.data.SupplementInteractionSeverity
import com.haneul.medassist.ui.theme.Danger
import com.haneul.medassist.ui.theme.Muted
import com.haneul.medassist.ui.theme.Primary
import com.haneul.medassist.ui.theme.PrimaryDark
import com.haneul.medassist.ui.theme.SurfaceSoft
import com.haneul.medassist.ui.theme.Warning

internal data class SupplementSeverityUi(
    val title: String,
    val guidance: String,
)

internal fun supplementSeverityUi(severity: SupplementInteractionSeverity): SupplementSeverityUi = when (severity) {
    SupplementInteractionSeverity.AVOID_COMBINATION -> SupplementSeverityUi(
        "병용 회피 근거 확인",
        "병용 회피 또는 전문가 확인이 필요한 검수 근거가 있습니다.",
    )
    SupplementInteractionSeverity.CAUTION -> SupplementSeverityUi(
        "병용 시 주의",
        "병용 시 주의가 필요한 검수 근거가 확인되었습니다.",
    )
    SupplementInteractionSeverity.NO_VERIFIED_RULE_FOUND -> SupplementSeverityUi(
        "현재 검수된 주의 정보 없음",
        "현재 검수된 병용섭취 규칙에서 일치하는 주의 정보를 찾지 못했습니다. 함께 복용해도 안전하다는 의미는 아닙니다.",
    )
    SupplementInteractionSeverity.UNKNOWN -> SupplementSeverityUi(
        "현재 데이터로 판단할 수 없음",
        "현재 확보된 데이터만으로 병용 여부를 충분히 확인할 수 없습니다.",
    )
}

internal fun supplementFailureMessage(code: String): String = when (code) {
    "MEDICATION_NOT_FOUND", "MEDICATION_PRODUCT_LOOKUP_FAILED" -> "처방약 정보를 확인할 수 없습니다."
    "MEDICATION_INGREDIENT_LOOKUP_FAILED", "MEDICATION_INGREDIENT_CODE_MISSING" ->
        "처방약 성분 정보를 확인하지 못했습니다."
    "MEDICATION_OVERVIEW_LOOKUP_FAILED" -> "의약품 부가정보를 확인하지 못했습니다."
    "SUPPLEMENT_NOT_FOUND", "SUPPLEMENT_PRODUCT_LOOKUP_FAILED" -> "건강기능식품 정보를 확인할 수 없습니다."
    "SUPPLEMENT_INGREDIENT_MAPPING_MISSING", "SUPPLEMENT_INGREDIENT_MAPPING_LOOKUP_FAILED" ->
        "검수된 건강기능식품 원료 정보가 부족합니다."
    "SUPPLEMENT_INGREDIENT_UNVERIFIED" -> "건강기능식품 원료 정보가 아직 검수되지 않았습니다."
    "RULE_CATALOG_UNAVAILABLE", "RULE_CATALOG_INVALID" -> "병용 정보 데이터베이스를 현재 사용할 수 없습니다."
    "RULE_LOOKUP_FAILED", "RULE_SOURCE_UNVERIFIED" -> "병용 정보를 완전히 확인하지 못했습니다."
    "PAIR_EVALUATION_INCOMPLETE" -> "일부 성분 조합의 분석이 완료되지 않았습니다."
    else -> "일부 정보를 확인하지 못했습니다."
}

@Composable
fun SupplementInteractionResultScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    nav: NavHostController,
) {
    FixedBackHeaderScreen(
        title = "약–건강기능식품 병용 결과",
        nav = nav,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        when (val result = state.supplementInteraction) {
            LoadState.Idle -> item { SupplementEmptyCard("아직 실행한 병용 분석이 없습니다.") }
            LoadState.Loading -> item { SupplementLoadingCard("공식 제품·성분과 검수 근거를 확인하는 중입니다.") }
            LoadState.Empty -> item { SupplementEmptyCard("분석 결과가 없습니다.") }
            is LoadState.Error -> item { SupplementErrorCard(result.message, viewModel::retrySupplementInteraction) }
            is LoadState.Content -> supplementInteractionResult(result.value)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.supplementInteractionResult(
    response: SupplementInteractionCheckResponse,
) {
    val severity = response.severityValue
    val ui = supplementSeverityUi(severity)
    val color = when (severity) {
        SupplementInteractionSeverity.AVOID_COMBINATION -> Danger
        SupplementInteractionSeverity.CAUTION -> Warning
        SupplementInteractionSeverity.NO_VERIFIED_RULE_FOUND -> Primary
        SupplementInteractionSeverity.UNKNOWN -> Muted
    }
    item {
        Card(
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (severity == SupplementInteractionSeverity.UNKNOWN) Icons.Default.HelpOutline else Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = color,
                    )
                    Text(ui.title, Modifier.padding(start = 8.dp), color = color, fontWeight = FontWeight.Bold)
                }
                Text(ui.guidance)
                Text(response.message, color = Muted)
            }
        }
    }
    item {
        SupplementInfoCard(
            "분석 제품",
            listOf(
                "처방약" to (response.medication?.productName ?: "확인되지 않음"),
                "건강기능식품" to (response.supplement?.productName ?: "확인되지 않음"),
            ),
        )
    }
    item {
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("설명", fontWeight = FontWeight.Bold)
                Text(response.explanation.summary)
                Text(response.explanation.rationale, color = Muted)
                response.explanation.keyPoints.forEach { Text("• $it") }
                Text(response.explanation.consultationAdvice, color = PrimaryDark, fontWeight = FontWeight.SemiBold)
                if (response.explanation.statusValue == SupplementInteractionExplanationStatus.UNAVAILABLE) {
                    Text(
                        "자동 설명을 사용할 수 없어 공식 분석 결과만 표시합니다.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (!response.coverage.complete) {
        item { SupplementNotice("일부 정보가 확인되지 않아 분석이 완전하지 않습니다.") }
    }
    if (response.failedSteps.isNotEmpty()) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E5)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("확인하지 못한 정보", fontWeight = FontWeight.Bold)
                    response.failedSteps.map(::supplementFailureMessage).distinct().forEach { Text("• $it") }
                }
            }
        }
    }
    if (response.evidence.isNotEmpty()) {
        item { Text("확인된 근거", style = MaterialTheme.typography.titleMedium) }
        items(response.evidence, key = { "${it.ruleId}:${it.sourceReferenceId}" }) { evidence ->
            var expanded by remember(evidence.ruleId, evidence.sourceReferenceId) { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                colors = CardDefaults.cardColors(containerColor = SurfaceSoft),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FactCheck, null, tint = Primary)
                        Text(evidence.sourceTitle, Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                    }
                    Text("${evidence.drugIngredientName} × ${evidence.supplementIngredientName}")
                    Text("출처 기관: ${evidence.sourceAuthority}", color = Muted)
                    HorizontalDivider()
                    Text(
                        evidence.originalText,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(if (expanded) "접기" else "근거 상세 보기", color = Primary)
                }
            }
        }
    }
    item { SupplementNotice(response.disclaimer) }
    item { Spacer(Modifier.height(24.dp)) }
}

@Composable
private fun SupplementLoadingCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(text, Modifier.padding(start = 14.dp))
        }
    }
}

@Composable
private fun SupplementErrorCard(text: String, retry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEEE)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = Danger)
            Text(text, color = Danger)
            Button(onClick = retry) { Text("다시 시도") }
        }
    }
}

@Composable
private fun SupplementEmptyCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(18.dp)) {
        Text(text, Modifier.fillMaxWidth().padding(20.dp), color = Muted)
    }
}

@Composable
private fun SupplementInfoCard(title: String, rows: List<Pair<String, String>>) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            rows.forEach { (label, value) -> Text("$label: $value") }
        }
    }
}

@Composable
private fun SupplementNotice(text: String) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFEAF6FF), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Default.Info, null, tint = Primary, modifier = Modifier.size(20.dp))
        Text(text, Modifier.padding(start = 8.dp), color = PrimaryDark)
    }
}
