#!/system/bin/sh

export PATH=/system/bin:/system/xbin:/sbin:$PATH

# 停止 krond 服务
PID=$(pidof krond)
[ -n "$PID" ] && kill "$PID" 2>/dev/null

# 等待开机后卸载 App
DEFER=/data/local/tmp/krond_defer_uninstall.sh
cat > "$DEFER" <<'EOF'
#!/system/bin/sh
export PATH=/system/bin:/system/xbin:/sbin:$PATH
for i in $(seq 1 90); do
    [ "$(getprop sys.boot_completed)" = "1" ] && break
    sleep 2
done
sleep 5
su -c "pm uninstall --user 0 online.youcd.krond" >/dev/null 2>&1
su -c "pm uninstall online.youcd.krond" >/dev/null 2>&1
pm uninstall --user 0 online.youcd.krond >/dev/null 2>&1
rm -f /data/local/tmp/krond_defer_uninstall.sh
EOF
chmod 755 "$DEFER"
nohup sh "$DEFER" >/dev/null 2>&1 &

# 清理运行目录
rm -rf /data/krond
