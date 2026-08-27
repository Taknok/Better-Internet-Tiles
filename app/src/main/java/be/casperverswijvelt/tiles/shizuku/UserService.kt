package be.casperverswijvelt.tiles.shizuku

import android.content.Context
import android.util.Log
import kotlin.system.exitProcess

class UserService(context: Context?) : IUserService.Stub() {

    // Default constructor for Shizuku < 13
    constructor() : this(null)

    override fun executeCommand(cmd: String): CommandResult {
        Log.d("ShizukuUserService", "Executing command: $cmd")
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            
            val stdout = process.inputStream.bufferedReader().use { it.readLines() }
            val stderr = process.errorStream.bufferedReader().use { it.readLines() }
            
            process.waitFor()
            val exitCode = process.exitValue()
            Log.d("ShizukuUserService", "Command finished with exit code: $exitCode")
            
            CommandResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            Log.e("ShizukuUserService", "Error executing command: ${e.message}", e)
            CommandResult(-1, emptyList(), listOf(e.message ?: "Unknown error"))
        }
    }

    override fun destroy() {
        Log.d("ShizukuUserService", "Destroying UserService")
        exitProcess(0)
    }
}
