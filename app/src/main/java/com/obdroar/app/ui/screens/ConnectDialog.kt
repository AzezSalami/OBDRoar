package com.obdroar.app.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdroar.app.ui.components.Pill
import com.obdroar.app.ui.theme.RoarColors

private val obdHints = listOf("OBD", "ELM", "V-LINK", "VLINK", "VGATE", "VEEPEAK", "KONNWEI", "ICAR", "BAFX", "CAR", "SCAN", "OBDII")

@SuppressLint("MissingPermission")
private fun pairedDevices(context: Context): List<BluetoothDevice> = runCatching {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    adapter.bondedDevices.orEmpty().sortedByDescending { d ->
        val n = (d.name ?: "").uppercase()
        obdHints.any { n.contains(it) }
    }
}.getOrDefault(emptyList())

@SuppressLint("MissingPermission")
@Composable
fun ConnectDialog(
    onDismiss: () -> Unit,
    onDevice: (BluetoothDevice) -> Unit,
    onDemo: () -> Unit,
) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf(pairedDevices(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.BluetoothSearching, null, tint = RoarColors.Orange) },
        title = { Text("Choose your OBD2 adapter") },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text(
                        "No paired devices found. Pair your ELM327 adapter in Android Bluetooth settings first " +
                            "(usually called OBDII, V-LINK or similar; PIN 1234 or 0000).",
                        fontSize = 13.sp, color = RoarColors.TextDim
                    )
                }
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(devices, key = { it.address }) { d ->
                        val name = runCatching { d.name }.getOrNull() ?: "Unknown"
                        val likely = obdHints.any { name.uppercase().contains(it) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onDevice(d) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Bluetooth, null,
                                tint = if (likely) RoarColors.Orange else RoarColors.TextDim
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Text(d.address, fontSize = 11.sp, color = RoarColors.TextDim)
                            }
                            if (likely) Pill("OBD", RoarColors.Orange)
                        }
                    }
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = RoarColors.Outline)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onDemo() }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Science, null, tint = RoarColors.Cyan)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Demo car", fontWeight = FontWeight.SemiBold)
                                Text("Try the app without an adapter", fontSize = 11.sp, color = RoarColors.TextDim)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            }) { Text("Bluetooth settings") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { devices = pairedDevices(context) }) { Text("Refresh") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
