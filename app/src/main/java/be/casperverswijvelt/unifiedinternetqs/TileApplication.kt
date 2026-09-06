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
import rikka.shizuku.Shizuku

class TileApplication : Application() {

    companion object {
        const val CHANNEL_ID = "tileSyncServiceChannel"
        const val CHANNEL_NAME = "Tile Synchronization service"
        const val TAG = "TileApplication"
    }

    private val applicationScope = MainScope()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Created Tile Application")

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

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