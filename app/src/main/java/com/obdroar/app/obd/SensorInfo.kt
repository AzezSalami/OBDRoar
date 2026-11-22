package com.obdroar.app.obd

/** Plain-language explanation of a sensor, shown when the user taps its (!) icon. */
data class SensorInfo(
    val title: String,
    /** What the sensor is / what it tells you. */
    val what: String,
    /** What the unit means. */
    val unit: String,
    /** Typical healthy values. */
    val normal: String,
    /** Optional practical hint. */
    val tip: String? = null,
)

object SensorInfoDb {
    /** Pseudo-code for the adapter's battery-voltage reading (AT RV), which is not an OBD PID. */
    const val BATTERY = -1

    private const val PCT = "Percent (%) – 0 % is the minimum, 100 % the maximum."
    private const val DEG_C = "Degrees Celsius (°C)."
    private const val KPA = "Kilopascal (kPa). 100 kPa ≈ 1 bar ≈ 14.5 psi ≈ normal air pressure at sea level."

    private fun o2(bank: Int, sensor: Int): SensorInfo {
        val upstream = sensor == 1
        return SensorInfo(
            title = "O2 sensor – bank $bank, sensor $sensor",
            what = "Oxygen sensor in the exhaust " +
                (if (upstream) "before the catalytic converter. The engine computer uses it to keep the air-fuel mixture right."
                else "after the catalytic converter. It checks whether the catalytic converter is still working.") +
                " Bank $bank is the side of the engine with cylinder ${if (bank == 1) "1" else "2"} (inline engines only have bank 1).",
            unit = "Volts (V). Around 0.1 V means lean (extra oxygen), around 0.9 V means rich (extra fuel).",
            normal = if (upstream)
                "Once warm, it should swing quickly between about 0.1 V and 0.9 V several times per second."
            else
                "Should stay fairly steady around 0.5–0.7 V once warm.",
            tip = if (upstream) "If it's slow or stuck at one value, the sensor may be worn (codes P0133, P0134)."
            else "If it switches as fast as the upstream sensor, the catalytic converter may be worn (code P0420)."
        )
    }

    private val db: Map<Int, SensorInfo> = mapOf(
        Pids.RPM to SensorInfo(
            "Engine RPM", "How fast the engine's crankshaft is turning.",
            "Revolutions per minute (rpm) – full turns of the crankshaft each minute.",
            "Warm idle: about 600–1,000 rpm (a little higher on a cold start). Don't go past the red zone on your tachometer.",
            "An idle that hunts up and down can mean a vacuum leak or a dirty throttle body."
        ),
        Pids.SPEED to SensorInfo(
            "Vehicle speed", "Road speed as the engine computer sees it, from the wheel or gearbox speed sensor.",
            "Kilometres per hour (km/h).",
            "Should match your GPS speed within a few percent. Dashboard speedometers usually read slightly high."
        ),
        Pids.LOAD to SensorInfo(
            "Calculated engine load", "How hard the engine is working compared with the most it could do at the current RPM, worked out from the airflow.",
            PCT, "Idle: about 15–35 %. Cruising: 20–50 %. Full throttle: close to 100 %.",
            "High load at idle can mean something is dragging the engine (A/C, alternator) or an airflow sensor problem."
        ),
        Pids.THROTTLE to SensorInfo(
            "Throttle position", "How far the throttle plate in the air intake is open.",
            PCT, "Closed throttle often reads 10–20 %, not 0 %. Wide open reads about 75–100 %, depending on the car."
        ),
        Pids.COOLANT to SensorInfo(
            "Coolant temperature", "Temperature of the engine coolant – the best sign of how warm the engine is.",
            DEG_C, "Warm engine: about 85–105 °C. Above 110 °C means overheating – stop and let it cool.",
            "If it never gets above about 75 °C, the thermostat may be stuck open (code P0128)."
        ),
        Pids.IAT to SensorInfo(
            "Intake air temperature", "Temperature of the air going into the engine.",
            DEG_C, "Close to the outside temperature while driving; 10–30 °C higher in traffic or after a hot stop.",
            "Cooler air is denser and gives more power. Turbo cars with a hot intake can lose power."
        ),
        Pids.OIL_TEMP to SensorInfo(
            "Engine oil temperature", "Temperature of the engine oil.",
            DEG_C, "Warm engine: about 90–120 °C. Oil warms up more slowly than coolant.",
            "Avoid hard revving until the oil is above about 70 °C."
        ),
        Pids.AMBIENT to SensorInfo("Ambient air temperature", "Outside air temperature measured by the car.", DEG_C, "Should match the weather. It can read high when the car is parked in the sun."),
        0x3C to SensorInfo(
            "Catalyst temperature (bank 1)", "Temperature inside the catalytic converter, measured or estimated by the engine computer.",
            DEG_C, "Normal driving: about 400–800 °C. Long periods above about 900 °C can damage the converter.",
            "Misfires send unburned fuel into the converter and overheat it."
        ),
        0x3D to SensorInfo("Catalyst temperature (bank 2)", "Catalytic converter temperature on the second cylinder bank (V engines).", DEG_C, "Normal driving: about 400–800 °C."),
        Pids.MAF to SensorInfo(
            "Mass air flow (MAF)", "Weight of air entering the engine each second, measured by the MAF sensor.",
            "Grams per second (g/s).",
            "Warm idle: very roughly 1 g/s per litre of engine size (2.0 L ≈ 2–4 g/s). It climbs steeply with throttle.",
            "A dirty MAF reads low and makes the engine run lean (P0171). It can be cleaned with MAF cleaner spray."
        ),
        Pids.MAP to SensorInfo(
            "Intake manifold pressure (MAP)", "Absolute air pressure inside the intake manifold.",
            KPA,
            "Idle: about 25–40 kPa (strong vacuum). Full throttle: about 100 kPa on normal engines, above 100 kPa on turbo engines (the extra is boost).",
            "High idle pressure can mean a vacuum leak or weak engine compression."
        ),
        Pids.BARO to SensorInfo("Barometric pressure", "Outside air pressure.", KPA, "About 101 kPa at sea level; about 1 kPa lower for every 85 m of altitude."),
        Pids.TIMING to SensorInfo(
            "Timing advance", "How early the spark fires before the piston reaches the top of its stroke.",
            "Degrees (°) of crankshaft rotation before top dead centre.",
            "Idle: about 5–20 °. Light cruising: often 30 ° or more.",
            "If timing drops suddenly under load, the computer is pulling timing because of engine knock – try higher-octane fuel."
        ),
        Pids.STFT1 to SensorInfo(
            "Short-term fuel trim (bank 1)", "The engine computer's instant fuel correction, based on the upstream O2 sensor.",
            "Percent (%). Positive = adding fuel (mixture was lean), negative = removing fuel (mixture was rich).",
            "Moves around quickly within about ±10 %.",
            "Read it together with long-term fuel trim: if short + long add up to more than ±10–15 %, look for a problem."
        ),
        Pids.LTFT1 to SensorInfo(
            "Long-term fuel trim (bank 1)", "The fuel correction the engine computer has learned over time.",
            "Percent (%). Positive = the engine needs extra fuel, negative = it needs less.",
            "Healthy: within about ±10 %.",
            "Above +10 %: vacuum leak, dirty MAF or weak fuel pump (P0171). Below −10 %: leaking injector or high fuel pressure (P0172)."
        ),
        0x08 to SensorInfo("Short-term fuel trim (bank 2)", "Instant fuel correction for the second cylinder bank (V engines).", "Percent (%). Positive = adding fuel, negative = removing fuel.", "Moves around quickly within about ±10 %."),
        0x09 to SensorInfo(
            "Long-term fuel trim (bank 2)", "Learned fuel correction for the second cylinder bank.", "Percent (%).", "Within about ±10 %.",
            "If only one bank is off, the problem is probably on that side (for example an intake leak or injector)."
        ),
        0x0A to SensorInfo("Fuel pressure", "Fuel pressure in the fuel supply line.", KPA, "Port-injected petrol engines: usually about 250–400 kPa."),
        0x22 to SensorInfo("Fuel rail pressure (relative)", "Fuel rail pressure compared with the pressure in the intake manifold.", KPA, "Depends on the car; should stay steady at a steady throttle."),
        0x23 to SensorInfo(
            "Fuel rail pressure", "High-pressure fuel rail pressure on direct-injection and diesel engines.", KPA,
            "Direct-injection petrol: about 3,000–20,000 kPa. Diesel common rail: up to 200,000 kPa or more."
        ),
        Pids.FUEL_LEVEL to SensorInfo("Fuel tank level", "How full the fuel tank is.", PCT, "0 % = empty, 100 % = full. It can jump around on hills and in corners."),
        Pids.FUEL_RATE to SensorInfo(
            "Engine fuel rate", "How much fuel the engine is burning right now.", "Litres per hour (L/h).",
            "Idle: about 0.5–1.5 L/h. It rises a lot under acceleration."
        ),
        0x52 to SensorInfo("Ethanol fuel", "Share of ethanol in the fuel (flex-fuel cars).", PCT, "E10 petrol ≈ 10 %, E85 ≈ 85 %."),
        0x44 to SensorInfo(
            "Commanded air-fuel ratio (λ)", "The air-fuel mixture the engine computer is aiming for, given as lambda.",
            "Lambda (λ). 1.000 = the chemically ideal mixture (14.7 parts air to 1 part fuel for petrol).",
            "Cruising: about 1.00. Full throttle: about 0.80–0.90 (rich, for power and cooling). Coasting with the throttle off: very lean."
        ),
        0x2C to SensorInfo("Commanded EGR", "How far the engine computer wants the exhaust gas recirculation (EGR) valve open. EGR sends some exhaust back into the intake to lower NOx emissions.", PCT, "Usually 0 % at idle and full throttle; opens at light cruise."),
        0x2D to SensorInfo("EGR error", "Difference between how far the EGR valve was told to open and how far it actually opened.", "Percent (%).", "Close to 0 %.", "A large error often means a clogged EGR valve (P0401)."),
        0x2E to SensorInfo("Commanded evap purge", "How far the purge valve is open. It lets fuel vapour from the tank's charcoal canister be burned in the engine.", PCT, "Varies while driving; often 0 % at idle on a cold engine."),
        0x43 to SensorInfo("Absolute load", "Air drawn in per engine cycle, compared with the most the engine can draw at normal air pressure.", "Percent (%). Can go above 100 % on turbo engines.", "Idle: about 15–30 %. Turbo at full boost: 150 % or more."),
        0x45 to SensorInfo("Relative throttle position", "Throttle opening compared with its learned closed position.", PCT, "About 0 % with your foot off the pedal."),
        0x47 to SensorInfo("Absolute throttle position B", "A second throttle position sensor, used to double-check the first one for safety.", PCT, "Should move together with the main throttle sensor."),
        0x49 to SensorInfo("Accelerator pedal position D", "First of two gas pedal sensors (drive-by-wire).", PCT, "At rest usually 10–20 %, not 0 %. Floored: about 80 % or more."),
        0x4A to SensorInfo("Accelerator pedal position E", "Second gas pedal sensor, used to double-check sensor D.", PCT, "Should move in step with sensor D (often half its value).", "If D and E disagree, you'll often see code P2138 and reduced power."),
        0x4C to SensorInfo("Commanded throttle actuator", "How far the engine computer tells the electronic throttle to open.", PCT, "Follows your pedal, plus small changes for idle control."),
        0x5A to SensorInfo("Relative accelerator pedal", "Gas pedal position compared with its rest position.", PCT, "0 % with your foot off the pedal, about 100 % floored."),
        0x5D to SensorInfo("Fuel injection timing", "When fuel injection starts, relative to the piston reaching the top of its stroke (mainly diesel).", "Degrees (°) of crankshaft rotation. Negative = after top dead centre.", "Depends on the engine; changes with load and RPM."),
        0x61 to SensorInfo("Driver demand torque", "Engine torque you're asking for with the pedal, as a share of the engine's reference torque.", "Percent (%) of reference torque.", "Near 0 % when coasting; high when the pedal is floored."),
        0x62 to SensorInfo("Actual engine torque", "Torque the engine is actually producing, as a share of the engine's reference torque.", "Percent (%) of reference torque.", "Should follow driver demand torque closely.", "Multiply by the 'Engine reference torque' value to get Nm."),
        0x63 to SensorInfo("Engine reference torque", "The engine's reference torque, used to turn the torque percentages into real numbers.", "Newton-metres (Nm).", "A fixed value for your engine – roughly its maximum torque."),
        Pids.MODULE_VOLTAGE to SensorInfo(
            "Control module voltage", "Voltage reaching the engine computer.", "Volts (V).",
            "Engine running: about 13.5–14.7 V (the alternator is charging). Engine off: about 12.4–12.7 V.",
            "Below 13 V with the engine running can mean a weak alternator."
        ),
        0x5B to SensorInfo("Hybrid battery remaining", "Charge level of the high-voltage hybrid battery.", PCT, "Hybrids usually keep it between about 40 % and 80 %."),
        0x1F to SensorInfo("Run time since start", "How long the engine has been running since it was last started.", "Seconds (s).", "Resets every time the engine starts."),
        0x21 to SensorInfo("Distance with MIL on", "Distance driven since the check-engine light (MIL) came on.", "Kilometres (km).", "0 km when the light is off.", "A large number means the fault has been ignored for a while."),
        0x31 to SensorInfo(
            "Distance since codes cleared", "Distance driven since trouble codes were last cleared, or the battery was last disconnected.", "Kilometres (km).",
            "Any value is possible.",
            "Buying a used car? A very low number can mean someone just cleared the codes to hide a problem."
        ),
        0x30 to SensorInfo("Warm-ups since codes cleared", "Number of engine warm-ups since trouble codes were last cleared.", "Count.", "Any value. Readiness monitors need several warm-ups to complete."),
        0x4D to SensorInfo("Time run with MIL on", "Engine running time since the check-engine light came on.", "Minutes (min).", "0 when the light is off."),
        0x4E to SensorInfo("Time since codes cleared", "Engine running time since trouble codes were last cleared.", "Minutes (min).", "Any value.", "Like distance since codes cleared, a very low number on a used car is a warning sign."),
        0x14 to o2(1, 1), 0x15 to o2(1, 2), 0x16 to o2(1, 3), 0x17 to o2(1, 4),
        0x18 to o2(2, 1), 0x19 to o2(2, 2), 0x1A to o2(2, 3), 0x1B to o2(2, 4),
        BATTERY to SensorInfo(
            "Battery voltage", "Voltage at the OBD2 port, measured by the adapter itself. It's basically your car battery's voltage.", "Volts (V).",
            "Engine off: 12.4–12.7 V means a healthy, charged battery; below 12.2 V is low. Engine running: 13.5–14.7 V means it's charging.",
            "Above about 15 V with the engine running means the system is overcharging – have the alternator regulator checked."
        ),
    )

    fun get(code: Int): SensorInfo? = db[code]
}

/** Display helpers that also cover the adapter battery reading (not an OBD PID). */
object Sensors {
    fun name(code: Int): String = Pids.byCode[code]?.name
        ?: if (code == SensorInfoDb.BATTERY) "Battery voltage" else "PID %02X".format(code)

    fun unit(code: Int): String = Pids.byCode[code]?.unit ?: if (code == SensorInfoDb.BATTERY) "V" else ""

    fun decimals(code: Int): Int = Pids.byCode[code]?.decimals ?: if (code == SensorInfoDb.BATTERY) 1 else 0
}
