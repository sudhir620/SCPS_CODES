package com.example.carparkingsystem.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.carparkingsystem.R
import com.example.carparkingsystem.SharedPrefManager
import com.example.carparkingsystem.api.MqttManager
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    companion object {

        private const val TAG = "SplashActivity"
        private const val PERMISSION_REQUEST_CODE = 100
    }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_splash
        )

        requestRequiredPermissions()

        lifecycleScope.launch {
            MqttManager.initialize(
                context = this@SplashActivity, onMessage = { topic, message ->
                    Log.d(TAG, "MQTT: $topic -> $message")
                },

                onConnectionChanged = { connected ->
                    Log.d(TAG, "MQTT Connected: $connected")
                })

            MqttManager.connect()

            Log.d(
                TAG, "Waiting for server configuration..."
            )

            val serverConfig = MqttManager.waitForServerConfig()
            Log.d(
                TAG, "Server configuration received: $serverConfig"
            )

            SharedPrefManager.setBaseUrl(serverConfig)

            Log.d(TAG, "base url: ${SharedPrefManager.getBaseUrl()}")
            navigateNext()
        }
    }

    private fun requestRequiredPermissions() {

        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String?>, grantResults: IntArray, deviceId: Int) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            permissions.forEachIndexed { index, permission ->

                val granted = grantResults[index] == PackageManager.PERMISSION_GRANTED

                when (permission) {

                    Manifest.permission.CAMERA -> {
                        if (granted) {
                            Log.d("Permission", "Camera granted")
                        } else {
                            Log.d("Permission", "Camera denied")
                        }
                    }

                    Manifest.permission.ACCESS_FINE_LOCATION -> {
                        if (granted) {
                            Log.d("Permission", "Fine location granted")
                        } else {
                            Log.d("Permission", "Fine location denied")
                        }
                    }

                    Manifest.permission.ACCESS_COARSE_LOCATION -> {
                        if (granted) {
                            Log.d("Permission", "Coarse location granted")
                        } else {
                            Log.d("Permission", "Coarse location denied")
                        }
                    }

                    Manifest.permission.POST_NOTIFICATIONS -> {
                        if (granted) {
                            Log.d("Permission", "Notification granted")
                        } else {
                            Log.d("Permission", "Notification denied")
                        }
                    }
                }
            }
        }
    }


    private fun navigateNext() {
        if (SharedPrefManager.isLoggedIn()) {
            startActivity(
                Intent(
                    this@SplashActivity, DashboardActivity::class.java
                )
            )

        } else {
            startActivity(
                Intent(
                    this@SplashActivity, LoginActivity::class.java
                )
            )
        }
        finish()
    }
}