#!/system/bin/sh

# 确保关键命令可用
export PATH=/system/bin:/system/xbin:/sbin:$PATH

# 停止 dcron 服务
PID=$(pidof crond)
[ -n "$PID" ] && kill "$PID" 2>/dev/null

# 本脚本在启动早期执行（日志时间戳常为 1970 年），此时 system_server 的 PackageManager 尚未就绪，
# 直接调用 pm/cmd package 会报 "Can't find service: package"，卸载必然失败。
# 因此此处不直接卸载，而是派发一个延迟脚本：等待开机完成后再以 root 执行 pm uninstall。
DEFER=/data/local/tmp/crond_defer_uninstall.sh
cat > "$DEFER" <<'EOF'
#!/system/bin/sh
export PATH=/system/bin:/system/xbin:/sbin:$PATH
for i in $(seq 1 90); do
    [ "$(getprop sys.boot_completed)" = "1" ] && break
    sleep 2
done
sleep 5
su -c "pm uninstall --user 0 com.cronapp" >/dev/null 2>&1
su -c "pm uninstall com.cronapp" >/dev/null 2>&1
pm uninstall --user 0 com.cronapp >/dev/null 2>&1
rm -f /data/local/tmp/crond_defer_uninstall.sh
EOF
chmod 755 "$DEFER"
nohup sh "$DEFER" >/dev/null 2>&1 &

# 清理运行目录
rm -rf /data/cron
