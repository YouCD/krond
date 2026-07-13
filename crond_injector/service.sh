#!/system/bin/sh

MODDIR=${0%/*}
CROND_BIN=$MODDIR/crond/bin/crond
CROND_LOG=$MODDIR/crond/crond.log

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

# 确保 dcron 运行目录存在（路径已编译进二进制）
for dir in /data/cron/crontabs /data/cron/system /data/cron/stamps /data/cron/tmp; do
    [ -d "$dir" ] || mkdir -p "$dir" 2>/dev/null
done

# 如果模块的默认 crontab 不存在于 /data/cron，则放置一份
if [ -f "$MODDIR/crond/crontabs/root" ] && [ ! -f /data/cron/crontabs/root ]; then
    cp "$MODDIR/crond/crontabs/root" /data/cron/crontabs/root
    chmod 644 /data/cron/crontabs/root
fi

# 杀死已存在的 dcron 进程
PID=$(pidof crond)
[ -n "$PID" ] && kill "$PID" 2>/dev/null && sleep 1

# 启动 dcron（后台运行，日志输出到 syslog/logcat）
$CROND_BIN -b -S 2>&1

if [ $? -eq 0 ]; then
    echo "[$(date)] dcron 服务启动成功" >> $CROND_LOG
else
    echo "[$(date)] dcron 服务启动失败" >> $CROND_LOG
fi
