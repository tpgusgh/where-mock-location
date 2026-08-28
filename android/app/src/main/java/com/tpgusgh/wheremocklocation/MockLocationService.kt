package com.tpgusgh.wheremocklocation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
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
                    try {
                        gateways.forEach { it.start() }
                        isStarted = true
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "모의 위치 시작 실패: 개발자 옵션에서 이 앱을 '모의 위치 앱 선택'으로 지정했는지 확인해줘",
                            Toast.LENGTH_LONG
                        ).show()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            ACTION_UPDATE -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
                val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
                if (isStarted && !lat.isNaN() && !lng.isNaN()) {
                    try {
                        gateways.forEach { it.update(lat, lng) }
                    } catch (e: Exception) {
                        Toast.makeText(this, "위치 업데이트 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            ACTION_STOP -> {
                if (isStarted) {
                    try {
                        gateways.forEach { it.stop() }
                    } catch (e: Exception) {
                        // 이미 해제됐거나 등록 안 된 provider — 무시하고 정상 종료 진행
                    }
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
            try {
                gateways.forEach { it.stop() }
            } catch (e: Exception) {
                // 이미 해제됐거나 등록 안 된 provider — 무시
            }
            isStarted = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "모의 위치", NotificationManager.IMPORTANCE_LOW)
            )
        }
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("모의 위치 작동 중")
            .setContentText("테스트용으로 GPS 위치를 조작하고 있습니다")
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
