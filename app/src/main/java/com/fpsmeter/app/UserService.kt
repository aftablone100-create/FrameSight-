package com.fpsmeter.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class UserService() : IUserService.Stub() {

    @Suppress("unused")
    constructor(context: Context) : this()

    override fun execCommand(cmd: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.waitFor()
            output
        } catch (e: Exception) {
            "ERROR:${e.message}"
        }
    }

    override fun destroy() {
        Runtime.getRuntime().exit(0)
    }
}
