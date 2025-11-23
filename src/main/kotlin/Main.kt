package com.maddoxh

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Duration
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class KtLoad : CliktCommand() {
    private val count: Int by option("--requests", "-r")
        .int()
        .default(100)
        .help("Total number of requests to send")

    private val concurrency: Int by option("--concurrency", "-c")
        .int()
        .default(10)
        .help("Number of concurrent workers")

    private val url: String by option("--url", "-u")
        .prompt("URL")
        .help("The URL to test")

    override fun run() {
        runBlocking {
            val results = runLoadTest(url, count, concurrency)
            println("Done, sent ${results.size} requests")
        }

        shutdownClient()
    }
}

data class RequestResult(
    val success: Boolean,
    val status: Int?,
    val latencyMs: Long,
    val error: String? = null
)

suspend fun runLoadTest(url: String, totalRequests: Int, concurrency: Int): List<RequestResult> = coroutineScope {
    require(totalRequests > 0)
    require(concurrency > 0)

    val remaining = AtomicInteger(totalRequests)
    val results = Collections.synchronizedList(mutableListOf<RequestResult>())

    repeat(concurrency) {
        launch(Dispatchers.IO) {
            while(true) {
                val idx = remaining.getAndDecrement()
                if(idx <= 0) {
                    break
                }

                val (status, nanos) = sendRequest(url)
                val ms = nanos / 1_000_000

                val result = if(status != null) {
                    RequestResult(true, status, ms)
                } else {
                    RequestResult(false, null, ms, error = "request failed")
                }

                results += result
            }
        }
    }

    return@coroutineScope results
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