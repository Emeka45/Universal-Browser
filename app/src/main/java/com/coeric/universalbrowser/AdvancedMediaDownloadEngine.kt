package com.coeric.universalbrowser

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import kotlin.math.min

/** Direct and non-DRM HLS/DASH downloader. Streaming resources are discovered by the media WebExtension. */
object AdvancedMediaDownloadEngine {
    data class Result(val fileName: String, val bytes: Long, val kind: String)
    private data class Output(val uri: Uri?, val file: File?, val stream: java.io.OutputStream)
    private data class Variant(val url: String, val bandwidth: Long, val codecs: String = "")
    private data class Segment(val url: String, val rangeStart: Long? = null, val rangeLength: Long? = null)

    fun isSupported(url: String): Boolean {
        if (url.isBlank() || url.startsWith("data:", true) || url.startsWith("blob:", true)) return false
        val path = url.substringBefore('?').lowercase(Locale.US)
        return listOf(".mp4", ".webm", ".mov", ".m4v", ".3gp", ".mkv", ".mp3", ".m4a", ".ogg", ".oga", ".m3u8", ".mpd").any(path::endsWith)
    }

    fun enqueue(context: Context, url: String, title: String, referer: String, onStarted: (String) -> Unit, onFinished: (Result) -> Unit, onError: (String) -> Unit) {
        if (!isSupported(url)) { onError("Unsupported media URL"); return }
        Thread {
            try {
                val path = url.substringBefore('?').lowercase(Locale.US)
                when {
                    path.endsWith(".m3u8") -> { onStarted("HLS stream"); onFinished(downloadHls(context, url, title, referer)) }
                    path.endsWith(".mpd") -> { onStarted("DASH stream"); onFinished(downloadDash(context, url, title, referer)) }
                    else -> { onStarted("Direct media"); enqueueDirect(context, url, title, referer) }
                }
            } catch (t: Throwable) { onError(t.message ?: "Media download failed") }
        }.start()
    }

    private fun enqueueDirect(context: Context, url: String, title: String, referer: String) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url)).setTitle(title.ifBlank { "Universal media" })
            .setDescription("Downloaded by Universal Browser")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true).setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFileName(url, title))
        CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let { request.addRequestHeader("Cookie", it) }
        if (referer.isNotBlank()) request.addRequestHeader("Referer", referer)
        manager.enqueue(request)
    }

    private fun downloadHls(context: Context, sourceUrl: String, title: String, referer: String): Result {
        var playlistUrl = sourceUrl
        var playlist = fetchText(playlistUrl, referer)
        if (playlist.contains("#EXT-X-STREAM-INF", true)) {
            val variants = parseMasterVariants(playlistUrl, playlist)
            if (variants.isEmpty()) throw IllegalArgumentException("HLS master playlist contains no variants")
            val video = variants.maxByOrNull { scoreVariant(it) }!!
            playlistUrl = video.url
            playlist = fetchText(playlistUrl, referer)
        }
        if (playlist.contains("#EXT-X-KEY", true) && !Regex("METHOD=NONE", RegexOption.IGNORE_CASE).containsMatchIn(playlist)) throw IllegalArgumentException("Encrypted/DRM HLS streams are not supported")
        val segments = parseHlsSegments(playlistUrl, playlist)
        if (segments.isEmpty()) throw IllegalArgumentException("HLS playlist has no media segments")
        val extension = if (playlist.contains("#EXT-X-MAP", true) || segments.any { it.url.substringBefore('?').endsWith(".m4s", true) }) "mp4" else "ts"
        val outputName = safeFileName(sourceUrl, title).substringBeforeLast('.') + "." + extension
        val output = beginOutput(context, outputName, if (extension == "mp4") "video/mp4" else "video/mp2t")
        var total = 0L
        try {
            BufferedOutputStream(output.stream).use { out -> for (segment in segments) { val bytes = fetchBytes(segment.url, referer, segment.rangeStart, segment.rangeLength); out.write(bytes); total += bytes.size } }
            finishOutput(context, output); return Result(outputName, total, "HLS")
        } catch (t: Throwable) { abandonOutput(context, output); throw t }
    }

    private fun parseMasterVariants(base: String, text: String): List<Variant> {
        val lines = text.lines().map(String::trim); val result = mutableListOf<Variant>()
        for (i in lines.indices) if (lines[i].startsWith("#EXT-X-STREAM-INF", true)) {
            val bw = Regex("(?:AVERAGE-BANDWIDTH|BANDWIDTH)=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val codecs = Regex("CODECS=\"([^\"]+)\"").find(lines[i])?.groupValues?.get(1).orEmpty()
            val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: continue
            result += Variant(resolveUrl(base, uri), bw, codecs)
        }
        return result
    }

    private fun scoreVariant(v: Variant): Long = v.bandwidth + if (v.codecs.contains("avc", true) || v.codecs.contains("hev", true)) 1 else 0

    private fun parseHlsSegments(base: String, text: String): List<Segment> {
        val lines = text.lines().map(String::trim); val result = mutableListOf<Segment>(); var pendingRange: Pair<Long, Long>? = null; var mapAdded = false
        for (line in lines) {
            if (line.startsWith("#EXT-X-MAP", true)) {
                val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1); val range = Regex("BYTERANGE=\"(\\d+)(?:@(\\d+))?\"").find(line)
                if (uri != null && !mapAdded) { result += Segment(resolveUrl(base, uri), range?.groupValues?.get(2)?.toLongOrNull(), range?.groupValues?.get(1)?.toLongOrNull()); mapAdded = true }
            } else if (line.startsWith("#EXT-X-BYTERANGE", true)) {
                val parts = line.substringAfter(':').trim().split('@'); pendingRange = Pair(parts[0].toLong(), parts.getOrNull(1)?.toLongOrNull() ?: -1L)
            } else if (line.isNotBlank() && !line.startsWith("#")) {
                val range = pendingRange; result += Segment(resolveUrl(base, line), range?.second?.takeIf { it >= 0 }, range?.first); pendingRange = null
            }
        }
        return result
    }

    private fun downloadDash(context: Context, sourceUrl: String, title: String, referer: String): Result {
        val xml = fetchText(sourceUrl, referer)
        if (xml.contains("ContentProtection", true)) throw IllegalArgumentException("Protected/DASH DRM media is not supported")
        val parser = android.util.Xml.newPullParser().apply { setInput(xml.reader()) }
        var baseUrl = sourceUrl.substringBeforeLast('/') + "/"; var mediaTemplate = ""; var initialization = ""; var startNumber = 1; var timescale = 1L; var duration = 0L
        val timeline = mutableListOf<Pair<Long, Long>>()
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
                "BaseURL" -> { parser.next(); if (parser.eventType == org.xmlpull.v1.XmlPullParser.TEXT) baseUrl = resolveUrl(baseUrl, parser.text.trim()) }
                "SegmentTemplate" -> { parser.getAttributeValue(null, "media")?.let { mediaTemplate = it }; parser.getAttributeValue(null, "initialization")?.let { initialization = it }; parser.getAttributeValue(null, "startNumber")?.toIntOrNull()?.let { startNumber = it }; parser.getAttributeValue(null, "timescale")?.toLongOrNull()?.let { timescale = it }; parser.getAttributeValue(null, "duration")?.toLongOrNull()?.let { duration = it } }
                "S" -> { val d = parser.getAttributeValue(null, "d")?.toLongOrNull() ?: 0L; val t = parser.getAttributeValue(null, "t")?.toLongOrNull() ?: (timeline.lastOrNull()?.let { it.first + it.second } ?: 0L); val r = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: 0; repeat(if (r >= 0) r + 1 else 1) { timeline += Pair(t + it * d, d) } }
            }
            parser.next()
        }
        if (mediaTemplate.isBlank()) throw IllegalArgumentException("DASH manifest has no SegmentTemplate")
        if (timeline.isEmpty() && duration > 0) { val count = min((duration / maxOf(1L, timescale)).toInt() + 1, 2000); repeat(count) { timeline += Pair(it.toLong() * duration / count, duration / count) } }
        if (timeline.isEmpty()) throw IllegalArgumentException("DASH manifest has no segment timeline")
        val outputName = safeFileName(sourceUrl, title).substringBeforeLast('.') + ".mp4"; val output = beginOutput(context, outputName, "video/mp4"); var total = 0L
        try {
            BufferedOutputStream(output.stream).use { out ->
                if (initialization.isNotBlank()) { val init = initialization.replace("\$RepresentationID\$", ""); val bytes = fetchBytes(resolveUrl(baseUrl, init), referer); out.write(bytes); total += bytes.size }
                timeline.forEachIndexed { index, pair -> val media = mediaTemplate.replace("\$Number\$", (startNumber + index).toString()).replace("\$Time\$", pair.first.toString()); val bytes = fetchBytes(resolveUrl(baseUrl, media), referer); out.write(bytes); total += bytes.size }
            }
            finishOutput(context, output); return Result(outputName, total, "DASH")
        } catch (t: Throwable) { abandonOutput(context, output); throw t }
    }

    private fun fetchText(url: String, referer: String) = open(url, referer).let { c -> try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() } }
    private fun fetchBytes(url: String, referer: String, start: Long? = null, length: Long? = null): ByteArray { val c = open(url, referer, start, length); return try { BufferedInputStream(c.inputStream).use { it.readBytes() } } finally { c.disconnect() } }
    private fun open(url: String, referer: String, start: Long? = null, length: Long? = null): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection; c.connectTimeout = 15000; c.readTimeout = 60000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) UniversalBrowser/0.7"); c.setRequestProperty("Accept", "*/*"); CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let { c.setRequestProperty("Cookie", it) }; if (referer.isNotBlank()) c.setRequestProperty("Referer", referer)
        if (start != null) c.setRequestProperty("Range", "bytes=$start-${if (length != null) start + length - 1 else ""}")
        if (c.responseCode !in 200..299 && c.responseCode != 206) throw IllegalArgumentException("Media server returned HTTP ${c.responseCode}"); return c
    }
    private fun resolveUrl(base: String, child: String): String = try { URI(base).resolve(child).toString() } catch (_: Throwable) { child }
    private fun beginOutput(context: Context, name: String, mime: String): Output {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mime); put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Universal Browser"); put(MediaStore.Downloads.IS_PENDING, 1) }; val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create Downloads entry"); return Output(uri, null, context.contentResolver.openOutputStream(uri) ?: error("Could not open Downloads entry")) }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Universal Browser"); dir.mkdirs(); val file = File(dir, name); return Output(null, file, FileOutputStream(file))
    }
    private fun finishOutput(context: Context, output: Output) { output.stream.close(); if (output.uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.contentResolver.update(output.uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) else output.file?.let { MediaScannerConnection.scanFile(context, arrayOf(it.absolutePath), null, null) } }
    private fun abandonOutput(context: Context, output: Output) { try { output.stream.close() } catch (_: Throwable) {}; output.uri?.let { context.contentResolver.delete(it, null, null) }; output.file?.delete() }
    private fun safeFileName(url: String, title: String): String { val path = Uri.parse(url).lastPathSegment.orEmpty(); val base = title.ifBlank { path.substringBeforeLast('.', "Universal-media").ifBlank { "Universal-media" } }.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().take(100).ifBlank { "Universal-media" }; val ext = Regex("\\.([A-Za-z0-9]{2,5})$").find(path.substringBefore('?'))?.groupValues?.get(1)?.lowercase(Locale.US); return if (ext != null && base.lowercase(Locale.US).endsWith(".$ext")) base else if (ext != null) "$base.$ext" else "$base.mp4" }
}
