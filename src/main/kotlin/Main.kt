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
import kotlinx.coroutines.delay
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

private const val CYAN = "\u001B[36m"
private const val GREEN = "\u001B[32m"
private const val RED = "\u001B[31m"
private const val DIM = "\u001B[2m"
private const val RESET = "\u001B[0m"

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
            println("$CYAN ktload v0.1$RESET")
            println("${DIM}Target        :$RESET $url")
            println("${DIM}Requests      :$RESET $count")
            println("${DIM}Concurrency   :$RESET $concurrency")

            val startNs = System.nanoTime()
            val results = runLoadTest(url, count, concurrency)
            val durationMs = (System.nanoTime() - startNs) / 1_000_000

            val stats = computeStats(results, durationMs)
            println()
            printSummary(url, count, concurrency, stats)
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

data class RunStats(
    val total: Int,
    val success: Int,
    val failure: Int,
    val minMs: Long,
    val maxMs: Long,
    val avgMs: Double,
    val p50Ms: Long,
    val p90Ms: Long,
    val durationMs: Long,
    val overallRps: Double
)

suspend fun runLoadTest(url: String, totalRequests: Int, concurrency: Int): List<RequestResult> = coroutineScope {
    require(totalRequests > 0)
    require(concurrency > 0)

    val remaining = AtomicInteger(totalRequests)
    val completed = AtomicInteger(0)
    val results = Collections.synchronizedList(mutableListOf<RequestResult>())

    val startNs = System.nanoTime()

    val progressJob = launch {
        while(true) {
            val done = completed.get()
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            val aps = if(elapsedMs > 0) {
                done.toDouble() / (elapsedMs.toDouble() / 1000.0)
            } else 0.0

            val bar = renderProgressBar(done, totalRequests, 30)
            val percent = if(totalRequests > 0) (done * 100 / totalRequests.toDouble()) else 0

            print("\r$bar  $done/$totalRequests (${percent}%)  APS: ${"%.1f".format(aps)}")
            System.out.flush()

            if(done >= totalRequests) {
                break
            }

            delay(200)
        }
    }

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
                completed.incrementAndGet()
            }
        }
    }

    progressJob.join()
    return@coroutineScope results
}

fun renderProgressBar(done: Int, total: Int, width: Int = 30): String {
    if(total <= 0) return "[${" ".repeat(width)}]"
    val ratio = done.toDouble() / total.toDouble()
    val filled = (ratio * width).toInt().coerceIn(0, width)
    val bar = "█".repeat(filled) + "░".repeat(width - filled)

    return "[$bar]"
}

fun computeStats(results: List<RequestResult>, durationMs: Long): RunStats {
    val total = results.size
    val success = results.count { it.success }
    val failure = total - success

    val latencies = results.map { it.latencyMs }.sorted()
    val min = latencies.minOrNull() ?: 0L
    val max = latencies.maxOrNull() ?: 0L
    val avg = if(latencies.isNotEmpty()) latencies.average() else 0.0

    fun percentile(p: Double): Long {
        if(latencies.isEmpty()) return 0
        val idx = (p * (latencies.size - 1)).toInt().coerceIn(0, latencies.size - 1)
        return latencies[idx]
    }

    val p50 = percentile(0.50)
    val p90 = percentile(0.90)

    val overallRps = if(durationMs > 0) total.toDouble() / (durationMs.toDouble() / 1000.0) else 0.0
    return RunStats(total, success, failure, min, max, avg, p50, p90, durationMs, overallRps)
}

fun printSummary(url: String, totalRequests: Int, concurrency: Int, stats: RunStats) {
    val line = "=".repeat(50)
    val subLine = "-".repeat(50)

    println()
    println(line)
    println("$CYAN ktload summary$RESET")
    println(subLine)

    println("${DIM}Target       :$RESET $url")
    println("${DIM}Requests     :$RESET $totalRequests")
    println("${DIM}Concurrency  :$RESET $concurrency")
    println("${DIM}Duration     :$RESET ${stats.durationMs} ms")
    println("${DIM}Overall RPS  :$RESET ${"%.1f".format(stats.overallRps)}")

    println()
    println("${DIM}Results:$RESET")
    println("  ${GREEN}Success      :$RESET ${stats.success}")
    println("  ${RED}Failure      :$RESET ${stats.failure}")
    println()
    println("${DIM}Latency (ms):$RESET")
    println("  min        : ${stats.minMs}")
    println("  avg        : ${"%.1f".format(stats.avgMs)}")
    println("  p50        : ${stats.p50Ms}")
    println("  p90        : ${stats.p90Ms}")
    println("  max        : ${stats.maxMs}")

    println(line)
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