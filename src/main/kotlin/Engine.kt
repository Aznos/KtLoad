package com.maddoxh

import com.maddoxh.cli.renderProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

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

                val (status, nanos) = sendRequest(HttpRequest(url=url))
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