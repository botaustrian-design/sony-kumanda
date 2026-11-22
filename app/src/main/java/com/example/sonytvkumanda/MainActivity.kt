package com.example.sonytvkumanda

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val tvIp = "192.168.8.102"
    private val tvMac = "e8:6f:38:a6:97:83"
    private val tvPsk = "1234"
    private val subnetBroadcast = "192.168.8.255"
    private val printerIp = "192.168.8.147"

    private enum class AppLang(val code: String, val labelWithFlag: String) {
        TR("tr", "🇹🇷  Türkçe"),
        EN("en", "🇬🇧  English"),
        DE("de", "🇩🇪  Deutsch")
    }

    private data class BgTheme(
        val key: String,
        val hex: String,
        val trName: String,
        val enName: String,
        val deName: String
    )

    private val themes = listOf(
        BgTheme("navy", "#0F172A", "Koyu Lacivert", "Dark Navy", "Dunkelblau"),
        BgTheme("black", "#09090B", "Gece Siyahı", "Pitch Black", "Tiefschwarz"),
        BgTheme("charcoal", "#18181B", "Koyu Gri", "Dark Charcoal", "Dunkelgrau"),
        BgTheme("forest", "#06281E", "Koyu Yeşil", "Deep Forest", "Dunkelgrün")
    )

    private var currentLang = AppLang.TR
    private var currentTheme = themes[0]

    private lateinit var scrollView: ScrollView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var menuButton: Button
    private lateinit var tvCard: LinearLayout
    private lateinit var tvHeader: TextView
    private lateinit var btnOn: Button
    private lateinit var btnOff: Button
    private lateinit var tvStatus: TextView

    private lateinit var printerCard: LinearLayout
    private lateinit var printerHeader: TextView
    private lateinit var btnPrinterOn: Button
    private lateinit var btnPrinterOff: Button
    private lateinit var btnPrinterCheck: Button
    private lateinit var printerStatus: TextView

    private var lastTvStatusKey = "tv_ready"
    private var lastTvStatusArg: String? = null
    private var lastTvColor = "#CBD5E1"

    private var lastPrinterStatusKey = "printer_ready"
    private var lastPrinterStatusArg: String? = null
    private var lastPrinterColor = "#CBD5E1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedLang = prefs.getString("lang", AppLang.TR.code) ?: AppLang.TR.code
        currentLang = AppLang.entries.firstOrNull { it.code == savedLang } ?: AppLang.TR

        val savedThemeKey = prefs.getString("theme", themes[0].key) ?: themes[0].key
        currentTheme = themes.firstOrNull { it.key == savedThemeKey } ?: themes[0]

        scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor(currentTheme.hex))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 44, 40, 48)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8)
        }

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        titleText = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = Typeface.DEFAULT_BOLD
        }

        subtitleText = TextView(this).apply {
            text = "TV: $tvIp  •  HP: $printerIp"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 4, 0, 0)
        }

        menuButton = Button(this).apply {
            text = "☰"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 22f
                setStroke(2, Color.parseColor("#475569"))
            }
            elevation = 4f
            stateListAnimator = null
            setOnClickListener { showTopMenu(it) }
        }

        titleBlock.addView(titleText)
        titleBlock.addView(subtitleText)
        topBar.addView(titleBlock)
        topBar.addView(menuButton, LinearLayout.LayoutParams(112, 112))

        tvCard = createCard()

        tvHeader = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }

        btnOn = createModernButton(
            label = "",
            bgColor = "#16A34A",
            borderColor = "#4ADE80"
        ).apply {
            setOnClickListener { sendDigitalPowerOn() }
        }

        btnOff = createModernButton(
            label = "",
            bgColor = "#DC2626",
            borderColor = "#F87171"
        ).apply {
            setOnClickListener { sendPowerOff() }
        }

        tvStatus = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(12, 18, 12, 0)
        }

        tvCard.addView(tvHeader)
        tvCard.addView(btnOn, buttonParams(bottomMargin = 14))
        tvCard.addView(btnOff, buttonParams(bottomMargin = 0))
        tvCard.addView(tvStatus)

        printerCard = createCard().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
        }

        printerHeader = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }

        btnPrinterOn = createModernButton(
            label = "",
            bgColor = "#16A34A",
            borderColor = "#4ADE80"
        ).apply {
            setOnClickListener { turnPrinterOn() }
        }

        btnPrinterOff = createModernButton(
            label = "",
            bgColor = "#DC2626",
            borderColor = "#F87171"
        ).apply {
            setOnClickListener { turnPrinterOff() }
        }

        btnPrinterCheck = createModernButton(
            label = "",
            bgColor = "#334155",
            borderColor = "#64748B"
        ).apply {
            setOnClickListener { checkPrinterStatus() }
        }

        printerStatus = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(12, 18, 12, 0)
        }

        printerCard.addView(printerHeader)
        printerCard.addView(btnPrinterOn, buttonParams(bottomMargin = 14))
        printerCard.addView(btnPrinterOff, buttonParams(bottomMargin = 14))
        printerCard.addView(btnPrinterCheck, buttonParams(bottomMargin = 0))
        printerCard.addView(printerStatus)

        root.addView(topBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 20
        })
        root.addView(tvCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(printerCard)

        scrollView.addView(root)
        setContentView(scrollView)

        applyLanguageAndTheme()
    }

    private fun showTopMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val (tvMenu, printerMenu, settingsMenu) = when (currentLang) {
            AppLang.TR -> Triple("Televizyon", "Yazıcı", "Ayarlar")
            AppLang.EN -> Triple("Television", "Printer", "Settings")
            AppLang.DE -> Triple("Fernseher", "Drucker", "Einstellungen")
        }

        popup.menu.add(0, 1, 0, tvMenu)
        popup.menu.add(0, 2, 1, printerMenu)
        popup.menu.add(0, 3, 2, settingsMenu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    scrollView.post { scrollView.smoothScrollTo(0, tvCard.top) }
                    true
                }
                2 -> {
                    scrollView.post { scrollView.smoothScrollTo(0, printerCard.top) }
                    true
                }
                3 -> {
                    openSettingsDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
        private fun openSettingsDialog() {
        val (dialogTitle, bgLabel, langLabel, closeLabel) = when (currentLang) {
            AppLang.TR -> listOf("Ayarlar", "Arka Plan Rengi", "Dil", "Kapat")
            AppLang.EN -> listOf("Settings", "Background Color", "Language", "Close")
            AppLang.DE -> listOf("Einstellungen", "Hintergrundfarbe", "Sprache", "Schließen")
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 24, 44, 16)
        }

        val bgTitle = TextView(this).apply {
            text = bgLabel
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 12)
        }
        container.addView(bgTitle)

        val bgButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        themes.forEachIndexed { idx, theme ->
            val swatch = Button(this).apply {
                text = if (theme.key == currentTheme.key) "✓" else ""
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(theme.hex))
                    cornerRadius = 20f
                    setStroke(
                        if (theme.key == currentTheme.key) 5 else 2,
                        if (theme.key == currentTheme.key) Color.parseColor("#38BDF8") else Color.parseColor("#64748B")
                    )
                }
                stateListAnimator = null
                setOnClickListener {
                    currentTheme = theme
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .edit().putString("theme", theme.key).apply()
                    applyLanguageAndTheme()
                    openSettingsDialogRefresh(container, bgButtonsRow)
                }
            }
            val lp = LinearLayout.LayoutParams(0, 110, 1f)
            if (idx < themes.lastIndex) lp.marginEnd = 14
            bgButtonsRow.addView(swatch, lp)
        }
        container.addView(bgButtonsRow)

        val langTitle = TextView(this).apply {
            text = langLabel
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 12)
        }
        container.addView(langTitle)

        var dialogRef: AlertDialog? = null
        AppLang.entries.forEach { lang ->
            val isSelected = lang == currentLang
            val langBtn = Button(this).apply {
                text = if (isSelected) "${lang.labelWithFlag}  ✓" else lang.labelWithFlag
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(28, 0, 28, 0)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (isSelected) "#1D4ED8" else "#1E293B"))
                    cornerRadius = 22f
                    setStroke(
                        3,
                        Color.parseColor(if (isSelected) "#60A5FA" else "#475569")
                    )
                }
                stateListAnimator = null
                setOnClickListener {
                    currentLang = lang
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .edit().putString("lang", lang.code).apply()
                    applyLanguageAndTheme()
                    dialogRef?.dismiss()
                }
            }
            container.addView(langBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                116
            ).apply {
                bottomMargin = 12
            })
        }

        dialogRef = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(container)
            .setPositiveButton(closeLabel, null)
            .create()
        dialogRef.show()
    }

    private fun openSettingsDialogRefresh(container: LinearLayout, bgButtonsRow: LinearLayout) {
        for (i in 0 until bgButtonsRow.childCount) {
            val child = bgButtonsRow.getChildAt(i) as? Button ?: continue
            val theme = themes[i]
            val selected = theme.key == currentTheme.key
            child.text = if (selected) "✓" else ""
            child.background = GradientDrawable().apply {
                setColor(Color.parseColor(theme.hex))
                cornerRadius = 20f
                setStroke(
                    if (selected) 5 else 2,
                    if (selected) Color.parseColor("#38BDF8") else Color.parseColor("#64748B")
                )
            }
        }
        container.invalidate()
    }

    private fun applyLanguageAndTheme() {
        scrollView.setBackgroundColor(Color.parseColor(currentTheme.hex))

        when (currentLang) {
            AppLang.TR -> {
                titleText.text = "EV KUMANDA MERKEZİ"
                tvHeader.text = "SONY BRAVIA ($tvIp)"
                btnOn.text = "⚡  TV'Yİ AÇ"
                btnOff.text = "⏻  TV'Yİ KAPAT"
                printerHeader.text = "HP ENVY 6400e ($printerIp)"
                btnPrinterOn.text = "⚡  YAZICIYI AÇ"
                btnPrinterOff.text = "⏻  YAZICIYI KAPAT"
                btnPrinterCheck.text = "📡  YAZICI DURUMU"
            }
            AppLang.EN -> {
                titleText.text = "HOME CONTROL HUB"
                tvHeader.text = "SONY BRAVIA ($tvIp)"
                btnOn.text = "⚡  TURN TV ON"
                btnOff.text = "⏻  TURN TV OFF"
                printerHeader.text = "HP ENVY 6400e ($printerIp)"
                btnPrinterOn.text = "⚡  TURN PRINTER ON"
                btnPrinterOff.text = "⏻  TURN PRINTER OFF"
                btnPrinterCheck.text = "📡  PRINTER STATUS"
            }
            AppLang.DE -> {
                titleText.text = "HEIMSTEUERUNG"
                tvHeader.text = "SONY BRAVIA ($tvIp)"
                btnOn.text = "⚡  TV EINSCHALTEN"
                btnOff.text = "⏻  TV AUSSCHALTEN"
                printerHeader.text = "HP ENVY 6400e ($printerIp)"
                btnPrinterOn.text = "⚡  DRUCKER EINSCHALTEN"
                btnPrinterOff.text = "⏻  DRUCKER AUSSCHALTEN"
                btnPrinterCheck.text = "📡  DRUCKERSTATUS"
            }
        }

        renderTvStatus()
        renderPrinterStatus()
    }

    private fun formatTvStatus(key: String, arg: String?): String = when (currentLang) {
        AppLang.TR -> when (key) {
            "tv_ready" -> "TV Hazır"
            "tv_sending_on" -> "Dijital açma komutları gönderiliyor..."
            "tv_sent_on" -> "WoL + HTTP(true) + IRCC gönderildi"
            "tv_err_on" -> "Açma hatası: ${arg ?: "Bilinmeyen hata"}"
            "tv_sending_off" -> "Kapatma komutu gönderiliyor..."
            "tv_off_ok" -> "TV kapatıldı (HTTP ${arg ?: ""})"
            "tv_off_code" -> "TV yanıt kodu: HTTP ${arg ?: ""}"
            "tv_err_off" -> "Kapatma hatası: ${arg ?: "Bağlantı yok"}"
            else -> arg ?: ""
        }
        AppLang.EN -> when (key) {
            "tv_ready" -> "TV Ready"
            "tv_sending_on" -> "Sending digital power-on commands..."
            "tv_sent_on" -> "WoL + HTTP(true) + IRCC sent"
            "tv_err_on" -> "Power-on error: ${arg ?: "Unknown error"}"
            "tv_sending_off" -> "Sending power-off command..."
            "tv_off_ok" -> "TV turned off (HTTP ${arg ?: ""})"
            "tv_off_code" -> "TV response code: HTTP ${arg ?: ""}"
            "tv_err_off" -> "Power-off error: ${arg ?: "No connection"}"
            else -> arg ?: ""
        }
        AppLang.DE -> when (key) {
            "tv_ready" -> "TV Bereit"
            "tv_sending_on" -> "Digitale Einschaltbefehle werden gesendet..."
            "tv_sent_on" -> "WoL + HTTP(true) + IRCC gesendet"
            "tv_err_on" -> "Einschaltfehler: ${arg ?: "Unbekannter Fehler"}"
            "tv_sending_off" -> "Ausschaltbefehl wird gesendet..."
            "tv_off_ok" -> "TV ausgeschaltet (HTTP ${arg ?: ""})"
            "tv_off_code" -> "TV-Antwortcode: HTTP ${arg ?: ""}"
            "tv_err_off" -> "Ausschaltfehler: ${arg ?: "Keine Verbindung"}"
            else -> arg ?: ""
        }
    }

    private fun formatPrinterStatus(key: String, arg: String?): String = when (currentLang) {
        AppLang.TR -> when (key) {
            "printer_ready" -> "Yazıcı Hazır ($printerIp)"
            "printer_waking" -> "Yazıcı açma/uyandırma komutu gönderiliyor..."
            "printer_awake" -> "Yazıcı uyandı ve yanıt verdi (${arg ?: ""})"
            "printer_reachable" -> "Yazıcı ağda yanıt verdi (uyku/bekleme)"
            "printer_unreachable" -> "Yazıcıya ulaşılamadı (kapalı veya farklı IP)"
            "printer_err_wake" -> "Açma hatası: ${arg ?: "Bağlantı yok"}"
            "printer_turning_off" -> "Yazıcı kapatma komutu gönderiliyor..."
            "printer_off_sent" -> "Kapatma komutu denendi (${arg ?: ""})"
            "printer_off_unsupported" -> "Bu HP modelinde ağdan kapatma açık değil"
            "printer_err_off" -> "Kapatma hatası: ${arg ?: "Bağlantı yok"}"
            "printer_checking" -> "Yazıcı durumu kontrol ediliyor..."
            "printer_online" -> arg ?: "Çevrimiçi"
            "printer_offline" -> "Yanıt yok (arka tuş kapalı veya uyku derin)"
            "printer_err_check" -> "Kontrol hatası: ${arg ?: "Hata"}"
            else -> arg ?: ""
        }
        AppLang.EN -> when (key) {
            "printer_ready" -> "Printer Ready ($printerIp)"
            "printer_waking" -> "Sending printer power-on/wake commands..."
            "printer_awake" -> "Printer awake and responded (${arg ?: ""})"
            "printer_reachable" -> "Printer responded on network (sleep/standby)"
            "printer_unreachable" -> "Printer unreachable (off or different IP)"
            "printer_err_wake" -> "Power-on error: ${arg ?: "No connection"}"
            "printer_turning_off" -> "Sending printer power-off command..."
            "printer_off_sent" -> "Power-off command attempted (${arg ?: ""})"
            "printer_off_unsupported" -> "Network power-off not exposed on this HP model"
            "printer_err_off" -> "Power-off error: ${arg ?: "No connection"}"
            "printer_checking" -> "Checking printer status..."
            "printer_online" -> arg ?: "Online"
            "printer_offline" -> "No response (power button off or deep sleep)"
            "printer_err_check" -> "Check error: ${arg ?: "Error"}"
            else -> arg ?: ""
        }
        AppLang.DE -> when (key) {
            "printer_ready" -> "Drucker Bereit ($printerIp)"
            "printer_waking" -> "Drucker-Einschalt-/Weckbefehle werden gesendet..."
            "printer_awake" -> "Drucker wach und erreichbar (${arg ?: ""})"
            "printer_reachable" -> "Drucker im Netz erreichbar (Ruhezustand)"
            "printer_unreachable" -> "Drucker nicht erreichbar (aus oder andere IP)"
            "printer_err_wake" -> "Einschaltfehler: ${arg ?: "Keine Verbindung"}"
            "printer_turning_off" -> "Drucker-Ausschaltbefehl wird gesendet..."
            "printer_off_sent" -> "Ausschaltbefehl gesendet (${arg ?: ""})"
            "printer_off_unsupported" -> "Netz-Ausschalten bei diesem HP-Modell nicht aktiv"
            "printer_err_off" -> "Ausschaltfehler: ${arg ?: "Keine Verbindung"}"
            "printer_checking" -> "Druckerstatus wird geprüft..."
            "printer_online" -> arg ?: "Online"
            "printer_offline" -> "Keine Antwort (Netztaste aus oder Tiefschlaf)"
            "printer_err_check" -> "Prüffehler: ${arg ?: "Fehler"}"
            else -> arg ?: ""
        }
    }
        private fun setTvStatusKey(key: String, colorHex: String, arg: String? = null) {
        lastTvStatusKey = key
        lastTvStatusArg = arg
        lastTvColor = colorHex
        runOnUiThread { renderTvStatus() }
    }

    private fun renderTvStatus() {
        tvStatus.text = formatTvStatus(lastTvStatusKey, lastTvStatusArg)
        tvStatus.setTextColor(Color.parseColor(lastTvColor))
    }

    private fun setPrinterStatusKey(key: String, colorHex: String, arg: String? = null) {
        lastPrinterStatusKey = key
        lastPrinterStatusArg = arg
        lastPrinterColor = colorHex
        runOnUiThread { renderPrinterStatus() }
    }

    private fun renderPrinterStatus() {
        printerStatus.text = formatPrinterStatus(lastPrinterStatusKey, lastPrinterStatusArg)
        printerStatus.setTextColor(Color.parseColor(lastPrinterColor))
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 32f
                setStroke(2, Color.parseColor("#334155"))
            }
            setPadding(36, 36, 36, 36)
        }
    }

    private fun buttonParams(bottomMargin: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            132
        ).apply {
            this.bottomMargin = bottomMargin
        }
    }

    private fun createModernButton(label: String, bgColor: String, borderColor: String): Button {
        return Button(this).apply {
            text = label
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = 26f
                setStroke(3, Color.parseColor(borderColor))
            }
            elevation = 6f
            stateListAnimator = null
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun turnPrinterOn() {
        setPrinterStatusKey("printer_waking", "#60A5FA")
        thread {
            try {
                val reachable = try {
                    InetAddress.getByName(printerIp).isReachable(2000)
                } catch (_: Exception) {
                    false
                }

                val portsToPoke = listOf(9100, 631, 80, 443)
                var anyOpen = false
                for (port in portsToPoke) {
                    if (isPortOpen(printerIp, port, 1500)) {
                        anyOpen = true
                    }
                }

                var httpCode = -1
                try {
                    val conn = (URL("http://$printerIp/").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 2000
                        readTimeout = 2000
                    }
                    httpCode = conn.responseCode
                    conn.disconnect()
                } catch (_: Exception) {
                }

                when {
                    httpCode in 200..499 || anyOpen -> {
                        setPrinterStatusKey("printer_awake", "#4ADE80", "HTTP $httpCode")
                    }
                    reachable -> {
                        setPrinterStatusKey("printer_reachable", "#FACC15")
                    }
                    else -> {
                        setPrinterStatusKey("printer_unreachable", "#F87171")
                    }
                }
            } catch (e: Exception) {
                setPrinterStatusKey("printer_err_wake", "#F87171", e.localizedMessage)
            }
        }
    }

    private fun turnPrinterOff() {
        setPrinterStatusKey("printer_turning_off", "#F87171")
        thread {
            try {
                val endpoints = listOf(
                    "http://$printerIp/cdm/system/v1/power" to """{"powerState":"off"}""",
                    "http://$printerIp/DevMgmt/ProductConfigDyn.xml" to """<ps:ProductConfigDyn xmlns:ps="http://www.hp.com/schemas/imaging/con/ledm/productconfigdyn/2007/11/05"><ps:PowerState>Off</ps:PowerState></ps:ProductConfigDyn>"""
                )

                var acceptedCode: Int? = null
                for ((endpoint, payload) in endpoints) {
                    try {
                        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                            requestMethod = "PUT"
                            setRequestProperty("Content-Type", "application/json")
                            connectTimeout = 2000
                            readTimeout = 2000
                            doOutput = true
                        }
                        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                        val code = conn.responseCode
                        conn.disconnect()
                        if (code in 200..299) {
                            acceptedCode = code
                            break
                        }
                    } catch (_: Exception) {
                    }
                }

                if (acceptedCode != null) {
                    setPrinterStatusKey("printer_off_sent", "#4ADE80", "HTTP $acceptedCode")
                } else {
                    setPrinterStatusKey("printer_off_unsupported", "#FACC15")
                }
            } catch (e: Exception) {
                setPrinterStatusKey("printer_err_off", "#F87171", e.localizedMessage)
            }
        }
    }

    private fun checkPrinterStatus() {
        setPrinterStatusKey("printer_checking", "#CBD5E1")
        thread {
            try {
                val port9100 = isPortOpen(printerIp, 9100, 1500)
                val port631 = isPortOpen(printerIp, 631, 1500)
                val port80 = isPortOpen(printerIp, 80, 1500)

                var httpCode = -1
                try {
                    val conn = (URL("http://$printerIp/").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 2000
                        readTimeout = 2000
                    }
                    httpCode = conn.responseCode
                    conn.disconnect()
                } catch (_: Exception) {
                }

                if (port9100 || port631 || port80 || httpCode in 200..499) {
                    val info = "Online • HTTP:${if (httpCode > 0) httpCode else "-"} • 9100:${if (port9100) "ON" else "OFF"}"
                    setPrinterStatusKey("printer_online", "#4ADE80", info)
                } else {
                    setPrinterStatusKey("printer_offline", "#F87171")
                }
            } catch (e: Exception) {
                setPrinterStatusKey("printer_err_check", "#F87171", e.localizedMessage)
            }
        }
    }

    private fun sendDigitalPowerOn() {
        setTvStatusKey("tv_sending_on", "#4ADE80")
        thread {
            var multicastLock: WifiManager.MulticastLock? = null
            try {
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
                multicastLock = wifiManager?.createMulticastLock("sony_wol_lock")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }

                val macBytes = tvMac.replace("-", ":").split(":").map { it.toInt(16).toByte() }.toByteArray()
                if (macBytes.size == 6) {
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
                    val ports = listOf(9, 7, 2304)
                    val socket = DatagramSocket().apply {
                        broadcast = true
                        soTimeout = 1500
                    }
                    for (addr in targets) {
                        for (port in ports) {
                            socket.send(DatagramPacket(magicPacket, magicPacket.size, addr, port))
                        }
                    }
                    socket.close()
                }

                try {
                    val url = URL("http://$tvIp/sony/system")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("X-Auth-PSK", tvPsk)
                        connectTimeout = 2000
                        readTimeout = 2000
                        doOutput = true
                    }
                    val body = """{"method":"setPowerStatus","id":1,"params":[{"status":true}],"version":"1.0"}"""
                    conn.outputStream.use { it.write(body.toByteArray()) }
                    conn.responseCode
                    conn.disconnect()
                } catch (_: Exception) {
                }

                try {
                    val irccUrl = URL("http://$tvIp/sony/ircc")
                    val irccConn = (irccUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "text/xml; charset=UTF-8")
                        setRequestProperty("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
                        setRequestProperty("X-Auth-PSK", tvPsk)
                        connectTimeout = 2000
                        readTimeout = 2000
                        doOutput = true
                    }
                    val soapBody = """
                        <?xml version="1.0"?>
                        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                          <s:Body>
                            <u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1">
                              <IRCCCode>AAAAAQAAAAEAAAAuAw==</IRCCCode>
                            </u:X_SendIRCC>
                          </s:Body>
                        </s:Envelope>
                    """.trimIndent()
                    irccConn.outputStream.use { it.write(soapBody.toByteArray(Charsets.UTF_8)) }
                    irccConn.responseCode
                    irccConn.disconnect()
                } catch (_: Exception) {
                }

                setTvStatusKey("tv_sent_on", "#4ADE80")
            } catch (e: Exception) {
                setTvStatusKey("tv_err_on", "#F87171", e.localizedMessage)
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

    private fun sendPowerOff() {
        setTvStatusKey("tv_sending_off", "#F87171")
        thread {
            try {
                val url = URL("http://$tvIp/sony/system")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Auth-PSK", tvPsk)
                    connectTimeout = 3000
                    readTimeout = 3000
                    doOutput = true
                }
                val body = """{"method":"setPowerStatus","id":1,"params":[{"status":false}],"version":"1.0"}"""
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                conn.disconnect()

                if (code in 200..299) {
                    setTvStatusKey("tv_off_ok", "#4ADE80", code.toString())
                } else {
                    setTvStatusKey("tv_off_code", "#FACC15", code.toString())
                }
            } catch (e: Exception) {
                setTvStatusKey("tv_err_off", "#F87171", e.localizedMessage)
            }
        }
    }
}
