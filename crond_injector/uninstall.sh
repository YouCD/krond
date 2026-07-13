#!/system/bin/sh

# 停止 dcron 服务
PID=$(pidof crond)
[ -n "$PID" ] && kill "$PID" 2>/dev/null

# 清理运行目录
rm -rf /data/cron

# 清理模块日志
rm -f /data/adb/modules/crond_injector/crond/crond.log
