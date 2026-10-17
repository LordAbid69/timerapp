package com.example.timer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

enum class Phase { Idle, Running, Paused, Finished }

data class TimerState(
    val phase: Phase = Phase.Idle,
    val totalMs: Long = 0L,
    val remainingMs: Long = 0L,   // valid when Paused
    val endElapsed: Long = 0L,    // SystemClock.elapsedRealtime() target, valid when Running
    val endWall: Long = 0L,       // wall-clock end time, for "Ends at"
)

/** Single source of truth shared by the service (writer) and the UI (reader). */
object TimerStore {
    val state = MutableStateFlow(TimerState())
    val tickEnabled = MutableStateFlow(true)
    val recents = MutableStateFlow<List<Long>>(emptyList())
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("timer", Context.MODE_PRIVATE)
        prefs = p
        tickEnabled.value = p.getBoolean("tick", true)
        recents.value = (p.getString("recents", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
    }

    fun setTick(on: Boolean) {
        tickEnabled.value = on
        prefs?.edit()?.putBoolean("tick", on)?.apply()
    }

    fun addRecent(ms: Long) {
        val list = (listOf(ms) + recents.value.filter { it != ms }).take(8)
        recents.value = list
        prefs?.edit()?.putString("recents", list.joinToString(","))?.apply()
    }
}
