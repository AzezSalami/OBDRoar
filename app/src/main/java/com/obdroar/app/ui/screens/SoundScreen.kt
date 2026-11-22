package com.obdroar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obdroar.app.ConnState
import com.obdroar.app.MainViewModel
import com.obdroar.app.sound.EngineProfile
import com.obdroar.app.sound.EngineProfiles
import com.obdroar.app.sound.EngineState
import com.obdroar.app.ui.components.*
import com.obdroar.app.ui.theme.RoarColors
import kotlinx.coroutines.delay
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundScreen(vm: MainViewModel) {
    val sound by vm.sound.collectAsStateWithLifecycle()
    val conn by vm.conn.collectAsStateWithLifecycle()
    val connected = conn.state == ConnState.CONNECTED
    val profile = EngineProfiles.byId(sound.profileId)

    var rpm by remember { mutableFloatStateOf(0f) }
    var boost by remember { mutableFloatStateOf(0f) }
    var engState by remember { mutableStateOf(EngineState.OFF) }
    LaunchedEffect(Unit) {
        while (true) {
            rpm = vm.engine.rpm
            boost = vm.engine.boost
            engState = vm.engine.state
            delay(33)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- tach + start button
        SectionCard {
            val gaugeMax = (ceil(profile.redline * 1.15f / 1000f) * 1000f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArcGauge(
                    value = if (engState == EngineState.OFF) 0f else rpm,
                    min = 0f, max = gaugeMax, label = profile.name.uppercase(), unit = "rpm",
                    modifier = Modifier.weight(1.3f), redFrom = profile.redline,
                    majorTicks = (gaugeMax / 1000f).toInt(),
                    tickLabel = { (it / 1000).toInt().toString() }
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    StartButton(sound.on, engState) { vm.setEngineOn(!sound.on) }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (engState) {
                            EngineState.OFF -> "Engine off"
                            EngineState.CRANKING -> "Cranking…"
                            EngineState.RUNNING -> if (sound.follow) "Following car" else "Running"
                            EngineState.STOPPING -> "Shutting down"
                        },
                        fontSize = 12.sp, color = RoarColors.TextDim
                    )
                    if (sound.turbo) {
                        Spacer(Modifier.height(8.dp))
                        Text("BOOST", fontSize = 10.sp, color = RoarColors.TextDim)
                        LinearProgressIndicator(
                            progress = { boost },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = RoarColors.Cyan, trackColor = RoarColors.SurfaceHigh
                        )
                    }
                }
            }
        }

        // ---- mode
        SectionCard(title = "Throttle source") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !sound.follow, onClick = { vm.setFollow(false) },
                    label = { Text("Rev it myself") },
                    leadingIcon = { Icon(Icons.Filled.TouchApp, null, Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = sound.follow, enabled = connected, onClick = { vm.setFollow(true) },
                    label = { Text("Follow my car's RPM") },
                    leadingIcon = { Icon(Icons.Filled.DirectionsCar, null, Modifier.size(18.dp)) }
                )
            }
            if (!connected) {
                Text(
                    "Connect to your car to make the sound follow the real engine speed while you drive.",
                    fontSize = 12.sp, color = RoarColors.TextDim
                )
            }
        }

        if (!sound.follow) {
            SectionCard(title = "Gas pedal") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThrottlePedal(
                        enabled = sound.on,
                        onThrottle = vm::setPedal,
                        modifier = Modifier.width(120.dp).height(200.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Press and slide up to rev.", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Let go suddenly at high RPM for pops & bangs. Hold it on the limiter for that brap-brap.",
                            fontSize = 12.sp, color = RoarColors.TextDim
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Hold throttle: ${(sound.cruise * 100).toInt()} %", fontSize = 12.sp, color = RoarColors.TextDim)
                        Slider(value = sound.cruise, onValueChange = vm::setCruise, enabled = sound.on)
                    }
                }
            }
        }

        // ---- engines
        SectionCard(title = "Engine") {
            EngineProfiles.all.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    row.forEach { p ->
                        ProfileCard(p, p.id == sound.profileId, Modifier.weight(1f)) { vm.setProfile(p.id) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // ---- effects
        SectionCard(title = "Effects") {
            ToggleRow(Icons.Filled.Air, "Turbo whistle & blow-off", sound.turbo, vm::setTurbo)
            ToggleRow(Icons.Filled.Whatshot, "Pops, bangs & limiter crackle", sound.pops, vm::setPops)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VolumeUp, null, tint = RoarColors.TextDim)
                Spacer(Modifier.width(10.dp))
                Slider(value = sound.volume, onValueChange = vm::setVolume, modifier = Modifier.weight(1f))
            }
            Text(
                "Tip: connect your phone to the car stereo over Bluetooth for full bass. " +
                    "Sound is synthesized live — no recordings — so it reacts instantly.",
                fontSize = 11.sp, color = RoarColors.TextDim
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StartButton(on: Boolean, state: EngineState, onClick: () -> Unit) {
    val ring = if (on) RoarColors.Red else RoarColors.Orange
    Box(
        Modifier
            .size(110.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(RoarColors.SurfaceHigh, Color(0xFF0E1218))))
            .border(4.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.PowerSettingsNew, null, tint = ring, modifier = Modifier.size(30.dp))
            Text("ENGINE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                if (on || state == EngineState.STOPPING) "STOP" else "START",
                fontSize = 13.sp, fontWeight = FontWeight.Black, color = ring
            )
        }
    }
}

@Composable
private fun ThrottlePedal(enabled: Boolean, onThrottle: (Float) -> Unit, modifier: Modifier = Modifier) {
    var level by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(RoarColors.SurfaceHigh)
            .border(2.dp, if (enabled) RoarColors.Orange.copy(alpha = 0.6f) else RoarColors.Outline, RoundedCornerShape(24.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun update(y: Float) {
                        level = (1f - y / size.height).coerceIn(0f, 1f)
                        onThrottle(level)
                    }
                    update(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        update(change.position.y)
                        change.consume()
                    }
                    level = 0f
                    onThrottle(0f)
                }
            }
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(level)
                .background(Brush.verticalGradient(listOf(RoarColors.Red, RoarColors.Orange, RoarColors.Amber)))
        )
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.KeyboardDoubleArrowUp, null, tint = Color.White.copy(alpha = 0.7f))
            Text("GAS", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color.White)
            Text("${(level * 100).toInt()}%", fontFamily = FontFamily.Monospace, color = Color.White, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ProfileCard(p: EngineProfile, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) RoarColors.Orange.copy(alpha = 0.14f) else RoarColors.SurfaceHigh)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) RoarColors.Orange else RoarColors.Outline,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(p.name, fontWeight = FontWeight.Bold, color = if (selected) RoarColors.Orange else Color.White)
        Text(p.tagline, fontSize = 11.sp, color = RoarColors.TextDim, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            "${p.cylinders} cyl · redline ${p.redline.toInt()}",
            fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = RoarColors.TextDim
        )
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = RoarColors.TextDim)
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
