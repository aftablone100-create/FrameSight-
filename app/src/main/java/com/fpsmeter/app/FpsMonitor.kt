package com.fpsmeter.app

import android.os.Handler
import android.os.HandlerThread

class FpsMonitor(
    private val onFpsUpdate: (Int) -> Unit,
    private val onError: (String) -> Unit
) {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var running = false
    private var intervalMs: Long = 1000L
    private var lastFps = 0

    private val focusRegex = Regex("mCurrentFocus=Window\\{[^}]*\\s([a-zA-Z0-9_.]+)/[a-zA-Z0-9_.$]+}")

    fun start(intervalMs: Long) {
        if (running) return
        this.intervalMs = intervalMs
        running = true
        thread = HandlerThread("FpsMonitorThread").also { it.start() }
        handler = Handler(thread!!.looper)
        handler?.post(pollRunnable)
    }

    fun stop() {
        running = false
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = null
    }

    fun updateInterval(newIntervalMs: Long) {
        intervalMs = newIntervalMs
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                val fps = pollFps()
                if (fps != null) {
                    lastFps = fps
                    onFpsUpdate(fps)
                } else {
                    onFpsUpdate(lastFps)
                }
            } catch (e: Exception) {
                onError(e.message ?: "FPS read error")
                onFpsUpdate(lastFps)
            }
            handler?.postDelayed(this, intervalMs)
        }
    }

    private fun pollFps(): Int? {
        if (!ShizukuHelper.isBound()) return null
        val focusOutput = ShizukuHelper.execCommand("dumpsys window windows") ?: return null
        val pkg = extractFocusedPackage(focusOutput) ?: return null
        val framestats = ShizukuHelper.execCommand("dumpsys gfxinfo $pkg framestats") ?: return null
        return parseFps(framestats)
    }

    private fun extractFocusedPackage(output: String): String? {
        val line = output.lineSequence().firstOrNull { it.contains("mCurrentFocus") } ?: return null
        return focusRegex.find(line)?.groupValues?.get(1)
    }

    private fun parseFps(data: String): Int? {
        val lines = data.lines()
        var headerIndex = -1
        var completedColIndex = -1
        var flagsColIndex = -1

        for (i in lines.indices) {
            if (lines[i].startsWith("Flags,")) {
                val cols = lines[i].split(",")
                completedColIndex = cols.indexOf("FrameCompleted")
                flagsColIndex = cols.indexOf("Flags")
                headerIndex = i
                break
            }
        }
        if (headerIndex == -1 || completedColIndex == -1) return null

        val timestamps = mutableListOf<Long>()
        for (i in headerIndex + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.startsWith("---")) break
            val cols = line.split(",")
            if (cols.size <= completedColIndex) continue
            val flags = cols.getOrNull(flagsColIndex)?.toLongOrNull() ?: 0L
            if (flags and 1L == 1L) continue
            val completed = cols[completedColIndex].toLongOrNull() ?: continue
            if (completed > 0) timestamps.add(completed)
        }

        if (timestamps.size < 2) return null
        timestamps.sort()
        val newest = timestamps.last()
        val windowNanos = 1_000_000_000L
        return timestamps.count { it >= newest - windowNanos }
    }
}
