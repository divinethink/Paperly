package com.paperly.app.data.sync.drive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import kotlin.concurrent.thread

fun hexOf(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

fun sha256Hex(bytes: ByteArray) = hexOf(MessageDigest.getInstance("SHA-256").digest(bytes))

fun md5Hex(bytes: ByteArray) = hexOf(MessageDigest.getInstance("MD5").digest(bytes))

/** One request/response on a socket. Only what [FakeDriveServer] needs (the JDK-only HttpServer is not on the Android test classpath). */
class HttpExchange(
    val requestMethod: String,
    val requestURI: URI,
    private val headers: Map<String, String>,
    body: ByteArray,
    private val socket: Socket,
) {
    val requestBody: InputStream = ByteArrayInputStream(body)
    private val extra = LinkedHashMap<String, String>()
    val requestHeaders = RequestHeaders(headers)
    val responseHeaders = ResponseHeaders(extra)
    val responseBody: OutputStream by lazy { socket.getOutputStream().let { Closing(it, socket) } }

    fun sendResponseHeaders(code: Int, length: Long) {
        val head = StringBuilder("HTTP/1.1 $code X\r\nContent-Length: ${if (length < 0) 0 else length}\r\nConnection: close\r\n")
        extra.forEach { (k, v) -> head.append("$k: $v\r\n") }
        socket.getOutputStream().write(head.append("\r\n").toString().toByteArray())
    }

    class RequestHeaders(private val map: Map<String, String>) {
        fun getFirst(name: String): String = map[name.lowercase()].orEmpty()
    }

    class ResponseHeaders(private val map: MutableMap<String, String>) {
        fun add(name: String, value: String) {
            map[name] = value
        }
    }

    private class Closing(private val out: OutputStream, private val socket: Socket) : OutputStream() {
        override fun write(b: Int) = out.write(b)

        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)

        override fun close() {
            out.flush()
            socket.close()
        }
    }
}

private fun readLine(input: InputStream): String? {
    val line = ByteArrayOutputStream()
    while (true) {
        val b = input.read()
        if (b < 0) return if (line.size() == 0) null else line.toString()
        if (b == '\n'.code) return line.toString().trimEnd('\r')
        line.write(b)
    }
}

private fun readChunked(input: InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    while (true) {
        val size = readLine(input)!!.substringBefore(';').trim().toInt(16)
        if (size == 0) {
            readLine(input)
            return out.toByteArray()
        }
        out.write(input.readNBytes(size))
        readLine(input)
    }
}

private fun readExchange(socket: Socket): HttpExchange? {
    val input = socket.getInputStream().buffered()
    val request = readLine(input)?.split(" ") ?: return null
    val headers = HashMap<String, String>()
    while (true) {
        val line = readLine(input).orEmpty()
        if (line.isEmpty()) break
        headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
    }
    val body = when {
        headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true -> readChunked(input)
        else -> input.readNBytes(headers["content-length"]?.toInt() ?: 0)
    }
    return HttpExchange(request[0], URI(request[1]), headers, body, socket)
}

class RemoteFile(
    val id: String,
    val documentId: String,
    val bytes: ByteArray,
    val createdMillis: Long = System.currentTimeMillis(),
)

/** A small in-process imitation of the parts of the Drive REST API that Paperly uses, with fault switches. */
class FakeDriveServer {
    val files = LinkedHashMap<String, RemoteFile>()
    var sessionsCreated = 0
    var requests = 0
    var chunkCount = 0
    var failChunkNumber: Int? = null
    var failAllWith: Int? = null
    var failAllBody = ""
    var omitSha = false
    var wrongSha = false
    var wrongMd5 = false
    var acceptOnlyHalfOnce = false

    private val buffers = HashMap<String, ByteArrayOutputStream>()
    private val totals = HashMap<String, Long>()
    private val documentOfSession = HashMap<String, String>()
    private var nextFileId = 1
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    val endpoints: DriveEndpoints
        get() = "http://127.0.0.1:${server.localPort}".let { DriveEndpoints("$it/drive/v3", "$it/upload/drive/v3") }

    fun start(): FakeDriveServer {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { serve(socket) }
            }
        }
        return this
    }

    private fun serve(socket: Socket) {
        val ex = runCatching { readExchange(socket) }.getOrNull() ?: return socket.close()
        try {
            handle(ex)
        } catch (e: IllegalStateException) {
            reply(ex, 500, e.message.orEmpty())
        }
    }

    fun stop() = server.close()

    private fun reply(ex: HttpExchange, code: Int, body: String, headers: Map<String, String> = emptyMap()) {
        val bytes = body.toByteArray()
        headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
        ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isEmpty()) ex.responseBody.close() else ex.responseBody.use { it.write(bytes) }
    }

    private fun fileJson(f: RemoteFile): String {
        val hash = if (omitSha) {
            """"md5Checksum":"${if (wrongMd5) "f".repeat(32) else md5Hex(f.bytes)}""""
        } else {
            """"sha256Checksum":"${if (wrongSha) "0".repeat(64) else sha256Hex(f.bytes)}""""
        }
        val created = java.time.Instant.ofEpochMilli(f.createdMillis)
        val props = """"appProperties":{"documentId":"${f.documentId}"}"""
        return """{"id":"${f.id}","size":"${f.bytes.size}","createdTime":"$created",$props,$hash}"""
    }

    private fun handle(ex: HttpExchange) {
        requests++
        failAllWith?.let { reply(ex, it, failAllBody) }
        if (failAllWith != null) return
        if (ex.requestURI.path.startsWith("/upload/drive/v3/files")) uploadCall(ex) else apiCall(ex)
    }

    private fun uploadCall(ex: HttpExchange) {
        if (ex.requestMethod == "POST") return startSession(ex)
        val id = Regex("upload_id=([^&]+)").find(ex.requestURI.rawQuery)!!.groupValues[1]
        check(ex.requestHeaders.getFirst("Authorization").isEmpty()) { "session call must not carry the token" }
        val buffer = buffers[id] ?: return reply(ex, 404, "gone")
        val total = totals.getValue(id)
        val range = ex.requestHeaders.getFirst("Content-Range")
        val data = ex.requestBody.readBytes()
        if (range.startsWith("bytes */")) return progress(ex, id, buffer, total)
        chunkCount++
        if (failChunkNumber == chunkCount) return reply(ex, 503, "busy")
        val (from, to) = Regex("bytes (\\d+)-(\\d+)/").find(range)!!.destructured
        check(data.size.toLong() == to.toLong() - from.toLong() + 1) { "length mismatch" }
        if (from.toLong() != buffer.size().toLong()) return reply(ex, 308, "", rangeHeader(buffer.size()))
        if (acceptOnlyHalfOnce && data.size > 1000) {
            acceptOnlyHalfOnce = false
            buffer.write(data, 0, data.size / 2)
            return reply(ex, 308, "", rangeHeader(buffer.size()))
        }
        buffer.write(data)
        progress(ex, id, buffer, total)
    }

    private fun startSession(ex: HttpExchange) {
        check(ex.requestHeaders.getFirst("Authorization") == "Bearer tok") { "missing bearer token" }
        val body = String(ex.requestBody.readBytes())
        check(body.contains("appDataFolder") && body.contains("\"documentId\"")) { "bad metadata: $body" }
        val id = "up${sessionsCreated++}"
        buffers[id] = ByteArrayOutputStream()
        totals[id] = ex.requestHeaders.getFirst("X-Upload-Content-Length").toLong()
        documentOfSession[id] = Regex("\"documentId\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        val port = server.localPort
        val location = "http://127.0.0.1:$port/upload/drive/v3/files?uploadType=resumable&upload_id=$id"
        reply(ex, 200, "", mapOf("Location" to location))
    }

    private fun progress(ex: HttpExchange, id: String, buffer: ByteArrayOutputStream, total: Long) {
        if (buffer.size().toLong() == total) {
            val fileId = "f${nextFileId++}"
            files[fileId] = RemoteFile(fileId, documentOfSession.getValue(id), buffer.toByteArray())
            reply(ex, 200, """{"id":"$fileId"}""")
        } else {
            reply(ex, 308, "", rangeHeader(buffer.size()))
        }
    }

    private fun rangeHeader(received: Int) = if (received == 0) emptyMap() else mapOf("Range" to "bytes=0-${received - 1}")

    private fun apiCall(ex: HttpExchange) {
        check(ex.requestHeaders.getFirst("Authorization") == "Bearer tok") { "missing bearer token" }
        val query = ex.requestURI.rawQuery.orEmpty()
        if (ex.requestURI.path == "/drive/v3/files") {
            val q = URLDecoder.decode(Regex("(?:^|&)q=([^&]*)").find(query)!!.groupValues[1], "UTF-8")
            val doc = Regex("value='([^']+)'").find(q)?.groupValues?.get(1)
            val matching = files.values.filter { doc == null || it.documentId == doc }
            val size = Regex("(?:^|&)pageSize=(\\d+)").find(query)?.groupValues?.get(1)?.toInt() ?: Int.MAX_VALUE
            val start = Regex("(?:^|&)pageToken=([^&]*)").find(query)?.groupValues?.get(1)?.toInt() ?: 0
            val page = matching.drop(start).take(size)
            val more = start + page.size < matching.size
            val token = if (more) ""","nextPageToken":"${start + page.size}"""" else ""
            return reply(ex, 200, """{"files":[${page.joinToString(",") { fileJson(it) }}]$token}""")
        }
        val file = files[ex.requestURI.path.substringAfterLast('/')]
        when {
            ex.requestMethod == "DELETE" -> {
                file?.let { files.remove(it.id) }
                reply(ex, 204, "")
            }
            file == null -> reply(ex, 404, "{}")
            query.contains("alt=media") -> {
                val bytes = file.bytes.copyOf()
                if (wrongSha) bytes[0] = (bytes[0] + 1).toByte()
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            else -> reply(ex, 200, fileJson(file))
        }
    }
}
