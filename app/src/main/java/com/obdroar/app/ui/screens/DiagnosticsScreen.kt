package com.obdroar.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obdroar.app.ConnState
import com.obdroar.app.MainViewModel
import com.obdroar.app.obd.DtcInfo
import com.obdroar.app.obd.Severity
import com.obdroar.app.ui.components.*
import com.obdroar.app.ui.theme.RoarColors
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticsScreen(vm: MainViewModel) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val diag by vm.diag.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    if (conn.state != ConnState.CONNECTED) {
        EmptyHint(Icons.Filled.Build, "Diagnostics need a connection", "Connect to your car to read trouble codes, the check-engine light status and emission readiness.")
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- summary
        val mil = diag.monitor?.milOn
        val total = diag.stored.size + diag.pending.size + diag.permanent.size
        val color = when {
            mil == true -> RoarColors.Red
            total > 0 -> RoarColors.Amber
            else -> RoarColors.Green
        }
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (total == 0 && mil != true) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    null, tint = color, modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            diag.scanning && diag.lastScan == null -> "Scanning…"
                            mil == true -> "Check engine light ON"
                            total > 0 -> "Codes stored, light off"
                            else -> "All clear"
                        },
                        style = MaterialTheme.typography.titleLarge, color = color
                    )
                    Text(
                        "${diag.stored.size} stored · ${diag.pending.size} pending · ${diag.permanent.size} permanent",
                        color = RoarColors.TextDim, fontSize = 13.sp
                    )
                    diag.lastScan?.let {
                        Text(
                            "Last scan " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)),
                            color = RoarColors.TextDim, fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = vm::scanDtcs, enabled = !diag.scanning, modifier = Modifier.weight(1f)) {
                    if (diag.scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(6.dp)); Text("Scan")
                }
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = !diag.clearing && total > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.DeleteSweep, null)
                    Spacer(Modifier.width(6.dp)); Text(if (diag.clearing) "Clearing…" else "Clear codes")
                }
            }
        }

        DtcSection("Stored codes", "Confirmed faults – these usually turn on the check-engine light.", diag.stored)
        DtcSection("Pending codes", "Detected once; will become stored if they happen again.", diag.pending)
        DtcSection("Permanent codes", "Only the car can erase these, after it verifies the repair.", diag.permanent)

        diag.monitor?.let { m ->
            SectionCard(title = "Emission readiness monitors" + if (m.diesel) " (diesel)" else "") {
                val list = m.monitors.filter { it.available }
                if (list.isEmpty()) Text("No monitors reported", color = RoarColors.TextDim)
                list.forEach { mon ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (mon.complete) Icons.Filled.CheckCircle else Icons.Filled.HourglassTop, null,
                            tint = if (mon.complete) RoarColors.Green else RoarColors.Amber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(mon.name, Modifier.weight(1f))
                        Text(
                            if (mon.complete) "Ready" else "Not ready",
                            color = if (mon.complete) RoarColors.Green else RoarColors.Amber, fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "\"Not ready\" is normal right after codes are cleared or the battery was disconnected – drive for a few days to complete them.",
                    fontSize = 11.sp, color = RoarColors.TextDim
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Filled.Warning, null, tint = RoarColors.Amber) },
            title = { Text("Clear all trouble codes?") },
            text = {
                Text(
                    "This turns off the check-engine light and resets emission readiness monitors and freeze-frame data. " +
                        "It does not fix the problem – the code will come back if the fault is still there.\n\n" +
                        "For best results: engine OFF, ignition ON."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearDtcs() }) { Text("Clear codes", color = RoarColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DtcSection(title: String, hint: String, list: List<DtcInfo>) {
    SectionCard(title = "$title (${list.size})") {
        if (list.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = RoarColors.Green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("None", color = RoarColors.TextDim)
            }
        } else {
            Text(hint, fontSize = 12.sp, color = RoarColors.TextDim)
            Spacer(Modifier.height(8.dp))
            list.forEachIndexed { i, dtc ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = RoarColors.Outline)
                DtcItem(dtc)
            }
        }
    }
}

private fun severityColor(s: Severity): Color = when (s) {
    Severity.HIGH -> RoarColors.Red
    Severity.MEDIUM -> RoarColors.Amber
    Severity.LOW -> RoarColors.Cyan
}

@Composable
private fun DtcItem(dtc: DtcInfo) {
    val context = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dtc.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, color = severityColor(dtc.severity)
            )
            Spacer(Modifier.width(10.dp))
            Pill(dtc.severity.label, severityColor(dtc.severity))
            if (dtc.manufacturerSpecific) {
                Spacer(Modifier.width(6.dp)); Pill("Brand-specific", RoarColors.TextDim)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(dtc.description, fontWeight = FontWeight.SemiBold)
        Text(dtc.system, fontSize = 12.sp, color = RoarColors.TextDim)
        if (dtc.causes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Common causes", fontSize = 12.sp, color = RoarColors.TextDim)
            dtc.causes.forEach { Text("•  $it", fontSize = 13.sp) }
        }
        TextButton(
            onClick = {
                val q = Uri.encode("${dtc.code} ${dtc.description} causes fix")
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q")))
                }
            },
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Look up fixes online", fontSize = 13.sp)
        }
    }
}
