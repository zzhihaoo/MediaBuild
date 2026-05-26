package com.example.mediabuild.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.mediabuild.service.OverlayService
import com.example.mediabuild.util.Platform

class MediaAccessibilityService : AccessibilityService() {

    private var currentPlatform: Platform = Platform.UNKNOWN
    private val detectedMediaUrls: MutableSet<String> = mutableSetOf()

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            info.notificationTimeout = 300
            serviceInfo = info
            isRunning = true
            Log.d(TAG, "无障碍服务已连接")
        } catch (e: Exception) {
            Log.e(TAG, "服务初始化失败", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val packageName = event.packageName?.toString() ?: return

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    currentPlatform = Platform.fromPackage(packageName)

                    if (Platform.isSupported(packageName)) {
                        detectedMediaUrls.clear()
                        startOverlayService()
                    } else {
                        currentPlatform = Platform.UNKNOWN
                    }
                }

                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    if (currentPlatform != Platform.UNKNOWN) {
                        scanCurrentWindow()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理事件异常", e)
        }
    }

    private fun scanCurrentWindow() {
        var root: AccessibilityNodeInfo? = null
        try {
            root = rootInActiveWindow ?: return
            val urls = mutableSetOf<String>()
            collectMediaUrls(root, urls, 0)

            if (urls.isNotEmpty() && urls != detectedMediaUrls) {
                detectedMediaUrls.addAll(urls)
                sendToOverlay(urls.toList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描异常", e)
        } finally {
            try { root?.recycle() } catch (_: Exception) {}
        }
    }

    private fun collectMediaUrls(node: AccessibilityNodeInfo?, urls: MutableSet<String>, depth: Int) {
        if (node == null || depth > 15 || urls.size >= 5) return

        try {
            // 检查 contentDescription
            val desc = node.contentDescription?.toString() ?: ""
            if (desc.isNotEmpty() && desc.length > 10 && desc.startsWith("http")) {
                urls.add(desc)
            }

            // 检查 text
            val text = node.text?.toString() ?: ""
            if (text.isNotEmpty() && text.length > 10 && text.startsWith("http")) {
                urls.add(text)
            }

            // 递归子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectMediaUrls(child, urls, depth + 1)
                try { child.recycle() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            // 忽略单个节点错误
        }
    }

    private fun startOverlayService() {
        try {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
                putExtra(OverlayService.EXTRA_PLATFORM, currentPlatform.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动悬浮窗失败", e)
        }
    }

    private fun sendToOverlay(urls: List<String>) {
        try {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_UPDATE_MEDIA
                putStringArrayListExtra(OverlayService.EXTRA_MEDIA_URLS, ArrayList(urls))
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "通知悬浮窗失败", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务中断")
    }

    override fun onDestroy() {
        isRunning = false
        try {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
            startService(intent)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MediaAccess"
        var isRunning: Boolean = false
            private set
    }
}
