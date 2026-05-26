package com.example.mediabuild

import com.example.mediabuild.parser.LinkParser
import org.junit.Assert.*
import org.junit.Test

class LinkParserTest {

    // ========== URL 提取测试 ==========

    @Test
    fun `extractUrl - 纯链接直接返回`() {
        val url = "https://v.douyin.com/iRNBho6u/"
        assertEquals(url, LinkParser.extractUrl(url))
    }

    @Test
    fun `extractUrl - 从抖音分享文案提取链接`() {
        val input = "在抖音上看到这个视频 https://v.douyin.com/iRNBho6u/ 复制此链接打开抖音"
        val result = LinkParser.extractUrl(input)
        assertTrue("应该提取到链接，实际: $result", result.startsWith("https://"))
        assertTrue("应该包含douyin", result.contains("douyin.com"))
    }

    @Test
    fun `extractUrl - 从小红书分享文案提取链接`() {
        val input = "超级好看的风景！ #旅行 https://www.xiaohongshu.com/explore/abc123"
        val result = LinkParser.extractUrl(input)
        assertTrue("应该提取到链接，实际: $result", result.startsWith("https://"))
        assertTrue("应该包含xiaohongshu", result.contains("xiaohongshu.com"))
    }

    @Test
    fun `extractUrl - 从微博分享文案提取链接`() {
        val input = "分享一篇好文章 https://m.weibo.cn/detail/1234567890"
        val result = LinkParser.extractUrl(input)
        assertTrue("应该提取到链接，实际: $result", result.startsWith("https://"))
        assertTrue("应该包含weibo", result.contains("weibo.cn"))
    }

    @Test
    fun `extractUrl - 提取t短链`() {
        val input = "看看这个 https://t.cn/A6xxxxxx"
        val result = LinkParser.extractUrl(input)
        assertTrue("应该提取到链接", result.startsWith("https://t.cn"))
    }

    @Test
    fun `extractUrl - 没有链接返回原文`() {
        val input = "这是一段没有链接的文字"
        assertEquals(input, LinkParser.extractUrl(input))
    }

    @Test
    fun `extractAllUrls - 提取多个链接`() {
        val input = "看看 https://v.douyin.com/xxx 和 https://www.xiaohongshu.com/yyy"
        val urls = LinkParser.extractAllUrls(input)
        assertEquals(2, urls.size)
    }

    // ========== 平台检测测试 ==========

    @Test
    fun `detectPlatform - 抖音链接`() {
        assertEquals("douyin", LinkParser.detectPlatform("https://v.douyin.com/iRNBho6u/"))
        assertEquals("douyin", LinkParser.detectPlatform("https://www.douyin.com/video/123456"))
        assertEquals("douyin", LinkParser.detectPlatform("https://www.iesdouyin.com/share/video/123"))
    }

    @Test
    fun `detectPlatform - 小红书链接`() {
        assertEquals("xiaohongshu", LinkParser.detectPlatform("https://www.xiaohongshu.com/explore/abc"))
        assertEquals("xiaohongshu", LinkParser.detectPlatform("https://xhslink.com/abc"))
    }

    @Test
    fun `detectPlatform - 微博链接`() {
        assertEquals("weibo", LinkParser.detectPlatform("https://m.weibo.cn/detail/12345"))
        assertEquals("weibo", LinkParser.detectPlatform("https://weibo.com/12345/abc"))
    }

    @Test
    fun `detectPlatform - 不支持的链接`() {
        assertEquals("unknown", LinkParser.detectPlatform("https://www.google.com"))
    }

    // ========== 分享文案综合测试 ==========

    @Test
    fun `综合测试 - 抖音分享文案`() {
        val input = "6.45 Lhb:/ 复制打开抖音，看看【xxx的作品】 https://v.douyin.com/iRNBho6u/"
        val url = LinkParser.extractUrl(input)
        assertEquals("douyin", LinkParser.detectPlatform(url))
    }

    @Test
    fun `综合测试 - 小红书分享文案`() {
        val input = "超级好看的风景 #旅行 vlog https://www.xiaohongshu.com/explore/654321abcdef"
        val url = LinkParser.extractUrl(input)
        assertEquals("xiaohongshu", LinkParser.detectPlatform(url))
    }

    @Test
    fun `综合测试 - 微博分享文案`() {
        val input = "分享微博文章 https://m.weibo.cn/detail/4912345678901"
        val url = LinkParser.extractUrl(input)
        assertEquals("weibo", LinkParser.detectPlatform(url))
    }

    @Test
    fun `综合测试 - 带中文标点的分享文案`() {
        val input = "好棒！https://v.douyin.com/iRNBho6u/，快看看"
        val url = LinkParser.extractUrl(input)
        assertTrue("应该提取到链接", url.startsWith("https://"))
    }

    // ========== URL 格式测试 ==========

    @Test
    fun `extractUrl - 处理带括号的URL`() {
        val input = "看看这个(https://v.douyin.com/xxx)"
        val url = LinkParser.extractUrl(input)
        assertTrue("应该提取到链接", url.startsWith("https://"))
    }

    @Test
    fun `extractUrl - 处理小红书短链`() {
        val input = "http://xhslink.com/o/AuiGnVDkYHt"
        val url = LinkParser.extractUrl(input)
        assertEquals(input, url)
        assertEquals("xiaohongshu", LinkParser.detectPlatform(url))
    }

    @Test
    fun `extractUrl - 从分享文案提取小红书短链`() {
        val input = "快来看看这个笔记 http://xhslink.com/o/AuiGnVDkYHt 复制链接"
        val url = LinkParser.extractUrl(input)
        assertTrue("应该提取到链接", url.contains("xhslink.com"))
        assertEquals("xiaohongshu", LinkParser.detectPlatform(url))
    }

    @Test
    fun `extractUrl - 处理带引号的URL`() {
        val input = "\"https://v.douyin.com/xxx\""
        val url = LinkParser.extractUrl(input)
        assertTrue("应该提取到链接", url.startsWith("https://"))
    }

    @Test
    fun `extractUrl - 处理短链接`() {
        val input = "https://t.cn/A6xxxxxx"
        val url = LinkParser.extractUrl(input)
        assertEquals(input, url)
    }
}
