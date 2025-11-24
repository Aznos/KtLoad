package com.maddoxh

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