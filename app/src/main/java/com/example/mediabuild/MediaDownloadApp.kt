package com.example.mediabuild

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MediaDownloadApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val overlayChannel = NotificationChannel(
            CHANNEL_OVERLAY,
            "悬浮窗服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "悬浮窗下载按钮"
        }

        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOAD,
            "下载通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "文件下载进度通知"
        }

        manager.createNotificationChannel(overlayChannel)
        manager.createNotificationChannel(downloadChannel)
    }

    companion object {
        const val CHANNEL_OVERLAY = "overlay_service"
        const val CHANNEL_DOWNLOAD = "download_service"
    }
}
