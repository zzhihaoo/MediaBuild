package com.example.mediabuild.parser

import com.example.mediabuild.model.MediaType
import com.example.mediabuild.model.ParseResult
import com.example.mediabuild.model.MediaItem
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.jsoup.Jsoup
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object LinkParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    // 从用户输入中提取有效 URL
    fun extractUrl(input: String): String {
        val trimmed = input.trim()

        // 如果已经是合法 URL，直接返回
        if (trimmed.matches(Regex("^https?://.*"))) {
            return trimmed
        }

        // 从文本中提取 URL
        val urlPattern = Pattern.compile(
            "(https?://[\\w\\-]+(\\.[\\w\\-]+)+[/\\w\\-.~:/?#@!$&'()*+,;=%]*)"
        )
        val matcher = urlPattern.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(0) ?: trimmed
        }

        return trimmed
    }

    // 将文本中的所有链接提取出来
    fun extractAllUrls(input: String): List<String> {
        val urls = mutableListOf<String>()
        val urlPattern = Pattern.compile(
            "(https?://[\\w\\-]+(\\.[\\w\\-]+)+[/\\w\\-.~:/?#@!$&'()*+,;=%]*)"
        )
        val matcher = urlPattern.matcher(input)
        while (matcher.find()) {
            val url = matcher.group(0) ?: continue
            if (!urls.contains(url)) {
                urls.add(url)
            }
        }
        return urls
    }

    fun detectPlatform(url: String): String {
        val lower = url.lowercase()
        return when {
            "douyin.com" in lower || "iesdouyin.com" in lower -> "douyin"
            "xiaohongshu.com" in lower || "xhslink.com" in lower || "xhsurl.com" in lower -> "xiaohongshu"
            "weibo.com" in lower || "weibo.cn" in lower || "m.weibo.cn" in lower -> "weibo"
            "tiktok.com" in lower -> "tiktok"
            else -> "unknown"
        }
    }

    fun isProfileUrl(url: String): Boolean {
        val lower = url.lowercase()
        return when {
            "douyin.com" in lower -> "/user/" in lower || "sec_uid=" in lower
            "xiaohongshu.com" in lower -> "/user/profile/" in lower
            "weibo.com" in lower || "weibo.cn" in lower -> "/u/" in lower || "/profile" in lower
            else -> false
        }
    }

    suspend fun parse(input: String): ParseResult {
        return try {
            val url = extractUrl(input)
            val platform = detectPlatform(url)
            when (platform) {
                "douyin" -> parseDouyin(url)
                "xiaohongshu" -> parseXiaohongshu(url)
                "weibo" -> parseWeibo(url)
                "tiktok" -> parseTiktok(url)
                else -> ParseResult(false, error = "不支持的链接，请输入抖音/小红书/微博链接")
            }
        } catch (e: Exception) {
            ParseResult(false, error = "解析失败: ${e.message}")
        }
    }

    private fun parseDouyin(url: String): ParseResult {
        val resolvedUrl = resolveShortUrl(url)
        return try {
            val doc = Jsoup.connect(resolvedUrl)
                .userAgent(USER_AGENT)
                .header("Referer", "https://www.douyin.com/")
                .timeout(15000)
                .get()

            val scripts = doc.select("script")
            for (script in scripts) {
                val html = script.html()
                if (html.contains("RENDER_DATA") || html.contains("window._ROUTER_DATA")) {
                    val result = parseDouyinRenderData(html)
                    if (result.success) return result
                }
            }

            // 尝试从 meta 标签获取
            val metaVideo = doc.select("meta[property=og:video]")
            if (metaVideo.isNotEmpty()) {
                val videoUrl = metaVideo.attr("content")
                if (videoUrl.isNotEmpty() && videoUrl.startsWith("http")) {
                    return ParseResult(
                        success = true,
                        items = listOf(
                            MediaItem(url = videoUrl, type = MediaType.VIDEO, title = doc.title())
                        )
                    )
                }
            }

            val metaImage = doc.select("meta[property=og:image]")
            if (metaImage.isNotEmpty()) {
                val imgUrl = metaImage.attr("content")
                if (imgUrl.isNotEmpty() && imgUrl.startsWith("http")) {
                    return ParseResult(
                        success = true,
                        items = listOf(
                            MediaItem(url = imgUrl, type = MediaType.IMAGE, title = doc.title())
                        )
                    )
                }
            }

            ParseResult(false, error = "无法解析此抖音链接，请确保链接完整有效")
        } catch (e: Exception) {
            ParseResult(false, error = "解析抖音链接失败: ${e.message}")
        }
    }

    private fun parseDouyinRenderData(html: String): ParseResult {
        return try {
            // 尝试 RENDER_DATA
            if (html.contains("RENDER_DATA")) {
                val raw = html.substringAfter("RENDER_DATA=").substringBefore("</script>").trim()
                val decoded = java.net.URLDecoder.decode(raw, "UTF-8")
                val json = gson.fromJson(decoded, JsonObject::class.java)
                return extractDouyinVideo(json)
            }

            // 尝试 _ROUTER_DATA
            if (html.contains("window._ROUTER_DATA")) {
                val raw = html.substringAfter("window._ROUTER_DATA = ").substringBefore("</script>").trim()
                if (raw.isNotEmpty()) {
                    val json = gson.fromJson(raw, JsonObject::class.java)
                    return extractDouyinVideo(json)
                }
            }

            ParseResult(false, error = "未能提取抖音视频信息")
        } catch (e: Exception) {
            ParseResult(false, error = "解析失败: ${e.message}")
        }
    }

    private fun extractDouyinVideo(json: JsonObject): ParseResult {
        val items = mutableListOf<MediaItem>()

        // 尝试 video_list
        val videoList = json.getAsJsonArray("video_list")
        if (videoList != null) {
            for (element in videoList) {
                val obj = element.asJsonObject
                val playAddr = obj.getAsJsonObject("play_addr")
                if (playAddr != null) {
                    val urlList = playAddr.getAsJsonArray("url_list")
                    if (urlList != null && urlList.size() > 0) {
                        val videoUrl = urlList[0].asString
                        if (videoUrl.startsWith("http")) {
                            items.add(
                                MediaItem(
                                    url = videoUrl,
                                    type = MediaType.VIDEO,
                                    title = json.get("desc")?.asString ?: ""
                                )
                            )
                        }
                    }
                }
            }
        }

        // 尝试嵌套结构
        val detail = json.getAsJsonObject("aweme")?.getAsJsonObject("detail")
        if (detail != null) {
            val video = detail.getAsJsonObject("video")
            if (video != null) {
                val playAddr = video.getAsJsonObject("play_addr")
                if (playAddr != null) {
                    val urlList = playAddr.getAsJsonArray("url_list")
                    if (urlList != null && urlList.size() > 0) {
                        val videoUrl = urlList[0].asString
                        if (videoUrl.startsWith("http")) {
                            items.add(
                                MediaItem(
                                    url = videoUrl,
                                    type = MediaType.VIDEO,
                                    title = detail.get("desc")?.asString ?: ""
                                )
                            )
                        }
                    }
                }
            }
        }

        return if (items.isNotEmpty()) ParseResult(success = true, items = items)
        else ParseResult(false, error = "未能提取视频信息")
    }

    private fun parseXiaohongshu(url: String): ParseResult {
        // 先用 OkHttp 解析短链重定向
        val resolvedUrl = resolveShortUrlWithOkHttp(url)
        android.util.Log.d("LinkParser", "原始URL: $url, 解析后: $resolvedUrl")

        // 策略1: 用 OkHttp 直接获取页面内容，再用 Jsoup 解析
        val result1 = parseXiaohongshuWithOkHttp(resolvedUrl)
        if (result1.success) return result1

        // 策略2: 用 Jsoup 直接连接
        val result2 = parseXiaohongshuWithJsoup(resolvedUrl)
        if (result2.success) return result2

        // 策略3: 尝试从 URL 中提取 noteId，用 API 获取
        val noteId = extractNoteIdFromUrl(resolvedUrl)
        if (noteId != null) {
            val result3 = parseXiaohongshuByNoteId(noteId)
            if (result3.success) return result3
        }

        return ParseResult(false, error = "无法解析此小红书链接，请复制完整的分享链接后重试")
    }

    private fun resolveShortUrlWithOkHttp(url: String): String {
        val extracted = extractUrl(url)
        val httpsUrl = if (extracted.startsWith("http://")) {
            extracted.replace("http://", "https://")
        } else {
            extracted
        }

        return try {
            val request = Request.Builder()
                .url(httpsUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val response = client.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            val contentType = response.header("Content-Type") ?: ""
            val body = response.body?.string() ?: ""
            response.close()

            // 如果响应是 HTML（不是标准重定向），从 HTML 中提取真实 URL
            if (body.contains("<a href=") && body.contains("xiaohongshu.com")) {
                val linkPattern = Regex("""<a\s+href="(https://www\.xiaohongshu\.com/[^"]+)"""")
                val match = linkPattern.find(body)
                if (match != null) {
                    val realUrl = match.groupValues[1]
                        .replace("&amp;", "&")
                    android.util.Log.d("LinkParser", "从HTML提取URL: $realUrl")
                    return realUrl
                }
            }

            android.util.Log.d("LinkParser", "短链解析: $httpsUrl -> $finalUrl")
            finalUrl
        } catch (e: Exception) {
            android.util.Log.e("LinkParser", "短链解析失败: ${e.message}")
            httpsUrl
        }
    }

    private fun parseXiaohongshuWithOkHttp(url: String): ParseResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://www.xiaohongshu.com/")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return ParseResult(false, error = "页面内容为空")
            response.close()

            android.util.Log.d("LinkParser", "页面长度: ${html.length}")
            android.util.Log.d("LinkParser", "包含INITIAL_STATE: ${html.contains("__INITIAL_STATE__")}")
            android.util.Log.d("LinkParser", "包含noteDetailMap: ${html.contains("noteDetailMap")}")

            // 策略1: 从 __INITIAL_STATE__ 解析
            if (html.contains("__INITIAL_STATE__")) {
                val stateStart = html.indexOf("__INITIAL_STATE__")
                val eqIdx = html.indexOf("=", stateStart)
                val scriptEnd = html.indexOf("</script>", eqIdx)
                if (eqIdx >= 0 && scriptEnd >= 0) {
                    var jsonStr = html.substring(eqIdx + 1, scriptEnd).trim()
                    if (jsonStr.startsWith(";")) jsonStr = jsonStr.substring(1).trim()
                    if (jsonStr.endsWith(";")) jsonStr = jsonStr.substring(0, jsonStr.length - 1).trim()
                    // 用括号匹配找到完整 JSON
                    val fullJson = extractBalancedJson(jsonStr)
                    if (fullJson != null) {
                        val stateStr = fullJson.replace("undefined", "null")
                        android.util.Log.d("LinkParser", "INITIAL_STATE长度: ${stateStr.length}")
                        val result = parseXiaohongshuStateFromJson(stateStr)
                        if (result.success) return result
                    }
                }
            }

            // 策略2: 从 meta 标签解析
            val doc = org.jsoup.Jsoup.parse(html)
            val items = extractMediaFromMeta(doc)
            if (items.isNotEmpty()) {
                return ParseResult(success = true, items = items)
            }

            // 策略3: 先检查视频 (masterUrl 可能含 / 编码)，优先于图片
            if (html.contains("masterUrl")) {
                val decodedHtml = html.replace("\\u002F", "/")
                val videoPattern = Regex("\"masterUrl\"\\s*:\\s*\"(https?://[^\"]+)\"")
                val videoMatch = videoPattern.find(decodedHtml)
                if (videoMatch != null) {
                    val videoUrl = videoMatch.groupValues[1]
                    val title = extractTitleFromHtml(html)
                    android.util.Log.d("LinkParser", "从masterUrl提取视频: $videoUrl")
                    // 同时提取图片（如果有）
                    val images = mutableListOf<MediaItem>()
                    val jsonPattern = Regex("\"imageList\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
                    val jsonMatch = jsonPattern.find(decodedHtml)
                    if (jsonMatch != null) {
                        val imgResult = parseXiaohongshuImageList(jsonMatch.groupValues[1])
                        if (imgResult.success) images.addAll(imgResult.items ?: emptyList())
                    }
                    val allItems = mutableListOf(MediaItem(url = videoUrl, type = MediaType.VIDEO, title = title))
                    allItems.addAll(images)
                    return ParseResult(success = true, items = allItems)
                }
            }

            // 策略4: 从 imageList 解析图片
            val jsonPattern = Regex("\"imageList\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonPattern.find(html)
            if (jsonMatch != null) {
                val result = parseXiaohongshuImageList(jsonMatch.groupValues[1])
                if (result.success) return result
            }

            ParseResult(false, error = "无法解析此小红书链接，请复制完整的分享链接后重试")
        } catch (e: Exception) {
            android.util.Log.e("LinkParser", "OkHttp解析失败: ${e.message}")
            ParseResult(false, error = "解析失败: ${e.message}")
        }
    }

    private fun parseXiaohongshuWithJsoup(url: String): ParseResult {
        return try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://www.xiaohongshu.com/")
                .maxBodySize(10 * 1024 * 1024)
                .timeout(15000)
                .get()

            val items = extractMediaFromMeta(doc)
            if (items.isNotEmpty()) {
                return ParseResult(success = true, items = items)
            }

            // 尝试从 script 标签解析
            val scripts = doc.select("script")
            for (script in scripts) {
                val html = script.html()
                if (html.contains("__INITIAL_STATE__")) {
                    val result = parseXiaohongshuState(html)
                    if (result.success) return result
                }
            }

            ParseResult(false, error = "无法解析此小红书链接")
        } catch (e: Exception) {
            android.util.Log.e("LinkParser", "Jsoup解析失败: ${e.message}")
            ParseResult(false, error = "Jsoup解析失败: ${e.message}")
        }
    }

    private fun extractMediaFromMeta(doc: org.jsoup.nodes.Document): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val title = doc.title()

        doc.select("meta[property=og:video]").forEach { el ->
            val u = el.attr("content")
            if (u.isNotEmpty() && u.startsWith("http")) {
                items.add(MediaItem(url = u, type = MediaType.VIDEO, title = title))
            }
        }

        doc.select("meta[property=og:image]").forEach { el ->
            val u = el.attr("content")
            if (u.isNotEmpty() && u.startsWith("http") && !items.any { it.url == u }) {
                items.add(MediaItem(url = u, type = MediaType.IMAGE, title = title))
            }
        }

        doc.select("meta[name=twitter:image]").forEach { el ->
            val u = el.attr("content")
            if (u.isNotEmpty() && u.startsWith("http") && !items.any { it.url == u }) {
                items.add(MediaItem(url = u, type = MediaType.IMAGE, title = title))
            }
        }

        doc.select("meta[name=twitter:player:stream]").forEach { el ->
            val u = el.attr("content")
            if (u.isNotEmpty() && u.startsWith("http") && !items.any { it.url == u }) {
                items.add(MediaItem(url = u, type = MediaType.VIDEO, title = title))
            }
        }

        return items
    }

    private fun parseXiaohongshuStateFromJson(stateStr: String): ParseResult {
        return try {
            val json = gson.fromJson(stateStr, JsonObject::class.java)
            val noteData = json.getAsJsonObject("note")?.getAsJsonObject("noteDetailMap")
                ?: json.getAsJsonObject("noteData")

            if (noteData != null) {
                val firstKey = noteData.keySet().firstOrNull()
                if (firstKey != null) {
                    val noteObj = noteData.getAsJsonObject(firstKey)
                    val note = noteObj?.getAsJsonObject("note") ?: noteObj
                    if (note != null) {
                        return extractMediaFromNote(note)
                    }
                }
            }

            ParseResult(false, error = "未能从JSON中提取内容")
        } catch (e: Exception) {
            android.util.Log.e("LinkParser", "JSON解析失败: ${e.message}")
            ParseResult(false, error = "JSON解析失败: ${e.message}")
        }
    }

    private fun extractMediaFromNote(note: JsonObject): ParseResult {
        val items = mutableListOf<MediaItem>()
        val title = note.get("title")?.asString ?: ""
        val desc = note.get("desc")?.asString ?: ""

        // 图片列表
        val imageList = note.getAsJsonArray("imageList")
        if (imageList != null) {
            for (img in imageList) {
                val imgObj = img.asJsonObject
                val imgUrl = imgObj.get("urlDefault")?.asString
                    ?: imgObj.get("url")?.asString
                    ?: imgObj.get("originUrl")?.asString
                    ?: imgObj.get("infoList")?.let { infoList ->
                        if (infoList.isJsonArray && infoList.asJsonArray.size() > 0) {
                            infoList.asJsonArray[0].asJsonObject.get("url")?.asString
                        } else null
                    }
                if (imgUrl != null && imgUrl.startsWith("http")) {
                    items.add(MediaItem(url = imgUrl, type = MediaType.IMAGE, title = title.ifEmpty { desc }))
                }
            }
        }

        // 视频
        val video = note.getAsJsonObject("video")
        if (video != null) {
            val media = video.getAsJsonArray("media")
            if (media != null && media.size() > 0) {
                val videoObj = media[0].asJsonObject
                val stream = videoObj.getAsJsonArray("stream")
                if (stream != null && stream.size() > 0) {
                    val bestStream = stream[stream.size() - 1].asJsonObject
                    val videoList = bestStream.getAsJsonArray("videoControllers")
                    if (videoList != null && videoList.size() > 0) {
                        val videoUrl = videoList[0].asJsonObject.get("masterUrl")?.asString
                        if (videoUrl != null && videoUrl.startsWith("http")) {
                            items.add(MediaItem(url = videoUrl, type = MediaType.VIDEO, title = title.ifEmpty { desc }))
                        }
                    }
                }
            }
        }

        val authorName = note.getAsJsonObject("user")?.get("nickname")?.asString ?: ""
        return if (items.isNotEmpty()) ParseResult(success = true, items = items, authorName = authorName)
        else ParseResult(false, error = "未能提取媒体内容")
    }

    private fun parseXiaohongshuImageList(imageListStr: String): ParseResult {
        return try {
            val items = mutableListOf<MediaItem>()
            val decoded = imageListStr.replace("\\u002F", "/")
            val urlPattern = Regex("\"url(?:Default)?\"\\s*:\\s*\"(https?://[^\"]+)\"")
            urlPattern.findAll(decoded).forEach { match ->
                val url = match.groupValues[1]
                if (!items.any { it.url == url }) {
                    items.add(MediaItem(url = url, type = MediaType.IMAGE))
                }
            }
            if (items.isNotEmpty()) ParseResult(success = true, items = items)
            else ParseResult(false, error = "未能提取图片列表")
        } catch (e: Exception) {
            ParseResult(false, error = "解析图片列表失败: ${e.message}")
        }
    }

    private fun extractNoteIdFromUrl(url: String): String? {
        // https://www.xiaohongshu.com/explore/abc123 -> abc123
        val explorePattern = Regex("/explore/([a-f0-9]+)")
        explorePattern.find(url)?.let { return it.groupValues[1] }

        // https://www.xiaohongshu.com/discovery/item/abc123 -> abc123
        val itemPattern = Regex("/item/([a-f0-9]+)")
        itemPattern.find(url)?.let { return it.groupValues[1] }

        return null
    }

    private fun parseXiaohongshuByNoteId(noteId: String): ParseResult {
        return try {
            val apiUrl = "https://edith.xiaohongshu.com/api/sns/web/v1/feed"
            val jsonBody = """{"source_note_id":"$noteId"}"""

            val request = Request.Builder()
                .url(apiUrl)
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.xiaohongshu.com/")
                .header("Origin", "https://www.xiaohongshu.com")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return ParseResult(false, error = "API响应为空")
            response.close()

            val json = gson.fromJson(body, JsonObject::class.java)
            val items = json.getAsJsonArray("items")
            if (items != null && items.size() > 0) {
                val note = items[0].asJsonObject.getAsJsonObject("note_card")
                if (note != null) {
                    return extractMediaFromNote(note)
                }
            }

            ParseResult(false, error = "API解析失败")
        } catch (e: Exception) {
            android.util.Log.e("LinkParser", "API请求失败: ${e.message}")
            ParseResult(false, error = "API请求失败: ${e.message}")
        }
    }

    private fun extractTitleFromHtml(html: String): String {
        val titlePattern = Regex("<title>(.*?)</title>")
        val match = titlePattern.find(html)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun parseXiaohongshuState(html: String): ParseResult {
        return try {
            val stateStr = html.substringAfter("__INITIAL_STATE__=").substringBefore("</script>")
                .replace("undefined", "null")
            val json = gson.fromJson(stateStr, JsonObject::class.java)
            val noteData = json.getAsJsonObject("note")?.getAsJsonObject("noteDetailMap")

            if (noteData != null) {
                val firstKey = noteData.keySet().firstOrNull()
                if (firstKey != null) {
                    val note = noteData.getAsJsonObject(firstKey)?.getAsJsonObject("note")
                    if (note != null) {
                        val items = mutableListOf<MediaItem>()
                        val title = note.get("title")?.asString ?: ""
                        val desc = note.get("desc")?.asString ?: ""

                        // 图片列表
                        val imageList = note.getAsJsonArray("imageList")
                        if (imageList != null) {
                            for (img in imageList) {
                                val imgObj = img.asJsonObject
                                val imgUrl = imgObj.get("urlDefault")?.asString
                                    ?: imgObj.get("url")?.asString
                                    ?: imgObj.get("originUrl")?.asString
                                if (imgUrl != null && imgUrl.startsWith("http")) {
                                    items.add(
                                        MediaItem(
                                            url = imgUrl,
                                            type = MediaType.IMAGE,
                                            title = title.ifEmpty { desc }
                                        )
                                    )
                                }
                            }
                        }

                        // 视频
                        val video = note.getAsJsonObject("video")
                        if (video != null) {
                            val media = video.getAsJsonArray("media")
                            if (media != null && media.size() > 0) {
                                val videoObj = media[0].asJsonObject
                                val stream = videoObj.getAsJsonArray("stream")
                                if (stream != null && stream.size() > 0) {
                                    // 取最高画质
                                    val bestStream = stream[stream.size() - 1].asJsonObject
                                    val videoList = bestStream.getAsJsonArray("videoControllers")
                                    if (videoList != null && videoList.size() > 0) {
                                        val videoUrl = videoList[0].asJsonObject
                                            .get("masterUrl")?.asString
                                        if (videoUrl != null && videoUrl.startsWith("http")) {
                                            items.add(
                                                MediaItem(
                                                    url = videoUrl,
                                                    type = MediaType.VIDEO,
                                                    title = title.ifEmpty { desc }
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (items.isNotEmpty()) {
                            return ParseResult(
                                success = true,
                                items = items,
                                authorName = note.getAsJsonObject("user")?.get("nickname")?.asString ?: ""
                            )
                        }
                    }
                }
            }

            ParseResult(false, error = "未能提取小红书内容")
        } catch (e: Exception) {
            ParseResult(false, error = "解析失败: ${e.message}")
        }
    }

    private fun parseWeibo(url: String): ParseResult {
        val resolvedUrl = resolveShortUrl(url)
        return try {
            val doc = Jsoup.connect(resolvedUrl)
                .userAgent(USER_AGENT)
                .header("Referer", "https://m.weibo.cn/")
                .timeout(15000)
                .get()

            val scripts = doc.select("script")
            for (script in scripts) {
                val html = script.html()
                if (html.contains("\$render_data") || html.contains("var \$render_data")) {
                    val result = parseWeiboRenderData(html)
                    if (result.success) return result
                }
            }

            // 从 meta 和页面内容获取
            val items = mutableListOf<MediaItem>()
            val title = doc.title()

            doc.select("meta[property=og:video]").forEach { el ->
                val u = el.attr("content")
                if (u.isNotEmpty() && u.startsWith("http")) {
                    items.add(MediaItem(url = u, type = MediaType.VIDEO, title = title))
                }
            }
            doc.select("meta[property=og:image]").forEach { el ->
                val u = el.attr("content")
                if (u.isNotEmpty() && u.startsWith("http")) {
                    items.add(MediaItem(url = u, type = MediaType.IMAGE, title = title))
                }
            }

            // 从页面 HTML 中用正则提取视频和图片
            val pageHtml = doc.html()
            val videoPattern = Regex("video_src.*?\"(https?://[^\"]+)\"")
            videoPattern.findAll(pageHtml).forEach { match ->
                val videoUrl = match.groupValues[1]
                if (!items.any { it.url == videoUrl }) {
                    items.add(MediaItem(url = videoUrl, type = MediaType.VIDEO, title = title))
                }
            }

            val imgPattern = Regex("\"url\":\\s*\"(https?://wx[^\"]+\\.(?:jpg|png|jpeg|webp)[^\"]*)\"")
            imgPattern.findAll(pageHtml).forEach { match ->
                val imgUrl = match.groupValues[1]
                if (!items.any { it.url == imgUrl }) {
                    items.add(MediaItem(url = imgUrl, type = MediaType.IMAGE, title = title))
                }
            }

            if (items.isNotEmpty()) ParseResult(success = true, items = items)
            else ParseResult(false, error = "无法解析此微博链接，请确保链接完整有效")
        } catch (e: Exception) {
            ParseResult(false, error = "解析微博链接失败: ${e.message}")
        }
    }

    private fun parseWeiboRenderData(html: String): ParseResult {
        return try {
            val renderStr = html.substringAfter("[").substringBeforeLast("]")
            val jsonArr = gson.fromJson("[$renderStr]", com.google.gson.JsonArray::class.java)

            val items = mutableListOf<MediaItem>()
            for (element in jsonArr) {
                if (element.isJsonObject) {
                    val data = element.asJsonObject.getAsJsonObject("data")
                    if (data != null) {
                        val status = data.getAsJsonObject("status")
                        if (status != null) {
                            // 图片
                            val pics = status.getAsJsonArray("pics")
                            if (pics != null) {
                                for (pic in pics) {
                                    val picObj = pic.asJsonObject
                                    val large = picObj.getAsJsonObject("large")
                                    val imgUrl = large?.get("url")?.asString
                                        ?: picObj.get("url")?.asString
                                    if (imgUrl != null && imgUrl.startsWith("http")) {
                                        items.add(
                                            MediaItem(
                                                url = imgUrl,
                                                type = MediaType.IMAGE,
                                                title = status.get("text_raw")?.asString ?: ""
                                            )
                                        )
                                    }
                                }
                            }

                            // 视频
                            val pageInfo = status.getAsJsonObject("page_info")
                            if (pageInfo?.get("type")?.asString == "video") {
                                val urls = pageInfo.getAsJsonArray("urls")
                                if (urls != null && urls.size() > 0) {
                                    val videoUrl = urls[0]?.asString
                                    if (videoUrl != null && videoUrl.startsWith("http")) {
                                        items.add(
                                            MediaItem(
                                                url = videoUrl,
                                                type = MediaType.VIDEO,
                                                title = status.get("text_raw")?.asString ?: ""
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (items.isNotEmpty()) ParseResult(success = true, items = items)
            else ParseResult(false, error = "未能提取微博内容")
        } catch (e: Exception) {
            ParseResult(false, error = "解析失败: ${e.message}")
        }
    }

    private fun parseTiktok(url: String): ParseResult {
        val resolvedUrl = resolveShortUrl(url)
        return try {
            val doc = Jsoup.connect(resolvedUrl)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .get()

            val scripts = doc.select("script")
            for (script in scripts) {
                val html = script.html()
                if (html.contains("SIGI_STATE") || html.contains("_ROUTER_DATA")) {
                    val raw = html.substringAfter("=").substringBefore(";").trim()
                    if (raw.isNotEmpty()) {
                        val json = gson.fromJson(raw, JsonObject::class.java)
                        val items = mutableListOf<MediaItem>()

                        val detail = json.getAsJsonObject("ItemModule")
                            ?.getAsJsonObject("items")
                            ?.let { items_ ->
                                if (items_.isJsonArray) items_.asJsonArray?.get(0)?.asJsonObject
                                else null
                            }

                        if (detail != null) {
                            val video = detail.getAsJsonObject("video")
                            if (video != null) {
                                val playAddr = video.get("playAddr")?.asString
                                    ?: video.get("downloadAddr")?.asString
                                if (playAddr != null && playAddr.startsWith("http")) {
                                    items.add(
                                        MediaItem(
                                            url = playAddr,
                                            type = MediaType.VIDEO,
                                            title = detail.get("desc")?.asString ?: ""
                                        )
                                    )
                                }
                            }
                        }

                        if (items.isNotEmpty()) return ParseResult(success = true, items = items)
                    }
                }
            }

            ParseResult(false, error = "无法解析此TikTok链接")
        } catch (e: Exception) {
            ParseResult(false, error = "解析TikTok链接失败: ${e.message}")
        }
    }

    private fun resolveShortUrl(url: String): String {
        val extracted = extractUrl(url)
        if (!extracted.startsWith("http")) return extracted

        // 尝试将 HTTP 转为 HTTPS
        val httpsUrl = if (extracted.startsWith("http://")) {
            extracted.replace("http://", "https://")
        } else {
            extracted
        }

        return try {
            val request = Request.Builder()
                .url(httpsUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val response = client.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            response.close()
            finalUrl
        } catch (e: Exception) {
            // 如果 HTTPS 失败，尝试 HTTP
            if (httpsUrl != extracted) {
                try {
                    val request = Request.Builder()
                        .url(extracted)
                        .header("User-Agent", USER_AGENT)
                        .build()
                    val response = client.newCall(request).execute()
                    val finalUrl = response.request.url.toString()
                    response.close()
                    finalUrl
                } catch (e2: Exception) {
                    httpsUrl
                }
            } else {
                httpsUrl
            }
        }
    }

    // 用括号匹配提取完整 JSON 字符串
    private fun extractBalancedJson(s: String): String? {
        if (s.isEmpty() || s[0] != '{') return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in s.indices) {
            val c = s[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\' && inString) {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (inString) continue
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return s.substring(0, i + 1)
            }
        }
        return null
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
