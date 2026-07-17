#!/system/bin/sh

OUTPUT_DIR=${1:-/sdcard/Download}
LOG_DIR=/data/krond/scripts/logs

if [ -z "$1" ]; then
    echo "用法: $0 <输出目录>"
    echo "示例: $0 /sdcard/Download"
    exit 1
fi

mkdir -p "$LOG_DIR" "$OUTPUT_DIR"
LOG_FILE="$LOG_DIR/calllog_$(date +%Y%m%d_%H%M%S).log"

echo "[$(date "+%Y-%m-%d %H:%M:%S")] 开始备份通话记录" | tee -a "$LOG_FILE"
echo "[$(date "+%Y-%m-%d %H:%M:%S")] 输出目录: $OUTPUT_DIR" | tee -a "$LOG_FILE"
echo "----------------------------------------" | tee -a "$LOG_FILE"

for retry in 1 2 3; do
    am start -n org.fossify.phone/.activities.BackupActivity \
      -e output_path "$OUTPUT_DIR" > /tmp/am_out.$$ 2>&1
    AM_EXIT=$?
    cat /tmp/am_out.$$ >> "$LOG_FILE"
    rm /tmp/am_out.$$
    [ $AM_EXIT -eq 0 ] && break
    [ $retry -lt 3 ] && echo "[$(date "+%Y-%m-%d %H:%M:%S")] 启动失败(退出码:$AM_EXIT)，重试第$((retry+1))次..." | tee -a "$LOG_FILE"
    sleep 3
done

for i in 1 2 3 4 5 6 7 8 9 10; do
    sleep 2
    BACKUP_FILE=
    for f in "$OUTPUT_DIR"/*.vcf "$OUTPUT_DIR"/*.xml; do
        [ -f "$f" ] && BACKUP_FILE=$f && break
    done
    [ -n "$BACKUP_FILE" ] && break
done

echo "----------------------------------------" | tee -a "$LOG_FILE"
if [ -n "$BACKUP_FILE" ]; then
    echo "[$(date "+%Y-%m-%d %H:%M:%S")] 备份完成: $BACKUP_FILE" | tee -a "$LOG_FILE"
    echo "文件大小: $(stat -c%s "$BACKUP_FILE" 2>/dev/null) 字节" | tee -a "$LOG_FILE"
else
    echo "[$(date "+%Y-%m-%d %H:%M:%S")] 等待超时，未检测到备份文件" | tee -a "$LOG_FILE"
fi

exit $AM_EXIT
