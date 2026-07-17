#!/system/bin/sh

SRC=$1
USER_HOST=$2
DST=$3
LOG_DIR=/data/krond/scripts/logs

if [ -z "$SRC" ] || [ -z "$USER_HOST" ] || [ -z "$DST" ]; then
    echo "用法: $0 <源目录> <用户@主机> <远程目标目录>"
    echo "示例: $0 /data/krond/scripts/backup ycd@192.168.104.126 /tmp/"
    exit 1
fi

mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/rsync_$(date +%Y%m%d_%H%M%S).log"

echo "[$(date "+%Y-%m-%d %H:%M:%S")] 开始同步" | tee -a "$LOG_FILE"
echo "[$(date "+%Y-%m-%d %H:%M:%S")] 源: $SRC  目标: $USER_HOST:$DST" | tee -a "$LOG_FILE"
echo "----------------------------------------" | tee -a "$LOG_FILE"

rsync -avz -e "ssh -oUserKnownHostsFile=/data/krond/scripts/ssh_key/known_hosts -i /data/krond/scripts/ssh_key/id_ed25519" "$SRC" "$USER_HOST:$DST" >> "$LOG_FILE" 2>&1
RSYNC_EXIT=$?

echo "----------------------------------------" | tee -a "$LOG_FILE"
if [ $RSYNC_EXIT -eq 0 ]; then
    echo "[$(date "+%Y-%m-%d %H:%M:%S")] 同步完成 (成功)" | tee -a "$LOG_FILE"
else
    echo "[$(date "+%Y-%m-%d %H:%M:%S")] 同步失败 (退出码: $RSYNC_EXIT)" | tee -a "$LOG_FILE"
fi

exit $RSYNC_EXIT
