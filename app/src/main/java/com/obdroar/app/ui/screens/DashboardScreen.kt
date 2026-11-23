package com.obdroar.app.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obdroar.app.ConnState
import com.obdroar.app.MainViewModel
import com.obdroar.app.obd.Pids
import com.obdroar.app.obd.SensorInfoDb
import com.obdroar.app.ui.components.*
import com.obdroar.app.ui.theme.RoarColors

@Composable
fun DashboardScreen(vm: MainViewModel, onConnect: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val diag by vm.diag.collectAsStateWithLifecycle()
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()

    fun v(code: Int) = live[code]?.value
    var infoFor by remember { mutableStateOf<Int?>(null) }
    var chartFor by remember { mutableStateOf<Int?>(null) }
    val batteryCode = if (vehicle.voltage != null) SensorInfoDb.BATTERY else Pids.MODULE_VOLTAGE

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (conn.state != ConnState.CONNECTED) {
            ConnectHero(conn.state, conn.status, conn.error, onConnect)
        } else {
            StatusBanner(
                milOn = diag.monitor?.milOn,
                codes = diag.stored.size + diag.pending.size,
                scanning = diag.scanning,
                onClick = onOpenDiagnostics
            )
        }

        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArcGauge(
                    value = v(Pids.RPM)?.toFloat(), min = 0f, max = 8000f, label = "ENGINE", unit = "rpm",
                    modifier = Modifier.weight(1f), redFrom = 6500f,
                    tickLabel = { (it / 1000).toInt().toString() },
                    onInfo = { infoFor = Pids.RPM },
                    onClick = { chartFor = Pids.RPM }
                )
                ArcGauge(
                    value = v(Pids.SPEED)?.toFloat(), min = 0f, max = 240f, label = "SPEED", unit = "km/h",
                    modifier = Modifier.weight(1f), accent = RoarColors.Cyan,
                    tickLabel = { it.toInt().toString() },
                    onInfo = { infoFor = Pids.SPEED },
                    onClick = { chartFor = Pids.SPEED }
                )
            }
        }

        val tiles = listOf(
            Tile(Pids.COOLANT, "Coolant", fmt(v(Pids.COOLANT)), "°C", Icons.Filled.Thermostat,
                if ((v(Pids.COOLANT) ?: 0.0) > 105) RoarColors.Red else RoarColors.Cyan,
                v(Pids.COOLANT)?.let { ((it + 40) / 170).toFloat() }),
            Tile(Pids.LOAD, "Engine load", fmt(v(Pids.LOAD)), "%", Icons.Filled.Speed, RoarColors.Orange,
                v(Pids.LOAD)?.let { (it / 100).toFloat() }),
            Tile(Pids.THROTTLE, "Throttle", fmt(v(Pids.THROTTLE)), "%", Icons.Filled.Tune, RoarColors.Amber,
                v(Pids.THROTTLE)?.let { (it / 100).toFloat() }),
            Tile(Pids.IAT, "Intake air", fmt(v(Pids.IAT)), "°C", Icons.Filled.Air, RoarColors.Cyan, null),
            Tile(Pids.MAF, "Air flow (MAF)", fmt(v(Pids.MAF), 1), "g/s", Icons.Filled.Air, RoarColors.Green, null),
            Tile(Pids.MAP, "Manifold", fmt(v(Pids.MAP)), "kPa", Icons.Filled.Sensors, RoarColors.Green, null),
            Tile(batteryCode, "Battery", fmt(vehicle.voltage ?: v(Pids.MODULE_VOLTAGE), 1), "V", Icons.Filled.BatteryChargingFull,
                batteryColor(vehicle.voltage ?: v(Pids.MODULE_VOLTAGE)), null),
            Tile(Pids.FUEL_LEVEL, "Fuel level", fmt(v(Pids.FUEL_LEVEL)), "%", Icons.Filled.LocalGasStation, RoarColors.Amber,
                v(Pids.FUEL_LEVEL)?.let { (it / 100).toFloat() }),
            Tile(Pids.STFT1, "Fuel trim ST/LT", "${fmt(v(Pids.STFT1))}/${fmt(v(Pids.LTFT1))}", "%", Icons.Filled.Tune,
                RoarColors.Orange, null),
            Tile(Pids.OIL_TEMP, "Oil temp", fmt(v(Pids.OIL_TEMP)), "°C", Icons.Filled.Opacity, RoarColors.Amber, null),
            Tile(Pids.TIMING, "Timing advance", fmt(v(Pids.TIMING), 1), "°", Icons.Filled.Timer, RoarColors.Cyan, null),
            Tile(Pids.FUEL_RATE, "Fuel rate", fmt(v(Pids.FUEL_RATE), 1), "L/h", Icons.Filled.LocalGasStation, RoarColors.Green, null),
        )
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t ->
                    StatTile(
                        t.label, t.value, t.unit, t.icon, Modifier.weight(1f), t.color, t.progress,
                        onInfo = { infoFor = t.code },
                        onClick = { chartFor = t.code }
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    chartFor?.let { code ->
        SensorHistorySheet(vm, code) { chartFor = null }
    }

    infoFor?.let { code ->
        SensorInfoDialog(
            code = code,
            currentValue = if (code == SensorInfoDb.BATTERY) vehicle.voltage else v(code),
            onDismiss = { infoFor = null }
        )
    }
}

private data class Tile(
    val code: Int,
    val label: String, val value: String, val unit: String, val icon: ImageVector,
    val color: Color, val progress: Float?
)

private fun batteryColor(v: Double?): Color = when {
    v == null -> RoarColors.Green
    v < 11.8 -> RoarColors.Red
    v < 12.4 -> RoarColors.Amber
    v > 15.0 -> RoarColors.Red
    else -> RoarColors.Green
}

@Composable
private fun ConnectHero(state: ConnState, status: String, error: String?, onConnect: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (state == ConnState.CONNECTING) Icons.Filled.BluetoothSearching else Icons.Filled.Bluetooth,
                null, tint = RoarColors.Orange, modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state == ConnState.CONNECTING) "Connecting…" else "Connect to your car",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    when {
                        state == ConnState.CONNECTING -> status
                        error != null -> error
                        else -> "Plug the ELM327 adapter into the OBD2 port, switch the ignition ON and pair it in Bluetooth settings (PIN 1234 or 0000)."
                    },
                    color = if (error != null && state != ConnState.CONNECTING) RoarColors.Red else RoarColors.TextDim,
                    fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (state == ConnState.CONNECTING) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = RoarColors.Orange)
        } else {
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.BluetoothSearching, null)
                Spacer(Modifier.width(8.dp))
                Text("Choose adapter", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusBanner(milOn: Boolean?, codes: Int, scanning: Boolean, onClick: () -> Unit) {
    val bad = milOn == true || codes > 0
    val color = if (bad) RoarColors.Amber else RoarColors.Green
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (bad) Icons.Filled.Warning else Icons.Filled.CheckCircle, null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        scanning -> "Scanning for problems…"
                        milOn == true -> "Check engine light is ON"
                        codes > 0 -> "$codes trouble code${if (codes > 1) "s" else ""} found"
                        else -> "No problems detected"
                    },
                    fontWeight = FontWeight.Bold, color = color
                )
                Text(
                    if (bad) "Tap to see what's wrong and how to fix it" else "Engine computer reports no trouble codes",
                    fontSize = 12.sp, color = RoarColors.TextDim
                )
            }
            if (scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = color)
            else Icon(Icons.Filled.ChevronRight, null, tint = RoarColors.TextDim)
        }
    }
}
