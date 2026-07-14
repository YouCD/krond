package online.youcd.krond

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import online.youcd.krond.ui.screens.CronScreen
import online.youcd.krond.ui.screens.LogScreen
import online.youcd.krond.ui.theme.CronAppTheme
import online.youcd.krond.viewmodel.CronViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CronAppTheme {
                val viewModel: CronViewModel = viewModel()
                val state by viewModel.state.collectAsState()
                if (state.showLogs) {
                    LogScreen(
                        logs = state.logs,
                        isLoading = state.isLoadingLogs,
                        currentLogTarget = state.currentLogTarget,
                        onBack = { viewModel.closeLogs() },
                        onRefresh = { viewModel.refreshLogs() },
                        onClear = { viewModel.clearLogs() },
                        onLogTargetChange = { viewModel.setLogTarget(it) }
                    )
                } else {
                    CronScreen(viewModel)
                }
            }
        }
    }
}
