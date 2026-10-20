package com.example.frnchatserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

// Service di latar depan supaya server tetap hidup walaupun aplikasi ditutup
// atau layar mati. Android mewajibkan notifikasi untuk service seperti ini.
class ServerService : Service() {

    private var core: ChatServerCore? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (core == null) begin()
        return START_NOT_STICKY
    }

    private fun begin() {
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        // Jaga CPU tetap aktif agar koneksi tidak terputus saat layar mati
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "frnchatserver:server").also {
            it.acquire()
        }

        ServerHub.setRunning(true)
        core = ChatServerCore(
            port = PORT,
            onLog = { ServerHub.log(it) },
            onClients = { ServerHub.setClients(it) },
            onStopped = { reason ->
                if (reason != null) ServerHub.log("Server berhenti: $reason")
                ServerHub.setRunning(false)
                stopSelf()
            }
        ).also { it.start() }
    }

    override fun onDestroy() {
        core?.stop()
        core = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServerHub.setRunning(false)
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Server chat", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Server FRN Chat aktif")
            .setContentText("Port $PORT. Ketuk untuk membuka.")
            .setContentIntent(open)
            .addAction(0, "Matikan", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val PORT = 2106
        const val ACTION_STOP = "com.example.frnchatserver.STOP"
        private const val CHANNEL_ID = "frn_server"
        private const val NOTIF_ID = 1
    }
}