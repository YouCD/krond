package online.youcd.krond

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import online.youcd.krond.ui.screens.CronScreen
import online.youcd.krond.ui.screens.LogScreen
import online.youcd.krond.ui.screens.StatsScreen
import online.youcd.krond.ui.theme.CronAppTheme
import online.youcd.krond.viewmodel.CronViewModel

class MainActivity : ComponentActivity() {
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotifPermission()

        setContent {
            CronAppTheme {
                val viewModel: CronViewModel = viewModel()
                val state by viewModel.state.collectAsState()

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = state.selectedTab == 0,
                                onClick = { viewModel.selectTab(0) },
                                icon = {
                                    Icon(
                                        if (state.selectedTab == 0) Icons.Filled.Schedule else Icons.Outlined.Schedule,
                                        contentDescription = "任务"
                                    )
                                },
                                label = { Text("任务") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = state.selectedTab == 1,
                                onClick = { viewModel.selectTab(1) },
                                icon = {
                                    Icon(
                                        if (state.selectedTab == 1) Icons.Filled.Terminal else Icons.Outlined.Terminal,
                                        contentDescription = "日志"
                                    )
                                },
                                label = { Text("日志") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = state.selectedTab == 2,
                                onClick = { viewModel.selectTab(2) },
                                icon = {
                                    Icon(
                                        if (state.selectedTab == 2) Icons.Filled.BarChart else Icons.Outlined.BarChart,
                                        contentDescription = "统计"
                                    )
                                },
                                label = { Text("统计") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                        when (state.selectedTab) {
                            0 -> CronScreen(viewModel)
                            1 -> LogScreen(
                                    logs = state.logs,
                                    isLoading = state.isLoadingLogs,
                                    currentLogTarget = state.currentLogTarget,
                                    onClear = { viewModel.clearLogs() },
                                    onLogTargetChange = { viewModel.setLogTarget(it) }
                                )
                            2 -> StatsScreen(viewModel)
                        }
                    }
                }
            }
        }
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
