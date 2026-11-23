package com.obdroar.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdroar.app.ui.theme.RoarColors
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun fmt(v: Double?, decimals: Int = 0): String =
    if (v == null) "--" else String.format(Locale.US, "%.${decimals}f", v)

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RoarColors.Surface)
            .border(1.dp, RoarColors.Outline.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        if (title != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = RoarColors.TextDim, modifier = Modifier.weight(1f)
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

/** Automotive-style 270° gauge with ticks, red zone and an animated needle. */
@Composable
fun ArcGauge(
    value: Float?,
    min: Float,
    max: Float,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    redFrom: Float? = null,
    majorTicks: Int = 8,
    decimals: Int = 0,
    tickLabel: (Float) -> String = { it.roundToInt().toString() },
    accent: Color = RoarColors.Orange,
    onInfo: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val target = (((value ?: min) - min) / (max - min)).coerceIn(0f, 1f)
    val frac by animateFloatAsState(
        target, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "gauge"
    )
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            color = RoarColors.TextDim.toArgb()
        }
    }
    val inRed = redFrom != null && value != null && value >= redFrom
    val arcColor = if (inRed) RoarColors.Red else accent

    Box(
        modifier.aspectRatio(1f).let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dim = size.minDimension
            val stroke = dim * 0.06f
            val inset = stroke / 2 + dim * 0.03f
            val arcSize = Size(dim - inset * 2, dim - inset * 2)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            val start = 135f
            val sweep = 270f
            val c = Offset(size.width / 2, size.height / 2)
            val r = arcSize.width / 2

            drawArc(
                color = RoarColors.SurfaceHigh, startAngle = start, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )
            if (redFrom != null) {
                val rf = ((redFrom - min) / (max - min)).coerceIn(0f, 1f)
                drawArc(
                    color = RoarColors.Red.copy(alpha = 0.45f), startAngle = start + sweep * rf,
                    sweepAngle = sweep * (1 - rf), useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt)
                )
            }
            // glow + value arc
            drawArc(
                color = arcColor.copy(alpha = 0.18f), startAngle = start, sweepAngle = sweep * frac, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round)
            )
            drawArc(
                color = arcColor, startAngle = start, sweepAngle = sweep * frac, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )

            // ticks
            paint.textSize = dim * 0.058f
            val minor = majorTicks * 5
            for (i in 0..minor) {
                val ang = Math.toRadians((start + sweep * i / minor).toDouble())
                val major = i % 5 == 0
                val r1 = r - stroke * 1.1f
                val r2 = r1 - if (major) dim * 0.05f else dim * 0.022f
                val p1 = Offset(c.x + r1 * cos(ang).toFloat(), c.y + r1 * sin(ang).toFloat())
                val p2 = Offset(c.x + r2 * cos(ang).toFloat(), c.y + r2 * sin(ang).toFloat())
                drawLine(
                    if (major) Color(0xFFB8C0CC) else Color(0xFF4A5568), p1, p2,
                    strokeWidth = if (major) dim * 0.009f else dim * 0.005f, cap = StrokeCap.Round
                )
                if (major) {
                    val tv = min + (max - min) * i / minor
                    val rl = r2 - dim * 0.065f
                    drawContext.canvas.nativeCanvas.drawText(
                        tickLabel(tv),
                        c.x + rl * cos(ang).toFloat(),
                        c.y + rl * sin(ang).toFloat() + paint.textSize / 3f,
                        paint
                    )
                }
            }

            // needle
            val na = Math.toRadians((start + sweep * frac).toDouble())
            val tip = Offset(c.x + r * 0.78f * cos(na).toFloat(), c.y + r * 0.78f * sin(na).toFloat())
            val tail = Offset(c.x - r * 0.12f * cos(na).toFloat(), c.y - r * 0.12f * sin(na).toFloat())
            drawLine(Color.White, tail, tip, strokeWidth = dim * 0.016f, cap = StrokeCap.Round)
            drawCircle(RoarColors.SurfaceHigh, radius = dim * 0.05f, center = c)
            drawCircle(arcColor, radius = dim * 0.025f, center = c)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (value == null) "--" else fmt(value.toDouble(), decimals),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 26.sp,
                color = if (inRed) RoarColors.Red else Color.White
            )
            Text("$unit · $label", fontSize = 11.sp, color = RoarColors.TextDim)
        }
        if (onInfo != null) InfoIcon(onInfo, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = RoarColors.Orange,
    progress: Float? = null,
    onInfo: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .background(RoarColors.Surface)
            .border(1.dp, RoarColors.Outline.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 12.sp, color = RoarColors.TextDim, maxLines = 1, modifier = Modifier.weight(1f))
            if (onInfo != null) InfoIcon(onInfo, Modifier.size(28.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = 12.sp, color = RoarColors.TextDim, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = accent,
                trackColor = RoarColors.SurfaceHigh,
            )
        }
    }
}

@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier, color: Color = RoarColors.Orange) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val mn = values.min()
        val mx = values.max()
        val range = if (mx - mn < 1e-6f) 1f else mx - mn
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height - (v - mn) / range * size.height * 0.9f - size.height * 0.05f
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun EmptyHint(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = RoarColors.TextDim, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = RoarColors.TextDim, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
