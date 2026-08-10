package com.haneul.medassist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.haneul.medassist.AppUiState
import com.haneul.medassist.AppViewModel
import com.haneul.medassist.ui.theme.Muted
import com.haneul.medassist.ui.theme.Primary
import kotlinx.coroutines.delay

@Composable
fun OcrLoadingScreen(state: AppUiState, viewModel: AppViewModel, nav: NavHostController) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.submitPhotos { ready = true } }
    LaunchedEffect(ready) {
        if (ready) {
            delay(650)
            nav.navigate(Routes.REVIEW) { popUpTo(Routes.OCR_LOADING) { inclusive = true } }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (ready) {
                Icon(Icons.Default.CheckCircle, contentDescription = "OCR 완료", tint = Primary, modifier = Modifier.size(104.dp))
                Text("OCR 분석이 완료되었습니다", style = MaterialTheme.typography.titleLarge)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(86.dp), strokeWidth = 7.dp)
                Text("잠시만 기다려 주세요", style = MaterialTheme.typography.titleLarge)
                Text("성분 조합 분석 중...", color = Muted)
            }
        }
    }
}
