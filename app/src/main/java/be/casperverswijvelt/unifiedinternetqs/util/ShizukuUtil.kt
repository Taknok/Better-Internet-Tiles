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
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Some convenience functions for handling using Shizuku.
 */
object ShizukuUtil {
    private var userService: IUserService? = null
    private val isBinding = AtomicBoolean(false)
    private var deferredService = CompletableDeferred<IUserService>()
    private val mutex = Mutex()
    private var unbindJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d("ShizukuUtil", "UserService onServiceConnected")
            isBinding.set(false)
            if (binder != null && binder.isBinderAlive) {
                val service = IUserService.Stub.asInterface(binder)
                userService = service
                deferredService.complete(service)
                Log.d("ShizukuUtil", "UserService connected and interface retrieved")
            } else {
                Log.e("ShizukuUtil", "UserService binder is null or dead in onServiceConnected")
                deferredService.completeExceptionally(IllegalStateException("Binder is null or dead"))
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w("ShizukuUtil", "UserService onServiceDisconnected")
            isBinding.set(false)
            userService = null
            // Prepare for next binding attempt
            if (!deferredService.isCompleted) {
                 deferredService.completeExceptionally(IllegalStateException("Disconnected while binding"))
            }
            deferredService = CompletableDeferred()
        }
    }

    private fun bindUserService(context: Context) {
        if (isBinding.get()) return
        
        if (shizukuAvailable && hasShizukuPermission()) {
            isBinding.set(true)
            // Ensure deferred is fresh if we were disconnected
            if (deferredService.isCompleted) {
                deferredService = CompletableDeferred()
            }
            
            Log.d("ShizukuUtil", "Attempting to bind Shizuku UserService...")
            val serviceComponent = ComponentName(context.packageName, UserService::class.java.name)
            val args = Shizuku.UserServiceArgs(serviceComponent)
                .daemon(false) // Transient service
                .processNameSuffix("privileged")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)
            try {
                Shizuku.bindUserService(args, userServiceConnection)
            } catch (e: Exception) {
                isBinding.set(false)
                Log.e("ShizukuUtil", "Exception while binding Shizuku UserService: ${e.message}", e)
                deferredService.completeExceptionally(e)
            }
        } else {
            Log.w("ShizukuUtil", "Cannot bind: available=$shizukuAvailable, permission=${hasShizukuPermission()}")
        }
    }

    private fun unbindUserService() {
        Log.d("ShizukuUtil", "Auto-unbinding UserService due to inactivity")
        val args = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name))
            .processNameSuffix("privileged")
        try {
            Shizuku.unbindUserService(args, userServiceConnection, true)
            userService = null
            isBinding.set(false)
            deferredService = CompletableDeferred()
        } catch (e: Exception) {
            Log.e("ShizukuUtil", "Exception while unbinding Shizuku UserService: ${e.message}")
        }
    }

    private suspend fun getService(context: Context): IUserService? = mutex.withLock {
        // Reset unbind job as we are about to use the service
        unbindJob?.cancel()
        
        var current = userService
        if (current == null || !current.asBinder().isBinderAlive) {
            Log.d("ShizukuUtil", "Service not ready, initiating bind...")
            bindUserService(context)
            
            try {
                current = withTimeout(5000L) {
                    deferredService.await()
                }
            } catch (e: Exception) {
                Log.e("ShizukuUtil", "Failed to await service: ${e.message}")
                isBinding.set(false)
                current = null
            }
        }
        
        // Schedule auto-unbind after 30 seconds of inactivity
        scheduleUnbind()
        
        return@withLock current
    }

    private fun scheduleUnbind() {
        unbindJob?.cancel()
        unbindJob = scope.launch {
            delay(30000L) // 30 seconds
            unbindUserService()
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

    suspend fun executeCommand(command: String, context: Context): CommandResult {
        Log.d("ShizukuUtil", "executeCommand: $command")
        
        val service = getService(context)
        if (service == null) {
            Log.e("ShizukuUtil", "Failed to obtain UserService")
            return CommandResult(-1, emptyList(), listOf("Failed to connect to Shizuku UserService"))
        }

        return withContext(Dispatchers.IO) {
            try {
                service.executeCommand(command) ?: CommandResult(-1, emptyList(), listOf("Service returned null"))
            } catch (e: Exception) {
                Log.e("ShizukuUtil", "Remote call failed: ${e.message}", e)
                CommandResult(-1, emptyList(), listOf("Remote error: ${e.message}"))
            }
        }
    }
}
