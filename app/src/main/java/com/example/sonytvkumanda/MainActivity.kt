package com.example.sonytvkumanda

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val tvIp = "192.168.8.102"
    private val tvMac = "e8:6f:38:a6:97:83"
    private val pskKey = "1234"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnTvAc = findViewById<Button>(R.id.btnTvAc)
        val btnTvKapat = findViewById<Button>(R.id.btnTvKapat)
        val tvDurum = findViewById<TextView>(R.id.tvDurum)

        btnTvAc.setOnClickListener {
            tvDurum.text = "TV açılıyor..."
            thread {
                sendWakeOnLan(tvMac)
                val ok = setPowerStatus(true)
                runOnUiThread {
                    tvDurum.text = if (ok) "Açma komutu gönderildi" else "WoL paketi gönderildi (TV uyanıyor)"
                }
            }
        }

        btnTvKapat.setOnClickListener {
            tvDurum.text = "TV kapatılıyor..."
            thread {
                val ok = setPowerStatus(false)
                runOnUiThread {
                    tvDurum.text = if (ok) "TV kapatıldı" else "Bağlantı hatası (TV kapalı veya aynı Wi-Fi'de değil)"
                }
            }
        }
    }

    private fun setPowerStatus(turnOn: Boolean): Boolean {
        return try {
            val url = URL("http://$tvIp/sony/system")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Auth-PSK", pskKey)
            }

            val body = """{"method":"setPowerStatus","id":55,"params":[{"status":$turnOn}],"version":"1.0"}"""
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun sendWakeOnLan(macStr: String) {
        try {
            val macBytes = macStr.split(":", "-").map { it.toInt(16).toByte() }.toByteArray()
            if (macBytes.size != 6) return

            val magicPacket = ByteArray(6 + 16 * 6)
            for (i in 0 until 6) magicPacket[i] = 0xFF.toByte()
            for (i in 6 until magicPacket.size step 6) {
                System.arraycopy(macBytes, 0, magicPacket, i, 6)
            }

            val broadcast = InetAddress.getByName("255.255.255.255")
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val packet = DatagramPacket(magicPacket, magicPacket.size, broadcast, 9)
                socket.send(packet)
            }
        } catch (_: Exception) {
        }
    }
}
