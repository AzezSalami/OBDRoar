package com.obdroar.app.obd

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

data class Monitor(val name: String, val available: Boolean, val complete: Boolean)

data class MonitorStatus(
    val milOn: Boolean,
    val dtcCount: Int,
    val diesel: Boolean,
    val monitors: List<Monitor>
)

/**
 * Talks the ELM327 dialect on top of an [ObdTransport].
 * All requests are serialized with a mutex so polling and diagnostics can share the link.
 */
class ObdSession(val transport: ObdTransport) {

    private val mutex = Mutex()

    var adapterVersion: String = ""; private set
    var protocolNumber: String = ""; private set
    var protocolName: String = ""; private set
    var supportedPids: Set<Int> = emptySet(); private set

    val isCan: Boolean
        get() {
            val p = protocolNumber.uppercase()
            val n = if (p.length == 2 && p[0] == 'A') p.substring(1) else p
            return n in setOf("6", "7", "8", "9", "A", "B", "C")
        }

    suspend fun initialize(onStep: (String) -> Unit) {
        onStep("Opening Bluetooth link…")
        transport.open()
        onStep("Resetting adapter…")
        adapterVersion = textLines(send("ATZ", 5000), "ATZ").lastOrNull() ?: "ELM327"
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1")) send(cmd)
        send("ATSP0")
        onStep("Searching vehicle protocol… (can take 10 s)")
        val first = send("0100", 20000)
        if (isError(first)) {
            throw IOException(
                "Car did not answer (${textLines(first, "0100").joinToString(" ")}). " +
                    "Is the ignition ON?"
            )
        }
        protocolNumber = textLines(send("ATDPN"), "ATDPN").firstOrNull() ?: ""
        protocolName = (textLines(send("ATDP"), "ATDP").firstOrNull() ?: "").removePrefix("AUTO, ")
        onStep("Reading supported sensors…")
        supportedPids = runCatching { readSupportedPids() }.getOrDefault(emptySet())
    }

    fun close() = transport.close()

    // ---------------------------------------------------------------- low level

    suspend fun send(cmd: String, timeoutMs: Long = 4000): String =
        mutex.withLock { transport.exchange(cmd, timeoutMs) }

    private fun textLines(raw: String, cmd: String): List<String> =
        raw.split('\r', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(cmd, true) && !it.startsWith("SEARCHING") }

    private fun isError(raw: String): Boolean {
        val u = raw.uppercase()
        return listOf("NO DATA", "UNABLE TO CONNECT", "ERROR", "STOPPED", "?", "NO CONNECT", "BUS INIT")
            .any { u.contains(it) } && !u.contains("41")
    }

    /** Hex-only payload lines, with CAN multi-frame prefixes ("0:", "1:") and length headers removed. */
    private fun hexLines(raw: String, cmd: String): List<String> {
        val hexRegex = Regex("^[0-9A-F]+$")
        return textLines(raw, cmd).mapNotNull { line ->
            var l = line.replace(" ", "").uppercase()
            if (l.length >= 2 && l[1] == ':') l = l.substring(2)
            when {
                !hexRegex.matches(l) -> null
                l.length == 3 -> null // ISO-TP length header, e.g. "014"
                else -> l
            }
        }
    }

    private fun indexOfEven(s: String, needle: String, from: Int): Int {
        var i = s.indexOf(needle, from)
        while (i >= 0 && i % 2 != 0) i = s.indexOf(needle, i + 1)
        return i
    }

    private fun hexToBytes(hex: String): IntArray =
        IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16) }

    // ---------------------------------------------------------------- mode 01

    /** Data bytes of every ECU that answered Mode 01 [pid]. */
    suspend fun mode01All(pid: Int): List<IntArray> {
        val cmd = "01%02X".format(pid)
        return parseMode01(send(cmd), cmd, pid)
    }

    private fun parseMode01(raw: String, cmd: String, pid: Int): List<IntArray> {
        val prefix = "41%02X".format(pid)
        return hexLines(raw, cmd).mapNotNull { line ->
            val i = indexOfEven(line, prefix, 0)
            if (i < 0) null else hexToBytes(line.substring(i + 4).let { it.substring(0, it.length - it.length % 2) })
        }
    }

    suspend fun readPid(pid: PidDef): Double? {
        val data = mode01All(pid.code).firstOrNull { it.size >= pid.bytes } ?: return null
        return pid.formula(data)
    }

    /** Whether the "reply count" suffix works with this adapter/car; null = not known yet. */
    private var singleReplyWorks: Boolean? = null

    /**
     * Like [readPid] but asks for "01xx1": the trailing 1 tells the ELM327 to return as soon as the
     * first ECU answers instead of waiting for its silence timeout, which makes each reading much
     * faster. If the adapter or car doesn't understand it we fall back to the normal request.
     */
    suspend fun readPidFast(pid: PidDef): Double? {
        if (singleReplyWorks != false) {
            val cmd = "01%02X1".format(pid.code)
            val data = parseMode01(send(cmd), cmd, pid.code).firstOrNull { it.size >= pid.bytes }
            if (data != null) {
                singleReplyWorks = true
                return pid.formula(data)
            }
            if (singleReplyWorks == true) return null // suffix is fine, this PID just has no data
            // Not known yet: see if the plain request works. If it does, the suffix is the problem.
            val plain = readPid(pid)
            if (plain != null) singleReplyWorks = false
            return plain
        }
        return readPid(pid)
    }

    private suspend fun readSupportedPids(): Set<Int> {
        val result = sortedSetOf<Int>()
        var base = 0
        while (base <= 0xE0) {
            val answers = mode01All(base).filter { it.size >= 4 }
            if (answers.isEmpty()) break
            for (data in answers) {
                for (i in 0 until 32) {
                    if ((data[i / 8] shr (7 - i % 8)) and 1 == 1) result += base + i + 1
                }
            }
            if (!result.contains(base + 0x20)) break
            base += 0x20
        }
        return result
    }

    suspend fun readVoltage(): Double? {
        val line = textLines(send("ATRV"), "ATRV").firstOrNull() ?: return null
        return Regex("([0-9]+\\.?[0-9]*)").find(line)?.value?.toDoubleOrNull()
    }

    suspend fun readMonitorStatus(): MonitorStatus? {
        val d = mode01All(Pids.MONITOR_STATUS).firstOrNull { it.size >= 4 } ?: return null
        val diesel = (d[1] and 0x08) != 0
        val monitors = mutableListOf(
            Monitor("Misfire", d[1] and 0x01 != 0, d[1] and 0x10 == 0),
            Monitor("Fuel system", d[1] and 0x02 != 0, d[1] and 0x20 == 0),
            Monitor("Components", d[1] and 0x04 != 0, d[1] and 0x40 == 0),
        )
        val names = if (diesel) listOf(
            "NMHC catalyst", "NOx / SCR", null, "Boost pressure", null,
            "Exhaust gas sensor", "PM filter", "EGR / VVT"
        ) else listOf(
            "Catalyst", "Heated catalyst", "Evaporative system", "Secondary air",
            "A/C refrigerant", "O2 sensor", "O2 sensor heater", "EGR / VVT"
        )
        names.forEachIndexed { bit, name ->
            if (name != null) {
                monitors += Monitor(name, (d[2] shr bit) and 1 == 1, (d[3] shr bit) and 1 == 0)
            }
        }
        return MonitorStatus((d[0] and 0x80) != 0, d[0] and 0x7F, diesel, monitors)
    }

    // ---------------------------------------------------------------- trouble codes

    /** mode 0x03 = stored, 0x07 = pending, 0x0A = permanent */
    suspend fun readDtcs(mode: Int): List<String> {
        val cmd = "%02X".format(mode)
        val prefix = "%02X".format(mode + 0x40)
        val raw = send(cmd, 8000)
        val lines = hexLines(raw, cmd)
        val codes = linkedSetOf<String>()
        if (isCan) {
            val joined = lines.joinToString("")
            var i = indexOfEven(joined, prefix, 0)
            while (i >= 0) {
                if (i + 4 > joined.length) break
                val count = joined.substring(i + 2, i + 4).toInt(16)
                var p = i + 4
                repeat(count) {
                    if (p + 4 <= joined.length) {
                        decodeDtc(joined.substring(p, p + 4))?.let { codes += it }
                        p += 4
                    }
                }
                i = indexOfEven(joined, prefix, p)
            }
        } else {
            for (line in lines) {
                if (!line.startsWith(prefix)) continue
                var p = 2
                while (p + 4 <= line.length) {
                    decodeDtc(line.substring(p, p + 4))?.let { codes += it }
                    p += 4
                }
            }
        }
        return codes.toList()
    }

    suspend fun clearDtcs(): Boolean {
        val raw = send("04", 10000)
        return raw.replace(" ", "").contains("44")
    }

    // ---------------------------------------------------------------- vehicle info

    suspend fun readVin(): String? {
        val raw = send("0902", 8000)
        val sb = StringBuilder()
        for (line in hexLines(raw, "0902")) {
            var l = line
            val i = indexOfEven(l, "4902", 0)
            if (i >= 0) l = l.substring(minOf(l.length, i + 6)) // drop 49 02 + message counter
            sb.append(l)
        }
        val hex = sb.toString().let { it.substring(0, it.length - it.length % 2) }
        val text = hexToBytes(hex).map { it.toChar() }
            .filter { it in 'A'..'Z' || it in '0'..'9' }
            .joinToString("")
        return if (text.length >= 17) text.takeLast(17) else null
    }

    companion object {
        fun decodeDtc(hex4: String): String? {
            val v = hex4.toIntOrNull(16) ?: return null
            if (v == 0) return null
            val letter = "PCBU"[v shr 14]
            val second = (v shr 12) and 0x3
            return "$letter$second%03X".format(v and 0xFFF)
        }
    }
}
