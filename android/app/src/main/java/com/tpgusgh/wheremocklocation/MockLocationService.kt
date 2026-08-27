package com.tpgusgh.wheremocklocation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MockLocationService : Service() {

    private lateinit var gateways: List<LocationProviderGateway>
    private var isStarted: Boolean = false

    override fun onCreate() {
        super.onCreate()
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        gateways = listOf(
            SystemLocationProviderGateway(locationManager, LocationManager.GPS_PROVIDER),
            SystemLocationProviderGateway(locationManager, LocationManager.NETWORK_PROVIDER)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isStarted) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    gateways.forEach { it.start() }
                    isStarted = true
                }
            }
            ACTION_UPDATE -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
                val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
                if (isStarted && !lat.isNaN() && !lng.isNaN()) {
                    gateways.forEach { it.update(lat, lng) }
                }
            }
            ACTION_STOP -> {
                if (isStarted) {
                    gateways.forEach { it.stop() }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                isStarted = false
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (isStarted) {
            gateways.forEach { it.stop() }
            isStarted = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Mock location", NotificationManager.IMPORTANCE_LOW)
            )
        }
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mock location active")
            .setContentText("Faking GPS location for testing")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.tpgusgh.wheremocklocation.action.START"
        const val ACTION_UPDATE = "com.tpgusgh.wheremocklocation.action.UPDATE"
        const val ACTION_STOP = "com.tpgusgh.wheremocklocation.action.STOP"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        private const val CHANNEL_ID = "mock_location_channel"
        private const val NOTIFICATION_ID = 1
    }
}
