package com.example.carparkingsystem.api

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.carparkingsystem.NotificationHelper
import com.example.carparkingsystem.socket.IParkingUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.time.Duration.Companion.milliseconds

object MqttManager {

    private const val TAG = "MqttManager"
    private const val BROKER_HOST = "broker.hivemq.com"
    private const val BROKER_PORT = 1883
    const val SERVER_CONFIG_TOPIC = "2026091519991228/SERVER_CONFIG"
    const val NOTIFICATION_TOPIC = "2026091519991228/NOTIFICATION"
    const val UPDATE_PARKING_SLOT = "2026091519991228/SLOT/UPDATE_STATUS"

    private var mqttClient: MqttClient? = null
    private var appContext: Context? = null

    private var messageListener: ((topic: String, message: String) -> Unit)? = null

    private var connectionListener: ((connected: Boolean) -> Unit)? = null

    private var serverConfigDeferred = CompletableDeferred<String>()

    private var iParkingUpdate: IParkingUpdate? = null

    fun addListener(iParkingUpdate: IParkingUpdate){
        this.iParkingUpdate = iParkingUpdate
    }

    fun removeListener(){
        this.iParkingUpdate = null
    }

    suspend fun initialize(
        context: Context, onMessage: ((topic: String, message: String) -> Unit)? = null, onConnectionChanged: ((connected: Boolean) -> Unit)? = null
    ) {
        appContext = context.applicationContext

        messageListener = onMessage
        connectionListener = onConnectionChanged

        if (mqttClient != null) {
            Log.d(TAG, "MQTT client already initialized")
            return
        }

        withContext(Dispatchers.IO) {

            try {

                Log.d(TAG, "Resolving MQTT broker: $BROKER_HOST")

                val addresses = InetAddress.getAllByName(BROKER_HOST)

                val ipv4Address = addresses.firstOrNull {
                    it is Inet4Address
                }

                if (ipv4Address == null) {

                    Log.e(
                        TAG, "No IPv4 address found for $BROKER_HOST"
                    )

                    connectionListener?.invoke(false)
                    return@withContext
                }

                val ipv4 = ipv4Address.hostAddress

                val brokerUrl = "tcp://$ipv4:$BROKER_PORT"

                Log.d(TAG, "Broker hostname: $BROKER_HOST")
                Log.d(TAG, "Broker IPv4: $ipv4")
                Log.d(TAG, "Broker URL: $brokerUrl")

                val clientId = "android_${System.currentTimeMillis()}"

                mqttClient = MqttClient(
                    brokerUrl, clientId, MemoryPersistence()
                )

                mqttClient?.setCallback(object : MqttCallbackExtended {

                    override fun connectComplete(
                        reconnect: Boolean, serverURI: String?
                    ) {

                        Log.d(
                            TAG, "MQTT connected successfully"
                        )

                        Log.d(
                            TAG, "Server URI: $serverURI"
                        )

                        Log.d(
                            TAG, "Reconnect: $reconnect"
                        )

                        connectionListener?.invoke(true)

                        // Subscribe automatically after every connection/reconnection.
                        subscribe(
                            SERVER_CONFIG_TOPIC, 1
                        )

                        subscribe(
                            NOTIFICATION_TOPIC, 1
                        )
                    }


                    override fun connectionLost(
                        cause: Throwable?
                    ) {

                        Log.e(
                            TAG, "MQTT connection lost", cause
                        )

                        connectionListener?.invoke(false)
                    }


                    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
                    override fun messageArrived(
                        topic: String?, message: MqttMessage?
                    ) {

                        if (topic == null || message == null) {
                            return
                        }

                        val payload = String(message.payload)

                        Log.d(
                            TAG, "MQTT message received ${message}"
                        )

                        Log.d(
                            TAG, "Topic: $topic"
                        )

                        Log.d(
                            TAG, "Message: $payload"
                        )

                        // Complete the SplashActivity wait when server_config arrives.
                        if (topic == SERVER_CONFIG_TOPIC && !serverConfigDeferred.isCompleted) {

                            serverConfigDeferred.complete(
                                payload
                            )
                        }
                        if (topic == NOTIFICATION_TOPIC) {

                            appContext?.let { context ->

                                NotificationHelper.showNotification(
                                    context = context, message = payload
                                )
                            }
                        }

                        if (topic == UPDATE_PARKING_SLOT){
                            iParkingUpdate?.message(payload)
                        }

                        messageListener?.invoke(
                            topic, payload
                        )
                    }


                    override fun deliveryComplete(
                        token: IMqttDeliveryToken?
                    ) {

                        Log.d(
                            TAG, "MQTT message delivered"
                        )
                    }
                })

                Log.d(
                    TAG, "MQTT client initialized successfully"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG, "MQTT initialization failed", e
                )

                connectionListener?.invoke(false)
            }
        }
    }


    /**
     * Connect to HiveMQ Public Broker.
     *
     * Keeps retrying until a connection is established.
     */
    suspend fun connect() {

        val client = mqttClient

        if (client == null) {

            Log.e(
                TAG, "MQTT client is not initialized"
            )

            return
        }

        if (client.isConnected) {

            Log.d(
                TAG, "MQTT already connected"
            )

            return
        }

        withContext(Dispatchers.IO) {

            while (!client.isConnected) {

                try {

                    val options = MqttConnectOptions().apply {

                        isCleanSession = true
                        connectionTimeout = 10
                        keepAliveInterval = 30
                        isAutomaticReconnect = true
                    }

                    Log.d(
                        TAG, "Connecting to HiveMQ Public Broker..."
                    )

                    client.connect(options)

                    // connectComplete() handles the connected callback
                    // and subscribes to server_config.

                } catch (e: MqttException) {

                    Log.e(
                        TAG, "MQTT connection failed: ${e.message}"
                    )

                    connectionListener?.invoke(false)

                    // Retry after 3 seconds.
                    delay(3000.milliseconds)

                } catch (e: Exception) {

                    Log.e(
                        TAG, "MQTT connection failed", e
                    )

                    connectionListener?.invoke(false)

                    delay(3000.milliseconds)
                }
            }
        }
    }


    /**
     * Wait until the server_config MQTT message is received.
     *
     * This is intended to be called from SplashActivity.
     */
    suspend fun waitForServerConfig(): String {

        Log.d(
            TAG, "Waiting for server_config..."
        )

        return serverConfigDeferred.await()
    }


    /**
     * Subscribe to an MQTT topic.
     */
    fun subscribe(
        topic: String, qos: Int = 1
    ) {

        val client = mqttClient

        if (client == null) {

            Log.e(
                TAG, "MQTT client is not initialized"
            )

            return
        }

        if (!client.isConnected) {

            Log.e(
                TAG, "MQTT is not connected"
            )

            return
        }

        try {

            client.subscribe(
                topic, qos
            )

            Log.d(
                TAG, "Subscribed successfully: $topic"
            )

        } catch (e: Exception) {

            Log.e(
                TAG, "MQTT subscribe failed", e
            )
        }
    }


    /**
     * Publish an MQTT message.
     */
    fun publish(
        topic: String, message: String, qos: Int = 1, retained: Boolean = false
    ) {

        val client = mqttClient

        if (client == null) {

            Log.e(
                TAG, "MQTT client is not initialized"
            )

            return
        }

        if (!client.isConnected) {

            Log.e(
                TAG, "MQTT is not connected"
            )

            return
        }

        try {

            val mqttMessage = MqttMessage(
                message.toByteArray()
            ).apply {

                this.qos = qos
                isRetained = retained
            }

            client.publish(
                topic, mqttMessage
            )

            Log.d(
                TAG, "MQTT published"
            )

            Log.d(
                TAG, "Topic: $topic"
            )

            Log.d(
                TAG, "Message: $message"
            )

        } catch (e: Exception) {

            Log.e(
                TAG, "MQTT publish failed", e
            )
        }
    }


    /**
     * Unsubscribe from an MQTT topic.
     */
    fun unsubscribe(
        topic: String
    ) {

        val client = mqttClient

        if (client == null || !client.isConnected) {
            return
        }

        try {

            client.unsubscribe(topic)

            Log.d(
                TAG, "Unsubscribed successfully: $topic"
            )

        } catch (e: Exception) {

            Log.e(
                TAG, "MQTT unsubscribe failed", e
            )
        }
    }


    /**
     * Disconnect from MQTT broker.
     */
    suspend fun disconnect() {

        withContext(Dispatchers.IO) {

            try {

                val client = mqttClient

                if (client != null && client.isConnected) {

                    client.disconnect()

                    Log.d(
                        TAG, "MQTT disconnected"
                    )
                }

                connectionListener?.invoke(false)

            } catch (e: Exception) {

                Log.e(
                    TAG, "MQTT disconnect failed", e
                )
            }
        }
    }


    /**
     * Check whether MQTT is connected.
     */
    fun isConnected(): Boolean {

        return mqttClient?.isConnected == true
    }

}