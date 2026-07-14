package online.youcd.krond.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class JobResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getIntExtra("job_id", -1)
        val exitCode = intent.getIntExtra("exit_code", -1)
        val duration = intent.getStringExtra("duration") ?: ""
        val jobName = intent.getStringExtra("job_name") ?: "未知"

        if (jobId < 0) return

        NotificationHelper.notifyJobResult(context, jobId, jobName, exitCode, duration)
    }
}
