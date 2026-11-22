package com.obdroar.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obdroar.app.ui.screens.ConnectDialog
import com.obdroar.app.ui.screens.DashboardScreen
import com.obdroar.app.ui.screens.DiagnosticsScreen
import com.obdroar.app.ui.screens.LiveDataScreen
import com.obdroar.app.ui.screens.SoundScreen
import com.obdroar.app.ui.screens.VehicleScreen
import com.obdroar.app.ui.theme.OBDRoarTheme
import com.obdroar.app.ui.theme.RoarColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { OBDRoarTheme { App(vm) } }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.onAppBackground()
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Dashboard", Icons.Filled.Dashboard),
    Tab("Live data", Icons.Filled.ShowChart),
    Tab("Diagnose", Icons.Filled.Build),
    Tab("Sound", Icons.Filled.GraphicEq),
    Tab("Vehicle", Icons.Filled.DirectionsCar),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: MainViewModel) {
    val context = LocalContext.current
    val conn by vm.conn.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(conn.error) { conn.error?.let { snackbar.showSnackbar(it) } }

    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        showPicker = true
    }

    fun openPicker() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter != null && !adapter.isEnabled) {
            runCatching { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                .onFailure { showPicker = true }
        } else {
            showPicker = true
        }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) openPicker()
        else {
            showPicker = true // demo mode still works
            scope.launch { snackbar.showSnackbar("Bluetooth permission is needed to talk to the adapter") }
        }
    }

    val onConnect: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            openPicker()
        }
    }

    Scaffold(
        containerColor = RoarColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("OBD", fontWeight = FontWeight.Black)
                        Text(" ROAR", fontWeight = FontWeight.Black, color = RoarColors.Orange)
                    }
                },
                actions = {
                    val (label, color) = when (conn.state) {
                        ConnState.CONNECTED -> (conn.device ?: "Connected") to RoarColors.Green
                        ConnState.CONNECTING -> "Connecting…" to RoarColors.Amber
                        ConnState.DISCONNECTED -> "Connect" to RoarColors.TextDim
                    }
                    AssistChip(
                        onClick = { if (conn.state == ConnState.CONNECTED) tab = 4 else onConnect() },
                        label = { Text(label, maxLines = 1) },
                        leadingIcon = {
                            Canvas(Modifier.size(8.dp)) { drawCircle(color) }
                        },
                        colors = AssistChipDefaults.assistChipColors(labelColor = color),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoarColors.Background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = RoarColors.Surface) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, t.title) },
                        label = { Text(t.title, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RoarColors.Orange,
                            selectedTextColor = RoarColors.Orange,
                            indicatorColor = RoarColors.Orange.copy(alpha = 0.15f),
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> DashboardScreen(vm, onConnect = onConnect, onOpenDiagnostics = { tab = 2 })
                1 -> LiveDataScreen(vm)
                2 -> DiagnosticsScreen(vm)
                3 -> SoundScreen(vm)
                else -> VehicleScreen(vm, onConnect = onConnect)
            }
        }
    }

    if (showPicker) {
        ConnectDialog(
            onDismiss = { showPicker = false },
            onDevice = { showPicker = false; vm.connectBluetooth(it); tab = 0 },
            onDemo = { showPicker = false; vm.connectDemo(); tab = 0 },
        )
    }

}
