package com.fpsmeter.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: TextView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var prefs: PrefsManager
    private var fpsMonitor: FpsMonitor? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_REFRESH_STYLE -> applyStyle()
                ACTION_UPDATE_INTERVAL -> fpsMonitor?.updateInterval(prefs.updateIntervalMs)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()

        val filter = IntentFilter().apply {
            addAction(ACTION_REFRESH_STYLE)
            addAction(ACTION_UPDATE_INTERVAL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(refreshReceiver, filter)
        }

        ShizukuHelper.bindService(this)
        setupOverlay()
        startMonitoring()
    }

    private fun startForegroundNotification() {
        val channelId = "fps_meter_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "FPS Meter", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("FPS Meter running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun setupOverlay() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = prefs.posX
        layoutParams.y = prefs.posY

        val tv = TextView(this)
        tv.text = "0 FPS"
        overlayView = tv
        applyStyle()

        tv.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(v, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.posX = layoutParams.x
                    prefs.posY = layoutParams.y
                    true
                }
                else -> false
            }
        }

        windowManager.addView(overlayView, layoutParams)
    }

    private fun applyStyle() {
        val tv = overlayView ?: return
        tv.textSize = prefs.fontSize
        tv.setTextColor(prefs.textColor)
        tv.setPadding(prefs.padding, prefs.padding, prefs.padding, prefs.padding)

        val bg = GradientDrawable()
        bg.cornerRadius = prefs.cornerRadius.toFloat()
        val bgColor = prefs.backgroundColor
        val alpha = prefs.backgroundAlpha
        bg.setColor(Color.argb(alpha, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor)))
        tv.background = bg

        tv.scaleX = prefs.overlayScale
        tv.scaleY = prefs.overlayScale
    }

    private fun startMonitoring() {
        fpsMonitor = FpsMonitor(
            onFpsUpdate = { fps -> mainHandler.post { overlayView?.text = "$fps FPS" } },
            onError = { }
        )
        fpsMonitor?.start(prefs.updateIntervalMs)
    }

    override fun onDestroy() {
        fpsMonitor?.stop()
        overlayView?.let { try { windowManager.removeView(it) } catch (e: Exception) { } }
        try { unregisterReceiver(refreshReceiver) } catch (e: Exception) { }
        ShizukuHelper.unbindService()
        super.onDestroy()
    }

    companion object {
        const val ACTION_REFRESH_STYLE = "com.fpsmeter.app.ACTION_REFRESH_STYLE"
        const val ACTION_UPDATE_INTERVAL = "com.fpsmeter.app.ACTION_UPDATE_INTERVAL"
    }
}
