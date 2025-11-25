package com.maddoxh

data class ErrorBreakdown(
    val networkFailures: Int,
    val statusFailures: Int,
    val latencyFailures: Int,
    val statusCounts: Map<Int, Int>
)

fun computeErrorBreakdown(results: List<RequestResult>, assertions: AssertionSpec): ErrorBreakdown {
    var network = 0
    var statusFail = 0
    var latencyFail = 0
    val statusCounts = mutableMapOf<Int, Int>()

    for(r in results) {
        if(r.status != null) {
            statusCounts[r.status] = statusCounts.getOrDefault(r.status, 0) + 1
        }

        val hasNetworkError = r.error != null || r.status == null
        if(hasNetworkError) {
            network++
            continue
        }

        val statusOk = assertions.expectedStatus?.let { expected ->
            r.status == expected
        } ?: (r.status in 200..299)

        if(!statusOk) {
            statusFail++
            continue
        }

        val latencyOk = assertions.maxLatencyMs?.let { max ->
            r.latencyMs <= max
        } ?: true

        if(!latencyOk) latencyFail++
    }

    return ErrorBreakdown(network, statusFail, latencyFail, statusCounts)
}