package com.cronapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CronParserTest {

    @Test
    fun `parseCommandLine 启用行`() {
        val (schedule, command, enabled) = CronParser.parseCommandLine("* * * * * echo hi")!!
        assertEquals("* * * * *", schedule)
        assertEquals("echo hi", command)
        assertTrue(enabled)
    }

    @Test
    fun `parseCommandLine 单层注释为禁用`() {
        val (schedule, command, enabled) = CronParser.parseCommandLine("# 0 0 * * * echo b")!!
        assertEquals("0 0 * * *", schedule)
        assertEquals("echo b", command)
        assertFalse(enabled)
    }

    @Test
    fun `parseCommandLine 多层注释仍为禁用`() {
        val (schedule, _, enabled) = CronParser.parseCommandLine("## 0 0 * * * echo b")!!
        assertEquals("0 0 * * *", schedule)
        assertFalse(enabled)
    }

    @Test
    fun `parseCommandLine 非 cron 行返回 null`() {
        assertNull(CronParser.parseCommandLine("just some text"))
    }

    @Test
    fun `isCronLine 各类写法`() {
        assertTrue(CronParser.isCronLine("@reboot echo hi"))
        assertTrue(CronParser.isCronLine("0 12 * * * echo a"))
        assertFalse(CronParser.isCronLine("* * * *"))
        assertFalse(CronParser.isCronLine("foo bar baz"))
    }

    @Test
    fun `parseCrontab 解析锚点任务并保留非托管行`() {
        val lines = listOf(
            "# [cronapp] id=1 enabled=true name=job1",
            "* * * * * echo a",
            "",
            "# [cronapp] id=2 enabled=false name=job2",
            "# 0 0 * * * echo b",
            "SHELL=/bin/sh",
            "@reboot echo boot"
        )
        val (jobs, preserved) = CronParser.parseCrontab(lines)

        assertEquals(2, jobs.size)

        val job1 = jobs[0]
        assertEquals(1, job1.id)
        assertEquals("job1", job1.name)
        assertEquals("* * * * *", job1.schedule)
        assertEquals("echo a", job1.command)
        assertTrue(job1.enabled)

        val job2 = jobs[1]
        assertEquals(2, job2.id)
        assertEquals("job2", job2.name)
        assertEquals("0 0 * * *", job2.schedule)
        assertEquals("echo b", job2.command)
        assertFalse(job2.enabled)

        assertTrue(preserved.contains("SHELL=/bin/sh"))
        assertTrue(preserved.contains("@reboot echo boot"))
    }

    @Test
    fun `renderCrontab 还原锚点与保留行`() {
        val jobs = listOf(
            CronJob(id = 1, name = "job1", schedule = "* * * * *", command = "echo a", enabled = true),
            CronJob(id = 2, name = "job2", schedule = "0 0 * * *", command = "echo b", enabled = false)
        )
        val preserved = listOf("SHELL=/bin/sh", "@reboot echo boot")
        val content = CronParser.renderCrontab(jobs, preserved)

        assertTrue(content.contains("# [cronapp] id=1 enabled=true name=job1"))
        assertTrue(content.contains("* * * * * echo a"))
        assertTrue(content.contains("# [cronapp] id=2 enabled=false name=job2"))
        assertTrue(content.contains("# 0 0 * * * echo b"))
        assertTrue(content.contains("SHELL=/bin/sh"))
        assertTrue(content.contains("@reboot echo boot"))
    }
}
