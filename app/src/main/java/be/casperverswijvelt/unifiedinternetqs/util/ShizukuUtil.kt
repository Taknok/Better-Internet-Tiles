package be.casperverswijvelt.unifiedinternetqs.util

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.util.Log
import android.os.IBinder
import be.casperverswijvelt.unifiedinternetqs.BuildConfig
import be.casperverswijvelt.tiles.shizuku.CommandResult
import be.casperverswijvelt.tiles.shizuku.IUserService
import be.casperverswijvelt.tiles.shizuku.UserService
import rikka.shizuku.Shizuku

/**
 * Some convenience functions for handling using Shizuku.
 */
object ShizukuUtil {
    private var userService: IUserService? = null

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.isBinderAlive) {
                userService = IUserService.Stub.asInterface(binder)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
        }
    }

    fun bindUserService(context: Context) {
        if (shizukuAvailable && hasShizukuPermission()) {
            val args = Shizuku.UserServiceArgs(ComponentName(context, UserService::class.java))
                .daemon(false)
                .processNameSuffix("privileged")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE) // Use version code to force refresh on update
            Shizuku.bindUserService(args, userServiceConnection)
        }
    }

    fun unbindUserService() {
        val args = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name))
            .processNameSuffix("privileged")
        try {
            Shizuku.unbindUserService(args, userServiceConnection, true)
        } catch (e: Exception) {
            // Service might not be bound
        }
    }

    /**
     * Checks if Shizuku is available. If the Shizuku Manager app
     * is either uninstalled OR isn't running, this will return
     * false.
     */
    val shizukuAvailable: Boolean
        get() = Shizuku.pingBinder()

    /**
     * Checks if the current app has permission to use Shizuku.
     */
    fun hasShizukuPermission(): Boolean {
        if (!shizukuAvailable) {
            return false
        }

        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Request permission to use Shizuku if it's not already granted. This works
     * for all versions of the Shizuku API.
     *
     * @param callback invoked when the permission grant result is received.
     */
    fun requestShizukuPermission(callback: (granted: Boolean) -> Unit) {
        if (Shizuku.pingBinder()) {
            Shizuku.addRequestPermissionResultListener(object :
                Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(
                    requestCode: Int,
                    grantResult: Int
                ) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    callback(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            })
            Shizuku.requestPermission(69101)
        } else {
            callback(false)
        }
    }

    fun executeCommand(command: String): CommandResult {
        Log.d("ShizukuUtil", "Executing command via UserService: $command")
        return try {
            val res = userService?.executeCommand(command) 
            if (res == null) {
                Log.e("ShizukuUtil", "UserService is not connected or returned null")
                CommandResult(-1, emptyList(), listOf("UserService not connected"))
            } else {
                Log.d("ShizukuUtil", "UserService result: ${res.exitCode}")
                res
            }
        } catch (e: Exception) {
            Log.e("ShizukuUtil", "Error calling UserService: ${e.message}", e)
            CommandResult(-1, emptyList(), listOf(e.message ?: "Unknown error"))
        }
    }
}
