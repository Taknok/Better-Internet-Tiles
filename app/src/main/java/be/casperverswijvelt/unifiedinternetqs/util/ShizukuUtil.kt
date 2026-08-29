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

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Some convenience functions for handling using Shizuku.
 */
object ShizukuUtil {
    private var userService: IUserService? = null
    private val isBinding = AtomicBoolean(false)

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d("ShizukuUtil", "UserService onServiceConnected")
            isBinding.set(false)
            if (binder != null && binder.isBinderAlive) {
                userService = IUserService.Stub.asInterface(binder)
                Log.d("ShizukuUtil", "UserService connected and interface retrieved")
            } else {
                Log.e("ShizukuUtil", "UserService binder is null or dead in onServiceConnected")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w("ShizukuUtil", "UserService onServiceDisconnected")
            isBinding.set(false)
            userService = null
        }
    }

    fun bindUserService(context: Context) {
        Log.d("ShizukuUtil", "bindUserService called, isBinding=${isBinding.get()}")
        if (isBinding.get()) return
        
        if (shizukuAvailable && hasShizukuPermission()) {
            isBinding.set(true)
            Log.d("ShizukuUtil", "Attempting to bind Shizuku UserService...")
            val args = Shizuku.UserServiceArgs(ComponentName(context, UserService::class.java))
                .daemon(true)
                .processNameSuffix("privileged")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)
            try {
                Shizuku.bindUserService(args, userServiceConnection)
            } catch (e: Exception) {
                isBinding.set(false)
                Log.e("ShizukuUtil", "Exception while binding Shizuku UserService: ${e.message}", e)
            }
        } else {
            Log.w("ShizukuUtil", "Cannot bind UserService: available=$shizukuAvailable, permission=${hasShizukuPermission()}")
        }
    }

    fun unbindUserService() {
        Log.d("ShizukuUtil", "unbindUserService called")
        val args = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name))
            .processNameSuffix("privileged")
        try {
            Shizuku.unbindUserService(args, userServiceConnection, true)
            userService = null
            isBinding.set(false)
        } catch (e: Exception) {
            Log.e("ShizukuUtil", "Exception while unbinding Shizuku UserService: ${e.message}")
        }
    }

    /**
     * Checks if Shizuku is available. If the Shizuku Manager app
     * is either uninstalled OR isn't running, this will return
     * false.
     */
    val shizukuAvailable: Boolean
        get() {
            val available = Shizuku.pingBinder()
            if (!available) Log.w("ShizukuUtil", "Shizuku binder ping failed")
            return available
        }

    /**
     * Checks if the current app has permission to use Shizuku.
     */
    fun hasShizukuPermission(): Boolean {
        if (!shizukuAvailable) {
            return false
        }

        val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        if (!granted) Log.w("ShizukuUtil", "Shizuku permission not granted")
        return granted
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
        Log.d("ShizukuUtil", "executeCommand: $command")
        
        var currentService = userService
        if (currentService == null || !currentService.asBinder().isBinderAlive) {
            Log.w("ShizukuUtil", "UserService is null or dead, binding...")
            bindUserService(context)
            
            // Wait up to 5 seconds for binding
            val startTime = System.currentTimeMillis()
            while (userService == null && (System.currentTimeMillis() - startTime) < 5000) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            currentService = userService
        }

        if (currentService == null) {
            val reason = if (isBinding.get()) "Binding in progress timeout" else "Binding failed or not possible"
            Log.e("ShizukuUtil", "UserService connection failed: $reason")
            return CommandResult(-1, emptyList(), listOf("UserService connection failed: $reason"))
        }

        return try {
            Log.d("ShizukuUtil", "Calling UserService.executeCommand...")
            val res = currentService.executeCommand(command) 
            if (res == null) {
                Log.e("ShizukuUtil", "UserService.executeCommand returned null")
                CommandResult(-1, emptyList(), listOf("UserService returned null result"))
            } else {
                Log.d("ShizukuUtil", "UserService.executeCommand success, code=${res.exitCode}")
                res
            }
        } catch (e: Exception) {
            Log.e("ShizukuUtil", "Error during remote executeCommand call: ${e.message}", e)
            CommandResult(-1, emptyList(), listOf("Remote error: ${e.message}"))
        }
    }
}
