package com.tpgusgh.wheremocklocation

interface LocationProviderGateway {
    fun start()
    fun update(lat: Double, lng: Double)
    fun stop()
}
