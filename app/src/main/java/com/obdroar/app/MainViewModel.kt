package com.obdroar.app

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obdroar.app.data.HistoryRepository
import com.obdroar.app.data.Sample
import com.obdroar.app.obd.BluetoothTransport
import com.obdroar.app.obd.DemoTransport
import com.obdroar.app.obd.DtcDatabase
import com.obdroar.app.obd.DtcInfo
import com.obdroar.app.obd.MonitorStatus
import com.obdroar.app.obd.ObdSession
import com.obdroar.app.obd.ObdTransport
import com.obdroar.app.obd.PidDef
import com.obdroar.app.obd.SensorInfoDb
import com.obdroar.app.obd.Pids
import com.obdroar.app.obd.VinDecoder
import com.obdroar.app.sound.EngineProfiles
import com.obdroar.app.sound.EngineSoundEngine
import com.obdroar.app.sound.EngineState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Calendar

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED }

data class ConnUi(
    val state: ConnState = ConnState.DISCONNECTED,
    val device: String? = null,
    val status: String = "",
    val error: String? = null,
    val demo: Boolean = false,
)

data class Reading(val value: Double, val min: Double, val max: Double, val history: List<Float>)

data class DiagUi(
    val scanning: Boolean = false,
    val clearing: Boolean = false,
    val stored: List<DtcInfo> = emptyList(),
    val pending: List<DtcInfo> = emptyList(),
    val permanent: List<DtcInfo> = emptyList(),
    val monitor: MonitorStatus? = null,
    val lastScan: Long? = null,
)

data class VehicleUi(
    val vin: String? = null,
    val make: String? = null,
    val year: Int? = null,
    val protocol: String = "",
    val adapter: String = "",
    val voltage: Double? = null,
    val supported: Set<Int> = emptySet(),
)

data class SoundUi(
    val on: Boolean = false,
    val profileId: String = EngineProfiles.all.first().id,
    val follow: Boolean = false,
    val turbo: Boolean = false,
    val pops: Boolean = true,
    val volume: Float = 0.8f,
    val cruise: Float = 0f,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val engine = EngineSoundEngine()

    private val _conn = MutableStateFlow(ConnUi())
    val conn: StateFlow<ConnUi> = _conn.asStateFlow()

    private val _live = MutableStateFlow<Map<Int, Reading>>(emptyMap())
    val live: StateFlow<Map<Int, Reading>> = _live.asStateFlow()

    private val _diag = MutableStateFlow(DiagUi())
    val diag: StateFlow<DiagUi> = _diag.asStateFlow()

    private val _vehicle = MutableStateFlow(VehicleUi())
    val vehicle: StateFlow<VehicleUi> = _vehicle.asStateFlow()

    private val _sound = MutableStateFlow(SoundUi())
    val sound: StateFlow<SoundUi> = _sound.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    // ---- history (saved readings)
    val history = HistoryRepository(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _recordHistory = MutableStateFlow(prefs.getBoolean("record_history", true))
    val recordHistory: StateFlow<Boolean> = _recordHistory.asStateFlow()

    /** Id of the session being recorded right now, or null. */
    private val _currentSession = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSession.asStateFlow()

    /** Bumped whenever sessions are added, closed or deleted, so the History list reloads. */
    private val _historyVersion = MutableStateFlow(0)
    val historyVersion: StateFlow<Int> = _historyVersion.asStateFlow()

    private val pendingSamples = ArrayList<Sample>()
    private val lastSaved = HashMap<Int, Long>()
    private var flushJob: Job? = null
    private var startRecordingJob: Job? = null

    private var session: ObdSession? = null
    private var connectJob: Job? = null
    private var pollJob: Job? = null
    private var stopSoundJob: Job? = null
    private var pedal = 0f

    // ======================================================================= connection

    fun connectBluetooth(device: BluetoothDevice) = connect(BluetoothTransport(device), demo = false)
    fun connectDemo() = connect(DemoTransport(), demo = true)

    private fun connect(transport: ObdTransport, demo: Boolean) {
        disconnect()
        _conn.value = ConnUi(ConnState.CONNECTING, transport.displayName, "Connecting…", demo = demo)
        connectJob = viewModelScope.launch {
            val s = ObdSession(transport)
            try {
                s.initialize { step -> _conn.update { it.copy(status = step) } }
                session = s
                _vehicle.value = VehicleUi(
                    protocol = s.protocolName.ifBlank { "Protocol ${s.protocolNumber}" },
                    adapter = s.adapterVersion,
                    supported = s.supportedPids,
                )
                _conn.update { it.copy(state = ConnState.CONNECTED, status = "Connected", error = null) }
                startRecording()
                startPolling(s, demo)
                loadVehicleInfo(s)
                scanDtcs()
            } catch (e: CancellationException) {
                s.close(); throw e
            } catch (e: Exception) {
                s.close()
                session = null
                _conn.value = ConnUi(error = e.message ?: "Connection failed")
            }
        }
    }

    fun disconnect() {
        stopRecording()
        pollJob?.cancel(); pollJob = null
        connectJob?.cancel(); connectJob = null
        session?.close(); session = null
        engine.externalRpm = null
        _conn.value = ConnUi()
        _live.value = emptyMap()
        _diag.value = DiagUi()
    }

    private fun connectionLost(e: Exception) {
        stopRecording()
        session?.close(); session = null
        engine.externalRpm = null
        _conn.value = ConnUi(error = "Connection lost: ${e.message}")
    }

    // ======================================================================= live data

    private fun startPolling(s: ObdSession, demo: Boolean) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val supported = s.supportedPids.ifEmpty { Pids.fallback }
            val fastCodes = setOf(Pids.RPM, Pids.SPEED, Pids.THROTTLE, Pids.LOAD)
            val fast = Pids.all.filter { it.code in supported && it.code in fastCodes }.toMutableList()
            val slow = Pids.all.filter { it.code in supported && it.code !in fastCodes }.toMutableList()
            val failures = HashMap<Int, Int>()
            var idx = 0
            var cycle = 0
            var errors = 0
            while (isActive) {
                try {
                    // RPM is read before every other request so the engine sound can follow the
                    // car closely: RPM, speed, RPM, throttle, RPM, load, RPM, slow sensor…
                    val rpmPid = fast.firstOrNull { it.code == Pids.RPM }
                    suspend fun pollRpm() { if (rpmPid != null && rpmPid in fast) poll(s, rpmPid, failures, fast) }
                    for (pid in fast.filter { it.code != Pids.RPM }) { pollRpm(); poll(s, pid, failures, fast) }
                    // while the engine sound follows the car, read fewer slow sensors per cycle
                    val slowPerCycle = if (_sound.value.follow && _sound.value.on) 1 else 2
                    repeat(slowPerCycle) {
                        if (slow.isNotEmpty()) {
                            pollRpm()
                            val pid = slow[idx % slow.size]
                            idx++
                            poll(s, pid, failures, slow)
                        }
                    }
                    if (cycle % 25 == 0) {
                        s.readVoltage()?.let { v ->
                            _vehicle.update { it.copy(voltage = v) }
                            recordSample(SensorInfoDb.BATTERY, v)
                        }
                    }
                    cycle++
                    errors = 0
                    if (demo) delay(30)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    errors++
                    if (errors >= 3) { connectionLost(e); return@launch }
                    delay(300)
                }
            }
        }
    }

    private suspend fun poll(s: ObdSession, pid: PidDef, failures: MutableMap<Int, Int>, list: MutableList<PidDef>) {
        val v = s.readPidFast(pid)
        if (v == null) {
            val f = (failures[pid.code] ?: 0) + 1
            failures[pid.code] = f
            if (f >= 4) list.remove(pid) // the car doesn't really support it
            return
        }
        failures[pid.code] = 0
        record(pid.code, v)
        if (_sound.value.follow) {
            when (pid.code) {
                Pids.RPM -> engine.externalRpm = v.toFloat()
                Pids.LOAD -> engine.throttle = ((v - 18.0) / 82.0).toFloat().coerceIn(0f, 1f)
            }
        }
    }

    private fun record(code: Int, v: Double) {
        _live.update { map ->
            val old = map[code]
            val hist = ((old?.history ?: emptyList()) + v.toFloat()).takeLast(120)
            map + (code to Reading(v, minOf(old?.min ?: v, v), maxOf(old?.max ?: v, v), hist))
        }
        recordSample(code, v)
    }

    // ======================================================================= diagnostics

    fun scanDtcs() {
        val s = session ?: return
        if (_diag.value.scanning) return
        viewModelScope.launch {
            _diag.update { it.copy(scanning = true) }
            try {
                val monitor = runCatching { s.readMonitorStatus() }.getOrNull()
                val stored = s.readDtcs(0x03).map(DtcDatabase::lookup)
                val pending = runCatching { s.readDtcs(0x07) }.getOrDefault(emptyList()).map(DtcDatabase::lookup)
                val permanent = runCatching { s.readDtcs(0x0A) }.getOrDefault(emptyList()).map(DtcDatabase::lookup)
                _diag.value = DiagUi(
                    stored = stored, pending = pending, permanent = permanent,
                    monitor = monitor, lastScan = System.currentTimeMillis()
                )
                _currentSession.value?.let { sid ->
                    val seen = stored.map { it.code to "stored" } + pending.map { it.code to "pending" } +
                        permanent.map { it.code to "permanent" }
                    if (seen.isNotEmpty()) {
                        runCatching { history.addDtcs(sid, seen) }
                        _historyVersion.update { it + 1 }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _diag.update { it.copy(scanning = false) }
                _messages.tryEmit("Scan failed: ${e.message}")
            }
        }
    }

    fun clearDtcs() {
        val s = session ?: return
        viewModelScope.launch {
            _diag.update { it.copy(clearing = true) }
            val ok = try { s.clearDtcs() } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
            _diag.update { it.copy(clearing = false) }
            _messages.tryEmit(
                if (ok) "Trouble codes cleared. Check-engine light should turn off."
                else "The car refused to clear codes. Try with engine OFF and ignition ON."
            )
            delay(1200)
            scanDtcs()
        }
    }

    private suspend fun loadVehicleInfo(s: ObdSession) {
        val vin = try { s.readVin() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        if (vin != null) {
            _currentSession.value?.let { sid -> runCatching { history.setVin(sid, vin) } }
            _vehicle.update {
                it.copy(
                    vin = vin,
                    make = VinDecoder.manufacturer(vin),
                    year = VinDecoder.modelYear(vin, Calendar.getInstance().get(Calendar.YEAR)),
                )
            }
        }
    }

    // ======================================================================= engine sound

    fun setEngineOn(on: Boolean) {
        stopSoundJob?.cancel()
        if (on) {
            engine.startAudio()
            engine.setIgnition(true)
        } else {
            engine.setIgnition(false)
            stopSoundJob = viewModelScope.launch {
                // let it spin down, then release the audio device
                var waited = 0
                while (engine.state != EngineState.OFF && waited < 5000) { delay(100); waited += 100 }
                delay(400)
                engine.stopAudio()
            }
        }
        _sound.update { it.copy(on = on) }
    }

    fun setProfile(id: String) {
        engine.profile = EngineProfiles.byId(id)
        _sound.update { it.copy(profileId = id) }
    }

    fun setFollow(follow: Boolean) {
        _sound.update { it.copy(follow = follow) }
        if (!follow) {
            engine.externalRpm = null
            applyManualThrottle()
        } else {
            _live.value[Pids.RPM]?.let { engine.externalRpm = it.value.toFloat() }
        }
    }

    fun setPedal(value: Float) { pedal = value; applyManualThrottle() }
    fun setCruise(value: Float) { _sound.update { it.copy(cruise = value) }; applyManualThrottle() }
    fun setTurbo(on: Boolean) { engine.turbo = on; _sound.update { it.copy(turbo = on) } }
    fun setPops(on: Boolean) { engine.popsAndBangs = on; _sound.update { it.copy(pops = on) } }
    fun setVolume(v: Float) { engine.volume = v; _sound.update { it.copy(volume = v) } }

    private fun applyManualThrottle() {
        if (!_sound.value.follow) engine.throttle = maxOf(pedal, _sound.value.cruise)
    }

    fun onAppBackground() {
        if (_sound.value.on) setEngineOn(false)
    }

    // ======================================================================= history

    fun setRecordHistory(on: Boolean) {
        prefs.edit().putBoolean("record_history", on).apply()
        _recordHistory.value = on
        if (on && _conn.value.state == ConnState.CONNECTED) startRecording() else if (!on) stopRecording()
    }

    private fun startRecording() {
        if (!_recordHistory.value || _currentSession.value != null || startRecordingJob?.isActive == true) return
        val device = _conn.value.device
        val demo = _conn.value.demo
        startRecordingJob = viewModelScope.launch {
            val id = try { history.startSession(device, demo) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _messages.tryEmit("Could not start saving history: ${e.message}"); return@launch
            }
            lastSaved.clear()
            _currentSession.value = id
            _vehicle.value.vin?.let { vin -> runCatching { history.setVin(id, vin) } }
            _historyVersion.update { it + 1 }
            flushJob?.cancel()
            flushJob = launch {
                while (isActive) {
                    delay(2000)
                    val batch = drainSamples()
                    if (batch.isNotEmpty()) runCatching { history.addSamples(id, batch) }
                }
            }
        }
    }

    /** Saves at most one reading per sensor per second to keep the database small. */
    private fun recordSample(code: Int, v: Double) {
        if (_currentSession.value == null) return
        val now = System.currentTimeMillis()
        val last = lastSaved[code]
        if (last != null && now - last < 1000) return
        lastSaved[code] = now
        synchronized(pendingSamples) { pendingSamples += Sample(now, code, v) }
    }

    private fun drainSamples(): List<Sample> = synchronized(pendingSamples) {
        val copy = ArrayList(pendingSamples)
        pendingSamples.clear()
        copy
    }

    private fun stopRecording() {
        startRecordingJob?.cancel(); startRecordingJob = null
        flushJob?.cancel(); flushJob = null
        val sid = _currentSession.value ?: return
        _currentSession.value = null
        val batch = drainSamples()
        lastSaved.clear()
        history.finishAsync(sid, batch) { _historyVersion.update { it + 1 } }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            runCatching { history.deleteSession(id) }
            _historyVersion.update { it + 1 }
        }
    }

    fun deleteAllHistory() {
        viewModelScope.launch {
            runCatching { history.deleteAll(_currentSession.value) }
            _historyVersion.update { it + 1 }
            _messages.tryEmit("History deleted")
        }
    }

    suspend fun exportCsv(id: Long): File? =
        try { history.exportCsv(id) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _messages.tryEmit("Export failed: ${e.message}"); null
        }

    override fun onCleared() {
        engine.stopAudio()
        disconnect()
    }
}
