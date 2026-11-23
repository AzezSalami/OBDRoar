package com.obdroar.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obdroar.app.MainViewModel
import com.obdroar.app.data.SessionSummary
import com.obdroar.app.data.Series
import com.obdroar.app.obd.Sensors
import com.obdroar.app.ui.components.*
import com.obdroar.app.ui.theme.RoarColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Bottom sheet with the saved graph of one sensor. Opens on the drive being recorded now
 * (or the newest drive) and lets you switch to any earlier drive.
 * [code] is an OBD PID or SensorInfoDb.BATTERY.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorHistorySheet(vm: MainViewModel, code: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by vm.historyVersion.collectAsStateWithLifecycle()
    val currentId by vm.currentSessionId.collectAsStateWithLifecycle()
    val recording by vm.recordHistory.collectAsStateWithLifecycle()

    var tick by remember { mutableIntStateOf(0) }
    val sessions by produceState<List<SessionSummary>?>(null, version, tick) {
        value = runCatching { vm.history.sessions() }.getOrDefault(emptyList())
    }
    var selected by remember { mutableStateOf<Long?>(null) }
    val sessionId = selected ?: currentId ?: sessions?.firstOrNull()?.id
    val live = sessionId != null && sessionId == currentId

    // refresh every 2 s while looking at the drive being recorded
    LaunchedEffect(live) {
        while (live) { delay(2000); tick++ }
    }
    val series by produceState<Series?>(null, sessionId, code, tick) {
        value = sessionId?.let { id -> runCatching { vm.history.series(id, code) }.getOrNull() }
    }
    var exporting by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RoarColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Sensors.name(code), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (live) Pill("● Live", RoarColors.Red)
                TextButton(onClick = onDismiss) { Text("Close") }
            }

            val list = sessions
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() || sessionId == null -> EmptyHint(
                    Icons.Filled.ShowChart, "No saved readings yet",
                    if (recording) "Connect to your car (or the demo car) and the readings will be saved for the graph."
                    else "Saving readings is switched off. Turn it on in the Car tab."
                )
                else -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it.id }) { s ->
                            FilterChip(
                                selected = s.id == sessionId,
                                onClick = { selected = s.id },
                                label = {
                                    Text(if (s.id == currentId) "This drive" else formatDateTime(s.startMs), maxLines = 1)
                                }
                            )
                        }
                    }
                    val s = series
                    when {
                        s == null -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        else -> {
                            TimeChart(s.times, s.values, Sensors.unit(code), Sensors.decimals(code))
                            if (s.times.size >= 2) {
                                Text(
                                    "${s.times.size} readings · touch and drag the graph to read any moment",
                                    fontSize = 11.sp, color = RoarColors.TextDim
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            exporting = true
                            scope.launch {
                                val file = vm.exportCsv(sessionId)
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
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (exporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Share, null)
                        Spacer(Modifier.width(6.dp)); Text("Export this drive as CSV")
                    }
                }
            }
        }
    }
}

internal fun formatDateTime(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))

internal fun formatBytes(b: Long): String = when {
    b >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", b / 1_000_000.0)
    b >= 1_000 -> String.format(Locale.getDefault(), "%.0f kB", b / 1_000.0)
    else -> "$b B"
}
