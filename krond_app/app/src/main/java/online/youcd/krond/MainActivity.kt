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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                        NavigationBar(
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                            BottomNavItem(
                                selected = state.selectedTab == 0,
                                onClick = { viewModel.selectTab(0) },
                                selectedIcon = Icons.Filled.Schedule,
                                unselectedIcon = Icons.Outlined.Schedule,
                                label = "任务"
                            )
                            BottomNavItem(
                                selected = state.selectedTab == 1,
                                onClick = { viewModel.selectTab(1) },
                                selectedIcon = Icons.Filled.Terminal,
                                unselectedIcon = Icons.Outlined.Terminal,
                                label = "日志"
                            )
                            BottomNavItem(
                                selected = state.selectedTab == 2,
                                onClick = { viewModel.selectTab(2) },
                                selectedIcon = Icons.Filled.BarChart,
                                unselectedIcon = Icons.Outlined.BarChart,
                                label = "统计"
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

@Composable
private fun RowScope.BottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else Color.Transparent,
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (selected) selectedIcon else unselectedIcon,
                        contentDescription = label,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                ),
                color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            indicatorColor = Color.Transparent
        )
    )
}
