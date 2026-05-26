package com.example.mediabuild.model

data class MediaItem(
    val url: String,
    val type: MediaType,
    val title: String = "",
    val thumbnail: String = "",
    val quality: String = ""
)

enum class MediaType {
    VIDEO,
    IMAGE,
    IMAGE_SET
}

data class ParseResult(
    val success: Boolean,
    val items: List<MediaItem> = emptyList(),
    val authorName: String = "",
    val error: String = ""
)

data class DownloadTask(
    val id: String,
    val mediaItem: MediaItem,
    val status: DownloadStatus,
    val progress: Int = 0,
    val localPath: String = ""
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}
