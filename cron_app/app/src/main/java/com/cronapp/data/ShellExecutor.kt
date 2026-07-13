package com.cronapp.data

import android.util.Log

/**
 * 对 root shell 的抽象。便于在单元测试中替换为假实现。
 */
interface ShellExecutor {
    /** 执行命令，合并 stdout/stderr 并返回。 */
    fun exec(vararg cmd: String): String

    /** 通过 su -c 执行命令，并把 input 作为标准输入写入。 */
    fun execPipe(cmd: String, input: String): String

    /** 通过 su -c 执行命令（每个单词独立参数），并把 input 作为标准输入写入。 */
    fun execPipe(input: String, vararg cmd: String): String

    /** 最近一次命令的退出码。 */
    fun lastExitCode(): Int
}

class SuShellExecutor : ShellExecutor {
    private var lastExitCode = 0
    private val tag = "SuShell"

    override fun exec(vararg cmd: String): String {
        Log.d(tag, "exec: ${cmd.joinToString(" ")}")
        val process = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        lastExitCode = process.waitFor()
        Log.d(tag, "exit=$lastExitCode output=[${output.trim()}]")
        return output
    }

    override fun execPipe(cmd: String, input: String): String {
        Log.d(tag, "execPipe: su -c $cmd | stdin=${input.length}B")
        val process = ProcessBuilder("su", "-c", cmd)
            .redirectErrorStream(true)
            .start()
        process.outputStream.write(input.toByteArray())
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        lastExitCode = process.waitFor()
        Log.d(tag, "exit=$lastExitCode output=[${output.trim()}]")
        return output
    }

    override fun execPipe(input: String, vararg cmd: String): String {
        Log.d(tag, "execPipe-v2: ${cmd.joinToString(" ")} | stdin=${input.length}B")
        val process = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
        process.outputStream.write(input.toByteArray())
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        lastExitCode = process.waitFor()
        Log.d(tag, "exit=$lastExitCode output=[${output.trim()}]")
        return output
    }

    override fun lastExitCode(): Int = lastExitCode
}
