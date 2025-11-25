package com.maddoxh

import com.maddoxh.cli.renderProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

data class AssertionSpec(
    val expectedStatus: Int? = null,
    val maxLatencyMs: Long? = null
)

data class RequestResult(
    val success: Boolean,
    val status: Int?,
    val latencyMs: Long,
    val error: String? = null
)

suspend fun runLoadTest(request: HttpRequest, totalRequests: Int, concurrency: Int, assertions: AssertionSpec): List<RequestResult> = coroutineScope {
    require(totalRequests > 0)
    require(concurrency > 0)

    val remaining = AtomicInteger(totalRequests)
    val completed = AtomicInteger(0)
    val successful = AtomicInteger(0)
    val results = Collections.synchronizedList(mutableListOf<RequestResult>())

    val startNs = System.nanoTime()

    val progressJob = launch {
        while(true) {
            val done = completed.get()
            val ok = successful.get()
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            val rps = if(elapsedMs > 0) {
                done.toDouble() / (elapsedMs.toDouble() / 1000.0)
            } else 0.0

            val bar = renderProgressBar(done, totalRequests, 30)
            val percent = if(totalRequests > 0) (done * 100 / totalRequests.toDouble()) else 0
            val success = if(done > 0) {
                ok * 100 / done.toDouble()
            } else 0.0

            print(
                "\r$bar  $done/$totalRequests " +
                        "(${String.format("%.1f", percent)}%)  " +
                        "RPS: ${"%.1f".format(rps)}  " +
                        "OK: ${"%.1f".format(success)}%"
            )
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

                val (status, nanos) = sendRequest(request)
                val ms = nanos / 1_000_000

                val networkError = if (status == null) "request failed" else null
                val success = evaluateSuccess(status, ms, networkError, assertions)
                val result = RequestResult(success, status, ms, networkError)
                results += result
                completed.incrementAndGet()

                if(success) successful.incrementAndGet()
            }
        }
    }

    progressJob.join()
    return@coroutineScope results
}

private fun evaluateSuccess(status: Int?, ms: Long, networkError: String?, assertions: AssertionSpec): Boolean {
    if(networkError != null || status == null) return false

    val statusOk = assertions.expectedStatus?.let { expected ->
        status == expected
    } ?: (status in 200..299)
    if(!statusOk) return false

    val latencyOk = assertions.maxLatencyMs?.let { max ->
        ms <= max
    } ?: true
    if(!latencyOk) return false

    return true
}