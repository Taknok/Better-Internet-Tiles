package be.casperverswijvelt.tiles.shizuku

import android.content.Context
import kotlin.system.exitProcess

class UserService(context: Context?) : IUserService.Stub() {

    // Default constructor for Shizuku < 13
    constructor() : this(null)

    override fun executeCommand(cmd: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            
            val stdout = process.inputStream.bufferedReader().use { it.readLines() }
            val stderr = process.errorStream.bufferedReader().use { it.readLines() }
            
            process.waitFor()
            
            CommandResult(process.exitValue(), stdout, stderr)
        } catch (e: Exception) {
            CommandResult(-1, emptyList(), listOf(e.message ?: "Unknown error"))
        }
    }

    override fun destroy() {
        exitProcess(0)
    }
}
