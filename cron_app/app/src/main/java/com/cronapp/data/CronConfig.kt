package com.cronapp.data

/**
 * 集中管理模块路径与运行目录，避免 App 与模块 id、日志路径等硬耦合。
 * 修改模块名或目录时只需改这里。
 */
object CronConfig {
    const val MODULE_ID = "crond_injector"
    const val CROND_BIN = "/data/adb/modules/$MODULE_ID/crond/bin/crond"
    const val CRON_LOG = "/data/cron/crond.log"
    val CRON_DIRS: List<String> = listOf(
        "/data/cron/crontabs",
        "/data/cron/system",
        "/data/cron/stamps",
        "/data/cron/tmp"
    )
}
