package com.example.timer

import android.content.Context
import java.util.Date

object Fmt {
    fun clock(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun compact(ms: Long): String {
        val t = ms / 1000
        val parts = buildList {
            if (t / 3600 > 0) add("${t / 3600}h")
            if ((t % 3600) / 60 > 0) add("${(t % 3600) / 60}m")
            if (t % 60 > 0) add("${t % 60}s")
        }
        return if (parts.isEmpty()) "0s" else parts.joinToString(" ")
    }

    fun timeOfDay(ctx: Context, millis: Long): String =
        android.text.format.DateFormat.getTimeFormat(ctx).format(Date(millis))
}
