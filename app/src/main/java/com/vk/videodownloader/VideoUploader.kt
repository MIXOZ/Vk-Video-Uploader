package com.vk.videodownloader

import android.util.Log
import androidx.lifecycle.ViewModel
import com.vk.videodownloader.Constants.Companion.BUFFER_SIZE
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.util.*

class VideoUploader(
    private val inputStream: InputStream,
    private val url: String
) : ViewModel() {

    private val buffer: ByteArray = ByteArray(BUFFER_SIZE)
    private var leftRange: Long = 0L
    private val uuid: String = UUID.randomUUID().toString()
    private val client: OkHttpClient = OkHttpClient()
    private val size: Int = inputStream.available() + 208
    private var isOnPause: Boolean = false
    private var isCrashed: Boolean = false
    private lateinit var request: Request
    private var bytesRead = 0


    private fun getRequestBody(buffer: ByteArray, size: Int): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return "multipart/form-data".toMediaTypeOrNull()
            }

            override fun contentLength(): Long {
                return size.toLong()
            }

            override fun writeTo(sink: BufferedSink) {
                sink.write(buffer, 0, size)
            }
        }
    }

    fun pause() {
        isOnPause = true
    }

    fun resume() {
        if (isCrashed) {
            if (executeRequest()) {
                return
            }
        }
        isOnPause = false
        upload()
    }

    fun upload() {
        while (!isOnPause && (inputStream.read(buffer).also { bytesRead = it } != -1)) {

            val requestBody: RequestBody = MultipartBody.Builder()
                .addFormDataPart("video_file", "i", getRequestBody(buffer, bytesRead))
                .build()

            request = Request.Builder()
                .addHeader("Content-Type", "video/mp4")
                .addHeader("Content-Disposition", "attachment; filename=\"video.mp4\"")
                .addHeader(
                    "Content-Range",
                    "bytes " + leftRange.toString() + "-" + (leftRange + bytesRead) + '/' + (size + 207)
                )
                .addHeader(
                    "Session-ID",
                    uuid
                )
                .addHeader(
                    "Content-Length",
                    requestBody.contentLength().toString()
                )
                .url(url)
                .put(requestBody)
                .build()

            if (executeRequest()) break
        }
    }

    private fun executeRequest(): Boolean {
        val response: Response
        try {
            response = client.newCall(request).execute()
        } catch (e: IOException) {
            pause()
            isCrashed = true
            return true
        }
        Log.d("Success", response.message)
        Log.d("Success", response.code.toString())
        response.header("Range")?.let { Log.d("Success", it) }
        response.header("Connection")?.let { Log.d("Success", it) }
        response.header("Content-Length")?.let { Log.d("Success", it) }
        response.header("Date")?.let { Log.d("Success", it) }

        leftRange += if (leftRange == 0L) {
            (bytesRead + 208)
        } else {
            bytesRead
        }
        response.close()

        return false
    }
}