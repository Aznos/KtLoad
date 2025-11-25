package com.maddoxh

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.resume

val dispatcher = Dispatcher().apply {
    maxRequests = 512
    maxRequestsPerHost = 512
}

val client: OkHttpClient = OkHttpClient.Builder()
    .dispatcher(dispatcher)
    .callTimeout(Duration.ofSeconds(10))
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(10))
    .build()

data class HttpRequest(
    val method: String = "GET",
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
) {
    fun toOkHttpRequest(): Request {
        val builder = Request.Builder().url(url)
        for((k, v) in headers) builder.header(k, v)

        val requestBody = body?.let { bodyText ->
            val mediaType = "application/json; charset=utf-8".toMediaType()
            bodyText.toRequestBody(mediaType)
        }

        return builder.method(method.uppercase(), requestBody).build()
    }
}

suspend fun sendRequest(httpRequest: HttpRequest): Pair<Int?, Long> {
    val request = httpRequest.toOkHttpRequest()
    val start = System.nanoTime()

    return suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)

        cont.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resume(null to (System.nanoTime() - start))
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response.code to (System.nanoTime() - start))
                response.close()
            }
        })
    }
}

fun shutdownClient() {
    client.dispatcher.executorService.shutdown()
    client.connectionPool.evictAll()
    client.cache?.close()
}