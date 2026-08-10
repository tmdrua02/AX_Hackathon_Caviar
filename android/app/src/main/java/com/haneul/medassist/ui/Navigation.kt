package com.haneul.medassist.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.haneul.medassist.AppUiState
import com.haneul.medassist.AppViewModel
import com.haneul.medassist.R

object Routes {
    const val HOME = "home"
    const val ALARM = "alarm"
    const val INTERACTION = "interaction/list"
    const val CAPTURE = "interaction/capture"
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

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "홈", Icons.Default.Home),
    Tab(Routes.ALARM, "복용알람", Icons.Default.Alarm),
    Tab(Routes.INTERACTION, "동시복용", Icons.Default.Medication),
    Tab(Routes.RECORDING, "진료녹음", Icons.Default.Mic),
    Tab(Routes.RECORDS, "기록", Icons.Default.FactCheck),
)

@Composable
fun MedAssistNavigation(state: AppUiState, viewModel: AppViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: Routes.HOME
    val topLevel = tabs.any { it.route == route }
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHost.showSnackbar(it); viewModel.consumeSnackbar() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = { if (topLevel) BottomBar(nav, route) },
        floatingActionButton = {
            if (route != Routes.CHAT && route != Routes.CAPTURE && route != Routes.REVIEW && route != Routes.ANALYZING && route != Routes.RESULT) {
                FloatingActionButton(
                    onClick = { nav.navigate(Routes.CHAT) },
                    containerColor = Color.Transparent,
                    contentColor = Color.Unspecified,
                    modifier = Modifier.size(68.dp),
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.medibot_pill_button),
                        contentDescription = "메디봇 챗봇 열기",
                        modifier = Modifier.size(86.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            composable(Routes.HOME) { HomeScreen(state, viewModel, padding) }
            composable(Routes.ALARM) { AlarmScreen(state, padding) }
            composable(Routes.INTERACTION) {
                InteractionListScreen(
                    state = state,
                    viewModel = viewModel,
                    padding = padding,
                    onAdd = { nav.navigate(Routes.CAPTURE) },
                    onStart = { viewModel.startAnalysis { nav.navigate(Routes.ANALYZING) } },
                    onSupplementResult = { nav.navigate(Routes.SUPPLEMENT_RESULT) },
                )
            }
            composable(Routes.CAPTURE) { CaptureScreen(state, viewModel, nav) }
            composable(Routes.REVIEW) { ReviewScreen(state, viewModel, nav) }
            composable(Routes.ANALYZING) { AnalyzingScreen(state, viewModel, nav) }
            composable(Routes.RESULT) { ResultScreen(state, viewModel, nav) }
            composable(Routes.SUPPLEMENT_RESULT) { SupplementInteractionResultScreen(state, viewModel, nav) }
            composable(Routes.RECORDING) { RecordingHomeScreen(state, viewModel, padding, nav) }
            composable(Routes.ACTIVE_RECORDING) { ActiveRecordingScreen(state, viewModel, nav) }
            composable(Routes.RECORDING_FILES) { RecordingFilesScreen(state, viewModel, nav) }
            composable(Routes.RECORDING_DETAIL) { entry -> RecordingDetailScreen(state, entry.arguments?.getString("id").orEmpty(), viewModel, nav) }
            composable(Routes.RECORDS) { RecordsScreen(state, padding, nav) }
            composable(Routes.CHAT) { ChatScreen(state, viewModel, nav) }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String) {
    NavigationBar(containerColor = Color.White) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { androidx.compose.material3.Text(tab.label) },
                alwaysShowLabel = true,
            )
        }
    }
}
