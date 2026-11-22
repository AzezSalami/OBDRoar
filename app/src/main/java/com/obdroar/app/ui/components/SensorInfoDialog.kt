package com.obdroar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdroar.app.obd.Pids
import com.obdroar.app.obd.SensorInfoDb
import com.obdroar.app.ui.theme.RoarColors

/** The small (!) button placed next to a sensor. Has a 32 dp touch target. */
@Composable
fun InfoIcon(onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = RoarColors.Cyan) {
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = "What is this sensor?",
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Explains a sensor: what it is, its unit, normal values, a tip, and the current reading.
 * [code] is an OBD PID, or [SensorInfoDb.BATTERY] for the adapter's voltage reading.
 */
@Composable
fun SensorInfoDialog(code: Int, currentValue: Double?, onDismiss: () -> Unit) {
    val info = SensorInfoDb.get(code)
    val pid = Pids.byCode[code]
    val unit = pid?.unit ?: if (code == SensorInfoDb.BATTERY) "V" else ""
    val decimals = pid?.decimals ?: if (code == SensorInfoDb.BATTERY) 1 else 0
    val title = info?.title ?: pid?.name ?: "Sensor"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.ErrorOutline, null, tint = RoarColors.Cyan) },
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // current reading
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RoarColors.SurfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("CURRENT READING", fontSize = 10.sp, color = RoarColors.TextDim, letterSpacing = 0.8.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                fmt(currentValue, decimals), fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Color.White
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(unit, fontSize = 13.sp, color = RoarColors.TextDim, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    Pill(if (pid != null) "PID %02X".format(code) else "Adapter", RoarColors.Cyan)
                }

                if (info == null) {
                    Text("No description for this sensor yet.", color = RoarColors.TextDim)
                } else {
                    InfoSection("What it is", info.what)
                    InfoSection("Measured in", info.unit)
                    InfoSection("Normal values", info.normal)
                    info.tip?.let { InfoSection("Tip", it, RoarColors.Amber) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}

@Composable
private fun InfoSection(label: String, body: String, accent: Color = RoarColors.Orange) {
    Column {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent, letterSpacing = 0.6.sp)
        Spacer(Modifier.size(2.dp))
        Text(body, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
