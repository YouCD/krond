#!/system/bin/sh

export PATH=/system/bin:/system/xbin:/sbin:$PATH

MODDIR=${0%/*}
KROND_BIN=$MODDIR/krond
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

# 确保运行目录存在
for dir in /data/krond /data/krond/logs; do
    [ -d "$dir" ] || mkdir -p "$dir" 2>/dev/null
done

KROND_LOG=/data/krond/logs/krond.log

# 如果 /data/krond 还没有默认配置，复制模块自带配置
if [ -f "$MODDIR/krond.yaml" ] && [ ! -f /data/krond/krond.yaml ]; then
    cp "$MODDIR/krond.yaml" /data/krond/krond.yaml
    chmod 644 /data/krond/krond.yaml
fi

# 如果 /data/krond/scripts 还没有 databackup_cron.sh 则复制
if [ -f "$MODDIR/scripts/databackup_cron.sh" ] && [ ! -f /data/krond/scripts/databackup_cron.sh ]; then
    cp "$MODDIR/scripts/databackup_cron.sh" /data/krond/scripts/databackup_cron.sh
    chmod 755 /data/krond/scripts/databackup_cron.sh
fi

# 安装/更新管理 App
APK_SRC=$MODDIR/KrondInjector.apk
if [ -f "$APK_SRC" ]; then
    cp "$APK_SRC" /data/local/tmp/KrondInjector.apk 2>/dev/null
    chmod 644 /data/local/tmp/KrondInjector.apk
    su -c "pm install -r -d /data/local/tmp/KrondInjector.apk" >/dev/null 2>&1
    rm -f /data/local/tmp/KrondInjector.apk
fi

# 设置系统时区（Android 属性 -> 环境变量，Go 通过 time/tzdata 加载）
export TZ=$(getprop persist.sys.timezone)

# 杀死已存在的 krond 进程
PID=$(pidof krond)
[ -n "$PID" ] && kill "$PID" 2>/dev/null && sleep 1

# 使用 logwrapper 启动 krond（stdout 进入 logcat，-s krond 可过滤）
# 若设备无 logwrapper 则直接运行 krond run（降级，仅文件日志）
if command -v logwrapper >/dev/null 2>&1; then
    logwrapper "$KROND_BIN" run >> "$KROND_LOG" 2>&1 &
else
    nohup "$KROND_BIN" run >> "$KROND_LOG" 2>&1 &
fi

sleep 2
PID=$(pidof krond)
if [ -n "$PID" ]; then
    echo "[$(date)] krond 服务启动成功 (pid $PID)" >> "$KROND_LOG"
else
    echo "[$(date)] krond 服务启动失败" >> "$KROND_LOG"
fi
