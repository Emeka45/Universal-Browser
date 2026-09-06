package com.coeric.universalbrowser

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

/**
 * Download engine for media which is not a single ordinary file.
 *
 * It deliberately handles only non-DRM resources. HLS AES-128 encrypted
 * playlists and protected media are rejected instead of attempting to bypass
 * protection. HLS playlists made of ordinary TS/fMP4 segments are assembled
 * into one file. Simple DASH SegmentTemplate streams can also be captured as
 * a single representation; full multi-track DASH muxing is intentionally left
 * to a future media container layer.
 */
object AdvancedMediaDownloadEngine {
    data class Result(val fileName: String, val bytes: Long, val kind: String)

    fun isSupported(url: String): Boolean {
        if (url.isBlank() || url.startsWith("blob:", true) || url.startsWith("data:", true)) return false
        val path = url.substringBefore('?').lowercase(Locale.US)
        return listOf(
            ".mp4", ".webm", ".mov", ".m4v", ".3gp", ".mkv", ".mp3", ".m4a", ".ogg", ".oga",
            ".m3u8", ".mpd"
        ).any { path.endsWith(it) }
    }

    fun enqueue(
        context: Context,
        url: String,
        title: String,
        referer: String,
        onStarted: (String) -> Unit,
        onFinished: (Result) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isSupported(url)) {
            onError("Unsupported media URL")
            return
        }
        Thread {
            try {
                val lower = url.substringBefore('?').lowercase(Locale.US)
                if (lower.endsWith(".m3u8")) {
                    val result = downloadHls(context, url, title, referer)
                    onFinished(result)
                } else if (lower.endsWith(".mpd")) {
                    val result = downloadDash(context, url, title, referer)
                    onFinished(result)
                } else {
                    onStarted("Direct media")
                    downloadDirectWithManager(context, url, title, referer)
                }
            } catch (t: Throwable) {
                onError(t.message ?: "Media download failed")
            }
        }.start()
    }

    private fun downloadDirectWithManager(context: Context, url: String, title: String, referer: String) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val request = android.app.DownloadManager.Request(Uri.parse(url))
            .setTitle(title.ifBlank { "Universal media" })
            .setDescription("Downloaded by Universal Browser")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFileName(url, title))
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("Cookie", it) }
        if (referer.isNotBlank()) request.addRequestHeader("Referer", referer)
        manager.enqueue(request)
    }

    private fun downloadHls(context: Context, sourceUrl: String, title: String, referer: String): Result {
        var playlistUrl = sourceUrl
        var playlist = fetchText(playlistUrl, referer)
        if (playlist.contains("#EXT-X-STREAM-INF")) {
            val variants = parseMasterVariants(playlistUrl, playlist)
            if (variants.isEmpty()) throw IllegalArgumentException("HLS master playlist contains no media variants")
            val selected = variants.maxByOrNull { it.bandwidth }
                ?: throw IllegalArgumentException("No HLS variant available")
            playlistUrl = selected.url
            playlist = fetchText(playlistUrl, referer)
        }
        if (playlist.contains("#EXT-X-KEY", ignoreCase = true) && !playlist.contains("METHOD=NONE", ignoreCase = true)) {
            throw IllegalArgumentException("Encrypted/DRM HLS streams are not supported")
        }
        val segments = parseHlsSegments(playlistUrl, playlist)
        if (segments.isEmpty()) throw IllegalArgumentException("HLS playlist has no media segments")
        val ext = if (segments.any { it.url.substringBefore('?').endsWith(".m4s", true) }) "mp4" else "ts"
        val outputName = safeFileName(sourceUrl, title).substringBeforeLast('.') + "." + ext
        val output = beginOutput(context, outputName, "video/$ext")
        var total = 0L
        try {
            BufferedOutputStream(output.stream).use { out ->
                segments.forEachIndexed { index, segment ->
                    val bytes = fetchBytes(segment.url, referer)
                    out.write(bytes)
                    total += bytes.size
                    if (index == 0) onProgress(context, "Universal media", 0)
                }
            }
            finishOutput(context, output, total)
            return Result(outputName, total, "HLS")
        } catch (t: Throwable) {
            abandonOutput(context, output)
            throw t
        }
    }

    private fun downloadDash(context: Context, sourceUrl: String, title: String, referer: String): Result {
        val xml = fetchText(sourceUrl, referer)
        if (xml.contains("ContentProtection", ignoreCase = true)) {
            throw IllegalArgumentException("Protected/DASH DRM media is not supported")
        }
        val parser = android.util.Xml.newPullParser().apply {
            setInput(xml.reader())
        }
        var baseUrl = sourceUrl.substringBeforeLast('/') + "/"
        var mediaTemplate = ""
        var initialization = ""
        var startNumber = 1
        var duration = 0L
        var timescale = 1L
        var segmentCount = 0
        var mime = "video/mp4"
        var inVideo = false
        var depth = 0
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name) {
                        "AdaptationSet" -> {
                            val type = parser.getAttributeValue(null, "contentType") ?: parser.getAttributeValue(null, "mimeType") ?: ""
                            inVideo = type.contains("video", true)
                        }
                        "Representation" -> {
                            val rMime = parser.getAttributeValue(null, "mimeType") ?: ""
                            if (rMime.isNotBlank() && rMime.contains("video", true)) mime = "video/mp4"
                        }
                        "BaseURL" -> if (baseUrl.endsWith('/')) { parser.next(); if (parser.eventType == org.xmlpull.v1.XmlPullParser.TEXT) baseUrl = resolveUrl(sourceUrl, parser.text.trim()) }
                        "SegmentTemplate" -> {
                            parser.getAttributeValue(null, "media")?.let { mediaTemplate = it }
                            parser.getAttributeValue(null, "initialization")?.let { initialization = it }
                            parser.getAttributeValue(null, "startNumber")?.toIntOrNull()?.let { startNumber = it }
                            parser.getAttributeValue(null, "duration")?.toLongOrNull()?.let { duration = it }
                            parser.getAttributeValue(null, "timescale")?.toLongOrNull()?.let { timescale = it }
                        }
                        "SegmentTimeline" -> { }
                        "S" -> {
                            parser.getAttributeValue(null, "r")?.toIntOrNull()?.let { segmentCount += it + 1 } ?: run { segmentCount++ }
                        }
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> depth--
            }
            parser.next()
        }
        if (mediaTemplate.isBlank()) throw IllegalArgumentException("Unsupported DASH manifest: SegmentTemplate is required")
        if (segmentCount <= 0 && duration > 0) segmentCount = min((duration / maxOf(1, timescale)).toInt() + 2, 1000)
        if (segmentCount <= 0) throw IllegalArgumentException("DASH manifest has no downloadable segments")
        segmentCount = min(segmentCount, 1000)
        val outputName = safeFileName(sourceUrl, title).substringBeforeLast('.') + ".mp4"
        val output = beginOutput(context, outputName, mime)
        var total = 0L
        try {
            BufferedOutputStream(output.stream).use { out ->
                if (initialization.isNotBlank()) {
                    val initUrl = resolveUrl(baseUrl, initialization.replace("$RepresentationID$", ""))
                    val bytes = fetchBytes(initUrl, referer); out.write(bytes); total += bytes.size
                }
                for (i in 0 until segmentCount) {
                    val number = startNumber + i
                    val media = mediaTemplate.replace("$Number$", number.toString())
                    val segmentUrl = resolveUrl(baseUrl, media)
                    val bytes = fetchBytes(segmentUrl, referer)
                    out.write(bytes)
                    total += bytes.size
                }
            }
            finishOutput(context, output, total)
            return Result(outputName, total, "DASH")
        } catch (t: Throwable) {
            abandonOutput(context, output)
            throw t
        }
    }

    private data class Variant(val url: String, val bandwidth: Long)
    private data class Segment(val url: String)

    private fun parseMasterVariants(base: String, text: String): List<Variant> {
        val lines = text.lines().map { it.trim() }
        val result = mutableListOf<Variant>()
        for (i in lines.indices) {
            if (!lines[i].startsWith("#EXT-X-STREAM-INF")) continue
            val bandwidth = Regex("(?:AVERAGE-BANDWIDTH|BANDWIDTH)=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: continue
            result += Variant(resolveUrl(base, next), bandwidth)
        }
        return result
    }

    private fun parseHlsSegments(base: String, text: String): List<Segment> {
        return text.lines().map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { Segment(resolveUrl(base, it)) }
    }

    private fun fetchText(url: String, referer: String): String {
        val conn = open(url, referer)
        return try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }

    private fun fetchBytes(url: String, referer: String): ByteArray {
        val conn = open(url, referer)
        return try { BufferedInputStream(conn.inputStream).use { it.readBytes() } } finally { conn.disconnect() }
    }

    private fun open(url: String, referer: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 45000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) UniversalBrowser/0.6")
        c.setRequestProperty("Accept", "*/*")
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { c.setRequestProperty("Cookie", it) }
        if (referer.isNotBlank()) c.setRequestProperty("Referer", referer)
        if (c.responseCode !in 200..299) throw IllegalArgumentException("Media server returned HTTP ${c.responseCode}")
        return c
    }

    private fun resolveUrl(base: String, child: String): String = try { URI(base).resolve(child).toString() } catch (_: Throwable) { child }

    private data class Output(val uri: Uri?, val file: File?, val stream: java.io.OutputStream)

    private fun beginOutput(context: Context, name: String, mime: String): Output {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Universal Browser")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create Downloads entry")
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open Downloads entry")
            return Output(uri, null, stream)
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Universal Browser")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        return Output(null, file, FileOutputStream(file))
    }

    private fun finishOutput(context: Context, output: Output, bytes: Long) {
        output.stream.close()
        if (output.uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(output.uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } else if (output.file != null) {
            MediaScannerConnection.scanFile(context, arrayOf(output.file.absolutePath), null, null)
        }
    }

    private fun abandonOutput(context: Context, output: Output) {
        try { output.stream.close() } catch (_: Throwable) { }
        output.uri?.let { context.contentResolver.delete(it, null, null) }
        output.file?.delete()
    }

    private fun onProgress(context: Context, title: String, percent: Int) { }

    private fun safeFileName(url: String, title: String): String {
        val path = try { Uri.parse(url).lastPathSegment.orEmpty() } catch (_: Throwable) { "" }
        val base = title.ifBlank { path.substringBeforeLast('.', path).ifBlank { "Universal-media" } }
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().take(100).ifBlank { "Universal-media" }
        val ext = Regex("\\.([A-Za-z0-9]{2,5})$").find(path.substringBefore('?'))?.groupValues?.get(1)?.lowercase(Locale.US)
        return if (ext != null && !base.lowercase(Locale.US).endsWith(".$ext")) "$base.$ext" else "$base.mp4"
    }
}
