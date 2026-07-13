#!/system/bin/sh

# 确保关键命令（pm 等）在模块脚本上下文中可用
export PATH=/system/bin:/system/xbin:/sbin:$PATH

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

# 安装/更新管理 App：以普通应用方式随模块分发，开机后由 pm 安装（用户 App 在 LineageOS 上资源加载稳定，
# 而 system/app overlay 系统应用在本 ROM 会导致 Resources 为 null 而崩溃）。本脚本已等待 boot_completed，
# 此时 PackageManager 已就绪，pm 可用。
APK_SRC=$MODDIR/CrondInjector.apk
if [ -f "$APK_SRC" ]; then
    cp "$APK_SRC" /data/local/tmp/CrondInjector.apk 2>/dev/null
    chmod 644 /data/local/tmp/CrondInjector.apk
    su -c "pm install -r /data/local/tmp/CrondInjector.apk" >/dev/null 2>&1
    rm -f /data/local/tmp/CrondInjector.apk
fi

# 如果模块的默认 crontab 不存在于 /data/cron，则放置一份
if [ -f "$MODDIR/crond/crontabs/root" ] && [ ! -f /data/cron/crontabs/root ]; then
    cp "$MODDIR/crond/crontabs/root" /data/cron/crontabs/root
    chmod 644 /data/cron/crontabs/root
fi

# 杀死已存在的 dcron 进程
PID=$(pidof crond)
[ -n "$PID" ] && kill "$PID" 2>/dev/null && sleep 1

# 启动 dcron（后台运行，日志写入文件供 App 读取）
$CROND_BIN -b -L /data/cron/crond.log -l info 2>&1

if [ $? -eq 0 ]; then
    echo "[$(date)] dcron 服务启动成功" >> $CROND_LOG
else
    echo "[$(date)] dcron 服务启动失败" >> $CROND_LOG
fi
