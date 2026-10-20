package com.example.frnchatserver

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

// Inti server chat: menerima koneksi lalu meneruskan setiap baris pesan ke semua klien.
// Pesan sudah dienkripsi oleh klien, jadi server tidak perlu tahu isinya.
// Logikanya sama dengan ChatServer di laptop, hanya dibungkus agar bisa dipakai di Android.
class ChatServerCore(
    private val port: Int,
    private val onLog: (String) -> Unit,
    private val onClients: (Int) -> Unit,
    private val onStopped: (String?) -> Unit
) {

    private val clients = CopyOnWriteArrayList<ClientHandler>()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    fun start() {
        running = true
        thread(name = "chat-accept") {
            var reason: String? = null
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                onLog("Server berjalan di port $port")

                while (running) {
                    val socket = ss.accept()
                    onLog("Client baru terhubung dari ${socket.remoteSocketAddress}")
                    val handler = ClientHandler(socket)
                    clients.add(handler)
                    onClients(clients.size)
                    thread(name = "chat-client") { handler.run() }
                }
            } catch (e: IOException) {
                // Kalau dihentikan lewat stop(), exception ini normal
                if (running) reason = e.message ?: "Server berhenti"
            } finally {
                running = false
                closeAll()
                onStopped(reason)
            }
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            // abaikan
        }
        closeAll()
    }

    private fun closeAll() {
        for (c in clients) c.close()
        clients.clear()
        onClients(0)
    }

    // Pesan dikirim ke semua orang
    private fun broadcast(message: String) {
        for (c in clients) c.sendMessage(message)
    }

    private inner class ClientHandler(private val socket: Socket) {

        // Antrean kirim per klien: satu HP yang lambat tidak menahan klien lain
        private val outbox = LinkedBlockingQueue<String>()

        @Volatile
        private var clientName: String? = null

        fun run() {
            val sender = thread(isDaemon = true, name = "chat-send") {
                try {
                    val writer = PrintWriter(
                        OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true
                    )
                    while (true) {
                        writer.println(outbox.take())
                        if (writer.checkError()) break
                    }
                } catch (e: InterruptedException) {
                    // klien keluar
                } catch (e: IOException) {
                    // koneksi putus
                }
            }

            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val name = reader.readLine() ?: return
                clientName = name
                onLog("$name bergabung")
                broadcast("[SYSTEM]: $name bergabung ke dalam obrolan.")

                while (true) {
                    val message = reader.readLine() ?: break
                    broadcast("[$name]: $message")
                }
            } catch (e: IOException) {
                // klien terputus tiba-tiba
            } catch (e: OutOfMemoryError) {
                onLog("Pesan dari ${clientName ?: "klien"} terlalu besar, koneksi diputus")
            } finally {
                clients.remove(this)
                onClients(clients.size)
                clientName?.let {
                    onLog("$it keluar")
                    broadcast("[SYSTEM]: $it telah meninggalkan obrolan.")
                }
                sender.interrupt()
                close()
            }
        }

        fun sendMessage(message: String) {
            outbox.put(message)
        }

        fun close() {
            try {
                socket.close()
            } catch (e: IOException) {
                // abaikan
            }
        }
    }
}