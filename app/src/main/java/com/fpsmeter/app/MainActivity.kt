package com.fpsmeter.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var statusOverlay: TextView
    private lateinit var statusShizuku: TextView
    private lateinit var switchEnable: Switch

    private val colorNames = arrayOf("White", "Black", "Red", "Green", "Blue", "Yellow", "Cyan", "Magenta")
    private val colorValues = intArrayOf(
        Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA
    )

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refreshStatus() } }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { runOnUiThread { refreshStatus() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = PrefsManager(this)

        statusOverlay = findViewById(R.id.status_overlay)
        statusShizuku = findViewById(R.id.status_shizuku)
        switchEnable = findViewById(R.id.switch_enable_overlay)

        findViewById<Button>(R.id.btn_overlay_permission).setOnClickListener { requestOverlayPermission() }
        findViewById<Button>(R.id.btn_shizuku_permission).setOnClickListener { requestShizukuPermission() }
        findViewById<Button>(R.id.btn_reset_position).setOnClickListener {
            prefs.posX = 50
            prefs.posY = 150
            sendRefresh()
            Toast.makeText(this, "Position reset", Toast.LENGTH_SHORT).show()
        }

        switchEnable.isChecked = prefs.overlayEnabled
        switchEnable.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_SHORT).show()
                    switchEnable.isChecked = false
                    return@setOnCheckedChangeListener
                }
                if (!ShizukuHelper.isShizukuAvailable() || !ShizukuHelper.hasPermission()) {
                    Toast.makeText(this, "Grant Shizuku permission first", Toast.LENGTH_SHORT).show()
                    switchEnable.isChecked = false
                    return@setOnCheckedChangeListener
                }
                prefs.overlayEnabled = true
                ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
            } else {
                prefs.overlayEnabled = false
                stopService(Intent(this, OverlayService::class.java))
            }
        }

        setupSeekBar(R.id.seek_font_size, 8, 40, prefs.fontSize.toInt()) { prefs.fontSize = it.toFloat(); sendRefresh() }
        setupSeekBar(R.id.seek_padding, 0, 48, prefs.padding) { prefs.padding = it; sendRefresh() }
        setupSeekBar(R.id.seek_corner_radius, 0, 48, prefs.cornerRadius) { prefs.cornerRadius = it; sendRefresh() }
        setupSeekBar(R.id.seek_alpha, 0, 255, prefs.backgroundAlpha) { prefs.backgroundAlpha = it; sendRefresh() }
        setupSeekBar(R.id.seek_scale, 50, 250, (prefs.overlayScale * 100).toInt()) { prefs.overlayScale = it / 100f; sendRefresh() }
        setupSeekBar(R.id.seek_interval, 200, 3000, prefs.updateIntervalMs.toInt()) { prefs.updateIntervalMs = it.toLong(); sendUpdateInterval() }

        setupColorSpinner(R.id.spinner_text_color, prefs.textColor) { prefs.textColor = it; sendRefresh() }
        setupColorSpinner(R.id.spinner_bg_color, prefs.backgroundColor) { prefs.backgroundColor = it; sendRefresh() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    private fun setupSeekBar(id: Int, min: Int, max: Int, current: Int, onChange: (Int) -> Unit) {
        val seek = findViewById<SeekBar>(id)
        seek.max = max - min
        seek.progress = (current - min).coerceIn(0, max - min)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress + min)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupColorSpinner(id: Int, current: Int, onChange: (Int) -> Unit) {
        val spinner = findViewById<Spinner>(id)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colorNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val idx = colorValues.indexOf(current).let { if (it < 0) 0 else it }
        spinner.setSelection(idx)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                onChange(colorValues[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun requestOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun requestShizukuPermission() {
        if (!ShizukuHelper.isShizukuAvailable()) {
            Toast.makeText(this, "Shizuku is not running. Start it first.", Toast.LENGTH_LONG).show()
            return
        }
        ShizukuHelper.requestPermission { granted ->
            runOnUiThread {
                refreshStatus()
                if (!granted) Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendRefresh() {
        sendBroadcast(Intent(OverlayService.ACTION_REFRESH_STYLE).setPackage(packageName))
    }

    private fun sendUpdateInterval() {
        sendBroadcast(Intent(OverlayService.ACTION_UPDATE_INTERVAL).setPackage(packageName))
    }

    private fun refreshStatus() {
        statusOverlay.text = if (Settings.canDrawOverlays(this)) "Overlay permission: granted" else "Overlay permission: not granted"
        statusShizuku.text = when {
            !ShizukuHelper.isShizukuAvailable() -> "Shizuku: not running"
            !ShizukuHelper.hasPermission() -> "Shizuku: permission not granted"
            else -> "Shizuku: ready"
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }
}
