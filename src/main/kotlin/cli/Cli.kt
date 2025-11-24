package com.maddoxh.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.int
import com.maddoxh.HttpRequest
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

    private val method: String by option("--method", "-X")
        .default("GET")
        .help("HTTP method to use (GET, POST, PUT, DELETE, ...)")

    private val rawHeaders: List<String> by option("--header", "-H")
        .help("Header in 'Key: Value' format (can be passed multiple times)")
        .multiple()

    private val body: String? by option("--body")
        .help("Request body as a string")

    override fun run() {
        runBlocking {
            val headers = parseHeaders(rawHeaders)
            val request = HttpRequest(method, url, headers, body)

            printHeader(url, count, concurrency)

            val startNs = System.nanoTime()
            val results = runLoadTest(request, count, concurrency)
            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            val stats = computeStats(results, durationMs)

            println()
            printSummary(url, count, concurrency, stats)
        }

        shutdownClient()
    }

    private fun parseHeaders(raw: List<String>): Map<String, String> {
        return raw.mapNotNull { line ->
            val idx = line.indexOf(':')
            if(idx <= 0) {
                null
            } else {
                val key = line.take(idx).trim()
                val value = line.substring(idx + 1).trim()
                key to value
            }
        }.toMap()
    }
}