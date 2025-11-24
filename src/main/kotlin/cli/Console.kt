package com.maddoxh.cli

import com.maddoxh.RunStats

private const val CYAN = "\u001B[36m"
private const val GREEN = "\u001B[32m"
private const val RED = "\u001B[31m"
private const val DIM = "\u001B[2m"
private const val RESET = "\u001B[0m"

fun printHeader(url: String, requests: Int, concurrency: Int) {
    println("$CYAN ktload v0.1$RESET")
    println("${DIM}Target      :$RESET $url")
    println("${DIM}Requests    :$RESET $requests")
    println("${DIM}Concurrency :$RESET $concurrency")
    println()
}

fun renderProgressBar(done: Int, total: Int, width: Int = 30): String {
    if(total <= 0) return "[${" ".repeat(width)}]"
    val ratio = done.toDouble() / total.toDouble()
    val filled = (ratio * width).toInt().coerceIn(0, width)
    val bar = "█".repeat(filled) + "░".repeat(width - filled)

    return "[$bar]"
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