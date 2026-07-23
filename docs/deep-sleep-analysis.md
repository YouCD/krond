# krond 深度睡眠导致任务漏执行 — 完整分析

## 1. 背景

- **krond**：基于 Go 自研的守护进程，`robfig/cron` 做调度，跑在 KernelSU root 环境下
- **运行方式**：由 `service.sh` 在开机完成后以 `logwrapper krond run` 启动，以 root 身份常驻
- **问题现象**：凌晨 00:00/00:30/01:00 配置的备份任务（应用备份、通话记录、rsync 同步等）从未执行

---

## 2. 日志取证

krond 日志位于 `/data/krond/krond.log`，以下为 2026/07/19 20:10 ~ 07/20 09:14 的关键切片。

### 2.1 时间线

```
2026/07/19 20:10:00  ← 测试任务正常执行（每分钟/每五分钟）
2026/07/19 20:11:15  ← App 端同步任务，scheduler 在 3 秒内 reload 了 3 次
2026/07/19 20:14:44  ← 诊断 goroutine 第 1 次输出
2026/07/19 20:37:33  ← 诊断（间隔 23 分）
2026/07/19 21:07:13  ← 诊断（间隔 30 分）
2026/07/19 21:28:15  ← 诊断（间隔 21 分）
2026/07/19 22:17:05  ← 诊断（间隔 49 分）← 最后一条
                     ╔══════════════════════════════╗
                     ║   4 小时 45 分钟 零活动      ║
                     ╚══════════════════════════════╝
2026/07/20 03:02:08  ← 诊断（断层后第 1 条）
2026/07/20 05:38:51  ← 诊断恢复 10 分钟间隔
2026/07/20 07:28:37  ← 正常
2026/07/20 09:14:26  ← 正常
```

### 2.2 关键证据：cron 条目冻结

22:17 时所有启用的任务及其 `next` 时间：

```
entry=25 next=00:00:00 prev=-   # 应用备份 (每天 0:00)
entry=27 next=00:00:00 prev=-   # 通话记录备份 (每天 0:00)
entry=30 next=00:30:00 prev=-   # 同步应用数据 (每天 0:30)
entry=28 next=01:00:00 prev=-   # 拷贝普通数据 (每天 1:00)
entry=29 next=01:00:00 prev=-   # 同步照片 (每天 1:00)
entry=26 next=16:30:00 prev=-   # 联系人备份 (每天 16:30)
```

到次日 **03:02:08**，所有条目依然**一模一样**：

```
entry=25 next=00:00:00 prev=-   ← next 已是 3 小时前，prev 从未被写入
entry=27 next=00:00:00 prev=-
...
```

两个异常：
1. `next` 时间全部在过去的墙钟时间，但 `robfig/cron` 的 Go timer 从未触发
2. `prev` 始终为 `-`，证明对应条目从来没有被执行过

### 2.3 进程未挂

03:02 后 krond 正常恢复输出，无重启迹象，PID 不变。排除进程奔溃。

---

## 3. 根因 1：`AllNextInPast()` 逻辑缺陷

### 3.1 排错过程

凌晨 3 点，5 个任务的 `next` 已经是过去时，为什么诊断 goroutine 没有触发修复？

### 3.2 原代码

```go
// scheduler.go:108-116（修复前）
func (s *Scheduler) AllNextInPast() bool {
    now := time.Now()
    for _, e := range s.cron.Entries() {
        if e.Next.IsZero() || e.Next.After(now) {
            return false   // 只要有一个在将来就返回 false
        }
    }
    return len(s.cron.Entries()) > 0
}
```

凌晨 3 点时：
- 5 个任务的 `next` 已过期（00:00 ~ 01:00） ✓
- 但 `entry=26` 的 `next=16:30:00` 还在未来 ✗

→ **`AllNextInPast()` 返回 `false`** → 诊断 goroutine 不触发 `Reload()` → 5 个过期任务永不被修复。

### 3.3 结论

函数命名意为"全部在"过去，逻辑上正确。但业务场景需要的是"任意一个过期即修复"而非"全部过期才修复"。已改为 `HasStaleEntry()`（`scheduler.go:108`），检查**任意一个** next 超过 2 分钟未触发。

---

## 4. 根因 2：深度睡眠（核心问题）

### 4.1 从日志反推

诊断 goroutine 使用 `time.NewTicker(10 * time.Minute)`，每 10 分钟打印一次。

如果 CPU 一直运行，日志应该是匀速的 10 分钟间隔。实际：

- 23 分 / 30 分 / 21 分 / 49 分 → 间隔逐渐变长（短 suspend 开始出现）
- 22:17 后**消失 4 小时 45 分钟**，期间无任何输出

→ 唯一解释：**Go 进程被全面冻结，timer 全部停摆**。这就是 CPU 进入 suspend 状态。

### 4.2 内核验证

```bash
$ cat /sys/power/suspend_stats/success
8571

$ dmesg | grep "PM: suspend entry"
PM: suspend entry (s2idle)   ← 每次进入
PM: suspend exit              ← 每次退出
...循环往复...
```

设备跑 s2idle（Suspend-to-Idle），进入/退出间隔仅 100~200ms，进入后停留 100ms~数秒。8571 次进入、1544 次失败。

### 4.3 为什么 `robfig/cron` 的 timer 不触发

Go 的 `time.Timer` 在内核上使用 `CLOCK_MONOTONIC`。CPU 进入 s2idle 时，单调时钟**暂停**：

```
设备 22:00 进入 suspend，timer deadline 还剩 8 分钟（monotonic time）
设备 03:00 唤醒（墙钟过了 5 小时）
monotonic clock 认为才过了 1 秒，timer 的 deadline 还没到
→ timer 不触发 → cron job 永不执行
```

Go 官方文档确认：

> On some systems the monotonic clock will stop if the computer goes to sleep.

### 4.4 不是 krond "级别"的问题

Android 的 `system_server`、`init`（PID 1）等所有用户态进程在 s2idle 期间同样被冻结。suspend 是硬件层（CPU）的暂停，不管进程是什么权限/级别。Android 自身的 `system_server` 靠 `AlarmManager` 对接 RTC 硬件闹钟来规避。

---

## 5. 修复方案对比

### 5.1 方案 A：`cmd power wakeup`（Android AlarmManager 中转）

```bash
cmd power wakeup 60000   # 60 秒后唤醒
```

**结果：失败。** 实测报错：

```
Calling uid 0 is not an android package.
Cannot schedule a delayed wakeup on behalf of it.
```

Android AlarmManager 通过 `Binder.getCallingUid()` + `PackageManager.getNameForUid()` 校验调用者必须是合法 App 包。root（uid 0）无对应包名而被拒绝。

→ 只能走 App 中转（App 设 AlarmManager 闹钟 → 唤醒后通过 `@krond` socket 通知 krond reload），引入 App 故障点，不推荐。

### 5.2 方案 B：`/sys/class/rtc/rtc0/wakealarm`（RTC 硬件闹钟）

```bash
echo $(($(date +%s) + 300)) > /sys/class/rtc/rtc0/wakealarm
```

**结果：可写，已实测成功。**

- 该设备 RTC 闹钟路径可用
- 内容为单位为 epoch 秒的 32-bit 整数
- 问题：全球仅此一个寄存器，Android AlarmManager 和 krond 竞争写入，存在**竞态**
- 竞争方案是先读、只在 krond 时间更早时写，但读写非原子，无法完全消除
- 不同厂商 ROM 可能有不同 RTC 驱动实现，兼容性不可控

### 5.3 方案 C：`timerfd_create(CLOCK_BOOTTIME_ALARM)`（推荐）

Linux 内核 3.15+ 提供 `CLOCK_BOOTTIME_ALARM` 时钟：

```
CLOCK_BOOTTIME_ALARM: like CLOCK_BOOTTIME (includes suspend time),
but will wake the system if suspended.
Requires CAP_WAKE_ALARM (root).
```

通过 `golang.org/x/sys/unix` 的 syscall 封装直接使用：

```go
import "golang.org/x/sys/unix"

fd, _ := unix.TimerfdCreate(unix.CLOCK_BOOTTIME_ALARM, 0)
unix.TimerfdSettime(fd, unix.TFD_TIMER_ABSTIME, &newValue, nil)
```

- per-process timer，不与 AlarmManager 抢 RTC 寄存器
- 内核自动处理多源唤醒，无竞态
- Go 已有封装，无需 CGo
- 标准 Linux syscall，不受 Android 厂商定制影响

### 5.4 已实现的兜底方案

| 层次 | 机制 | 位置 |
|---|---|---|
| 墙钟跳跃检测 | 每次 tick 比较 `time.Since(lastCheck) > 30min` → 触发修复 | `main.go:120` |
| 过期任务检测 | `HasStaleEntry()` 检测任意 next 超 2 分钟未触发 | `scheduler.go:108` |
| 补执行 | `MissedJobs()` 返回超期未执行任务，`executeJobFn()` 逐条补跑 | `scheduler.go:181/main.go:124` |
| 重调度 | `Reload()` 基于当前墙钟重建 cron 实例 | `scheduler.go:118` |

---

## 6. 方案推荐

### 6.1 组合策略

```
                   ┌─────────────────┐
                   │ C: timerfd 唤醒 │  ← 主力（到点精准唤醒 CPU）
                   └────────┬────────┘
                            │ 失败回退
                            ▼
┌──────────────┐    ┌────────────────┐
│ 墙钟跳跃检测  │───►│ HasStaleEntry  │  ← 兜底（最坏情况 30 分钟内修复）
└──────────────┘    └────────┬───────┘
                             │
                             ▼
                    ┌──────────────┐
                    │  MissedJobs   │  ← 补执行错过的任务
                    │  + Reload()   │
                    └──────────────┘
```

### 6.2 优先级

1. **P0（推荐实现）**：timerfd `CLOCK_BOOTTIME_ALARM` 方案，零竞态 + 全兼容
2. **P1（已实现）**：墙钟跳跃 + HasStaleEntry + MissedJobs 兜底
3. **P2（可选）**：RTC wakealarm 作为 timerfd 的降级备选
4. **不推荐**：App 中转 AlarmManager（增加 App 故障点）、wakelock 持锁（耗电）

---

## 7. 设备信息

| 项目 | 值 |
|---|---|
| 设备 | Redmi K60 |
| OS | Android 16 (LineageOS based, API 36) |
| 内核 | GKI, KernelSU |
| suspend 模式 | s2idle (`freeze mem`) |
| suspend 成功次数 | 8,571 |
| RTC wakealarm | `/sys/class/rtc/rtc0/wakealarm` 可用 |
