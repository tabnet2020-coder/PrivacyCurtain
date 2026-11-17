package com.tabony.privacycurtain

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.*
import kotlin.math.max
import kotlin.math.min

class PrivacyCurtainService : Service() {

    companion object {
        var isRunning = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var curtainView: CurtainOverlayView
    private lateinit var handleView: View
    private lateinit var handleParams: WindowManager.LayoutParams

    enum class Mode { CURTAIN_TOP, PEEPHOLE, PIXEL_MESH }
    private var currentMode = Mode.CURTAIN_TOP
    private var curtainPositionY = 700f
    private var peepholeHeight = 260f
    private var filterAlpha = 235

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForegroundServiceNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        setupCurtainLayer()
        setupEdgeHandleLayer()
    }

    private fun setupCurtainLayer() {
        curtainView = CurtainOverlayView(this)

        val curtainParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(curtainView, curtainParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupEdgeHandleLayer() {
        handleView = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#10B981")
            }
            private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                strokeWidth = 5f
                strokeCap = Paint.Cap.ROUND
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
                canvas.drawRoundRect(rect, 24f, 24f, paint)
                val cy = height / 2f
                canvas.drawLine(12f, cy - 10f, width - 12f, cy - 10f, linePaint)
                canvas.drawLine(12f, cy + 10f, width - 12f, cy + 10f, linePaint)
            }
        }

        val density = resources.displayMetrics.density
        val handleWidth = (24 * density).toInt()
        val handleHeight = (80 * density).toInt()

        handleParams = WindowManager.LayoutParams(
            handleWidth,
            handleHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = curtainPositionY.toInt() - (handleHeight / 2)
        }

        var initialY = 0
        var initialTouchY = 0f
        var lastClickTime = 0L

        handleView.setOnTouchListener { _, event ->
            val screenHeight = resources.displayMetrics.heightPixels.toFloat()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = handleParams.y
                    initialTouchY = event.rawY

                    val clickTime = System.currentTimeMillis()
                    if (clickTime - lastClickTime < 300) {
                        cyclePrivacyMode()
                    }
                    lastClickTime = clickTime
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialTouchY
                    val newY = (initialY + deltaY).coerceIn(50f, screenHeight - handleView.height - 50f)
                    handleParams.y = newY.toInt()
                    curtainPositionY = newY + (handleView.height / 2f)

                    windowManager.updateViewLayout(handleView, handleParams)
                    curtainView.invalidate()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(handleView, handleParams)
    }

    private fun cyclePrivacyMode() {
        currentMode = when (currentMode) {
            Mode.CURTAIN_TOP -> Mode.PEEPHOLE
            Mode.PEEPHOLE -> Mode.PIXEL_MESH
            Mode.PIXEL_MESH -> Mode.CURTAIN_TOP
        }
        curtainView.invalidate()
    }

    inner class CurtainOverlayView(context: Context) : View(context) {

        private val solidPaint = Paint().apply {
            color = Color.argb(filterAlpha, 8, 12, 20)
            style = Paint.Style.FILL
        }

        private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        private val meshPaint = Paint().apply {
            shader = createPixelMeshShader()
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        private fun createPixelMeshShader(): BitmapShader {
            val patternBitmap = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888)
            val darkPixel = Color.argb(240, 0, 0, 0)
            val semiPixel = Color.argb(140, 0, 0, 0)

            patternBitmap.setPixel(0, 0, darkPixel)
            patternBitmap.setPixel(1, 0, darkPixel)
            patternBitmap.setPixel(0, 1, darkPixel)
            patternBitmap.setPixel(3, 3, semiPixel)

            return BitmapShader(patternBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            when (currentMode) {
                Mode.CURTAIN_TOP -> {
                    canvas.drawRect(0f, 0f, w, curtainPositionY, solidPaint)
                    canvas.drawLine(0f, curtainPositionY, w, curtainPositionY, borderPaint)
                }
                Mode.PEEPHOLE -> {
                    canvas.drawRect(0f, 0f, w, h, solidPaint)
                    val topHole = max(0f, curtainPositionY - peepholeHeight / 2f)
                    val bottomHole = min(h, curtainPositionY + peepholeHeight / 2f)
                    canvas.drawRoundRect(16f, topHole, w - 16f, bottomHole, 24f, 24f, clearPaint)
                    canvas.drawRoundRect(16f, topHole, w - 16f, bottomHole, 24f, 24f, borderPaint)
                }
                Mode.PIXEL_MESH -> {
                    canvas.drawRect(0f, 0f, w, curtainPositionY, meshPaint)
                    canvas.drawLine(0f, curtainPositionY, w, curtainPositionY, borderPaint)
                }
            }
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "privacy_curtain_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "خدمة ستار الخصوصية",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("ستار الخصوصية مفعل")
                .setContentText("اسحب المقبض للتحكم أو انقر مرتين لتغيير الوضع")
                .setSmallIcon(android.R.drawable.ic_secure)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("ستار الخصوصية مفعل")
                .setSmallIcon(android.R.drawable.ic_secure)
                .build()
        }

        startForeground(101, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (::curtainView.isInitialized) windowManager.removeView(curtainView)
        if (::handleView.isInitialized) windowManager.removeView(handleView)
    }
}
