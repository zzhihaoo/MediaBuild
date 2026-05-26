package com.example.mediabuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mediabuild.downloader.DownloadManager
import com.example.mediabuild.model.DownloadStatus
import com.example.mediabuild.model.DownloadTask
import com.example.mediabuild.model.MediaType

@Composable
fun DownloadScreen() {
    val context = LocalContext.current
    val downloadManager = remember { DownloadManager.getInstance(context) }
    var tasks by remember { mutableStateOf(downloadManager.tasks) }

    LaunchedEffect(Unit) {
        downloadManager.setListener(object : DownloadManager.DownloadListener {
            override fun onTaskUpdate(task: DownloadTask) {
                tasks = downloadManager.tasks.toList()
            }
            override fun onAllComplete() {
                tasks = downloadManager.tasks.toList()
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "下载管理",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (tasks.any { it.status == DownloadStatus.COMPLETED }) {
                TextButton(
                    onClick = {
                        downloadManager.clearCompleted()
                        tasks = downloadManager.tasks.toList()
                    }
                ) {
                    Text("清除已完成", color = Color(0xFF888888))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF333333),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无下载任务",
                        fontSize = 16.sp,
                        color = Color(0xFF555555)
                    )
                    Text(
                        text = "解析链接或使用屏幕检测下载媒体",
                        fontSize = 13.sp,
                        color = Color(0xFF444444),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            tasks.forEach { task ->
                DownloadTaskItem(task = task)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun DownloadTaskItem(task: DownloadTask) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 预览缩略图
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    val thumbnailUrl = if (task.mediaItem.type == MediaType.VIDEO) {
                        task.mediaItem.thumbnail.ifEmpty { task.mediaItem.url }
                    } else {
                        task.mediaItem.url
                    }

                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    if (task.mediaItem.type == MediaType.VIDEO) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = task.mediaItem.title.ifEmpty {
                            if (task.mediaItem.type == com.example.mediabuild.model.MediaType.VIDEO) "视频" else "图片"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = when (task.status) {
                            DownloadStatus.PENDING -> "等待中"
                            DownloadStatus.DOWNLOADING -> "下载中 ${task.progress}%"
                            DownloadStatus.COMPLETED -> "已完成"
                            DownloadStatus.FAILED -> "下载失败"
                        },
                        fontSize = 12.sp,
                        color = when (task.status) {
                            DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
                            DownloadStatus.FAILED -> Color(0xFFEF5350)
                            else -> Color(0xFF888888)
                        }
                    )
                }

                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        CircularProgressIndicator(
                            progress = { task.progress / 100f },
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                            color = Color(0xFF2196F3),
                            trackColor = Color(0xFF2A2A2A)
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    DownloadStatus.FAILED -> {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    else -> {}
                }
            }

            if (task.status == DownloadStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF2196F3),
                    trackColor = Color(0xFF2A2A2A)
                )
            }
        }
    }
}
