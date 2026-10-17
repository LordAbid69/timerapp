package com.example.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the countdown. Runs as a foreground service so the timer, the ticking sound and the
 * alarm keep working with the app closed or the screen off. The end of the timer is also
 * armed with AlarmManager.setAlarmClock so it fires exactly on time even in Doze.
 */
class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.example.timer.START"
        const val ACTION_PAUSE = "com.example.timer.PAUSE"
        const val ACTION_RESUME = "com.example.timer.RESUME"
        const val ACTION_ADD = "com.example.timer.ADD_MINUTE"
        const val ACTION_CANCEL = "com.example.timer.CANCEL"
        const val ACTION_FINISH = "com.example.timer.FINISH"
        const val EXTRA_MS = "ms"
        private const val CH_RUN = "timer_running"
        private const val CH_DONE = "timer_done"
        private const val ID_RUN = 1
        private const val ID_DONE = 2
        private val ORANGE = Color.parseColor("#FF9F0A")

        fun command(ctx: Context, action: String, ms: Long = 0L) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, TimerService::class.java).setAction(action).putExtra(EXTRA_MS, ms)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var autoStop: Job? = null
    private lateinit var nm: NotificationManager
    private lateinit var alarmMgr: AlarmManager
    private var ticker: Ticker? = null
    private var alarm: AlarmPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var fgId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TimerStore.init(this)
        nm = getSystemService(NotificationManager::class.java)
        alarmMgr = getSystemService(AlarmManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RUN, "Running timer", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Timer finished", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        // Hold the CPU awake for ticking only while the tick sound is enabled.
        scope.launch { TimerStore.tickEnabled.collect { updateWake() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getLongExtra(EXTRA_MS, 0L))
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_ADD -> addMinute()
            ACTION_FINISH -> if (TimerStore.state.value.phase == Phase.Running) finish()
            ACTION_CANCEL -> cancel()
        }
        val s = TimerStore.state.value
        if (s.phase == Phase.Idle) {
            if (fgId == 0) show(s)   // must call startForeground once before stopping
            shutdown()
        }
        return START_NOT_STICKY
    }

    // ---- state transitions -------------------------------------------------------------

    private fun set(s: TimerState) {
        TimerStore.state.value = s
        if (s.phase != Phase.Idle) show(s)
    }

    private fun start(ms: Long) {
        if (ms <= 0) return
        stopAlarm()
        TimerStore.addRecent(ms)
        begin(ms, ms)
    }

    private fun begin(total: Long, remaining: Long) {
        val end = SystemClock.elapsedRealtime() + remaining
        set(TimerState(Phase.Running, total, remaining, end, System.currentTimeMillis() + remaining))
        scheduleAlarm(remaining)
        runLoop()
    }

    private fun pause() {
        val s = TimerStore.state.value
        if (s.phase != Phase.Running) return
        val rem = (s.endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        stopLoop(); cancelAlarmClock()
        set(s.copy(phase = Phase.Paused, remainingMs = rem))
        updateWake()
    }

    private fun resume() {
        val s = TimerStore.state.value
        if (s.phase == Phase.Paused) begin(s.totalMs, s.remainingMs)
    }

    private fun addMinute() {
        val s = TimerStore.state.value
        when (s.phase) {
            Phase.Running -> begin(
                s.totalMs + 60_000,
                (s.endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 60_000
            )
            Phase.Paused -> set(s.copy(totalMs = s.totalMs + 60_000, remainingMs = s.remainingMs + 60_000))
            Phase.Finished -> { stopAlarm(); begin(60_000, 60_000) }
            Phase.Idle -> Unit
        }
    }

    private fun cancel() {
        stopLoop(); cancelAlarmClock(); stopAlarm()
        TimerStore.state.value = TimerState()
        updateWake()
    }

    private fun finish() {
        if (TimerStore.state.value.phase == Phase.Finished) return
        val s = TimerStore.state.value
        stopLoop(); cancelAlarmClock()
        set(s.copy(phase = Phase.Finished, remainingMs = 0))
        updateWake()
        alarm = (alarm ?: AlarmPlayer(this)).also { it.start() }
        autoStop?.cancel()
        autoStop = scope.launch {           // don't ring forever
            delay(60_000)
            cancel(); shutdown()
        }
    }

    private fun stopAlarm() { autoStop?.cancel(); alarm?.stop() }

    private fun shutdown() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        nm.cancel(ID_RUN); nm.cancel(ID_DONE)
        fgId = 0
        stopSelf()
    }

    // ---- loop / ticking ----------------------------------------------------------------

    private fun runLoop() {
        loop?.cancel()
        updateWake()
        loop = scope.launch {
            while (isActive) {
                val rem = TimerStore.state.value.endElapsed - SystemClock.elapsedRealtime()
                if (rem <= 0) break
                var wait = rem % 1000
                if (wait == 0L) wait = 1000
                delay(wait)
                val left = TimerStore.state.value.endElapsed - SystemClock.elapsedRealtime()
                if (left > 100 && TimerStore.tickEnabled.value) {
                    (ticker ?: Ticker().also { ticker = it }).tick()
                }
            }
            if (isActive) finish()
        }
    }

    private fun stopLoop() { loop?.cancel(); loop = null }

    private fun updateWake() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        val s = TimerStore.state.value
        if (s.phase == Phase.Running && TimerStore.tickEnabled.value) {
            val rem = (s.endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "timer:tick")
                .also { it.acquire(rem + 5_000) }
        }
    }

    private fun finishPi() = PendingIntent.getService(
        this, 10, Intent(this, TimerService::class.java).setAction(ACTION_FINISH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun scheduleAlarm(remaining: Long) {
        try {
            alarmMgr.setAlarmClock(
                AlarmManager.AlarmClockInfo(System.currentTimeMillis() + remaining, openApp()), finishPi()
            )
        } catch (_: SecurityException) { }
    }

    private fun cancelAlarmClock() = alarmMgr.cancel(finishPi())

    // ---- notifications -----------------------------------------------------------------

    private fun openApp() = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun act(action: String, req: Int) = PendingIntent.getForegroundService(
        this, req, Intent(this, TimerService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun show(s: TimerState) {
        val id = if (s.phase == Phase.Finished) ID_DONE else ID_RUN
        val n = build(s)
        if (fgId != id) {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, id, n, type)
            if (id == ID_DONE) nm.cancel(ID_RUN)
            fgId = id
        } else nm.notify(id, n)
    }

    private fun build(s: TimerState): Notification {
        val b = NotificationCompat.Builder(this, if (s.phase == Phase.Finished) CH_DONE else CH_RUN)
            .setSmallIcon(R.drawable.ic_timer)
            .setColor(ORANGE)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        when (s.phase) {
            Phase.Finished -> b.setContentTitle("Time's up")
                .setContentText("Your ${Fmt.compact(s.totalMs)} timer has finished")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOnlyAlertOnce(false)
                .setFullScreenIntent(openApp(), true)
                .addAction(0, "Stop", act(ACTION_CANCEL, 1))
                .addAction(0, "+1 min", act(ACTION_ADD, 2))
            Phase.Paused -> b.setContentTitle("Timer paused")
                .setContentText("${Fmt.clock((s.remainingMs + 999) / 1000)} remaining")
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .addAction(0, "Resume", act(ACTION_RESUME, 3))
                .addAction(0, "Cancel", act(ACTION_CANCEL, 1))
            else -> b.setContentTitle("Timer")
                .setContentText("Ends at ${Fmt.timeOfDay(this, s.endWall)}")
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)   // system draws the live countdown: zero per-second updates
                .setShowWhen(true)
                .setWhen(s.endWall)
                .addAction(0, "Pause", act(ACTION_PAUSE, 4))
                .addAction(0, "+1 min", act(ACTION_ADD, 2))
                .addAction(0, "Cancel", act(ACTION_CANCEL, 1))
        }
        return b.build()
    }

    override fun onDestroy() {
        scope.cancel()
        ticker?.release()
        alarm?.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
