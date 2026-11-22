package com.example.sonytvkumanda

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
    private val subnetBroadcast = "192.168.8.255"

    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 64, 48, 64)
        }

        val titleText = TextView(this).apply {
            text = "SONY BRAVIA"
            textSize = 26f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitleText = TextView(this).apply {
            text = "IP: $tvIp  •  PSK: $tvPsk\nMAC: $tvMac"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 36)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 36f
                setStroke(2, Color.parseColor("#334155"))
            }
            setPadding(40, 44, 40, 44)
        }

        val btnOn = createModernButton(
            label = "⚡  TV'Yİ AÇ",
            bgColor = "#16A34A",
            borderColor = "#4ADE80"
        ).apply {
            setOnClickListener {
                setStatus("Açma (WoL) paketi gönderiliyor...", "#4ADE80")
                sendWakeOnLan(tvMac)
            }
        }

        val btnOff = createModernButton(
            label = "⏻  TV'Yİ KAPAT",
            bgColor = "#DC2626",
            borderColor = "#F87171"
        ).apply {
            setOnClickListener {
                setStatus("Kapatma komutu gönderiliyor...", "#F87171")
                sendPowerOff(tvIp, tvPsk)
            }
        }

        tvStatus = TextView(this).apply {
            text = "Hazır — Ağ: $subnetBroadcast"
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(16, 32, 16, 0)
        }

        val onParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            148
        ).apply {
            bottomMargin = 24
        }

        val offParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            148
        )

        card.addView(btnOn, onParams)
        card.addView(btnOff, offParams)
        card.addView(tvStatus)

        root.addView(titleText)
        root.addView(subtitleText)
        root.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        scrollView.addView(root)
        setContentView(scrollView)
    }

    private fun createModernButton(label: String, bgColor: String, borderColor: String): Button {
        return Button(this).apply {
            text = label
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = 28f
                setStroke(3, Color.parseColor(borderColor))
            }
            elevation = 8f
            stateListAnimator = null
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
    }

    private fun setStatus(message: String, colorHex: String) {
        runOnUiThread {
            tvStatus.text = message
            tvStatus.setTextColor(Color.parseColor(colorHex))
        }
    }

    private fun sendWakeOnLan(macAddress: String) {
        thread {
            var multicastLock: WifiManager.MulticastLock? = null
            try {
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
                multicastLock = wifiManager?.createMulticastLock("sony_wol_lock")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }

                val cleanMac = macAddress.replace("-", ":")
                val macBytes = cleanMac.split(":").map { it.toInt(16).toByte() }.toByteArray()
                if (macBytes.size != 6) {
                    setStatus("MAC adresi hatalı", "#F87171")
                    return@thread
                }

                val magicPacket = ByteArray(6 + 16 * macBytes.size)
                for (i in 0 until 6) magicPacket[i] = 0xFF.toByte()
                for (i in 6 until magicPacket.size) {
                    magicPacket[i] = macBytes[i % macBytes.size]
                }

                val targets = listOf(
                    InetAddress.getByName(tvIp),
                    InetAddress.getByName(subnetBroadcast),
                    InetAddress.getByName("255.255.255.255")
                )
                val ports = listOf(9, 7)

                val socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 2000
                }

                for (addr in targets) {
                    for (port in ports) {
                        val packet = DatagramPacket(magicPacket, magicPacket.size, addr, port)
                        socket.send(packet)
                    }
                }
                socket.close()

                setStatus("WoL gönderildi ($tvIp & $subnetBroadcast)", "#4ADE80")
            } catch (e: Exception) {
                setStatus("WoL hatası: ${e.localizedMessage ?: "Bilinmeyen hata"}", "#F87171")
            } finally {
                try {
                    if (multicastLock?.isHeld == true) {
                        multicastLock.release()
                    }
                } catch (_: Exception) {
                }
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
                val code = conn.responseCode
                conn.disconnect()

                if (code in 200..299) {
                    setStatus("TV kapatıldı (HTTP $code)", "#4ADE80")
                } else {
                    setStatus("TV yanıt kodu: HTTP $code", "#FACC15")
                }
            } catch (e: Exception) {
                setStatus("Kapatma hatası: ${e.localizedMessage ?: "Bağlantı yok"}", "#F87171")
            }
        }
    }
}
