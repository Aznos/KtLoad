package com.maddoxh

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.resume

class KtLoad : CliktCommand() {
    val count: Int by option().int().default(1000).help("Number of requests")
    val url: String by option().prompt("URL").help("The URL to test")

    override fun run() {
        runBlocking {
            repeat(count) { i ->
                val (status, nanos) = sendRequest(url)
                val ms = nanos / 1_000_000
                println("[$i] status=$status latency=${ms}ms")
            }
        }

        shutdownClient()
    }
}

val client = OkHttpClient.Builder()
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

fun main(args: Array<String>) = KtLoad().main(args)