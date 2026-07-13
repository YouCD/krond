package com.cronapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cronapp.ui.screens.CronScreen
import com.cronapp.ui.screens.LogScreen
import com.cronapp.ui.theme.CronAppTheme
import com.cronapp.viewmodel.CronViewModel

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
                        onBack = { viewModel.closeLogs() },
                        onRefresh = { viewModel.refreshLogs() },
                        onClear = { viewModel.clearLogs() }
                    )
                } else {
                    CronScreen(viewModel)
                }
            }
        }
    }
}
