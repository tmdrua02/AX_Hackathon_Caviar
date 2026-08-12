package com.haneul.medassist.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.haneul.medassist.AppUiState
import com.haneul.medassist.AppViewModel
import com.haneul.medassist.R
import com.haneul.medassist.ui.theme.Muted

object Routes {
    const val HOME = "home"
    const val ALARM = "alarm"
    const val ALARM_EDIT = "alarm/edit?id={id}"
    const val ALARM_SELECT = "alarm/select"
    const val INTERACTION_BASE = "interaction/list"
    const val INTERACTION = "interaction/list?section={section}"
    const val MANUAL_MEDICATION = "interaction/manual-add"
    const val CAPTURE = "interaction/capture?mode={mode}"
    const val OCR_LOADING = "interaction/ocr-loading"
    const val REVIEW = "interaction/review"
    const val ANALYZING = "interaction/analyzing"
    const val RESULT = "interaction/result"
    const val SUPPLEMENT_RESULT = "interaction/supplement-result"
    const val RECORDING = "recording/home"
    const val ACTIVE_RECORDING = "recording/active"
    const val RECORDING_FILES = "recording/files"
    const val RECORDING_DETAIL = "recording/detail/{id}"
    const val RECORDS = "records"
    const val CHAT = "chat"
}

private data class Tab(
    val route: String,
    val label: String,
    val selectedIconRes: Int,
    val unselectedIconRes: Int,
)

private val tabs = listOf(
    Tab(Routes.HOME, "홈", R.drawable.nav_home_selected, R.drawable.nav_home_unselected),
    Tab(Routes.ALARM, "복용알람", R.drawable.nav_alarm_selected, R.drawable.nav_alarm_unselected),
    Tab(Routes.INTERACTION_BASE, "복용약 확인", R.drawable.nav_medication_selected, R.drawable.nav_medication_unselected),
    Tab(Routes.RECORDING, "진료녹음", R.drawable.nav_recording_selected, R.drawable.nav_recording_unselected),
    Tab(Routes.RECORDS, "기록", R.drawable.nav_records_selected, R.drawable.nav_records_unselected),
)

@Composable
fun MedAssistNavigation(state: AppUiState, viewModel: AppViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: Routes.HOME
    val selectedTabRoute = topLevelRoute(route)
    val isTopLevel = isTopLevelDestination(route)
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHost.showSnackbar(it); viewModel.consumeSnackbar() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = { if (isTopLevel) BottomBar(nav, selectedTabRoute) },
        floatingActionButton = {
            if (isTopLevel) {
                FloatingActionButton(
                    onClick = { nav.navigate(Routes.CHAT) },
                    containerColor = Color.Transparent,
                    contentColor = Color.Unspecified,
                    modifier = Modifier.requiredSize(width = 56.dp, height = 56.dp).offset(x = 8.dp, y = 8.dp),
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                ) {
                    Box(Modifier.fillMaxSize().clip(CircleShape), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.medibot_pill_button),
                            contentDescription = "메디봇 챗봇 열기",
                            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.6f, scaleY = 1.6f),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            composable(Routes.HOME) {
                HomeScreen(state, viewModel, padding) {
                    nav.navigate(Routes.INTERACTION_BASE) { launchSingleTop = true }
                }
            }
            composable(Routes.ALARM) {
                AlarmScreen(
                    state = state,
                    viewModel = viewModel,
                    padding = padding,
                    onAdd = { nav.navigate("alarm/edit?id=") },
                    onEdit = { nav.navigate("alarm/edit?id=$it") },
                    onManage = { nav.navigate(Routes.ALARM_SELECT) },
                )
            }
            composable(Routes.ALARM_SELECT) {
                AlarmSelectionScreen(state, viewModel, onBack = { nav.popBackStack() })
            }
            composable(
                route = Routes.ALARM_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                MedicationAlarmEditScreen(
                    alarmId = entry.arguments?.getString("id").orEmpty().ifBlank { null },
                    state = state,
                    viewModel = viewModel,
                    scaffoldPadding = padding,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                route = Routes.INTERACTION,
                arguments = listOf(navArgument("section") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                InteractionListScreen(
                    state = state,
                    viewModel = viewModel,
                    padding = padding,
                    onAdd = { mode -> nav.navigate("interaction/capture?mode=$mode") },
                    onManualAdd = { nav.navigate(Routes.MANUAL_MEDICATION) },
                    onStart = { viewModel.startAnalysis { nav.navigate(Routes.ANALYZING) } },
                    initialSection = entry.arguments?.getString("section")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { runCatching { MedicationListFilter.valueOf(it) }.getOrNull() },
                )
            }
            composable(Routes.MANUAL_MEDICATION) { ManualMedicationScreen(viewModel, onBack = { nav.popBackStack() }) }
            composable(
                route = Routes.CAPTURE,
                arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "prescription" }),
            ) { entry -> CaptureScreen(state, viewModel, nav, entry.arguments?.getString("mode") ?: "prescription") }
            composable(Routes.OCR_LOADING) { OcrLoadingScreen(state, viewModel, nav) }
            composable(Routes.REVIEW) {
                ManualMedicationScreen(
                    viewModel = viewModel,
                    onBack = { nav.popBackStack() },
                    title = "추가 정보 입력",
                    initialDraft = state.draft,
                    onSaved = {
                        viewModel.resetComparisonCapture()
                        nav.popBackStack(Routes.INTERACTION, false)
                    },
                )
            }
            composable(Routes.ANALYZING) { AnalyzingScreen(state, viewModel, nav) }
            composable(Routes.RESULT) { ResultScreen(state, viewModel, nav) }
            composable(Routes.SUPPLEMENT_RESULT) { SupplementInteractionResultScreen(state, viewModel, nav) }
            composable(Routes.RECORDING) { RecordingHomeScreen(state, viewModel, padding, nav) }
            composable(Routes.ACTIVE_RECORDING) { ActiveRecordingScreen(state, viewModel, nav, padding) }
            composable(Routes.RECORDING_FILES) { RecordingFilesScreen(state, viewModel, nav) }
            composable(Routes.RECORDING_DETAIL) { entry -> RecordingDetailScreen(state, entry.arguments?.getString("id").orEmpty(), viewModel, nav) }
            composable(Routes.RECORDS) { RecordsScreen(state, padding, nav) }
            composable(Routes.CHAT) { ChatScreen(state, viewModel, nav) }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String) {
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth().height(88.dp).shadow(10.dp, RoundedCornerShape(44.dp)).clip(RoundedCornerShape(44.dp)),
            containerColor = Color.White,
            tonalElevation = 0.dp,
        ) {
            tabs.forEach { tab ->
                val selected = current == tab.route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        nav.navigate(tab.route) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    icon = {
                        Image(
                            painter = painterResource(if (selected) tab.selectedIconRes else tab.unselectedIconRes),
                            contentDescription = tab.label,
                            modifier = Modifier.size(28.dp),
                            contentScale = ContentScale.Fit,
                        )
                    },
                    label = {
                        androidx.compose.material3.Text(
                            tab.label,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF222222),
                        selectedTextColor = Color(0xFF222222),
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = Color(0xFFC4C7CF),
                        unselectedTextColor = Color(0xFFC4C7CF),
                    ),
                )
            }
        }
    }
}

private fun topLevelRoute(route: String): String = when {
    route == Routes.HOME -> Routes.HOME
    route.startsWith("alarm/") || route == Routes.ALARM -> Routes.ALARM
    route.startsWith("interaction/") -> Routes.INTERACTION_BASE
    route.startsWith("recording/") || route == Routes.RECORDING -> Routes.RECORDING
    route == Routes.RECORDS -> Routes.RECORDS
    else -> ""
}

private fun isTopLevelDestination(route: String): Boolean = route == Routes.HOME ||
    route == Routes.ALARM ||
    route == Routes.INTERACTION ||
    route == Routes.RECORDING ||
    route == Routes.RECORDS
