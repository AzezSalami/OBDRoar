package com.obdroar.app.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obdroar.app.MainViewModel
import com.obdroar.app.data.HistoryStats
import com.obdroar.app.data.LoggedDtc
import com.obdroar.app.data.SessionSummary
import com.obdroar.app.data.Series
import com.obdroar.app.obd.DtcDatabase
import com.obdroar.app.obd.Pids
import com.obdroar.app.obd.Sensors
import com.obdroar.app.ui.components.*
import com.obdroar.app.ui.theme.RoarColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: MainViewModel) {
    var openSession by rememberSaveable { mutableStateOf<Long?>(null) }
    val id = openSession
    if (id == null) {
        SessionList(vm, onOpen = { openSession = it })
    } else {
        BackHandler { openSession = null }
        SessionDetail(vm, id, onBack = { openSession = null })
    }
}

/** Re-runs every 5 s while [active], so the session being recorded updates live. */
@Composable
private fun rememberTicker(active: Boolean): Int {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        while (active) { delay(5000); tick++ }
    }
    return tick
}

// ============================================================================ list

@Composable
private fun SessionList(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val version by vm.historyVersion.collectAsStateWithLifecycle()
    val recording by vm.recordHistory.collectAsStateWithLifecycle()
    val currentId by vm.currentSessionId.collectAsStateWithLifecycle()
    val tick = rememberTicker(currentId != null)
    val sessions by produceState<List<SessionSummary>?>(null, version, tick) {
        value = runCatching { vm.history.sessions() }.getOrDefault(emptyList())
    }
    val stats by produceState<HistoryStats?>(null, version, tick) {
        value = runCatching { vm.history.stats() }.getOrNull()
    }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(title = "Saved history") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, null, tint = RoarColors.Orange)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Save readings while connected", fontWeight = FontWeight.SemiBold)
                        Text(
                            "One reading per sensor per second, plus every trouble code found.",
                            fontSize = 12.sp, color = RoarColors.TextDim
                        )
                    }
                    Switch(checked = recording, onCheckedChange = vm::setRecordHistory)
                }
                stats?.let { st ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${st.sessions} drive${if (st.sessions == 1) "" else "s"} · " +
                            "${String.format(Locale.getDefault(), "%,d", st.samples)} readings · ${formatBytes(st.bytes)}",
                        fontSize = 12.sp, color = RoarColors.TextDim
                    )
                }
                if ((stats?.sessions ?: 0) > 0) {
                    TextButton(onClick = { confirmDeleteAll = true }, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Filled.DeleteForever, null, tint = RoarColors.Red, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete all history", color = RoarColors.Red)
                    }
                }
            }
        }

        val list = sessions
        when {
            list == null -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            list.isEmpty() -> item {
                EmptyHint(
                    Icons.Filled.History, "No saved drives yet",
                    "Connect to your car (or the demo car) and the readings will be saved here automatically."
                )
            }
            else -> items(list, key = { it.id }) { s ->
                SessionCard(s, isLive = s.id == currentId) { onOpen(s.id) }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            icon = { Icon(Icons.Filled.DeleteForever, null, tint = RoarColors.Red) },
            title = { Text("Delete all saved history?") },
            text = { Text("All saved drives, readings and logged trouble codes will be permanently removed from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDeleteAll = false; vm.deleteAllHistory() }) { Text("Delete all", color = RoarColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SessionCard(s: SessionSummary, isLive: Boolean, onClick: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDateTime(s.startMs), fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(formatDuration((s.endMs ?: System.currentTimeMillis()) - s.startMs), s.device)
                        .joinToString(" · "),
                    fontSize = 12.sp, color = RoarColors.TextDim
                )
            }
            if (isLive) Pill("● Recording", RoarColors.Red)
            else if (s.demo) Pill("Demo", RoarColors.Cyan)
            Icon(Icons.Filled.ChevronRight, null, tint = RoarColors.TextDim)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MiniStat("Max RPM", fmt(s.maxRpm))
            MiniStat("Top speed", s.maxSpeed?.let { fmt(it) + " km/h" } ?: "--")
            MiniStat("Readings", String.format(Locale.getDefault(), "%,d", s.samples))
            MiniStat(
                "Codes", if (s.dtcCount == 0) "None" else s.dtcCount.toString(),
                if (s.dtcCount > 0) RoarColors.Amber else RoarColors.Green
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White) {
    Column {
        Text(label, fontSize = 10.sp, color = RoarColors.TextDim)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = color)
    }
}

// ============================================================================ detail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetail(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by vm.historyVersion.collectAsStateWithLifecycle()
    val currentId by vm.currentSessionId.collectAsStateWithLifecycle()
    val live = currentId == id
    val tick = rememberTicker(live)

    val summary by produceState<SessionSummary?>(null, id, version, tick) {
        value = runCatching { vm.history.session(id) }.getOrNull()
    }
    val pids by produceState<List<Int>>(emptyList(), id, tick) {
        value = runCatching { vm.history.pids(id) }.getOrDefault(emptyList())
    }
    var selected by rememberSaveable(id) { mutableStateOf<Int?>(null) }
    val pid = selected ?: pids.firstOrNull { it == Pids.RPM } ?: pids.firstOrNull()
    val series by produceState<Series?>(null, id, pid, tick) {
        value = pid?.let { p -> runCatching { vm.history.series(id, p) }.getOrNull() }
    }
    val dtcs by produceState<List<LoggedDtc>>(emptyList(), id, version, tick) {
        value = runCatching { vm.history.dtcs(id) }.getOrDefault(emptyList())
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(summary?.let { formatDateTime(it.startMs) } ?: "Drive", style = MaterialTheme.typography.titleLarge)
                summary?.let { s ->
                    Text(
                        listOfNotNull(
                            formatDuration((s.endMs ?: System.currentTimeMillis()) - s.startMs),
                            s.device, s.vin
                        ).joinToString(" · "),
                        fontSize = 12.sp, color = RoarColors.TextDim
                    )
                }
            }
            if (live) Pill("● Recording", RoarColors.Red)
        }

        if (pids.isEmpty()) {
            EmptyHint(Icons.Filled.ShowChart, "No readings saved", "This drive has no sensor readings yet.")
        } else {
            SectionCard(title = "Sensor") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pids) { p ->
                        FilterChip(
                            selected = p == pid,
                            onClick = { selected = p },
                            label = { Text(Sensors.name(p), maxLines = 1) }
                        )
                    }
                }
            }
            SectionCard(title = pid?.let { Sensors.name(it) } ?: "") {
                val s = series
                if (s == null || pid == null) {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    TimeChart(s.times, s.values, Sensors.unit(pid), Sensors.decimals(pid))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${s.times.size} readings · touch and drag the graph to read any moment",
                        fontSize = 11.sp, color = RoarColors.TextDim
                    )
                }
            }
        }

        SectionCard(title = "Trouble codes seen on this drive") {
            if (dtcs.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = RoarColors.Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("None", color = RoarColors.TextDim)
                }
            } else {
                dtcs.forEach { d ->
                    val info = DtcDatabase.lookup(d.code)
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = RoarColors.Amber)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info.description, fontSize = 13.sp)
                            Text(
                                "${d.kind.replaceFirstChar { it.uppercase() }} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(d.timeMs))}",
                                fontSize = 11.sp, color = RoarColors.TextDim
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    exporting = true
                    scope.launch {
                        val file = vm.exportCsv(id)
                        exporting = false
                        if (file != null) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, file.name)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(Intent.createChooser(send, "Export drive")) }
                        }
                    }
                },
                enabled = !exporting && pids.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                if (exporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Share, null)
                Spacer(Modifier.width(6.dp)); Text("Export CSV")
            }
            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = !live,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete")
            }
        }
        if (live) {
            Text(
                "This drive is still being recorded. Disconnect to finish it.",
                fontSize = 11.sp, color = RoarColors.TextDim
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.Delete, null, tint = RoarColors.Red) },
            title = { Text("Delete this drive?") },
            text = { Text("Its saved readings and logged trouble codes will be permanently removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteSession(id); onBack() }) {
                    Text("Delete", color = RoarColors.Red)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

// ============================================================================ formatting

private fun formatDateTime(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "${h} h ${m} min"
        m > 0 -> "${m} min"
        else -> "${s} s"
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", b / 1_000_000.0)
    b >= 1_000 -> String.format(Locale.getDefault(), "%.0f kB", b / 1_000.0)
    else -> "$b B"
}
