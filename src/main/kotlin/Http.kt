package com.maddoxh

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.resume

val client: OkHttpClient = OkHttpClient.Builder()
    .callTimeout(Duration.ofSeconds(10))
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(10))
    .build()

suspend fun sendRequest(url: String): Pair<Int?, Long> {
    val request = Request.Builder().url(url).build()
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