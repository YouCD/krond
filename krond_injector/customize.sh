#!/system/bin/sh

# 设置 krond 二进制权限
set_perm $MODPATH/krond 0 0 0755

# 安装时预创建运行目录
for dir in /data/krond /data/krond/logs /data/krond/scripts; do
    mkdir -p "$dir" 2>/dev/null
done

# 首次安装或升级时，若 /data/krond 还没有 krond.yaml 则复制默认配置
if [ -f "$MODPATH/krond.yaml" ] && [ ! -f /data/krond/krond.yaml ]; then
    cp "$MODPATH/krond.yaml" /data/krond/krond.yaml
    chmod 644 /data/krond/krond.yaml
fi

# 如果 /data/krond/scripts 还没有 databackup_cron.sh 则复制
if [ -f "$MODPATH/scripts/databackup_cron.sh" ] && [ ! -f /data/krond/scripts/databackup_cron.sh ]; then
    cp "$MODPATH/scripts/databackup_cron.sh" /data/krond/scripts/databackup_cron.sh
    chmod 755 /data/krond/scripts/databackup_cron.sh
fi

# 安装管理 App
APK_SRC=$MODPATH/KrondInjector.apk
if [ -f "$APK_SRC" ]; then
    cp "$APK_SRC" /data/local/tmp/KrondInjector.apk 2>/dev/null
    chmod 644 /data/local/tmp/KrondInjector.apk
    pm install -r -d /data/local/tmp/KrondInjector.apk 2>/dev/null || \
    su -c "pm install -r -d /data/local/tmp/KrondInjector.apk" 2>/dev/null || true
    rm -f /data/local/tmp/KrondInjector.apk
fi

ui_print "- krond 模块安装完成"
ui_print "- krond 守护进程基于 Go，监听 @krond 抽象 Unix socket"
ui_print "- 配置目录: /data/krond"
ui_print "- 重启后 krond 自动启动"
