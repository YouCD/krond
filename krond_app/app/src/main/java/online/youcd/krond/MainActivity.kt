package online.youcd.krond

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import online.youcd.krond.ui.screens.CronScreen
import online.youcd.krond.ui.screens.LogScreen
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

                if (state.showLogs) {
                    LogScreen(
                        logs = state.logs,
                        isLoading = state.isLoadingLogs,
                        currentLogTarget = state.currentLogTarget,
                        onBack = { viewModel.closeLogs() },
                        onClear = { viewModel.clearLogs() },
                        onLogTargetChange = { viewModel.setLogTarget(it) }
                    )
                } else {
                    CronScreen(viewModel)
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
