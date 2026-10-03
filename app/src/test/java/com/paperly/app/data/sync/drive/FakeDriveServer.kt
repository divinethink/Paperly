package com.paperly.app.data.sync.drive

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.MessageDigest

fun hexOf(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

fun sha256Hex(bytes: ByteArray) = hexOf(MessageDigest.getInstance("SHA-256").digest(bytes))

fun md5Hex(bytes: ByteArray) = hexOf(MessageDigest.getInstance("MD5").digest(bytes))

class RemoteFile(val id: String, val documentId: String, val bytes: ByteArray)

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
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val endpoints: DriveEndpoints
        get() = "http://127.0.0.1:${server.address.port}".let { DriveEndpoints("$it/drive/v3", "$it/upload/drive/v3") }

    fun start(): FakeDriveServer {
        server.createContext("/") { ex ->
            try {
                handle(ex)
            } catch (e: IllegalStateException) {
                reply(ex, 500, e.message.orEmpty())
            }
        }
        server.start()
        return this
    }

    fun stop() = server.stop(0)

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
        return """{"id":"${f.id}","size":"${f.bytes.size}",$hash}"""
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
        check(ex.requestHeaders.getFirst("Authorization") == null) { "session call must not carry the token" }
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
        val port = server.address.port
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
            val doc = Regex("value='([^']+)'").find(q)!!.groupValues[1]
            val list = files.values.filter { it.documentId == doc }.joinToString(",") { fileJson(it) }
            return reply(ex, 200, """{"files":[$list]}""")
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
