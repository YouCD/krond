#!/system/bin/sh
# ============================================================
# databackup_cron.sh — 供 crond 定时调用的 Swift Backup 封装
# 用法:
#   1. 在 Swift Backup 中设置备份计划
#   2. 长按计划标题复制 schedule_id
#   3. 加入 crontab：
#        0 3 * * * /data/cron/scripts/databackup_cron.sh
#   或指定计划：
#        0 3 * * * /data/cron/scripts/databackup_cron.sh schedule_id1 schedule_id2
# ============================================================

LOG_FILE="/data/cron/databackup.log"

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG_FILE"
}

log "===== 开始 Swift Backup ====="

if [ $# -ge 1 ]; then
  am start -n org.swiftapps.swiftbackup/.shortcuts.ShortcutsActivity -e "cmd" "-s" "$@" 2>&1 | tee -a "$LOG_FILE"
else
  am start -n org.swiftapps.swiftbackup/.shortcuts.ShortcutsActivity 2>&1 | tee -a "$LOG_FILE"
fi

EXIT_CODE=${PIPESTATUS[0]}
log "am start 退出码: $EXIT_CODE"
log "===== 结束 ====="

exit $EXIT_CODE
