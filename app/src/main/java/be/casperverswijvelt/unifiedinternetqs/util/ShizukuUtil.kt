package be.casperverswijvelt.unifiedinternetqs.util

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
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
            Shizuku.bindUserService(args, userServiceConnection)
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

    fun enforceWriteSecureSettingsPermission() {
        executeCommand("pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS")
    }

    fun revokeWriteSecureSettingsPermission() {
        executeCommand("pm revoke ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS")
    }

    fun hasWriteSecureSettingsPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    fun executeCommand(command: String): CommandResult {
        return try {
            userService?.executeCommand(command) ?: CommandResult(-1, emptyList(), listOf("UserService not connected"))
        } catch (e: Exception) {
            CommandResult(-1, emptyList(), listOf(e.message ?: "Unknown error"))
        }
    }
}
