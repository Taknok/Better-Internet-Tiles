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
    private var isBinding = false

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            isBinding = false
            if (binder != null && binder.isBinderAlive) {
                userService = IUserService.Stub.asInterface(binder)
                Log.d("ShizukuUtil", "UserService connected")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBinding = false
            userService = null
            Log.w("ShizukuUtil", "UserService disconnected")
        }
    }

    fun bindUserService(context: Context) {
        if (isBinding) return
        if (shizukuAvailable && hasShizukuPermission()) {
            isBinding = true
            Log.d("ShizukuUtil", "Binding UserService...")
            val args = Shizuku.UserServiceArgs(ComponentName(context, UserService::class.java))
                .daemon(true)
                .processNameSuffix("privileged")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE) // Use version code to force refresh on update
            try {
                Shizuku.bindUserService(args, userServiceConnection)
            } catch (e: Exception) {
                isBinding = false
                Log.e("ShizukuUtil", "Error binding UserService: ${e.message}")
            }
        }
    }

    fun unbindUserService() {
        val args = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name))
            .processNameSuffix("privileged")
        try {
            Shizuku.unbindUserService(args, userServiceConnection, true)
            userService = null
            isBinding = false
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

    fun executeCommand(command: String, context: Context): CommandResult {
        Log.d("ShizukuUtil", "Executing command via UserService: $command")
        
        var currentService = userService
        if (currentService == null || !currentService.asBinder().isBinderAlive) {
            Log.w("ShizukuUtil", "UserService not ready, binding...")
            bindUserService(context)
            
            // Wait up to 3 seconds for binding (called on background thread)
            var attempts = 30
            while (userService == null && attempts > 0) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    break
                }
                attempts--
            }
            currentService = userService
        }

        if (currentService == null) {
            Log.e("ShizukuUtil", "UserService is still not connected or returned null")
            return CommandResult(-1, emptyList(), listOf("UserService not connected"))
        }

        return try {
            val res = currentService.executeCommand(command) 
            if (res == null) {
                Log.e("ShizukuUtil", "UserService returned null result")
                CommandResult(-1, emptyList(), listOf("UserService returned null"))
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
