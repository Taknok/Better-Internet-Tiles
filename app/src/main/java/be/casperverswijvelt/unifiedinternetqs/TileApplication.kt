package be.casperverswijvelt.unifiedinternetqs

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import be.casperverswijvelt.unifiedinternetqs.data.BITPreferences
import be.casperverswijvelt.unifiedinternetqs.data.ShellMethod
import be.casperverswijvelt.unifiedinternetqs.util.ExecutorServiceSingleton
import be.casperverswijvelt.unifiedinternetqs.util.ShizukuUtil
import be.casperverswijvelt.unifiedinternetqs.util.getInstallId
import be.casperverswijvelt.unifiedinternetqs.util.initializeFirebase
import be.casperverswijvelt.unifiedinternetqs.util.reportToAnalytics
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import rikka.shizuku.Shizuku

class TileApplication : Application() {

    companion object {
        const val CHANNEL_ID = "tileSyncServiceChannel"
        const val CHANNEL_NAME = "Tile Synchronization service"
        const val TAG = "TileApplication"
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        val preferences = BITPreferences(this)
        runBlocking {
            if (preferences.getShellMethod.first() == ShellMethod.SHIZUKU) {
                if (!ShizukuUtil.hasShizukuPermission()) {
                    ShizukuUtil.requestShizukuPermission { granted ->
                        if (granted) {
                            ShizukuUtil.bindUserService(this@TileApplication)
                        }
                    }
                } else {
                    ShizukuUtil.bindUserService(this@TileApplication)
                }
            }
        }
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

        initializeFirebase(
            this,
            getInstallId(this)
        )

        createNotificationChannel()

        val preferences = BITPreferences(this)
        runBlocking {
            when (preferences.getShellMethod.first()) {
                ShellMethod.ROOT -> {
                    Shell.getShell {
                        reportToAnalytics(this@TileApplication)
                    }
                }

                ShellMethod.SHIZUKU -> {
                    if (ShizukuUtil.shizukuAvailable && !ShizukuUtil.hasShizukuPermission()) {
                        ShizukuUtil.requestShizukuPermission { granted ->
                            if (granted) {
                                ShizukuUtil.bindUserService(this@TileApplication)
                            }
                        }
                    }
                    reportToAnalytics(this@TileApplication)
                }

                ShellMethod.AUTO -> {
                    // Mode AUTO is when user has not explicitly set a
                    Shell.getShell {

                        if (Shell.isAppGrantedRoot() == true) {
                            runBlocking {
                                preferences.setShellMethod(ShellMethod.ROOT)
                            }
                        } else if (ShizukuUtil.hasShizukuPermission()) {
                            runBlocking {
                                preferences.setShellMethod(ShellMethod.SHIZUKU)
                            }
                        }

                        reportToAnalytics(this@TileApplication)
                    }
                }
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