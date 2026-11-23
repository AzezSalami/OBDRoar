package com.obdroar.app.obd

import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A fake ELM327 + car (CAN 11-bit/500k) so you can try every screen without a dongle.
 * It reports two stored codes and one pending code until you clear them.
 */
class DemoTransport : ObdTransport {
    override val displayName = "Demo car (simulated)"

    private val start = System.currentTimeMillis()
    private var cleared = false
    private val vin = "WBA3A5C51CF256789"

    private val supported = setOf(
        0x01, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11,
        0x14, 0x15, 0x1F, 0x20, 0x2F, 0x31, 0x33, 0x40, 0x42, 0x46, 0x5C, 0x5E
    )

    override suspend fun open() = delay(700)
    override fun close() {}

    override suspend fun exchange(command: String, timeoutMs: Long): String {
        val cmd = command.uppercase(Locale.US).replace(" ", "")
        delay(if (cmd == "ATZ" || cmd == "0100") 300 else 22)
        return respond(cmd) + "\r\r"
    }

    private val t: Double get() = (System.currentTimeMillis() - start) / 1000.0

    // A little driving loop: throttle pulses, rpm follows, speed drifts.
    private fun throttle(): Double = ((sin(t * 0.55) + 1) / 2).pow(3.0)
    private fun rpm(): Double = 780 + throttle() * 5200 + 60 * sin(t * 7)
    private fun speed(): Double = max(0.0, 55 + 45 * sin(t * 0.12))

    private fun hex(vararg b: Int) = b.joinToString("") { "%02X".format(it and 0xFF) }

    private fun respond(cmd: String): String {
        if (cmd.startsWith("AT")) {
            return when (cmd) {
                "ATZ", "ATI" -> "ELM327 v1.5 (demo)"
                "ATRV" -> String.format(Locale.US, "%.1fV", 14.1 + 0.15 * sin(t))
                "ATDPN" -> "A6"
                "ATDP" -> "AUTO, ISO 15765-4 (CAN 11/500)"
                else -> "OK"
            }
        }
        when (cmd) {
            "03" -> return if (cleared) "4300" else "430201710300"
            "07" -> return if (cleared) "4700" else "47010133"
            "0A" -> return if (cleared) "4A00" else "4A010171"
            "04" -> { cleared = true; return "44" }
            "0902" -> return vinFrames()
        }
        if ((cmd.length == 4 || (cmd.length == 5 && cmd.endsWith("1"))) && cmd.startsWith("01")) {
            val pid = cmd.substring(2, 4).toIntOrNull(16) ?: return "?"
            if (pid % 0x20 == 0) return "41" + hex(pid) + bitmask(pid)
            val data = pidData(pid) ?: return "NO DATA"
            return "41" + hex(pid) + data
        }
        return "?"
    }

    private fun bitmask(base: Int): String {
        val bytes = IntArray(4)
        for (i in 0 until 32) {
            if ((base + i + 1) in supported) bytes[i / 8] = bytes[i / 8] or (1 shl (7 - i % 8))
        }
        return hex(*bytes)
    }

    private fun pidData(pid: Int): String? {
        if (pid !in supported) return null
        val thr = throttle()
        val warm = min(1.0, t / 90.0)
        return when (pid) {
            0x01 -> if (cleared) hex(0x00, 0x07, 0xE5, 0xE5) else hex(0x82, 0x07, 0xE5, 0x20)
            0x04 -> hex((thr * 0.8 + 0.18).times(255).roundToInt())
            0x05 -> hex((20 + 70 * warm + 40).roundToInt())
            0x06 -> hex((128 + 6 * sin(t * 3)).roundToInt())
            0x07 -> hex(if (cleared) 128 else 141)
            0x0B -> hex((30 + 70 * thr).roundToInt())
            0x0C -> { val v = (rpm() * 4).roundToInt(); hex(v shr 8, v) }
            0x0D -> hex(speed().roundToInt())
            0x0E -> hex(((12 - 8 * thr) + 64).times(2).roundToInt())
            0x0F -> hex((28 + 40).toInt())
            0x10 -> { val v = ((3 + thr * 120) * 100).roundToInt(); hex(v shr 8, v) }
            0x11 -> hex(((0.12 + 0.86 * thr) * 255).roundToInt())
            0x14 -> hex(((0.45 + 0.4 * sin(t * 4)) * 200).roundToInt(), 0xFF)
            0x15 -> hex(((0.65 + 0.05 * sin(t)) * 200).roundToInt(), 0xFF)
            0x1F -> { val v = t.toInt(); hex(v shr 8, v) }
            0x2F -> hex((0.62 * 255).roundToInt())
            0x31 -> hex(0x04, 0xD2)
            0x33 -> hex(101)
            0x42 -> { val v = ((14.1 + 0.15 * sin(t)) * 1000).roundToInt(); hex(v shr 8, v) }
            0x46 -> hex(19 + 40)
            0x5C -> hex((20 + 75 * warm + 40).roundToInt())
            0x5E -> { val v = ((0.8 + thr * 22) * 20).roundToInt(); hex(v shr 8, v) }
            else -> null
        }
    }

    /** ISO-TP multi-frame response exactly like a real ELM327 with headers off. */
    private fun vinFrames(): String {
        val payload = "490201" + vin.map { "%02X".format(it.code) }.joinToString("")
        val lines = mutableListOf("%03X".format(payload.length / 2))
        var p = 0
        var idx = 0
        while (p < payload.length) {
            val take = if (idx == 0) 12 else 14
            lines += "$idx:" + payload.substring(p, min(payload.length, p + take))
            p += take
            idx = (idx + 1) % 16
        }
        return lines.joinToString("\r")
    }
}
