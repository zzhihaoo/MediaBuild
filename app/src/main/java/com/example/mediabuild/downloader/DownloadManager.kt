package com.example.mediabuild.downloader

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.mediabuild.model.DownloadStatus
import com.example.mediabuild.model.DownloadTask
import com.example.mediabuild.model.MediaItem
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _tasks = mutableListOf<DownloadTask>()
    private var listener: DownloadListener? = null

    val tasks: List<DownloadTask> get() = _tasks.toList()

    interface DownloadListener {
        fun onTaskUpdate(task: DownloadTask)
        fun onAllComplete()
    }

    fun setListener(listener: DownloadListener) {
        this.listener = listener
    }

    fun downloadMedia(item: MediaItem): String {
        val taskId = UUID.randomUUID().toString()
        val task = DownloadTask(taskId, item, DownloadStatus.PENDING)
        _tasks.add(task)

        scope.launch {
            try {
                updateTask(task.copy(status = DownloadStatus.DOWNLOADING, progress = 0))

                val request = Request.Builder()
                    .url(item.url)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://www.google.com/")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    updateTask(task.copy(status = DownloadStatus.FAILED))
                    return@launch
                }

                val body = response.body ?: run {
                    updateTask(task.copy(status = DownloadStatus.FAILED))
                    return@launch
                }

                val contentType = body.contentType()?.toString() ?: ""
                val ext = when {
                    item.type == com.example.mediabuild.model.MediaType.VIDEO -> ".mp4"
                    "webp" in contentType -> ".webp"
                    "png" in contentType -> ".png"
                    else -> ".jpg"
                }

                val fileName = "${item.type.name.lowercase()}_${System.currentTimeMillis()}$ext"
                val localPath = saveToMediaStore(body.byteStream(), fileName, contentType)
                    ?: saveToDownloads(fileName, body.byteStream())

                updateTask(
                    task.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 100,
                        localPath = localPath ?: ""
                    )
                )
            } catch (e: Exception) {
                updateTask(task.copy(status = DownloadStatus.FAILED))
            }
        }

        return taskId
    }

    fun downloadAll(items: List<MediaItem>): List<String> {
        return items.map { downloadMedia(it) }
    }

    private fun saveToMediaStore(inputStream: java.io.InputStream, fileName: String, mimeType: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = when {
                    fileName.endsWith(".mp4") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        if (fileName.endsWith(".mp4"))
                            Environment.DIRECTORY_MOVIES + "/MediaBuild"
                        else
                            Environment.DIRECTORY_PICTURES + "/MediaBuild"
                    )
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(collection, values) ?: return null

                resolver.openOutputStream(uri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                uri.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveToDownloads(fileName: String, inputStream: java.io.InputStream): String? {
        return try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MediaBuild"
            )
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, fileName)
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun updateTask(task: DownloadTask) {
        val index = _tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            _tasks[index] = task
        }
        listener?.onTaskUpdate(task)

        if (task.status == DownloadStatus.COMPLETED || task.status == DownloadStatus.FAILED) {
            if (_tasks.all { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }) {
                listener?.onAllComplete()
            }
        }
    }

    fun cancel(taskId: String) {
        val index = _tasks.indexOfFirst { it.id == taskId }
        if (index >= 0) {
            _tasks[index] = _tasks[index].copy(status = DownloadStatus.FAILED)
        }
    }

    fun clearCompleted() {
        _tasks.removeAll { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }
    }

    fun destroy() {
        scope.cancel()
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var instance: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
