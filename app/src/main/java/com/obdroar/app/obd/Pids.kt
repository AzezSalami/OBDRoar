package com.obdroar.app.obd

/** An SAE J1979 Mode 01 parameter. [formula] receives the data bytes (A, B, C, ...). */
data class PidDef(
    val code: Int,
    val name: String,
    val unit: String,
    val bytes: Int,
    val min: Double,
    val max: Double,
    val decimals: Int = 0,
    val category: String = "Engine",
    val formula: (IntArray) -> Double
)

object Pids {
    const val MONITOR_STATUS = 0x01
    const val LOAD = 0x04
    const val COOLANT = 0x05
    const val STFT1 = 0x06
    const val LTFT1 = 0x07
    const val MAP = 0x0B
    const val RPM = 0x0C
    const val SPEED = 0x0D
    const val TIMING = 0x0E
    const val IAT = 0x0F
    const val MAF = 0x10
    const val THROTTLE = 0x11
    const val FUEL_LEVEL = 0x2F
    const val BARO = 0x33
    const val MODULE_VOLTAGE = 0x42
    const val AMBIENT = 0x46
    const val OIL_TEMP = 0x5C
    const val FUEL_RATE = 0x5E

    private fun ab(d: IntArray) = (d[0] * 256 + d[1]).toDouble()
    private fun trim(d: IntArray) = (d[0] - 128) * 100.0 / 128.0

    private fun o2(code: Int, n: Int): PidDef =
        PidDef(code, "O2 sensor $n voltage", "V", 2, 0.0, 1.275, 3, "Oxygen sensors") { it[0] / 200.0 }

    val all: List<PidDef> = listOf(
        PidDef(RPM, "Engine RPM", "rpm", 2, 0.0, 8000.0) { ab(it) / 4.0 },
        PidDef(SPEED, "Vehicle speed", "km/h", 1, 0.0, 240.0) { it[0].toDouble() },
        PidDef(LOAD, "Calculated engine load", "%", 1, 0.0, 100.0) { it[0] / 2.55 },
        PidDef(THROTTLE, "Throttle position", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255.0 },
        PidDef(COOLANT, "Coolant temperature", "°C", 1, -40.0, 130.0, category = "Temperatures") { it[0] - 40.0 },
        PidDef(IAT, "Intake air temperature", "°C", 1, -40.0, 90.0, category = "Temperatures") { it[0] - 40.0 },
        PidDef(OIL_TEMP, "Engine oil temperature", "°C", 1, -40.0, 150.0, category = "Temperatures") { it[0] - 40.0 },
        PidDef(AMBIENT, "Ambient air temperature", "°C", 1, -40.0, 60.0, category = "Temperatures") { it[0] - 40.0 },
        PidDef(0x3C, "Catalyst temp B1S1", "°C", 2, -40.0, 1000.0, category = "Temperatures") { ab(it) / 10.0 - 40.0 },
        PidDef(0x3D, "Catalyst temp B2S1", "°C", 2, -40.0, 1000.0, category = "Temperatures") { ab(it) / 10.0 - 40.0 },
        PidDef(MAF, "Mass air flow", "g/s", 2, 0.0, 250.0, 1, "Air & fuel") { ab(it) / 100.0 },
        PidDef(MAP, "Intake manifold pressure", "kPa", 1, 0.0, 255.0, category = "Air & fuel") { it[0].toDouble() },
        PidDef(BARO, "Barometric pressure", "kPa", 1, 0.0, 255.0, category = "Air & fuel") { it[0].toDouble() },
        PidDef(TIMING, "Timing advance", "°", 1, -64.0, 64.0, 1) { it[0] / 2.0 - 64.0 },
        PidDef(STFT1, "Short term fuel trim B1", "%", 1, -100.0, 100.0, 1, "Air & fuel") { trim(it) },
        PidDef(LTFT1, "Long term fuel trim B1", "%", 1, -100.0, 100.0, 1, "Air & fuel") { trim(it) },
        PidDef(0x08, "Short term fuel trim B2", "%", 1, -100.0, 100.0, 1, "Air & fuel") { trim(it) },
        PidDef(0x09, "Long term fuel trim B2", "%", 1, -100.0, 100.0, 1, "Air & fuel") { trim(it) },
        PidDef(0x0A, "Fuel pressure", "kPa", 1, 0.0, 765.0, category = "Air & fuel") { it[0] * 3.0 },
        PidDef(0x22, "Fuel rail pressure (rel.)", "kPa", 2, 0.0, 5177.0, 1, "Air & fuel") { ab(it) * 0.079 },
        PidDef(0x23, "Fuel rail pressure", "kPa", 2, 0.0, 655350.0, category = "Air & fuel") { ab(it) * 10.0 },
        PidDef(FUEL_LEVEL, "Fuel tank level", "%", 1, 0.0, 100.0, category = "Air & fuel") { it[0] * 100.0 / 255.0 },
        PidDef(FUEL_RATE, "Engine fuel rate", "L/h", 2, 0.0, 100.0, 1, "Air & fuel") { ab(it) / 20.0 },
        PidDef(0x52, "Ethanol fuel", "%", 1, 0.0, 100.0, category = "Air & fuel") { it[0] * 100.0 / 255.0 },
        PidDef(0x44, "Commanded air-fuel ratio (λ)", "λ", 2, 0.0, 2.0, 3, "Air & fuel") { ab(it) * 2.0 / 65536.0 },
        PidDef(0x2C, "Commanded EGR", "%", 1, 0.0, 100.0, category = "Emissions") { it[0] * 100.0 / 255.0 },
        PidDef(0x2D, "EGR error", "%", 1, -100.0, 100.0, category = "Emissions") { trim(it) },
        PidDef(0x2E, "Commanded evap purge", "%", 1, 0.0, 100.0, category = "Emissions") { it[0] * 100.0 / 255.0 },
        PidDef(0x43, "Absolute load", "%", 2, 0.0, 400.0) { ab(it) * 100.0 / 255.0 },
        PidDef(0x45, "Relative throttle position", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255.0 },
        PidDef(0x47, "Absolute throttle B", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255.0 },
        PidDef(0x49, "Accelerator pedal D", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255.0 },
        PidDef(0x4A, "Accelerator pedal E", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255.0 },
        PidDef(0x4C, "Commanded throttle actuator", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255.0 },
        PidDef(0x5A, "Relative accelerator pedal", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255.0 },
        PidDef(0x5D, "Fuel injection timing", "°", 2, -210.0, 302.0, 1) { ab(it) / 128.0 - 210.0 },
        PidDef(0x61, "Driver demand torque", "%", 1, -125.0, 130.0) { it[0] - 125.0 },
        PidDef(0x62, "Actual engine torque", "%", 1, -125.0, 130.0) { it[0] - 125.0 },
        PidDef(0x63, "Engine reference torque", "Nm", 2, 0.0, 65535.0) { ab(it) },
        PidDef(MODULE_VOLTAGE, "Control module voltage", "V", 2, 0.0, 18.0, 2, "Electrical") { ab(it) / 1000.0 },
        PidDef(0x5B, "Hybrid battery remaining", "%", 1, 0.0, 100.0, category = "Electrical") { it[0] * 100.0 / 255.0 },
        PidDef(0x1F, "Run time since start", "s", 2, 0.0, 65535.0, category = "Counters") { ab(it) },
        PidDef(0x21, "Distance with MIL on", "km", 2, 0.0, 65535.0, category = "Counters") { ab(it) },
        PidDef(0x31, "Distance since codes cleared", "km", 2, 0.0, 65535.0, category = "Counters") { ab(it) },
        PidDef(0x30, "Warm-ups since codes cleared", "", 1, 0.0, 255.0, category = "Counters") { it[0].toDouble() },
        PidDef(0x4D, "Time run with MIL on", "min", 2, 0.0, 65535.0, category = "Counters") { ab(it) },
        PidDef(0x4E, "Time since codes cleared", "min", 2, 0.0, 65535.0, category = "Counters") { ab(it) },
        o2(0x14, 1), o2(0x15, 2), o2(0x16, 3), o2(0x17, 4),
        o2(0x18, 5), o2(0x19, 6), o2(0x1A, 7), o2(0x1B, 8),
    )

    val byCode: Map<Int, PidDef> = all.associateBy { it.code }

    /** Sensors used when the car does not report its supported list. */
    val fallback = setOf(RPM, SPEED, LOAD, THROTTLE, COOLANT, IAT, MAF, MAP, TIMING, STFT1, LTFT1, FUEL_LEVEL)
}
