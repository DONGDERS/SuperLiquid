package io.github.liuran001.mmliquidglass.ui.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * In-process diagnostic log ring buffer.
 *
 * `logcat -d` from an app uid only reads the app's own logs and may be
 * blocked by OEM SELinux policy — so every diagnostic line is ALSO stored
 * in memory here. Export writes this buffer directly to the SAF file: no
 * permissions, no logd dependency, always succeeds.
 */
object Diag {
    private const val TAG = "SuperLiquid"
    private const val MAX = 3000
    private val buf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(msg: String) {
        val line = fmt.format(Date()) + " I/" + TAG + ": " + msg
        synchronized(buf) {
            buf.addLast(line)
            if (buf.size > MAX) buf.removeFirst()
        }
        Log.i(TAG, msg)
    }

    fun dump(): String = synchronized(buf) { buf.joinToString("\n") }
}
