package com.obdroar.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obdroar.app.ConnState
import com.obdroar.app.MainViewModel
import com.obdroar.app.obd.PidDef
import com.obdroar.app.obd.Pids
import com.obdroar.app.Reading
import com.obdroar.app.ui.components.*
import com.obdroar.app.ui.theme.RoarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDataScreen(vm: MainViewModel) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf("All") }
    var chartFor by remember { mutableStateOf<Int?>(null) }
    var infoFor by remember { mutableStateOf<Int?>(null) }

    val connected = conn.state == ConnState.CONNECTED
    // After the link drops, keep showing the last readings (like the Dashboard does).
    if (!connected && live.isEmpty()) {
        EmptyHint(Icons.Filled.ShowChart, "No live data yet", "Connect to an OBD2 adapter (or the demo car) to stream every sensor your engine reports.")
        return
    }

    val pids: List<PidDef> = Pids.all.filter { it.code in live.keys || it.code in vehicle.supported }
    val categories = listOf("All") + pids.map { it.category }.distinct()
    val shown = pids.filter { category == "All" || it.category == category }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { c ->
                FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c) })
            }
        }
        if (!connected) {
            Text(
                if (conn.state == ConnState.CONNECTING) "Reconnecting… showing the last readings"
                else "Not connected · showing the last readings",
                fontSize = 12.sp, color = RoarColors.Amber,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)
            )
        }
        Text(
            "${live.size} of ${pids.size} sensors ${if (connected) "reporting" else "read"} · tap a sensor for its graph, (!) for an explanation",
            fontSize = 12.sp, color = RoarColors.TextDim, modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(shown, key = { it.code }) { pid ->
                PidRow(pid, live[pid.code], onInfo = { infoFor = pid.code }) { chartFor = pid.code }
            }
        }
    }

    chartFor?.let { code ->
        SensorHistorySheet(vm, code) { chartFor = null }
    }

    infoFor?.let { code ->
        SensorInfoDialog(code, live[code]?.value) { infoFor = null }
    }
}

@Composable
private fun PidRow(pid: PidDef, reading: Reading?, onInfo: () -> Unit, onClick: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pid.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    InfoIcon(onInfo, Modifier.size(28.dp))
                }
                Text(
                    "PID %02X".format(pid.code) + if (reading != null)
                        " · min ${fmt(reading.min, pid.decimals)} · max ${fmt(reading.max, pid.decimals)}" else " · waiting…",
                    fontSize = 11.sp, color = RoarColors.TextDim
                )
            }
            if (reading != null) {
                Sparkline(reading.history.takeLast(40), Modifier.width(64.dp).height(28.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                fmt(reading?.value, pid.decimals), fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 20.sp
            )
            Spacer(Modifier.width(4.dp))
            Text(pid.unit, fontSize = 12.sp, color = RoarColors.TextDim)
        }
    }
}
