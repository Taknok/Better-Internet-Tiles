package be.casperverswijvelt.unifiedinternetqs.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import be.casperverswijvelt.tiles.shizuku.CommandResult
import com.hamondev.shevery.api.SheveryApiConstants
import com.hamondev.shevery.api.SheveryPrivilege
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Convenience functions for handling Shevery API privilege and execution.
 */
object ShizukuUtil {

    private fun init(context: Context) {
        try {
            SheveryPrivilege.connectFast(context)
        } catch (e: Exception) {
            Log.e("ShizukuUtil", "Failed to connect fast to Shevery: ${e.message}", e)
        }
    }

    val shizukuAvailable: Boolean
        get() = try {
            SheveryPrivilege.isGranted() || true
        } catch (e: Exception) {
            false
        }

    fun hasShizukuPermission(): Boolean {
        return try {
            SheveryPrivilege.isGranted()
        } catch (e: Exception) {
            false
        }
    }

    fun requestShizukuPermission(callback: (granted: Boolean) -> Unit) {
        try {
            val listener = object : SheveryPrivilege.OnPermissionResultListener {
                override fun onPermissionResult(requestCode: Int, grantResult: Int) {
                    SheveryPrivilege.removePermissionResultListener(this)
                    callback(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            SheveryPrivilege.addPermissionResultListener(listener)
            SheveryPrivilege.requestPermission(100)
        } catch (e: Exception) {
            Log.e("ShizukuUtil", "Failed to request Shevery permission: ${e.message}", e)
            callback(false)
        }
    }

    suspend fun executeCommand(command: String, context: Context): CommandResult {
        Log.d("ShizukuUtil", "executeCommand via Shevery: $command")
        init(context)
        return withContext(Dispatchers.IO) {
            try {
                if (!SheveryPrivilege.hasCapability(SheveryApiConstants.CAP_SHELL_EXEC)) {
                    return@withContext CommandResult(-1, emptyList(), listOf("Shevery CAP_SHELL_EXEC capability not available"))
                }
                val res = SheveryPrivilege.execFast(arrayOf("sh", "-c", command))
                val exitCode = res.exitCode
                val outputText = res.outputText ?: ""
                val stdout = outputText.lines()
                CommandResult(exitCode, stdout, emptyList())
            } catch (e: Exception) {
                Log.e("ShizukuUtil", "Shevery remote call failed: ${e.message}", e)
                CommandResult(-1, emptyList(), listOf("Remote error: ${e.message}"))
            }
        }
    }
}
