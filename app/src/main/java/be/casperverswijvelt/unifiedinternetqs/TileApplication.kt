package be.casperverswijvelt.unifiedinternetqs

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import be.casperverswijvelt.unifiedinternetqs.data.BITPreferences
import be.casperverswijvelt.unifiedinternetqs.data.ShellMethod
import be.casperverswijvelt.unifiedinternetqs.util.ExecutorServiceSingleton
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.hamondev.shevery.api.SheveryPrivilege

class TileApplication : Application() {

    companion object {
        const val CHANNEL_ID = "tileSyncServiceChannel"
        const val CHANNEL_NAME = "Tile Synchronization service"
        const val TAG = "TileApplication"
    }

    private val applicationScope = MainScope()

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Created Tile Application")

        try {
            SheveryPrivilege.connectFast(this)
            Log.d(TAG, "Shevery fast connect initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to Shevery: ${e.message}", e)
        }

        ExecutorServiceSingleton.getInstance()

        createNotificationChannel()

        val preferences = BITPreferences(this)
        applicationScope.launch {
            when (preferences.getShellMethod.first()) {
                ShellMethod.ROOT -> {
                    Shell.getShell {
                    }
                }
                else -> {}
            }
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(serviceChannel)
    }
}