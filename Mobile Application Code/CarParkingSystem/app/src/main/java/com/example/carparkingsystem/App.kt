package com.example.carparkingsystem

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.carparkingsystem.api.MqttManager
import kotlinx.coroutines.launch

class MyApp : Application() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        SharedPrefManager.init(this)
        NotificationHelper.createNotificationChannel(this)

        ProcessLifecycleOwner.get().lifecycleScope.launch {

            MqttManager.initialize(
                this@MyApp,

                onMessage = { topic, message ->
                    Log.d(
                        "MQTT", "$topic -> $message"
                    )
                },

                onConnectionChanged = { connected ->
                    Log.d(
                        "MQTT", "Connected: $connected"
                    )
                })

            MqttManager.connect()
            MqttManager.subscribe(MqttManager.NOTIFICATION_TOPIC,1)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            MqttManager.disconnect()
        }
    }
}