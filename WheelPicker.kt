package com.example.timer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/** iOS-style snapping wheel with haptic detents. [visible] must be odd. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    itemHeight: Dp,
    modifier: Modifier = Modifier,
    visible: Int = 5,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value)
    val fling = rememberSnapFlingBehavior(state)
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val haptic = LocalHapticFeedback.current

    val selected by remember {
        derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0)
                .coerceIn(0, count - 1)
        }
    }

    // Report only once the wheel has settled (avoids fighting programmatic scrolls).
    LaunchedEffect(Unit) {
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().filter { !it }
            .collect { onValueChange(selected) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { selected }.drop(1).collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    // External changes (e.g. tapping a recent timer or Reset).
    LaunchedEffect(value) {
        if (value != selected && !state.isScrollInProgress) state.animateScrollToItem(value)
    }

    Box(modifier.height(itemHeight * visible)) {
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(itemHeight)
                .clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.12f))
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            LazyColumn(
                state = state,
                flingBehavior = fling,
                contentPadding = PaddingValues(vertical = itemHeight * (visible / 2)),
                modifier = Modifier.weight(1f).fillMaxSize()
            ) {
                items(count) { i ->
                    val d = abs(i - selected)
                    Box(Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = i.toString(),
                            color = Color.White.copy(alpha = when (d) { 0 -> 1f; 1 -> 0.5f; else -> 0.22f }),
                            fontSize = (itemHeight.value * 0.62f).sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.End,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }
            Text(
                label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
    }
}
