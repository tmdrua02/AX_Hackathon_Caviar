package com.haneul.medassist.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.haneul.medassist.AppUiState
import com.haneul.medassist.AppViewModel
import com.haneul.medassist.ui.theme.Muted
import com.haneul.medassist.ui.theme.Primary
import java.io.File

@Composable
fun CaptureScreen(state: AppUiState, viewModel: AppViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val isFront = state.frontPhoto == null
    val bothReady = state.frontPhoto != null && state.backPhoto != null
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pending = uri
    }

    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }
    DisposableEffect(hasPermission, lifecycleOwner) {
        if (hasPermission) controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = Color.White)
            }
            Text("제품·처방전 촬영", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            if (bothReady) "앞면과 뒷면 촬영 완료" else if (isFront) "앞면 1/2" else "뒷면 2/2",
            color = Primary, modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF17191D))) {
            when {
                pending != null -> AndroidView(
                    factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                    update = { it.setImageURI(pending) }, modifier = Modifier.fillMaxSize(),
                )
                bothReady -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "앞면과 뒷면 준비 완료", tint = Primary, modifier = Modifier.size(92.dp))
                    Text("두 장이 준비되었습니다", color = Color.White)
                }
                hasPermission -> AndroidView(
                    factory = { PreviewView(it).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NoPhotography, null, tint = Color.White, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("카메라 권한이 필요합니다. 권한이 거부되었으면 설정에서 허용하거나 갤러리 대체 경로를 사용하세요.", color = Color.White)
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("권한 다시 요청") }
                }
            }
            if (!bothReady && pending == null) {
                Column(Modifier.align(Alignment.TopCenter).padding(18.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .58f)).padding(12.dp)) {
                    Text("• 네 모서리를 모두 포함해 주세요", color = Color.White)
                    Text("• 그림자·빛 반사를 피해 촬영해 주세요", color = Color.White)
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        when {
            pending != null -> Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pending = null }, Modifier.weight(1f).heightIn(min = 54.dp)) { Text("다시 촬영") }
                Button(onClick = { pending?.let { viewModel.setPhoto(isFront, it) }; pending = null }, Modifier.weight(1f).heightIn(min = 54.dp)) { Text("이 사진 사용") }
            }
            bothReady -> Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { viewModel.clearPhoto(true) }, Modifier.weight(1f)) { Text("앞면 다시 선택") }
                    TextButton(onClick = { viewModel.clearPhoto(false) }, Modifier.weight(1f)) { Text("뒷면 다시 선택") }
                }
                Button(
                    onClick = { viewModel.submitPhotos { nav.navigate(Routes.REVIEW) } }, enabled = !state.draftLoading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp),
                ) { if (state.draftLoading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White) else Text("OCR 결과 검토하기") }
            }
            else -> Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("갤러리")
                }
                IconButton(
                    onClick = {
                        val file = File(context.cacheDir, "prescription-${System.currentTimeMillis()}.jpg")
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()
                        controller.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) { pending = Uri.fromFile(file); error = null }
                            override fun onError(exception: ImageCaptureException) { error = "촬영에 실패했습니다. 갤러리에서 선택하거나 다시 시도해 주세요." }
                        })
                    },
                    enabled = hasPermission,
                    modifier = Modifier.size(82.dp).clip(CircleShape).background(Color.White),
                ) { Icon(Icons.Default.Camera, contentDescription = if (isFront) "앞면 촬영" else "뒷면 촬영", tint = Color.Black, modifier = Modifier.size(40.dp)) }
                Spacer(Modifier.width(80.dp))
            }
        }
    }
}
