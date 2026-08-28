package com.tpgusgh.wheremocklocation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.tpgusgh.wheremocklocation.databinding.ActivityMainBinding
import org.json.JSONObject
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var presetRepository: PresetRepository
    private var pairingServer: PairingServer? = null
    private var isMocking = false
    private val pairingToken = PairingToken.generate()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestRuntimePermissionsIfNeeded()

        presetRepository = PresetRepository(this)
        refreshPresetList()
        startPairingServer()

        binding.btnToggleMock.setOnClickListener { onToggleMockClicked() }
        binding.btnSavePreset.setOnClickListener { onSavePresetClicked() }
        binding.listPresets.setOnItemClickListener { _, _, position, _ ->
            val preset = presetRepository.getAll()[position]
            binding.etLatitude.setText(preset.lat.toString())
            binding.etLongitude.setText(preset.lng.toString())
        }
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val missing = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_RUNTIME_PERMISSIONS)
        }
    }

    override fun onDestroy() {
        pairingServer?.stop()
        super.onDestroy()
    }

    private fun onToggleMockClicked() {
        val lat = binding.etLatitude.text.toString().toDoubleOrNull()
        val lng = binding.etLongitude.text.toString().toDoubleOrNull()
        if (lat == null || lng == null) return

        if (!isMocking) {
            startService(Intent(this, MockLocationService::class.java).setAction(MockLocationService.ACTION_START))
            sendLocationUpdate(lat, lng)
            binding.btnToggleMock.text = "모의 위치 중지"
        } else {
            startService(Intent(this, MockLocationService::class.java).setAction(MockLocationService.ACTION_STOP))
            binding.btnToggleMock.text = "모의 위치 시작"
        }
        isMocking = !isMocking
    }

    private fun sendLocationUpdate(lat: Double, lng: Double) {
        val intent = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_UPDATE
            putExtra(MockLocationService.EXTRA_LAT, lat)
            putExtra(MockLocationService.EXTRA_LNG, lng)
        }
        startService(intent)
    }

    private fun onSavePresetClicked() {
        val name = binding.etPresetName.text.toString()
        val lat = binding.etLatitude.text.toString().toDoubleOrNull()
        val lng = binding.etLongitude.text.toString().toDoubleOrNull()
        if (name.isBlank() || lat == null || lng == null) return
        presetRepository.add(LocationPreset(name, lat, lng))
        binding.etPresetName.text.clear()
        refreshPresetList()
    }

    private fun refreshPresetList() {
        val names = presetRepository.getAll().map { "${it.name} (${it.lat}, ${it.lng})" }
        binding.listPresets.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
    }

    private fun startPairingServer() {
        val server = PairingServer(
            port = PAIRING_PORT,
            expectedToken = pairingToken,
            onValidUpdate = { lat, lng ->
                val applied = isMocking
                runOnUiThread {
                    binding.etLatitude.setText(lat.toString())
                    binding.etLongitude.setText(lng.toString())
                    if (applied) sendLocationUpdate(lat, lng)
                }
                applied
            },
            onStarted = {
                runOnUiThread { showPairingInfo() }
            },
            onServerError = { ex ->
                runOnUiThread {
                    binding.tvPairingInfo.text = "페어링 서버 실패: ${ex.message}"
                }
            }
        )
        server.start()
        pairingServer = server
    }

    private fun showPairingInfo() {
        val ip = localIpAddress() ?: "unknown"
        binding.tvPairingInfo.text = "IP: $ip  포트: $PAIRING_PORT  토큰: $pairingToken"
        val payload = JSONObject()
            .put("ip", ip)
            .put("port", PAIRING_PORT)
            .put("token", pairingToken)
            .toString()
        binding.ivQrCode.setImageBitmap(renderQrCode(payload))
    }

    private fun localIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            val candidates = interfaces.filter { it.isUp && !it.isLoopback }
            val wifiFirst = candidates.sortedByDescending { it.name.startsWith("wlan") }
            wifiFirst.firstNotNullOfOrNull { iface ->
                iface.inetAddresses.asSequence()
                    .firstOrNull { addr -> !addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false }
                    ?.hostAddress
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun renderQrCode(content: String): Bitmap {
        val size = 512
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    companion object {
        private const val PAIRING_PORT = 8765
        private const val REQUEST_RUNTIME_PERMISSIONS = 1001
    }
}
