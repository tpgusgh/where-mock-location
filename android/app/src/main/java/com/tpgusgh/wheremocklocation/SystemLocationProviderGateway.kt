package com.tpgusgh.wheremocklocation

import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock

class SystemLocationProviderGateway(
    private val locationManager: LocationManager,
    private val providerName: String = LocationManager.GPS_PROVIDER
) : LocationProviderGateway {

    override fun start() {
        locationManager.addTestProvider(
            providerName,
            false, false, false, false, true, true, true,
            Criteria.POWER_LOW,
            Criteria.ACCURACY_FINE
        )
        locationManager.setTestProviderEnabled(providerName, true)
    }

    override fun update(lat: Double, lng: Double) {
        val location = Location(providerName).apply {
            latitude = lat
            longitude = lng
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        locationManager.setTestProviderLocation(providerName, location)
    }

    override fun stop() {
        locationManager.removeTestProvider(providerName)
    }
}
