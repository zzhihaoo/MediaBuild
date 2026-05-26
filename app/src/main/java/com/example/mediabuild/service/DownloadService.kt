package com.example.mediabuild.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.mediabuild.MainActivity
import com.example.mediabuild.MediaDownloadApp
import com.example.mediabuild.downloader.DownloadManager
import com.example.mediabuild.model.DownloadTask

class DownloadService : Service(), DownloadManager.DownloadListener {

    private val downloadManager by lazy { DownloadManager.getInstance(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        downloadManager.setListener(this)
        startForeground(NOTIFICATION_ID, createNotification("准备下载"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onTaskUpdate(task: DownloadTask) {
        val statusText = when (task.status) {
            com.example.mediabuild.model.DownloadStatus.DOWNLOADING -> "下载中: ${task.progress}%"
            com.example.mediabuild.model.DownloadStatus.COMPLETED -> "下载完成"
            com.example.mediabuild.model.DownloadStatus.FAILED -> "下载失败"
            else -> "等待中"
        }
        val notification = createNotification(statusText)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onAllComplete() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, MediaDownloadApp.CHANNEL_DOWNLOAD)
            .setContentTitle("MediaBuild")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadManager.setListener(object : DownloadManager.DownloadListener {
            override fun onTaskUpdate(task: DownloadTask) {}
            override fun onAllComplete() {}
        })
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
    }
}
