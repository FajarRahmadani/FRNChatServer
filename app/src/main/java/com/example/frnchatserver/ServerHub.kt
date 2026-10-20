package com.example.frnchatserver

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Penghubung antara ServerService (yang menjalankan server) dan layar MainActivity.
// Semua perubahan data dilakukan di main thread supaya aman dibaca oleh UI.
object ServerHub {

    private val main = Handler(Looper.getMainLooper())
    private val lines = ArrayDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    var running = false
        private set
    var clients = 0
        private set

    // Dipanggil di main thread setiap ada perubahan
    var listener: (() -> Unit)? = null

    fun setRunning(value: Boolean) {
        main.post {
            running = value
            if (!value) clients = 0
            listener?.invoke()
        }
    }

    fun setClients(count: Int) {
        main.post {
            clients = count
            listener?.invoke()
        }
    }

    fun log(message: String) {
        main.post {
            lines.addLast("${clock.format(Date())}  $message")
            while (lines.size > 200) lines.removeFirst()
            listener?.invoke()
        }
    }

    fun logText(): String = lines.joinToString("\n")
}