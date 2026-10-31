package com.jarvis.ai

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider

class JarvisApplication : Application() {
    private var started = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
                WakeService.appVisible(true)
                // After an update or restart, bring the wake word back if it was ON.
                try {
                    if (SecureSettings(this@JarvisApplication).wakeEnabled) {
                        WakeService.setEnabled(this@JarvisApplication, true)
                    }
                } catch (e: Exception) {
                }
                if (activity is ComponentActivity) {
                    try {
                        ViewModelProvider(activity)[JarvisViewModel::class.java].runPendingCommand()
                    } catch (e: Exception) {
                    }
                }
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) WakeService.appVisible(false)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
