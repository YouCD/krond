# Krond Injector — Magisk/KernelSU 模块

<p align="center">
  <img src="docs/image/icon_circle.png" alt="Krond Injector 图标" width="120"/>
</p>

基于 Go 自研守护进程 **`krond`** 的 Android 定时任务后端，完全替代 dcron。App 通过 **抽象 Unix socket (`@krond`)** 与 krond 通信（junixsocket + OkHttp），无需 root 即可管理任务。

---

## 目录

- [架构](#架构)
- [模块结构](#模块结构)
- [编译指南](#编译指南)
- [安装方法](#安装方法)
- [用法](#用法)
- [日志管理](#日志管理)
- [开发调试](#开发调试)
- [常见问题](#常见问题)

---

## 架构

```
┌─────────────────────────┐         @krond (AF_UNIX 抽象命名空间)
│  App (online.youcd.krond)│  HTTP    ┌──────────────────────────┐
│  OkHttp + junixsocket   │ ───────► │  krond daemon (root)     │
│  - 任务增删改查          │ ◄─────── │  - HTTP server @krond     │
│  - 启停(经 su 调子命令)  │  socket  │  - robfig/cron 调度       │
│  - 日志/配置查看设置     │          │  - 执行命令(root)         │
└─────────────────────────┘          └───────────┬──────────────┘
                                                 │ exec /system/bin/sh -c
                                                 ▼
                                        Swift Backup / 任意 shell 命令
                                        krond 日志 → krond.log + logcat
```

**关键设计决策**：

| 项 | 方案 |
|----|------|
| 后端 | Go 守护进程 krond，调度库 `robfig/cron/v3` |
| 通信 | 抽象命名空间 Unix socket `@krond` |
| App 端 | junixsocket (`AFUNIXSocketAddress.inAbstractNamespace` + `AFSocketFactory.fixedAddressSocketFactory` 注入 OkHttp) |
| 鉴权 | SELinux 隔离（`sepolicy.rule` 限制 App 域 `connectto`） |
| 启停 | App 经 `su -c krond start\|stop\|restart`；任务读写/状态/日志走 socket |
| 配置 | YAML（`/data/krond/krond.yaml`，含 `jobs` 数组，变更即落盘） |
| 日志 | 支持运行时切换：仅文件 / 仅 logcat / 双写 |

---

## 模块结构

```
krond_injector/
├── krond                          # Go 编译产物（arm64 静态二进制）
├── krond.yaml                     # 默认配置（首启复制到 /data/krond）
├── KrondInjector.apk              # 管理 App（由 service.sh 开机后 pm install）
├── module.prop                    # 模块元信息
├── customize.sh                   # 安装时：设权限、建目录、复制默认配置
├── service.sh                     # 启动时：等待 boot → pm install → logwrapper krond run
├── uninstall.sh                   # 卸载时：停止 krond、清理 /data/krond、延迟卸载 App
├── sepolicy.rule                  # SELinux 规则（允许 App 连接 @krond）
├── scripts/
│   └── databackup_cron.sh         # Swift Backup 封装脚本（示例 job）
└── META-INF/com/google/android/
    └── updater-script
```

### 关键文件说明

#### `krond` — Go 守护进程

子命令：

| 命令 | 说明 |
|------|------|
| `krond run` | 前台运行（供 service.sh 使用） |
| `krond start` | 后台启动（Setsid 脱离终端 + 写 pidfile） |
| `krond stop` | 停止（SIGTERM → 3s 超时 SIGKILL） |
| `krond restart` | restart |
| `krond version` | 版本号 |

#### `krond.yaml` — 默认配置

```yaml
socket: "@krond"
log_file: "/data/krond/krond.log"
pid_file: "/data/krond/krond.pid"
log_target: "both"      # file | logcat | both
jobs:
  - id: 1
    name: "Swift Backup"
    schedule: "0 3 * * *"
    command: "/data/krond/scripts/databackup_cron.sh"
    enabled: true
```

通过 App 日志页的设置下拉可运行时切换 `log_target`，无需重启 krond。

#### `service.sh` — 开机自启流程

1. 等待 `sys.boot_completed=1`
2. 确保 `/data/krond` 目录存在，复制默认配置/脚本
3. `pm install` 管理 App（若 APK 存在）
4. 停止旧 krond
5. 优先 `logwrapper krond run`（stdout → logcat）；若无 logwrapper 则降级为 `nohup`

#### `sepolicy.rule` — SELinux 规则

```
allow untrusted_app su:unix_stream_socket connectto;
```

若 App 无法连接 `@krond`，用 `ps -Z | grep krond` 确认 krond 的 domain，替换规则中的 `su`。

---

## 编译指南

### 前置条件

- Go 1.26+（交叉编译 krond）
- Android SDK + JDK 21（编译 App）

### 一键编译（模块 zip）

```bash
bash build-module.sh
```

生成 `krond_injector.zip`，包含 krond 二进制 + 默认配置 + App APK + 模块脚本。

### 分步编译

#### 编译 krond

```bash
cd krond
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -o ../krond_injector/krond .
```

#### 编译 App

```bash
cd krond_app
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ../krond_injector/KrondInjector.apk
```

---

## 安装方法

### 刷入 zip

```bash
adb push krond_injector.zip /sdcard/
# 在 KernelSU Manager 中刷入
```

刷入后 krond 在开机时自动启动，管理 App 由 `service.sh` 在开机完成后自动 `pm install`（首次需在 KernelSU Manager 中授予 App root 权限）。

### 手动部署（调试用）

```bash
# 1. 推送 krond 二进制与模块文件
adb push krond_injector/krond    /data/adb/modules/krond_injector/krond
adb push krond_injector/service.sh /data/adb/modules/krond_injector/
adb push krond_injector/customize.sh /data/adb/modules/krond_injector/
adb push krond_injector/module.prop /data/adb/modules/krond_injector/
adb push krond_injector/krond.yaml /data/adb/modules/krond_injector/
adb push krond_injector/scripts/databackup_cron.sh /data/adb/modules/krond_injector/scripts/

# 2. 设权限
adb shell chmod 755 /data/adb/modules/krond_injector/krond
adb shell chmod 755 /data/adb/modules/krond_injector/service.sh

# 3. 创建数据目录
adb shell mkdir -p /data/krond

# 4. 启动
adb shell /data/adb/modules/krond_injector/krond start

# 5. 验证
adb shell pidof krond
adb shell ss -x | grep krond
```

---

## 用法

### 管理定时任务

通过管理 App（Krond Injector）完成所有操作：

- **列表**：App 主页显示所有任务
- **新增/编辑**：表单输入调度表达式 + 命令
- **启停**：Switch 开关；长按卡片弹出编辑/删除
- **导入/导出**：JSON 文件

### Swift Backup 自动备份

模块内置示例 job（`krond.yaml` 中已配置），每天凌晨 3 点调用 `databackup_cron.sh` 触发 Swift Backup 的全部已启用计划。App 内即可编辑/停用此 job。

脚本单独调用：

```bash
# 触发所有计划
/data/krond/scripts/databackup_cron.sh

# 触发指定计划（schedule_id 在 Swift Backup 中长按复制）
/data/krond/scripts/databackup_cron.sh schedule_id1 schedule_id2
```

---

## 日志管理

krond 支持三种日志目标，通过 App 日志页的齿轮菜单或直接调 API 运行时切换：

| 模式 | 说明 |
|------|------|
| 仅文件 | 写入 `/data/krond/krond.log`，App 经 socket 读取 |
| 仅 Logcat | 写入 `adb logcat -s krond`（`logwrapper` 转发 stdout） |
| 双写 | 同时写入文件和 logcat（默认） |

App 日志页仅显示文件日志（krond.log），不受 `log_target` 设置影响。

### 查看日志

```bash
# 文件日志
adb shell tail -f /data/krond/krond.log

# logcat（需 log_target=logcat 或 both）
adb logcat -s krond
```

---

## 开发调试

### 常用调试命令

```bash
# 检查 krond 是否运行
adb shell pidof krond

# 检查抽象 socket
adb shell cat /proc/net/unix | grep krond

# 查看 krond 日志
adb shell tail -f /data/krond/krond.log

# SELinux 调试（A/B 测试）
adb shell su -c "setenforce 0"   # 临时关闭（调试用）
adb logcat | grep avc             # 看 SELinux 拒绝

# 手动启停
adb shell su -c "/data/adb/modules/krond_injector/krond restart"
```

### 测试后清理

```bash
# 停止 krond
adb shell su -c "/data/adb/modules/krond_injector/krond stop"

# 清理数据
adb shell rm -rf /data/krond
```

---

## 常见问题

### Q: App 显示 "krond 未运行"

A: 排查顺序：
1. `adb shell pidof krond` — 进程是否存在
2. `adb shell cat /proc/net/unix | grep krond` — socket 是否存在
3. `adb logcat | grep avc` — SELinux 是否阻止了 connectto
4. 检查 `/data/krond/krond.log` 中 krond 启动日志

### Q: krond 启动失败

A: 检查：
- `logwrapper` 是否可用（`command -v logwrapper`）；若无，service.sh 会自动降级
- `/data/krond/krond.yaml` 是否存在且格式正确
- 二进制架构是否匹配：`file /data/adb/modules/krond_injector/krond` 应为 `ARM aarch64`

### Q: Swift Backup 不执行

A: 检查 `databackup_cron.sh` 日志 `/data/krond/databackup.log`。若 `am start` 退出码非零，参考 `am start` 的 stdout/stderr。Swift Backup 需正确安装且至少有一个已启用计划。

### Q: 如何卸载？

A: 在 KernelSU Manager 中移除模块。`uninstall.sh` 会停止 krond、清理 `/data/krond`，并在开机完成后自动卸载管理 App。

---

## 参考

- [junixsocket](https://kohlschutter.github.io/junixsocket/) — Unix Domain Sockets for Java（Apache 2.0）
- [OkHttp](https://square.github.io/okhttp/) — HTTP 客户端
- [robfig/cron](https://github.com/robfig/cron) — Go 定时调度库
- [KernelSU 模块开发文档](https://kernelsu.org/guide/module.html)
- [Swift Backup](https://swiftapps.org/) — Android 备份工具

---

## 图标生成

App 启动图标为 Android 自适应图标：背景层纯绿 `#2A9D5C` + 前景层绿色圆环深色时钟表盘。

```bash
cd docs/image
./generate_icons.sh
```

依赖：`librsvg`（`rsvg-convert`）、`ImageMagick 7`（`magick`）。
