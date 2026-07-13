# Crond Injector — Magisk/KernelSU 模块

<p align="center">
  <img src="docs/image/icon_circle.png" alt="Crond Injector 图标" width="120"/>
</p>

基于 [dcron](https://github.com/dubiousjim/dcron) (Dillon's lightweight cron daemon) 的 Android crond 服务模块。

---

## 目录

- [编译指南](#编译指南)
- [模块结构](#模块结构)
- [安装方法](#安装方法)
- [用法](#用法)
- [App 集成](#app-集成)
- [常见问题](#常见问题)

---

## 编译指南

### 前置条件

- Android NDK（本文使用 r28c）
- 目标架构：ARM64 (aarch64)

### 编译 crond

```bash
# 1. 克隆 dcron 源码
git clone https://github.com/dubiousjim/dcron.git
cd dcron

# 2. 设置 NDK 工具链
export NDK=/path/to/your/android-ndk
export CC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android35-clang

# 3. 编译（硬编码路径到二进制中）
make CC="$CC" \
     CFLAGS="-static -O2 -fPIE -Wall" \
     LDFLAGS="-static -fPIE" \
     SCRONTABS="/data/cron/system" \
     CRONTABS="/data/cron/crontabs" \
     CRONSTAMPS="/data/cron/stamps" \
     LOG_IDENT="CrondInjector" \
     TIMESTAMP_FMT="%Y-%m-%d %H:%M:%S"
```

> **为什么不用 configure？** dcron 不依赖 autotools，路径通过 Makefile 的 `-D` 编译标志传入，编译时就决定了 crontab 目录、日志标识等。
>
> **日志时间格式**：日志头的时间戳由 `TIMESTAMP_FMT` 控制（`defs.h` 中默认 `%b %e %H:%M:%S`，即 syslog 风格 `Jul 13 15:07:01`）。本模块用 `TIMESTAMP_FMT="%Y-%m-%d %H:%M:%S"` 编译为 ISO 8601 格式（`2026-07-13 15:07:01`），更易读、更适合程序解析。注意 `TIMESTAMP_FMT` 只影响**日志头**，不影响 crontab 时间戳文件（后者由 `CRONSTAMP_FMT` 固定为 `%Y-%m-%d %H:%M`）。如果只改 `defs.h` 不生效，是因为 Makefile 第 14 行有同名变量、第 39 行通过 `-D` 强制注入，必须在 `make` 命令行覆盖。

### 编译 crontab 命令

在同一个源码目录：

```bash
# 清除之前的编译产物
rm -f crontab.o crontab

# 编译，crontab 已 patch 为自动搜索 $PATH（见下文「源码修改」）
make CC="$CC" \
     CFLAGS="-static -O2 -fPIE -Wall" \
     LDFLAGS="-static -fPIE" \
     SCRONTABS="/data/cron/system" \
     CRONTABS="/data/cron/crontabs" \
     CRONSTAMPS="/data/cron/stamps" \
     LOG_IDENT="CrondInjector" \
     crontab
```

### 裁剪体积

```bash
$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip crond
$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip crontab
```

strip 后体积约 500KB + 440KB。

### 环境变量说明

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SCRONTABS` | `/etc/cron.d` | 系统 crontab 目录（本模块设为 `/data/cron/system`）|
| `CRONTABS` | `/var/spool/cron/crontabs` | 用户 crontab 目录（本模块设为 `/data/cron/crontabs`）|
| `CRONSTAMPS` | `/var/spool/cron/cronstamps` | 时间戳目录（本模块设为 `/data/cron/stamps`）|
| `LOG_IDENT` | `crond` | 日志标识（本模块设为 `CrondInjector`，同时用于日志头）|
| `TIMESTAMP_FMT` | `%b %e %H:%M:%S` | 日志时间戳格式（本模块改为 `%Y-%m-%d %H:%M:%S`）|
| `PATH_VI` | `/usr/bin/vi` | 默认编辑器路径（crontab.c 已 patch 为搜索 `$PATH`，见「源码修改」）|

---

## 模块结构

```
crond_injector/
├── module.prop                      # ❗修改 id 和 version 以适应你的版本
├── META-INF/com/google/android/     # Magisk 安装器（兼容 KernelSU）
│   ├── update-binary
│   └── updater-script
├── CrondInjector.apk                # 管理 App，刷入后由 service.sh 在开机后通过 pm install 安装为普通应用
├── system/
│   └── bin/
│       └── crontab                  # ❗用编译产物替换
├── customize.sh                     # 安装时执行：创建目录、设权限、复制文件
├── service.sh                       # 启动时执行：等待 boot_completed → 启动 dcron
├── uninstall.sh                     # 卸载时停止服务 + 清理 /data/cron
└── crond/
    ├── bin/
    │   ├── crond                    # ❗用编译产物替换
    │   └── crontab                  # ❗用编译产物替换（也可删除）
    └── crontabs/
        └── root                     # 默认 crontab（首行必须是 # 注释或空行）
```

### 关键文件说明

#### `service.sh` — dcron 自启动

```bash
#!/system/bin/sh
MODDIR=${0%/*}
CROND_BIN=$MODDIR/crond/bin/crond

# 等待系统就绪
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

# 创建运行时目录（必须与编译时路径一致）
for dir in /data/cron/crontabs /data/cron/system /data/cron/stamps; do
    [ -d "$dir" ] || mkdir -p "$dir" 2>/dev/null
done

# 复制默认 crontab
[ -f "$MODDIR/crond/crontabs/root" ] && [ ! -f /data/cron/crontabs/root ] && \
    cp "$MODDIR/crond/crontabs/root" /data/cron/crontabs/root

# 启动（-b 后台；日志写入文件而非 syslog，App 直接读取）
$CROND_BIN -b -L /data/cron/crond.log -l info
```

> 注意：`/data/cron/*` 路径必须与编译时 `CRONTABS` 等标志一致，否则 dcron 找不到 crontab 文件。dcron 的 `-S` 选项走 syslog，而 Android 没有 syslog 守护进程，日志会丢失；因此改用 `-L file` 把日志写到 `/data/cron/crond.log`，App 的"执行日志"功能读取该文件。`-l info` 让 dcron 记录每条任务的执行情况。

#### `customize.sh` — 安装时初始化

安装时创建目录、设置权限。`system/bin/` 下的文件会由 Magisk/KernelSU 自动挂载到 `/system/bin/`。

---

## 安装方法

### 方法一：直接刷入 zip

```bash
cd crond_injector
zip -r ../crond_injector.zip .
# 推送并刷入
adb push ../crond_injector.zip /sdcard/
# 在 Magisk Manager / KernelSU Manager 中刷入
```

> 刷入后，`CrondInjector.apk` 会由 `service.sh` 在开机完成（`sys.boot_completed=1`）后通过 `pm install -r` **自动安装**为普通应用，无需手动 `adb install`（首次需在该 Manager 中授予 App root 权限）。这样可避开系统应用 overlay 在本 ROM 上资源加载崩溃的问题。**注意**：`uninstall.sh` 在启动早期执行时 PackageManager 尚未就绪，无法当场 `pm uninstall`；因此卸载模块时会派发一个延迟脚本，待开机完成后再自动卸载 App。

### 方法二：手动部署（开发调试用）

```bash
adb root  # 或 adb shell && su

# 1. 停止旧服务
adb shell killall crond 2>/dev/null

# 2. 推送模块文件
adb push crond/bin/crond    /data/adb/modules/crond_injector/crond/bin/crond
adb push system/bin/crontab /data/adb/modules/crond_injector/system/bin/crontab
adb push service.sh         /data/adb/modules/crond_injector/
adb push module.prop        /data/adb/modules/crond_injector/
adb push CrondInjector.apk  /data/adb/modules/crond_injector/CrondInjector.apk
# ... 其他文件

# 3. 设权限
adb shell chmod 755 /data/adb/modules/crond_injector/crond/bin/crond
adb shell chmod 755 /data/adb/modules/crond_injector/service.sh

# 4. 创建数据目录
adb shell mkdir -p /data/cron/{crontabs,system,stamps,tmp}
adb shell cp /data/adb/modules/crond_injector/crond/crontabs/root \
             /data/cron/crontabs/root

# 5. 启动
adb shell /data/adb/modules/crond_injector/crond/bin/crond -b -L /data/cron/crond.log -l info

# 6. 验证
adb shell pidof crond
adb shell logcat -s CrondInjector
```

> 手动部署时 App 会在下次开机由 `service.sh` 自动 `pm install`；也可直接 `adb install CrondInjector.apk` 立即安装。

---

## 用法

### 管理定时任务

```bash
# 列出任务
crontab -l

# 编辑任务（会调用 /system/bin/vi）
crontab -e

# 从标准输入安装
echo "0 6 * * * /system/bin/log -t mytag hello" | crontab -

# 删除 crontab
crontab -r
```

### 查看 dcron 日志

dcron 的日志写入 `/data/cron/crond.log`（由 `service.sh` 中的 `-L` 指定），每条任务执行都会记录一行：

```bash
cat /data/cron/crond.log
# 示例输出：
# 2026-07-13 15:07:01 localhost CrondInjector: FILE /data/cron/crontabs/root USER root PID 17360 echo ok >> /data/cron/test.log
```

App 内的"执行日志"（工具栏终端图标）读取的也是这个文件。

### 手动重载 crontab

dcron 在启动时一次性缓存所有 crontab，之后不监测文件变化。修改 crontab 后需要通过 `cron.update` 信号文件通知 crond 重新读取：

```bash
# 方式一：使用 crontab 命令（会自动创建 cron.update）
crontab /data/cron/crontabs/root

# 方式二：直接 touch 信号文件
touch /data/cron/crontabs/cron.update
```

dcron 每 60 秒检查一次 `cron.update`，检测到后立即删除该文件并重新加载对应用户的 crontab。

---

## App 集成

Android App 通过 KernelSU 的 `su -c` 调用 crontab 命令。**KernelSU 的 `su` 不会通过 shell 解析参数** — 它把 `argv[2]` 当作可执行文件、`argv[3:]` 当作其参数。因此不能传 `"su -c 'crontab -l'"` 这种 shell 风格，需拆成独立参数：

```kotlin
// Kotlin — 写入 crontab，直接使用模块内的 crontab binary
fun installCronTab(content: String) {
    val crontabBin = "/data/adb/modules/crond_injector/crond/bin/crontab"
    val process = Runtime.getRuntime().exec(arrayOf(
        "su", "-c", crontabBin, "-"
    ))
    process.outputStream.write(content.toByteArray())
    process.outputStream.close()
    process.waitFor()
}

// 读取 crontab
fun getCronTab(): String {
    val crontabBin = "/data/adb/modules/crond_injector/crond/bin/crontab"
    val process = Runtime.getRuntime().exec(arrayOf(
        "su", "-c", crontabBin, "-l"
    ))
    return process.inputStream.bufferedReader().readText()
}
```

> ⚠️ **KernelSU `su` 参数规则**：`Runtime.getRuntime().exec(arrayOf("su", "-c", "cat", "/path/file"))` 会执行 `cat /path/file`；而 `exec(arrayOf("su", "-c", "cat /path/file"))` 会尝试把 `"cat /path/file"` 整体当作可执行文件，导致失败。同理 `dd` 的 `of=` 参数也必须独立：`exec(arrayOf("su", "-c", "dd", "of=/data/cron/crontabs/root"))`。
>
> ⚠️ **使用模块内部路径而非系统 overlay**：KernelSU 的 hybrid mount 在模块激活时拍下 `system/` 的快照，刷入后推送新文件不会更新 overlay。因此始终使用模块内的 `crond/bin/crontab`，避免 `/system/bin/crontab` 还是旧版。
>
> `crontab -` 会替换整个 crontab。如需追加任务，先 `crontab -l` 读取，拼接后一次性写入。

---

## 常见问题

### Q: crond 启动后立刻崩溃？

A: 确认 `/data/cron/` 目录存在且权限正确。运行 `logcat -s CrondInjector` 查看错误日志。如果是 SIGSEGV，说明编译的二进制与设备不兼容（如架构不对，或缺少某些内核特性）。

### Q: crontab -e 报错 `/usr/bin/vi: not found`

A: 本模块的 `crontab` 已 patch 为按 `$PATH` 顺序搜索 `vi` → `vim` → `nano` → `editor`，都不存在才 fallback 到 `/usr/bin/vi`。如仍报错，说明这些编辑器均不在 PATH 中。也可通过 `EDITOR` 或 `VISUAL` 环境变量指定：

```bash
EDITOR=/system/bin/nano crontab -e
```

### Q: 添加任务后不执行？

A: dcron 不会立即扫描 crontab。它有 60 秒的轮询间隔。可以 touch 更新信号文件强制触发：

```bash
touch /data/cron/crontabs/cron.update
```

### Q: 日志里出现 `unable to exec /usr/sbin/sendmail`？

A: dcron 会在 cron job 产生 stdout/stderr 输出时尝试通过 sendmail 邮寄输出。Android 没有 sendmail，本模块已 patch `job.c` 删除了邮件派送子进程，输出直接丢弃，不会再有此日志。

### Q: 如何卸载模块？

A: 直接刷入 zip 卸载，或在 Manager 中移除模块。`uninstall.sh` 会自动停止 crond 进程并清理 `/data/cron`。

---

## 编译&调试工作流

```bash
# 1. 修改源码/配置
# 2. 重新编译
make CC="..." CFLAGS="..." LDFLAGS="..." crond

# 3. strip + 推送
llvm-strip crond
adb push crond /data/adb/modules/crond_injector/crond/bin/crond

# 4. 重启服务
adb shell killall crond
adb shell /data/adb/modules/crond_injector/crond/bin/crond -b -L /data/cron/crond.log -l debug

# 5. 看日志
adb shell logcat -s CrondInjector
```

> 调试时建议加 `-l debug` 参数启动，dcron 会输出每次任务检查和执行的详细信息。

---

## dcron 源码修改

本模块对 dcron 4.5 的源码做了以下修改，适配 Android 环境：

### `crontab.c` — `EditFile()` 编辑器搜索支持 `$PATH`

原代码在 `$EDITOR` / `$VISUAL` 均未设置时直接 fallback 到硬编码的 `PATH_VI`（默认 `/usr/bin/vi`），Android 上不存在。修改为：

1. 取 `$PATH` 环境变量，按 `:` 分割遍历每个目录
2. 依次检查 `vi` → `vim` → `nano` → `editor` 是否有可执行文件
3. 找到第一个即用作编辑器路径
4. 全部不存在则 fallback 到 `PATH_VI`

相关代码：`crontab.c:349-373`

### `job.c` — 移除 sendmail 邮件派送

dcron 在每个 cron job 产生输出时会 fork 子进程调用 `/usr/sbin/sendmail` 邮寄输出。Android 不包含 sendmail，导致每次执行都有一条 `unable to exec /usr/sbin/sendmail` 日志。修改为直接关闭 mail 文件描述符返回，不 fork、不 exec、无日志。

相关代码：`job.c:290-378` 整段删除

---

## 参考

- [dcron GitHub](https://github.com/dubiousjim/dcron) — Dillon's lightweight cron daemon
- [Magisk 模块开发文档](https://topjohnwu.github.io/Magisk/guides.html)
- [KernelSU 模块开发文档](https://kernelsu.org/guide/module.html)

---

## 图标生成

App 启动图标为 Android 自适应图标（Adaptive Icon）：背景层 `ic_launcher_background.xml`（纯绿 `#2A9D5C`） + 前景层 `ic_launcher_foreground.png`（绿色圆环 + 深色时钟表盘）。适配 Android 15 / 16（API 35+）。

> 关键点：自适应图标会把超出「安全区(72dp)」的内容裁掉。因此前景层的绿环必须缩进到安全区内，否则桌面看不到绿环。

源文件在 `docs/image/icon_circle.svg`。重新生成前景层：

```bash
cd docs/image
./generate_icons.sh
```

脚本会：① 用 `rsvg-convert` 渲染 SVG 为 432×432 PNG；② 用 ImageMagick 把图标整体缩进到安全区（绿圆半径 ≈35.5dp < 36dp），背景绿由 `ic_launcher_background.xml` 兜底。各密度 `mipmap-*` 仍保留作为低版本兜底。

依赖：`librsvg`（`rsvg-convert`）、`ImageMagick 7`（`magick`）。
