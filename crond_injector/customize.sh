#!/system/bin/sh

# 管理 App 通过模块 overlay（system/app/CrondInjector/CrondInjector.apk）挂载为系统应用，
# 此处保留该目录，不再清理。

# 设置二进制权限
set_perm $MODPATH/crond/bin/crond 0 0 0755
set_perm $MODPATH/crond/bin/crontab 0 0 0755
set_perm $MODPATH/system/bin/crontab 0 0 0755

# 安装时预创建运行目录
for dir in /data/cron/crontabs /data/cron/system /data/cron/stamps /data/cron/tmp; do
    mkdir -p "$dir" 2>/dev/null
done

# 如果 /data/cron 还没有 crontab，从模块复制默认配置
if [ -f "$MODPATH/crond/crontabs/root" ] && [ ! -f /data/cron/crontabs/root ]; then
    cp "$MODPATH/crond/crontabs/root" /data/cron/crontabs/root
    chmod 644 /data/cron/crontabs/root
fi

ui_print "- dcron crond 模块安装完成"
ui_print "- crontab -l / crontab -e 管理任务"
ui_print "- crontab 目录: /data/cron/crontabs"
ui_print "- 重启后 crond 自动启动"
