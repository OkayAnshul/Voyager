package com.cosmiclaboratory.voyager.platform.crash

import android.content.Context
import android.os.Build
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Captures uncaught exceptions to a sidecar file in filesDir during the crash itself,
 * then flushes them to HealthLogEntity on the next normal launch.
 *
 * Two-stage design because at crash time Hilt + coroutines may be in an unsafe state —
 * but writing a small file is allowed.
 */
class LocalCrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val payload = buildString {
                append("{\"thread\":\"").append(t.name.replace("\"", "'")).append("\",")
                append("\"manufacturer\":\"").append(Build.MANUFACTURER).append("\",")
                append("\"model\":\"").append(Build.MODEL).append("\",")
                append("\"sdk\":").append(Build.VERSION.SDK_INT).append(",")
                append("\"stack\":\"").append(sw.toString().take(4000).replace("\"", "'").replace("\n", "\\n"))
                append("\"}")
            }
            val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
            File(dir, "crash_${System.currentTimeMillis()}.json").writeText(payload)
        } catch (_: Throwable) {
            // never let crash handler itself crash
        }
        defaultHandler?.uncaughtException(t, e)
    }

    companion object {
        private const val CRASH_DIR = "crashes"

        fun install(context: Context) {
            val default = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(
                LocalCrashHandler(context.applicationContext, default)
            )
        }

        /**
         * Flushes any pending crash files to HealthLog. Safe to call on every cold start.
         * Errors are swallowed — this is best-effort observability.
         */
        fun flushPending(context: Context, healthLogDao: HealthLogDao, scope: CoroutineScope) {
            val dir = File(context.filesDir, CRASH_DIR)
            if (!dir.isDirectory) return
            val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }
                ?: return
            if (files.isEmpty()) return
            scope.launch(Dispatchers.IO) {
                for (file in files) {
                    try {
                        val body = file.readText()
                        healthLogDao.insert(
                            HealthLogEntity(
                                eventType = "CRASH",
                                eventAt = file.lastModified(),
                                detailsJson = body,
                            )
                        )
                        file.delete()
                    } catch (_: Throwable) {
                        // leave the file; we will try again next launch
                    }
                }
            }
        }
    }
}
