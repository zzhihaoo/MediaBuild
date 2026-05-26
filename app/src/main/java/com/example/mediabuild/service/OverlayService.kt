package com.example.mediabuild.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.mediabuild.MainActivity
import com.example.mediabuild.MediaDownloadApp
import com.example.mediabuild.downloader.DownloadManager
import com.example.mediabuild.model.MediaType

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var mediaPanel: View? = null
    private var mediaUrls: List<String> = emptyList()

    private val downloadManager by lazy { DownloadManager.getInstance(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val platform = intent.getStringExtra(EXTRA_PLATFORM) ?: ""
                showOverlayButton(platform)
            }
            ACTION_UPDATE_MEDIA -> {
                val urls = intent.getStringArrayListExtra(EXTRA_MEDIA_URLS) ?: arrayListOf()
                updateMediaUrls(urls)
            }
            ACTION_STOP -> {
                removeOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showOverlayButton(platform: String) {
        if (overlayView != null) return

        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.stat_sys_download)
            setColorFilter(0xFFFFFFFF.toInt())
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC333333.toInt())
            }
            background = bg
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        overlayView = iconView

        val params = WindowManager.LayoutParams(
            dpToPx(56),
            dpToPx(56),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dpToPx(8)
        }

        var initialX = 0
        var initialTouchX = 0f

        iconView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialTouchX = event.rawX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - initialTouchX).toInt()
                    try {
                        windowManager?.updateViewLayout(overlayView, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = Math.abs(event.rawX - initialTouchX)
                    if (dx < 10) {
                        toggleMediaPanel()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleMediaPanel() {
        if (mediaPanel != null) {
            removeMediaPanel()
        } else {
            showMediaPanel()
        }
    }

    private fun showMediaPanel() {
        if (mediaUrls.isEmpty()) {
            showToast("未检测到媒体内容")
            return
        }

        val panel = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(0xF0222222.toInt())
                cornerRadius = dpToPx(12).toFloat()
            }
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        val scrollView = ScrollView(this)
        val contentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val titleText = TextView(this).apply {
            text = "检测到 ${mediaUrls.size} 个媒体"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setPadding(0, 0, 0, dpToPx(12))
        }
        contentView.addView(titleText)

        mediaUrls.forEachIndexed { index, url ->
            val itemView = createMediaItemView(url, index)
            contentView.addView(itemView)
        }

        val downloadAllBtn = TextView(this).apply {
            text = "一键全部下载"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            background = GradientDrawable().apply {
                setColor(0xFF4CAF50.toInt())
                cornerRadius = dpToPx(8).toFloat()
            }
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            gravity = Gravity.CENTER
            setOnClickListener {
                downloadAllMedia()
            }
        }
        contentView.addView(downloadAllBtn)

        scrollView.addView(contentView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        panel.addView(scrollView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        mediaPanel = panel

        val params = WindowManager.LayoutParams(
            dpToPx(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dpToPx(72)
        }

        try {
            windowManager?.addView(mediaPanel, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createMediaItemView(url: String, index: Int): LinearLayout {
        val ctx = this@OverlayService
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            background = GradientDrawable().apply {
                setColor(0xFF333333.toInt())
                cornerRadius = dpToPx(8).toFloat()
            }

            val icon = ImageView(ctx).apply {
                setImageResource(android.R.drawable.stat_sys_download)
                setColorFilter(0xFF4CAF50.toInt())
                layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
            }
            addView(icon)

            val info = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dpToPx(8)
                }
            }

            val typeText = TextView(ctx).apply {
                text = if (url.contains(".mp4") || url.contains("video")) "视频 ${index + 1}" else "图片 ${index + 1}"
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 13f
            }
            info.addView(typeText)

            val urlText = TextView(ctx).apply {
                text = url.take(40) + if (url.length > 40) "..." else ""
                setTextColor(0xFF888888.toInt())
                textSize = 11f
                maxLines = 1
            }
            info.addView(urlText)

            addView(info)

            val downloadBtn = TextView(ctx).apply {
                text = "下载"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
                background = GradientDrawable().apply {
                    setColor(0xFF2196F3.toInt())
                    cornerRadius = dpToPx(6).toFloat()
                }
                setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
                setOnClickListener {
                    downloadSingleMedia(url)
                }
            }
            addView(downloadBtn)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(4)
            }
        }
    }

    private fun downloadSingleMedia(url: String) {
        val type = if (url.contains(".mp4") || url.contains("video")) MediaType.VIDEO else MediaType.IMAGE
        val item = com.example.mediabuild.model.MediaItem(url = url, type = type)
        downloadManager.downloadMedia(item)
        showToast("开始下载")
    }

    private fun downloadAllMedia() {
        val items = mediaUrls.map { url ->
            val type = if (url.contains(".mp4") || url.contains("video")) MediaType.VIDEO else MediaType.IMAGE
            com.example.mediabuild.model.MediaItem(url = url, type = type)
        }
        downloadManager.downloadAll(items)
        showToast("开始下载 ${items.size} 个文件")
    }

    private fun updateMediaUrls(urls: List<String>) {
        mediaUrls = urls
    }

    private fun removeMediaPanel() {
        mediaPanel?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        mediaPanel = null
    }

    private fun removeOverlay() {
        removeMediaPanel()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, MediaDownloadApp.CHANNEL_OVERLAY)
            .setContentTitle("MediaBuild")
            .setContentText("悬浮窗服务运行中")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    companion object {
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"
        const val ACTION_UPDATE_MEDIA = "action_update_media"
        const val EXTRA_PLATFORM = "extra_platform"
        const val EXTRA_MEDIA_URLS = "extra_media_urls"
        private const val NOTIFICATION_ID = 1001
    }
}
