package com.example.sonytvkumanda

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val tvIp = "192.168.8.102"
    private val tvMac = "e8:6f:38:a6:97:83"
    private val tvPsk = "1234"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 48, 48, 48)
        }

        val btnOn = Button(this).apply {
            text = "TV'YI AC"
            setOnClickListener {
                sendWakeOnLan(tvMac)
                Toast.makeText(this@MainActivity, "Acma sinyali gonderildi", Toast.LENGTH_SHORT).show()
            }
        }

        val btnOff = Button(this).apply {
            text = "TV'YI KAPAT"
            setOnClickListener {
                sendPowerOff(tvIp, tvPsk)
                Toast.makeText(this@MainActivity, "Kapatma komutu gonderildi", Toast.LENGTH_SHORT).show()
            }
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 32
        }

        root.addView(btnOn, params)
        root.addView(btnOff)
        setContentView(root)
    }

    private fun sendWakeOnLan(macAddress: String) {
        thread {
            try {
                val macBytes = macAddress.split(":").map { it.toInt(16).toByte() }.toByteArray()
                val magicPacket = ByteArray(6 + 16 * macBytes.size)
                for (i in 0 until 6) magicPacket[i] = 0xFF.toByte()
                for (i in 6 until magicPacket.size) {
                    magicPacket[i] = macBytes[i % macBytes.size]
                }
                val address = InetAddress.getByName("255.255.255.255")
                val socket = DatagramSocket()
                socket.broadcast = true
                val packet = DatagramPacket(magicPacket, magicPacket.size, address, 9)
                socket.send(packet)
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun sendPowerOff(ip: String, psk: String) {
        thread {
            try {
                val url = URL("http://$ip/sony/system")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Auth-PSK", psk)
                    connectTimeout = 3000
                    readTimeout = 3000
                    doOutput = true
                }
                val body = """{"method":"setPowerStatus","id":1,"params":[{"status":false}],"version":"1.0"}"""
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
