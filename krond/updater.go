package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"strings"
	"syscall"
	"time"

	"github.com/coreos/go-semver/semver"
)

const (
	githubAPIURL  = "https://api.github.com/repos/YouCD/krond/releases/latest"
	updateZipPath = "/data/local/tmp/krond_update.zip"
)

func newHTTPClient(timeout time.Duration) *http.Client {
	return &http.Client{Timeout: timeout}
}

const applyUpdateScript = `#!/system/bin/sh
ZIP="/data/local/tmp/krond_update.zip"
MODDIR="/data/adb/modules/krond_injector"
LOG="/data/krond/logs/krond.log"
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [UPDATE] $*" >> "$LOG"; }
log "等待 krond 退出..."
for i in 1 2 3 4 5 6 7 8 9 10; do
	pidof krond >/dev/null 2>&1 || break
	sleep 1
done
if pidof krond >/dev/null 2>&1; then
	log "krond 未正常退出，强制终止..."
	kill -9 $(pidof krond) 2>/dev/null
	sleep 2
fi
log "执行 KSU 模块安装..."
ksud module install "$ZIP" >> "$LOG" 2>&1
EXIT=$?
rm -f "$ZIP" "$0"
if [ $EXIT -ne 0 ]; then
	log "更新失败 (ksud exit=$EXIT)"
	exit $EXIT
fi
log "更新成功，重启 krond..."
sh "$MODDIR/service.sh" &
log "自更新流程完成"
`

type githubAsset struct {
	Name               string `json:"name"`
	BrowserDownloadURL string `json:"browser_download_url"`
	Size               int64  `json:"size"`
}

type githubRelease struct {
	TagName     string        `json:"tag_name"`
	Prerelease  bool          `json:"prerelease"`
	PublishedAt string        `json:"published_at"`
	Assets      []githubAsset `json:"assets"`
	Body        string        `json:"body"`
}

type updateStatus struct {
	CurrentVersion string `json:"currentVersion"`
	LatestVersion  string `json:"latestVersion"`
	HasUpdate      bool   `json:"hasUpdate"`
	DownloadURL    string `json:"downloadUrl,omitempty"`
	ChangelogURL   string `json:"changelogUrl,omitempty"`
	PublishedAt   string `json:"publishedAt,omitempty"`
	IsPreRelease  bool   `json:"isPreRelease"`
	AssetSize     int64  `json:"assetSize,omitempty"`
	Changelog     string `json:"changelog,omitempty"`
}

func checkUpdate() (*updateStatus, error) {
	client := newHTTPClient(15 * time.Second)
	req, err := http.NewRequest(http.MethodGet, githubAPIURL, nil)
	if err != nil {
		return nil, fmt.Errorf("创建请求失败: %w", err)
	}
	req.Header.Set("User-Agent", "krond/"+Version)
	if GithubToken != "" {
		req.Header.Set("Authorization", "Bearer "+GithubToken)
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("获取 GitHub Release 失败: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("GitHub API 返回 %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}

	var release githubRelease
	if err := json.NewDecoder(resp.Body).Decode(&release); err != nil {
		return nil, fmt.Errorf("解析 Release 信息失败: %w", err)
	}

	status := &updateStatus{
		CurrentVersion: Version,
		LatestVersion:  strings.TrimPrefix(release.TagName, "v"),
		ChangelogURL:   fmt.Sprintf("https://github.com/YouCD/krond/releases/tag/%s", release.TagName),
		PublishedAt:    release.PublishedAt,
		IsPreRelease:   release.Prerelease,
		Changelog:      release.Body,
	}

	curVer, err := semver.NewVersion(strings.TrimPrefix(Version, "v"))
	if err != nil {
		appLogger.Printf("当前版本(%s)无法解析为 semver，跳过版本对比", Version)
		status.LatestVersion = strings.TrimPrefix(release.TagName, "v")
		status.HasUpdate = false
		return status, nil
	}

	latestVer, err := semver.NewVersion(status.LatestVersion)
	if err != nil {
		return nil, fmt.Errorf("解析最新版本(%s)失败: %w", status.LatestVersion, err)
	}

	status.HasUpdate = curVer.LessThan(*latestVer)

	for _, asset := range release.Assets {
		if asset.Name == "krond_injector.zip" {
			status.DownloadURL = asset.BrowserDownloadURL
			status.AssetSize = asset.Size
			break
		}
	}

	return status, nil
}

func downloadUpdate(url string) error {
	appLogger.Printf("自更新: 开始下载更新包 %s", url)

	client := newHTTPClient(120 * time.Second)
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return fmt.Errorf("创建下载请求失败: %w", err)
	}
	req.Header.Set("User-Agent", "krond/"+Version)
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("下载失败: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("下载返回 %d, 预期 200", resp.StatusCode)
	}

	f, err := os.Create(updateZipPath)
	if err != nil {
		return fmt.Errorf("创建临时文件失败: %w", err)
	}
	defer f.Close()

	written, err := io.Copy(f, resp.Body)
	if err != nil {
		os.Remove(updateZipPath)
		return fmt.Errorf("写入文件失败: %w", err)
	}

	if written == 0 {
		os.Remove(updateZipPath)
		return fmt.Errorf("下载内容为空")
	}

	appLogger.Printf("自更新: 下载完成 (%d 字节)", written)
	return nil
}

func triggerUpdate() error {
	scriptPath := "/data/local/tmp/krond_apply_update.sh"
	if err := os.WriteFile(scriptPath, []byte(applyUpdateScript), 0755); err != nil {
		return fmt.Errorf("写入更新脚本失败: %w", err)
	}

	appLogger.Printf("自更新: 启动脚本 %s，krond 即将退出", scriptPath)

	cmd := exec.Command("/system/bin/sh", scriptPath)
	cmd.SysProcAttr = &syscall.SysProcAttr{Setsid: true}
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("启动更新脚本失败: %w", err)
	}

	appLogger.Printf("自更新: 脚本已启动 (PID=%d)", cmd.Process.Pid)
	return nil
}

func shutdownForUpdate() {
	appLogger.Println("自更新: 正在关闭 krond...")

	if sched != nil {
		waitJobs := sched.Stop()
		select {
		case <-waitJobs.Done():
			appLogger.Println("自更新: 所有任务执行完毕")
		case <-time.After(30 * time.Second):
			appLogger.Println("自更新: 等待任务超时，强制关闭")
		}
	}

	if httpServer != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := httpServer.Shutdown(ctx); err != nil {
			appLogger.Printf("自更新: HTTP server 关闭: %v", err)
		} else {
			appLogger.Println("自更新: HTTP server 已关闭")
		}
	}

	if logWriter != nil {
		logWriter.Close()
	}

	os.Exit(0)
}
