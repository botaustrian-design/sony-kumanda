package com.example.sonytvkumanda

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val tvIp = "192.168.8.102"
    private val tvMac = "e8:6f:38:a6:97:83"
    private val tvPsk = "1234"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootView = findViewById<View>(android.R.id.content)
        val buttons = findAllButtons(rootView)

        val btnOn = buttons.getOrNull(0)
        val btnOff = buttons.getOrNull(1)

        btnOn?.setOnClickListener {
            sendWakeOnLan(tvMac)
            Toast.makeText(this, "TV açma sinyali gönderildi", Toast.LENGTH_SHORT).show()
        }

        btnOff?.setOnClickListener {
            sendPowerOff(tvIp, tvPsk)
            Toast.makeText(this, "TV kapatma komutu gönderildi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findAllButtons(view: View?): List<Button> {
        if (view == null) return emptyList()
        val result = mutableListOf<Button>()
        if (view is Button) {
            result.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                result.addAll(findAllButtons(view.getChildAt(i)))
            }
        }
        return result
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
