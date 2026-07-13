# Crond Injector — Magisk/KernelSU 模块

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
     LOG_IDENT="CrondInjector"
```

> **为什么不用 configure？** dcron 不依赖 autotools，路径通过 Makefile 的 `-D` 编译标志传入，编译时就决定了 crontab 目录、日志标识等。

### 编译 crontab 命令

在同一个源码目录：

```bash
# 清除之前的编译产物
rm -f crontab.o crontab

# 编译，额外指定默认编辑器路径（Android 上常用 /system/bin/vi）
make CC="$CC" \
     CFLAGS="-static -O2 -fPIE -Wall -DPATH_VI='\"/system/bin/vi\"'" \
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
| `LOG_IDENT` | `crond` | syslog 标识（本模块设为 `CrondInjector`）|
| `PATH_VI` | `/usr/bin/vi` | 默认编辑器路径（本模块改为 `/system/bin/vi`）|

---

## 模块结构

```
crond_injector/
├── module.prop                      # ❗修改 id 和 version 以适应你的版本
├── META-INF/com/google/android/     # Magisk 安装器（兼容 KernelSU）
│   ├── update-binary
│   └── updater-script
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

# 启动（-b 后台，-S syslog）
$CROND_BIN -b -S
```

> 注意：`/data/cron/*` 路径必须与编译时 `CRONTABS` 等标志一致，否则 dcron 找不到 crontab 文件。

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
# ... 其他文件

# 3. 设权限
adb shell chmod 755 /data/adb/modules/crond_injector/crond/bin/crond
adb shell chmod 755 /data/adb/modules/crond_injector/service.sh

# 4. 创建数据目录
adb shell mkdir -p /data/cron/{crontabs,system,stamps,tmp}
adb shell cp /data/adb/modules/crond_injector/crond/crontabs/root \
             /data/cron/crontabs/root

# 5. 启动
adb shell /data/adb/modules/crond_injector/crond/bin/crond -b -S

# 6. 验证
adb shell pidof crond
adb shell logcat -s CrondInjector
```

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

```bash
logcat -s CrondInjector
```

### 手动重载 crontab

修改文件后 touch 更新信号文件：

```bash
touch /data/cron/crontabs/cron.update
```

dcron 在主循环中每次唤醒都会检查此文件，最多 60 秒内生效。

---

## App 集成

Android App 通过 `su -c` 调用 `crontab` 命令：

```kotlin
// Kotlin — 通过标准输入安装 crontab
fun installCronTab(content: String) {
    val process = Runtime.getRuntime().exec(arrayOf(
        "su", "-c", "crontab -"
    ))
    process.outputStream.write(content.toByteArray())
    process.outputStream.close()
    process.waitFor()
}

// 读取 crontab
fun getCronTab(): String {
    val process = Runtime.getRuntime().exec(arrayOf(
        "su", "-c", "crontab -l"
    ))
    return process.inputStream.bufferedReader().readText()
}
```

```java
// Java 示例
public void installCronTab(String content) throws Exception {
    Process p = Runtime.getRuntime().exec(new String[]{
        "su", "-c", "crontab -"
    });
    p.getOutputStream().write(content.getBytes());
    p.getOutputStream().close();
    p.waitFor();
}
```

> 注意：需要 App 有 root 权限（KernelSU/Magisk 授权）。`crontab -` 会替换整个 crontab，如果需要追加任务，先 `crontab -l` 读取，拼接后一次性写入。

---

## 常见问题

### Q: crond 启动后立刻崩溃？

A: 确认 `/data/cron/` 目录存在且权限正确。运行 `logcat -s CrondInjector` 查看错误日志。如果是 SIGSEGV，说明编译的二进制与设备不兼容（如架构不对，或缺少某些内核特性）。

### Q: crontab -e 报错 `/usr/bin/vi: not found`

A: 编译时没有正确设置 `-DPATH_VI`。Android 上的默认编辑器路径为 `/system/bin/vi`。如果不存在，可设置 `EDITOR` 环境变量：

```bash
EDITOR=/system/bin/nano crontab -e
```

### Q: 添加任务后不执行？

A: dcron 不会立即扫描 crontab。它有 60 秒的轮询间隔。可以 touch 更新信号文件强制触发：

```bash
touch /data/cron/crontabs/cron.update
```

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
adb shell /data/adb/modules/crond_injector/crond/bin/crond -b -S -l debug

# 5. 看日志
adb shell logcat -s CrondInjector
```

> 调试时建议加 `-l debug` 参数启动，dcron 会输出每次任务检查和执行的详细信息。

---

## 参考

- [dcron GitHub](https://github.com/dubiousjim/dcron) — Dillon's lightweight cron daemon
- [Magisk 模块开发文档](https://topjohnwu.github.io/Magisk/guides.html)
- [KernelSU 模块开发文档](https://kernelsu.org/guide/module.html)
