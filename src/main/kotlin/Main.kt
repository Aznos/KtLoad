package com.maddoxh

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

class KtLoad : CliktCommand() {
    val count: Int by option().int().default(100).help("Number of requests")

    override fun run() {
        repeat(count) {
            println("Sending request to server")
        }
    }
}

fun main(args: Array<String>) = KtLoad().main(args)