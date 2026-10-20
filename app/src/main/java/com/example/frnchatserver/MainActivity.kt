package com.example.frnchatserver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.net.Inet4Address
import java.net.NetworkInterface

// Layar utama aplikasi server: tombol nyalakan/matikan, alamat IP, jumlah klien, dan log.
class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvClients: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnToggle: Button
    private lateinit var scroll: ScrollView

    // Izin notifikasi (Android 13+). Server tetap dinyalakan walaupun izin ditolak.
    private val askNotification =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startServer() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Latar gelap, jadi ikon status bar dibuat terang
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        tvStatus = findViewById(R.id.tvStatus)
        tvAddress = findViewById(R.id.tvAddress)
        tvClients = findViewById(R.id.tvClients)
        tvLog = findViewById(R.id.tvLog)
        btnToggle = findViewById(R.id.btnToggle)
        scroll = findViewById(R.id.scroll)

        btnToggle.setOnClickListener { toggle() }
    }

    override fun onStart() {
        super.onStart()
        ServerHub.listener = { render() }
        render()
    }

    override fun onStop() {
        ServerHub.listener = null
        super.onStop()
    }

    private fun toggle() {
        if (ServerHub.running) {
            startService(Intent(this, ServerService::class.java).setAction(ServerService.ACTION_STOP))
        } else if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startServer()
        }
    }

    private fun startServer() {
        ContextCompat.startForegroundService(this, Intent(this, ServerService::class.java))
    }

    private fun render() {
        val running = ServerHub.running
        tvStatus.text = if (running) "● Server aktif di port ${ServerService.PORT}" else "○ Server mati"
        tvStatus.setTextColor(if (running) 0xFF7BC47F.toInt() else 0xFF9C8675.toInt())
        btnToggle.text = if (running) "Matikan Server" else "Nyalakan Server"
        tvClients.text = "Klien terhubung: ${ServerHub.clients}"
        tvAddress.text = if (running) addressText() else "Nyalakan server untuk melihat alamat IP."
        tvLog.text = ServerHub.logText()
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addressText(): String {
        val list = localAddresses()
        if (list.isEmpty()) {
            return "IP tidak ditemukan. Pastikan Wi-Fi atau hotspot menyala."
        }
        return "IP SERVER:\n\n" + list.joinToString("\n")
    }

    // Daftar IPv4 milik HP ini. wlan0 = Wi-Fi, swlan0/ap0 = hotspot.
    private fun localAddresses(): List<String> {
        val out = mutableListOf<String>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.inetAddresses.toList()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        out.add("${addr.hostAddress}   (${ni.name})")
                    }
                }
            }
        } catch (e: Exception) {
            // abaikan
        }
        return out
    }
}