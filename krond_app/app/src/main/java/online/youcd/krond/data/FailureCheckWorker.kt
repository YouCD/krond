package online.youcd.krond.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import online.youcd.krond.R

object NotificationHelper {
    private const val CHANNEL_ID = "krond_job_failed"
    private var channelCreated = false

    fun ensureChannel(context: Context) {
        if (channelCreated) return
        channelCreated = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Krond 任务状态", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "定时任务执行失败通知" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyJobResult(context: Context, jobId: Int, jobName: String, exitCode: Int, duration: String) {
        if (exitCode == 0) return
        ensureChannel(context)
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(largeIcon)
            .setContentTitle("任务执行失败: $jobName")
            .setContentText("退出码 $exitCode")
            .setStyle(NotificationCompat.BigTextStyle().bigText("任务「$jobName」执行失败，退出码 $exitCode，耗时 $duration"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(jobName.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }
}
