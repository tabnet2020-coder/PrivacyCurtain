package com.tabony.privacycurtain

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(60, 60, 60, 60)
        }

        val title = TextView(this).apply {
            text = "ستار الخصوصية الذكي"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        val desc = TextView(this).apply {
            text = "• اسحب المقبض الأخضر الجانبي للتحكم بالارتفاع\n• انقر مرتين على المقبض للتبديل بين (الستار / فتحة القراءة / الشبكة)"
            textSize = 15f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 60)
        }

        val toggleBtn = Button(this).apply {
            text = "تشغيل / إيقاف الستار الآن"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#10B981"))
            setTextColor(Color.WHITE)
            setPadding(40, 35, 40, 35)
            setOnClickListener { checkPermissionAndToggle() }
        }

        layout.addView(title)
        layout.addView(desc)
        layout.addView(toggleBtn)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val tileBtn = Button(this).apply {
                text = "إضافة زر إلى مركز التحكم السريع"
                textSize = 15f
                setBackgroundColor(Color.parseColor("#334155"))
                setTextColor(Color.WHITE)
                setPadding(40, 30, 40, 30)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 35 }
                layoutParams = params
                setOnClickListener { requestAddQuickTile() }
            }
            layout.addView(tileBtn)
        }

        setContentView(layout)
    }

    private fun checkPermissionAndToggle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "يرجى منح صلاحية الظهور فوق التطبيقات أولاً", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            val serviceIntent = Intent(this, PrivacyCurtainService::class.java)
            if (!PrivacyCurtainService.isRunning) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                moveTaskToBack(true)
            } else {
                stopService(serviceIntent)
            }
        }
    }

    private fun requestAddQuickTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = getSystemService(StatusBarManager::class.java)
            statusBarManager.requestAddTileService(
                ComponentName(this, PrivacyTileService::class.java),
                "ستار الخصوصية",
                Icon.createWithResource(this, android.R.drawable.ic_secure),
                mainExecutor
            ) {}
        }
    }
}
