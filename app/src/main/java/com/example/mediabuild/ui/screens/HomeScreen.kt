package com.example.mediabuild.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import com.example.mediabuild.model.MediaItem
import com.example.mediabuild.model.MediaType
import com.example.mediabuild.model.ParseResult
import com.example.mediabuild.parser.LinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var linkInput by remember { mutableStateOf("") }
    var isParsing by remember { mutableStateOf(false) }
    var parseResult by remember { mutableStateOf<ParseResult?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val downloadManager = remember { DownloadManager.getInstance(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "MediaBuild",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = "社交媒体媒体下载器",
            fontSize = 14.sp,
            color = Color(0xFF888888),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "链接解析",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("粘贴视频/图文链接", color = Color(0xFF666666)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF4CAF50)
                    ),
                    minLines = 2,
                    maxLines = 4
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (linkInput.isNotEmpty()) {
                                linkInput = ""
                            } else {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    linkInput = clip.getItemAt(0).text.toString()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (linkInput.isNotEmpty()) Color(0xFFEF5350) else Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(
                            if (linkInput.isNotEmpty()) Icons.Default.Close else Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (linkInput.isNotEmpty()) "清除" else "粘贴")
                    }

                    Button(
                        onClick = {
                            if (linkInput.isBlank()) {
                                Toast.makeText(context, "请输入链接", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isParsing = true
                            errorMsg = null
                            parseResult = null
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        LinkParser.parse(linkInput.trim())
                                    }
                                    parseResult = result
                                    if (!result.success) {
                                        errorMsg = result.error
                                    }
                                } catch (e: Exception) {
                                    errorMsg = "解析异常: ${e.message}"
                                } finally {
                                    isParsing = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isParsing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isParsing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("解析")
                    }
                }
            }
        }

        errorMsg?.let { msg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1F1F)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg,
                        color = Color(0xFFEF5350),
                        fontSize = 13.sp
                    )
                }
            }
        }

        parseResult?.let { result ->
            if (result.items.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "解析结果 (${result.items.size}个)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )

                            if (result.items.size > 1) {
                                Button(
                                    onClick = {
                                        val items = result.items.map { item ->
                                            MediaItem(
                                                url = item.url,
                                                type = item.type,
                                                title = item.title
                                            )
                                        }
                                        downloadManager.downloadAll(items)
                                        Toast.makeText(context, "开始下载 ${items.size} 个文件", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2196F3),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("全部下载", fontSize = 12.sp)
                                }
                            }
                        }

                        if (result.authorName.isNotEmpty()) {
                            Text(
                                text = "作者: ${result.authorName}",
                                fontSize = 13.sp,
                                color = Color(0xFF888888),
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )
                        }

                        result.items.forEachIndexed { index, item ->
                            ResultItem(
                                item = item,
                                index = index,
                                onDownload = {
                                    downloadManager.downloadMedia(item)
                                    Toast.makeText(context, "开始下载", Toast.LENGTH_SHORT).show()
                                }
                            )
                            if (index < result.items.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = Color(0xFF2A2A2A)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "使用说明",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val instructions = listOf(
                    "1. 开启无障碍服务后，自动检测屏幕上的视频/图片",
                    "2. 复制链接粘贴到上方输入框，点击解析",
                    "3. 支持抖音、小红书、微博链接",
                    "4. 视频支持原画质下载",
                    "5. 图文支持一键保存所有图片"
                )

                instructions.forEach { instruction ->
                    Text(
                        text = instruction,
                        fontSize = 13.sp,
                        color = Color(0xFFAAAAAA),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ResultItem(item: MediaItem, index: Int, onDownload: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 预览缩略图
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            val thumbnailUrl = if (item.type == MediaType.VIDEO) {
                // 视频用封面图作为预览（如果有）
                item.thumbnail.ifEmpty { item.url }
            } else {
                item.url
            }

            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            // 视频类型显示播放图标覆盖
            if (item.type == MediaType.VIDEO) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = "${if (item.type == MediaType.VIDEO) "视频" else "图片"} ${index + 1}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = item.url.take(50) + if (item.url.length > 50) "..." else "",
                fontSize = 11.sp,
                color = Color(0xFF666666),
                maxLines = 1
            )
        }

        IconButton(
            onClick = onDownload,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF4CAF50))
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "下载",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
