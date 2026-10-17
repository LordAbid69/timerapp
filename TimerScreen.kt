package com.example.timer.ui

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timer.Fmt
import com.example.timer.Phase
import com.example.timer.R
import com.example.timer.TimerService
import com.example.timer.TimerState
import com.example.timer.TimerStore
import kotlinx.coroutines.delay

private val DefaultPresets = listOf(60_000L, 180_000L, 300_000L, 600_000L, 900_000L, 1_800_000L)

@Composable
fun TimerApp() {
    val state by TimerStore.state.collectAsStateWithLifecycle()
    val recents by TimerStore.recents.collectAsStateWithLifecycle()
    val tick by TimerStore.tickEnabled.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var h by rememberSaveable { mutableIntStateOf(0) }
    var m by rememberSaveable { mutableIntStateOf(5) }
    var s by rememberSaveable { mutableIntStateOf(0) }
    val cmd: (String) -> Unit = { TimerService.command(ctx, it) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Pal.Bg).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val landscape = maxWidth > maxHeight
        val w = maxWidth
        val ht = maxHeight
        Crossfade(state.phase == Phase.Idle, animationSpec = tween(250), label = "phase") { idle ->
            if (idle) {
                Setup(
                    landscape, ht, h, m, s,
                    onChange = { nh, nm, ns -> h = nh; m = nm; s = ns },
                    recents = recents.ifEmpty { DefaultPresets }, hasRecents = recents.isNotEmpty(),
                    tick = tick,
                    onStart = { ms -> TimerService.command(ctx, TimerService.ACTION_START, ms) }
                )
            } else {
                Run(landscape, w, ht, state, tick, cmd)
            }
        }
    }
}

// ------------------------------------------------------------------------------ setup pane

@Composable
private fun Setup(
    landscape: Boolean, availH: Dp, h: Int, m: Int, s: Int,
    onChange: (Int, Int, Int) -> Unit,
    recents: List<Long>, hasRecents: Boolean, tick: Boolean,
    onStart: (Long) -> Unit,
) {
    val total = (h * 3600L + m * 60L + s) * 1000
    val ih = if (landscape) (availH * 0.62f / 5).coerceIn(30.dp, 48.dp)
    else (availH * 0.36f / 5).coerceIn(34.dp, 56.dp)

    val picker: @Composable () -> Unit = { PickerRow(h, m, s, onChange, ih) }
    val controls: @Composable () -> Unit = {
        EndsAt(total)
        Spacer(Modifier.height(if (landscape) 10.dp else 14.dp))
        Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
            CircleButton("Reset", Pal.Gray, Color.White, enabled = total > 0) { onChange(0, 0, 0) }
            CircleButton("Start", Pal.GreenBg, Pal.Green, enabled = total > 0) { onStart(total) }
        }
        Spacer(Modifier.height(if (landscape) 10.dp else 18.dp))
        Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recents) { ms -> Chip(Fmt.compact(ms), false) { onStart(ms) } }
            }
            Spacer(Modifier.size(8.dp))
            TickChip(tick)
        }
        if (!hasRecents) Text("Quick start", color = Pal.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }

    if (landscape) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) { picker() }
            Column(Modifier.weight(1f).fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { controls() }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Timer", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            Spacer(Modifier.weight(1f))
            picker()
            Spacer(Modifier.weight(0.6f))
            controls()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PickerRow(h: Int, m: Int, s: Int, onChange: (Int, Int, Int) -> Unit, ih: Dp) {
    Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WheelPicker(24, h, { onChange(it, m, s) }, "hours", ih, Modifier.weight(1f))
        WheelPicker(60, m, { onChange(h, it, s) }, "min", ih, Modifier.weight(1f))
        WheelPicker(60, s, { onChange(h, m, it) }, "sec", ih, Modifier.weight(1f))
    }
}

@Composable
private fun EndsAt(total: Long) {
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(15_000); value = System.currentTimeMillis() }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_bell), null, tint = Pal.Dim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            if (total > 0) "Ends at ${Fmt.timeOfDay(ctx, now + total)}" else "Set a duration",
            color = Pal.Dim, fontSize = 15.sp
        )
    }
}

// -------------------------------------------------------------------------------- run pane

@Composable
private fun Run(landscape: Boolean, availW: Dp, availH: Dp, state: TimerState, tick: Boolean, cmd: (String) -> Unit) {
    val ringSize = if (landscape) minOf(availH * 0.88f, availW * 0.46f) else minOf(availW * 0.88f, availH * 0.5f)
    val buttons: @Composable () -> Unit = {
        Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
            when (state.phase) {
                Phase.Finished -> {
                    CircleButton("+1 min", Pal.Gray, Color.White) { cmd(TimerService.ACTION_ADD) }
                    CircleButton("Stop", Pal.Red, Color.White) { cmd(TimerService.ACTION_CANCEL) }
                }
                Phase.Paused -> {
                    CircleButton("Cancel", Pal.Gray, Color.White) { cmd(TimerService.ACTION_CANCEL) }
                    CircleButton("Resume", Pal.GreenBg, Pal.Green) { cmd(TimerService.ACTION_RESUME) }
                }
                else -> {
                    CircleButton("Cancel", Pal.Gray, Color.White) { cmd(TimerService.ACTION_CANCEL) }
                    CircleButton("Pause", Pal.OrangeBg, Pal.Orange) { cmd(TimerService.ACTION_PAUSE) }
                }
            }
        }
    }
    val extras: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.phase != Phase.Finished) Chip("+1:00", false) { cmd(TimerService.ACTION_ADD) }
            TickChip(tick)
        }
    }
    if (landscape) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) { Ring(state, ringSize) }
            Column(Modifier.weight(1f).fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                buttons(); Spacer(Modifier.height(16.dp)); extras()
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Ring(state, ringSize)
            Spacer(Modifier.weight(1f))
            buttons()
            Spacer(Modifier.height(18.dp))
            extras()
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Live remaining time, updated per frame only while running (drawn, never recomposed). */
@Composable
private fun rememberLiveRemaining(state: TimerState): MutableLongState {
    val live = remember { mutableLongStateOf(state.remainingMs) }
    LaunchedEffect(state) {
        when (state.phase) {
            Phase.Running -> while (true) {
                androidx.compose.runtime.withFrameMillis {
                    live.longValue = (state.endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                }
            }
            Phase.Finished -> live.longValue = 0
            else -> live.longValue = state.remainingMs
        }
    }
    return live
}

@Composable
private fun Ring(state: TimerState, ringSize: Dp) {
    val ctx = LocalContext.current
    val live = rememberLiveRemaining(state)
    val secs by remember { derivedStateOf { (live.longValue + 999) / 1000 } }
    val finished = state.phase == Phase.Finished
    val paused = state.phase == Phase.Paused
    val pulse = if (finished) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            1f, 0.35f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pa"
        ).value
    } else 1f
    val color = if (finished) Pal.Red else Pal.Orange

    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.045f
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Pal.Gray, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            val frac = if (finished) 1f
            else if (state.totalMs > 0) (live.longValue.toFloat() / state.totalMs).coerceIn(0f, 1f) else 0f
            drawArc(color.copy(alpha = pulse), -90f, 360f * frac, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Fmt.clock(secs),
                color = Color.White.copy(alpha = if (paused) 0.6f else 1f),
                fontSize = (ringSize.value * 0.19f).sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tint = if (finished) Pal.Red else Pal.Dim
                Icon(painterResource(R.drawable.ic_bell), null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    when {
                        finished -> "Time's up"
                        paused -> "Paused"
                        else -> Fmt.timeOfDay(ctx, state.endWall)
                    },
                    color = tint, fontSize = 16.sp
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------ components

@Composable
private fun CircleButton(text: String, bg: Color, fg: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(stiffness = 600f), label = "scale")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "alpha")
    Box(
        Modifier.size(88.dp).scale(scale).alpha(alpha)
            .border(1.5.dp, bg.copy(alpha = 0.9f), CircleShape).padding(5.dp)
            .clip(CircleShape).background(bg)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) { Text(text, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) Pal.Orange.copy(alpha = 0.2f) else Pal.Card)
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Pal.Orange else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun TickChip(tick: Boolean) =
    Chip(if (tick) "Tick  On" else "Tick  Off", tick) { TimerStore.setTick(!tick) }
