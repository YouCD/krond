# Krond 设计方案

## 1. 背景与目标

原方案基于 dcron 实现 cron 管理 KernelSU 模块 + 配套 App（`online.youcd.krond`）。实践中暴露以下问题：

- App 解析 crontab 锚点时崩溃（锚点注释与普通注释冲突导致重复 `id=1`）
- dcron 硬编码 `sendmail`/`PATH_VI`，不易定制
- 依赖 `crontab` 信号重载，间接、不可控
- Swift Backup 触发时 `am start` 受 stdout/stderr 重定向限制（`>` 导致 `Failed transaction`）

**决策**：自研 Go 守护进程 **`krond`** 完全替换 dcron，采用本地 Unix socket 通信，YAML 配置，实现干净、可控、易调试的定时任务后端。

## 2. 整体架构

```
┌─────────────────────────┐         @krond (AF_UNIX 抽象命名空间)
│  App (online.youcd.krond)      │  HTTP    ┌──────────────────────────┐
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

KernelSU 模块 `krond_injector` 负责：开机启动 krond、安装 App、提供 `sepolicy.rule`、分发默认配置。

## 3. 关键决策（已确认）

| 项 | 决策 |
|----|------|
| 模块/目录命名 | 重命名为 **`krond_injector`**（binary `krond`、配置 `krond.yaml`、日志 `krond.log`、pid `/data/krond/krond.pid` 统一） |
| 后端实现 | 自研 Go 守护进程 krond（调度库 `robfig/cron/v3`） |
| 通信方式 | **Linux 抽象命名空间 Unix socket `@krond`** |
| App 端库 | **junixsocket**（`AFUNIXSocketAddress.inAbstractNamespace` + `AFSocketFactory.fixedAddressSocketFactory` 注入 OkHttp） |
| 鉴权 | **仅靠 SELinux 隔离**（模块 `sepolicy.rule` 限制 App 域 `connectto`），无 token |
| 启停控制 | App 经 `su -c krond start\|stop\|restart`；任务读写/状态/日志走 socket |
| 配置格式 | YAML（`krond.yaml`，含 `jobs` 数组，唯一真相源，变更即落盘） |
| 编译 | `CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build`（静态二进制，免 libc 依赖） |

## 4. 通信协议

- 地址：抽象命名空间 `@krond`（Go `net.Listen("unix", "@krond")`；junixsocket `AFUNIXSocketAddress.inAbstractNamespace("krond")`）。
- 客户端（App）：
  ```kotlin
  val addr = AFUNIXSocketAddress.inAbstractNamespace("krond")
  val factory = AFSocketFactory.fixedAddressSocketFactory(addr)
  val client = OkHttpClient.Builder().socketFactory(factory).build()
  // URL 用 http://localhost/...，host 被 factory 忽略
  ```
- 抽象 socket 无文件系统权限问题；连接权限由 SELinux 控制。
- **无需 `INTERNET` 权限**（AF_UNIX 不触网络栈）；若 OkHttp 异常报错再补 `android.permission.INTERNET`（回退方案）。

## 5. API 设计（HTTP over `@krond`，无 token）

Job 字段与 App `CronJob` 对齐：`id:int`、`name:string`、`schedule:string`、`command:string`、`enabled:bool`。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/jobs` | 返回 jobs 数组 |
| POST | `/api/jobs` | 新增（body 无 id），返回带 id 的 job |
| PUT | `/api/jobs/{id}` | 更新 job |
| DELETE | `/api/jobs/{id}` | 删除 |
| POST | `/api/jobs/{id}/toggle` | 切换 enabled |
| GET | `/api/status` | `{running,version,uptime,jobsCount}` |
| GET | `/api/logs?lines=500` | 返回 `krond.log` 末尾行 |
| POST | `/api/logs/clear` | 截断 `krond.log` |
| GET | `/api/config` | 返回 `{log_target,...}` |
| PUT | `/api/config` | 接收 `{log_target}` 热更新并持久化 |

krond 内存维护 jobs + `map[id]cron.EntryID`；任何变更写回 `krond.yaml` 并热更新调度器。连接异常（App 侧）视为 krond 未运行。

## 6. krond（Go daemon）设计

**目录 `krond/`**

| 文件 | 职责 |
|------|------|
| `go.mod` | module `krond`；deps `robfig/cron/v3`、`gopkg.in/yaml.v3` |
| `main.go` | 子命令：`run`（默认前台守护，监听 `@krond`）、`start`（`Setsid` 脱离终端 + 写 pidfile 后进入 run）、`stop`（读 pidfile kill）、`restart`、`version` |
| `config.go` | `Config`/`Job` 结构体；`Load`/`Save` YAML；`log_target` 字段（file\|logcat\|both）；默认路径常量 |
| `scheduler.go` | 基于 robfig/cron 的加载/增删/热更新，按 enabled 注册 `AddFunc`，维护 `map[id]cron.EntryID` |
| `server.go` | `net.Listen("unix","@krond")` + `http.Serve`，路由表与 JSON 编解码，日志读取/截断，config 热更新 |
| `executor.go` | 触发时 `exec.Command("/system/bin/sh","-c",job.Command)`，stdout/stderr 带时间戳写入 `krond.log`（以 root 运行） |
| `krond.yaml` | 默认配置，含一个 Swift Backup 示例 job |

**默认路径**：`config=/data/krond/krond.yaml`、`log=/data/krond/krond.log`、`pid=/data/krond/krond.pid`、`socket=@krond`。

## 7. App 设计（包 `online.youcd.krond`）

**新增**
- `KrondConfig.kt`：`MODULE_ID="krond_injector"`、`KROND_BIN="/data/adb/modules/krond_injector/krond"`、`KROND_YAML/LOG/PID`、`SOCKET_NAME="krond"`。
- `KrondClient.kt`：OkHttp 客户端（junixsocket SocketFactory 固定 `@krond`，URL `http://localhost/...`），封装 10 个 API 方法；连接异常视为"krond 未运行"。

**重写**
- `CronTabRepository.kt` → 调 `KrondClient`（删 `dd`/`crontab` 文件逻辑；`hasRoot()` 仍用 `su -c id`）。
- `CronServiceRepository.kt` → `startKrond/stopKrond/restartKrond` 经 `su -c $KROND_BIN start|stop|restart`；`isKrondRunning()` 用 socket `/api/status` 探活。
- `CronRepository.kt` 门面：内部委托改完的两仓库；`exportToJson/importFromJson` 改为经 API 读写。

**保留/微调**
- `CronParser.kt`：保留作 schedule 格式校验，移除文件读写用法。
- `CronJob` 不变；`CronViewModel`/`CronScreen` UI 文案 "crond" → "krond"。
- `ShellExecutor.kt`：保留（`su` 用于启停、`hasRoot`）。

**依赖（`app/build.gradle.kts`）**
```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.kohlschutter.junixsocket:junixsocket-core:2.10.1")
implementation("com.kohlschutter.junixsocket:junixsocket-native-android:2.10.1")
```
`defaultConfig` 加 `ndk { abiFilters += listOf("arm64-v8a") }`（模拟器调试补 `"x86_64"`）。无需 `INTERNET` 权限（回退才加）。

## 8. 模块 `krond_injector` 设计

| 操作 | 内容 |
|------|------|
| 重命名 | `crond_injector/` → `krond_injector/` |
| 删除 | 旧 `crond/bin/crond`、`crond/bin/crontab`、`system/bin/crontab`、`crond/crontabs/root` |
| 新增 | 编译产物 `krond_injector/krond`；`krond_injector/krond.yaml`（默认+示例任务）；`krond_injector/sepolicy.rule` |
| 保留 | `scripts/databackup_cron.sh`（Swift Backup 封装，已含 `\| tee -a` 修复） |
| `module.prop` | `id=krond_injector`、`name=Krond Injector`、更新 description、保留 `kernelSU=true` |
| `service.sh` | 等 `boot_completed` → 建 `/data/krond` → `pm install` 新 APK → `logwrapper krond run`（替代旧 `crond -b`） |
| `customize.sh` | `set_perm .../krond 0 0 0755`；建目录；首启复制默认 `krond.yaml` 到 `/data/krond`；移除 crontab bind mount |
| `uninstall.sh` | `krond stop`（或 pidof/kill）+ 删 `/data/krond` + 延迟 `pm uninstall online.youcd.krond` |
| APK | 重编译后放入 `krond_injector/KrondInjector.apk`，同步 `service.sh`/`uninstall.sh` 引用 |

## 9. SELinux（关键风险，必须步骤）

普通 App（`untrusted_app`）默认禁止 `connectto` root 进程的 `unix_stream_socket`。**必须**在模块放 `sepolicy.rule`，由 KernelSU/Magisk 加载时自动应用。

- 占位规则：`allow untrusted_app <krond_domain>:unix_stream_socket connectto;`
- krond 运行 domain 取决于 KernelSU 上下文（多为 `u:r:su:s0` 或 `u:r:kernel:s0`），需实测：
  - `ps -Z | grep krond` 确认 domain
  - 若 App 连不上：`logcat | grep avc` 看拒绝条目，补全 `<krond_domain>`
- 联调闭环：放宽→复现→看 avc→收紧为精确 allow。

## 10. 日志设计（最终）

**krond 侧（配置驱动 + 运行时切换）**
- `krond.yaml`：`log_target: both  # file | logcat | both`
- `Logger` 按 `log_target` 构造 writer：`file`→仅 `krond.log`；`logcat`→仅 `os.Stdout`（由 `service.sh` 的 `logwrapper` 转发进 logcat）；`both`→`io.MultiWriter(file, os.Stdout)`。
- `GET /api/config` 返回当前值；`PUT /api/config` 接收 `{log_target}` 校验合法值后热更新 writer 并持久化回 `krond.yaml`（无需重启）。

**App 侧（仅 krond.log 源 + 可改 log_target）**
- `LogRepository` 仅 `fetchFileLogs()`（经 `KrondClient.getLogs()`）+ `clearLogs()`（走 `/api/logs/clear`）。
- `LogScreen` **仅展示 krond 文件日志**（沿用 socket 轮询/刷新/清空）。
- 日志页顶部 `DropdownMenu`（文件 / Logcat / 双写）经 `KrondClient.putConfig(logTarget)` 调 `PUT /api/config` 下发，krond 即时生效并落盘；进入时 `GET /api/config` 回显。
- 全程走 `@krond` socket，无 root、无新权限。

## 11. 编译与构建

- **krond**：`cd krond && CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -o ../krond_injector/krond .`（静态，推到设备 `/data/adb/modules/krond_injector/krond`）。
- **App**：Android Studio / `./gradlew assembleDebug` 产出 APK，放入模块根。
- 质量：`go vet`/`go build`（krond）；Gradle lint/typecheck（App）。

## 12. 验证（真机 Redmi K60 / Android 16 / KernelSU）

1. 安装 `krond_injector` 模块并重启。
2. `pidof krond` 确认运行；`cat /proc/net/unix | grep krond` 确认抽象 socket 存在。
3. 安装 App，打开 → 能列出示例 Swift Backup 任务（socket 通、SELinux 生效）。
4. 若列不出 → `logcat | grep avc` 补 sepolicy 规则。
5. App 内增/删/改/启停任务 → 确认 `krond.yaml` 实时更新、调度到点触发、日志可见。
6. 启停按钮（`su` 调 `krond start/stop/restart`）验证。
7. 手动触发 `databackup_cron.sh` 验证 Swift Backup 拉起无 `Failed transaction`。
8. 切换 `log_target` 验证文件/logcat 双写行为符合配置。

## 13. 数据迁移

不自动迁移旧 dcron 的 `/data/krond/crontabs/root` 任务；默认 `krond.yaml` 内置一个 Swift Backup 示例 job，旧任务由用户手动在 App 重建。

## 14. 风险与待确认

- **SELinux domain 实测**：占位规则上线后需真机确定精确 allow 语句（第 9 节）。
- **junixsocket Android native**：若 `junixsocket-core` 未自动带齐 arm64 `.so`，需显式 `junixsocket-native-android`（已列入依赖）。
- **OkHttp + 自定义 SocketFactory**：URL 用 `http://localhost`（host 被忽略）；若触网络权限则补 `INTERNET`（回退）。
- **Go 静态二进制**：`CGO_ENABLED=0` 确保不依赖系统 libc（Android 用 bionic，动态链接 glibc 会失败）。
- **`logwrapper` 可用性**：部分 ROM 可能无 `logwrapper`；缺失时回退为 krond 直接 `run`（logcat 通道降级，文件日志不受影响）。
