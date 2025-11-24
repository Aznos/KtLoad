package com.maddoxh.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.int
import com.maddoxh.computeStats
import com.maddoxh.runLoadTest
import com.maddoxh.shutdownClient
import kotlinx.coroutines.runBlocking

class Cli : CliktCommand() {
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
            printHeader(url, count, concurrency)

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