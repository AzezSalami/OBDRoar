package com.obdroar.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdroar.app.ui.theme.RoarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Line chart of one sensor over time. Touch and drag across it to read the exact value at any moment.
 */
@Composable
fun TimeChart(
    times: LongArray,
    values: FloatArray,
    unit: String,
    decimals: Int,
    modifier: Modifier = Modifier,
    color: Color = RoarColors.Orange,
) {
    var scrub by remember(times) { mutableStateOf<Int?>(null) }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = RoarColors.TextDim.toArgb()
        }
    }

    if (times.size < 2) {
        Text("Not enough readings yet for a graph.", color = RoarColors.TextDim, fontSize = 13.sp, modifier = modifier)
        return
    }

    var mn = Float.MAX_VALUE
    var mx = -Float.MAX_VALUE
    var sum = 0.0
    for (v in values) { if (v < mn) mn = v; if (v > mx) mx = v; sum += v }
    val avg = sum / values.size
    val pad = if (mx - mn < 1e-6f) maxOf(1f, kotlin.math.abs(mx) * 0.1f) else (mx - mn) * 0.08f
    val lo = mn - pad
    val hi = mx + pad
    val t0 = times.first()
    val t1 = times.last().coerceAtLeast(t0 + 1)

    Column(modifier) {
        // header: scrubbed value or min / avg / max
        val i = scrub
        Row(verticalAlignment = Alignment.Bottom) {
            if (i != null) {
                Text(fmt(values[i].toDouble(), decimals), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.width(4.dp))
                Text("$unit  at ${timeFmt.format(Date(times[i]))}", fontSize = 12.sp, color = RoarColors.TextDim)
            } else {
                Stat("Min", fmt(mn.toDouble(), decimals), unit)
                Spacer(Modifier.width(16.dp))
                Stat("Avg", fmt(avg, decimals), unit)
                Spacer(Modifier.width(16.dp))
                Stat("Max", fmt(mx.toDouble(), decimals), unit)
            }
        }
        Spacer(Modifier.height(8.dp))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(times) {
                    val left = 44.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(x: Float) {
                            val frac = ((x - left) / (size.width - left)).coerceIn(0f, 1f)
                            val t = t0 + ((t1 - t0) * frac).toLong()
                            var idx = java.util.Arrays.binarySearch(times, t)
                            if (idx < 0) idx = (-idx - 1).coerceIn(0, times.size - 1)
                            if (idx > 0 && kotlin.math.abs(times[idx - 1] - t) < kotlin.math.abs(times[idx] - t)) idx--
                            scrub = idx
                        }
                        update(down.position.x)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            update(change.position.x)
                            change.consume()
                        }
                        scrub = null
                    }
                }
        ) {
            val left = 44.dp.toPx()
            val bottom = 18.dp.toPx()
            val w = size.width - left
            val h = size.height - bottom
            fun xOf(t: Long) = left + (t - t0).toFloat() / (t1 - t0) * w
            fun yOf(v: Float) = h - (v - lo) / (hi - lo) * h

            // grid + value labels
            paint.textSize = 10.sp.toPx()
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            for (g in 0..4) {
                val v = lo + (hi - lo) * g / 4f
                val y = yOf(v)
                drawLine(
                    RoarColors.Outline, Offset(left, y), Offset(size.width, y), strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
                drawContext.canvas.nativeCanvas.drawText(fmt(v.toDouble(), decimals), left - 6.dp.toPx(), y + 4.dp.toPx(), paint)
            }
            // time labels
            paint.textAlign = android.graphics.Paint.Align.CENTER
            for (g in 0..2) {
                val t = t0 + (t1 - t0) * g / 2
                val x = (left + w * g / 2f).coerceIn(left + 24.dp.toPx(), size.width - 24.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(timeFmt.format(Date(t)), x, size.height - 2.dp.toPx(), paint)
            }

            // line (thinned to ~1500 points for speed)
            val step = maxOf(1, times.size / 1500)
            val line = Path()
            var k = 0
            while (k < times.size) {
                val x = xOf(times[k]); val y = yOf(values[k])
                if (k == 0) line.moveTo(x, y) else line.lineTo(x, y)
                k += step
            }
            line.lineTo(xOf(times.last()), yOf(values.last()))
            val fill = Path().apply {
                addPath(line)
                lineTo(xOf(times.last()), h)
                lineTo(xOf(times.first()), h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent), startY = 0f, endY = h))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // scrub cursor
            scrub?.let { idx ->
                val x = xOf(times[idx]); val y = yOf(values[idx])
                drawLine(Color.White.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.5f)
                drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(x, y))
                drawCircle(color, radius = 3.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String) {
    Column {
        Text(label.uppercase(), fontSize = 10.sp, color = RoarColors.TextDim)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.width(3.dp))
            Text(unit, fontSize = 11.sp, color = RoarColors.TextDim)
        }
    }
}
