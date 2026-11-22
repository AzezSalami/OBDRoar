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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obdroar.app.BuildConfig
import com.obdroar.app.ConnState
import com.obdroar.app.MainViewModel
import com.obdroar.app.obd.Pids
import com.obdroar.app.ui.components.*
import com.obdroar.app.ui.theme.RoarColors

@Composable
fun VehicleScreen(vm: MainViewModel, onConnect: () -> Unit) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val connected = conn.state == ConnState.CONNECTED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionCard(title = "Vehicle") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsCar, null, tint = RoarColors.Orange, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        listOfNotNull(vehicle.year?.toString(), vehicle.make).joinToString(" ").ifBlank {
                            if (connected) "Unknown make" else "Not connected"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        vehicle.vin ?: if (connected) "VIN not reported by this car" else "--",
                        fontFamily = FontFamily.Monospace, color = RoarColors.TextDim, fontSize = 14.sp
                    )
                }
            }
        }

        SectionCard(title = "Connection") {
            InfoRow(Icons.Filled.Bluetooth, "Adapter", conn.device ?: "--")
            InfoRow(Icons.Filled.Memory, "Chip", vehicle.adapter.ifBlank { "--" })
            InfoRow(Icons.Filled.SettingsEthernet, "Protocol", vehicle.protocol.ifBlank { "--" })
            InfoRow(Icons.Filled.BatteryChargingFull, "Battery voltage", vehicle.voltage?.let { fmt(it, 1) + " V" } ?: "--")
            Spacer(Modifier.height(10.dp))
            if (connected) {
                OutlinedButton(onClick = vm::disconnect, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.LinkOff, null); Spacer(Modifier.width(6.dp)); Text("Disconnect")
                }
            } else {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.BluetoothSearching, null); Spacer(Modifier.width(6.dp)); Text("Connect")
                }
            }
        }

        if (vehicle.supported.isNotEmpty()) {
            SectionCard(title = "Supported sensors (${vehicle.supported.size})") {
                val known = vehicle.supported.mapNotNull { Pids.byCode[it] }
                known.forEach { Text("•  ${it.name}", fontSize = 13.sp) }
                val unknown = vehicle.supported.filter { Pids.byCode[it] == null && it % 0x20 != 0 && it != Pids.MONITOR_STATUS }
                if (unknown.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Other PIDs: " + unknown.joinToString(" ") { "%02X".format(it) },
                        fontSize = 11.sp, color = RoarColors.TextDim, fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        SectionCard(title = "About") {
            InfoRow(Icons.Filled.Info, "App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Spacer(Modifier.height(6.dp))
            Text(
                "Works with ELM327 Bluetooth Classic adapters on cars that support OBD2 " +
                    "(petrol 2001+ EU / 1996+ US, diesel 2004+ EU). Reading data is safe. " +
                    "Never interact with the app while driving.",
                fontSize = 12.sp, color = RoarColors.TextDim
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = RoarColors.TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = RoarColors.TextDim, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
