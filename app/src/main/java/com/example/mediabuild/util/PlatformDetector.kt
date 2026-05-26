package com.example.mediabuild.util

enum class Platform(val packageName: String, val displayName: String) {
    DOUYIN("com.ss.android.ugc.aweme", "抖音"),
    DOUYIN_LITE("com.ss.android.ugc.aweme.lite", "抖音极速版"),
    XIAOHONGSHU("com.xingin.xhs", "小红书"),
    WEIBO("com.sina.weibo", "微博"),
    WEIBO_LITE("com.sina.weibo.lite", "微博极速版"),
    TIKTOK("com.zhiliaoapp.musically", "TikTok"),
    UNKNOWN("", "未知平台");

    companion object {
        fun fromPackage(packageName: String): Platform {
            return entries.find { it.packageName == packageName } ?: UNKNOWN
        }

        fun isSupported(packageName: String): Boolean {
            return fromPackage(packageName) != UNKNOWN
        }
    }
}
