package com.obdroar.app.obd

enum class Severity(val label: String) { LOW("Minor"), MEDIUM("Moderate"), HIGH("Serious") }

data class DtcInfo(
    val code: String,
    val description: String,
    val system: String,
    val severity: Severity,
    val causes: List<String>,
    val manufacturerSpecific: Boolean
)

/** Generic SAE J2012 descriptions for the most common trouble codes + smart fallbacks. */
object DtcDatabase {

    private val H = Severity.HIGH
    private val M = Severity.MEDIUM
    private val L = Severity.LOW

    private val codes: Map<String, Pair<String, Severity>> = mapOf(
        // Camshaft / VVT
        "P0010" to ("Intake camshaft actuator circuit (Bank 1)" to M),
        "P0011" to ("Intake camshaft timing over-advanced (Bank 1)" to M),
        "P0012" to ("Intake camshaft timing over-retarded (Bank 1)" to M),
        "P0013" to ("Exhaust camshaft actuator circuit (Bank 1)" to M),
        "P0014" to ("Exhaust camshaft timing over-advanced (Bank 1)" to M),
        "P0016" to ("Crankshaft / camshaft position correlation (Bank 1 Sensor A)" to H),
        "P0017" to ("Crankshaft / camshaft position correlation (Bank 1 Sensor B)" to H),
        "P0018" to ("Crankshaft / camshaft position correlation (Bank 2 Sensor A)" to H),
        "P0020" to ("Intake camshaft actuator circuit (Bank 2)" to M),
        "P0021" to ("Intake camshaft timing over-advanced (Bank 2)" to M),
        // O2 heaters
        "P0030" to ("HO2S heater control circuit (Bank 1 Sensor 1)" to L),
        "P0036" to ("HO2S heater control circuit (Bank 1 Sensor 2)" to L),
        "P0037" to ("HO2S heater control circuit low (Bank 1 Sensor 2)" to L),
        "P0038" to ("HO2S heater control circuit high (Bank 1 Sensor 2)" to L),
        "P0050" to ("HO2S heater control circuit (Bank 2 Sensor 1)" to L),
        // Air metering
        "P0068" to ("MAP / MAF – throttle position correlation" to M),
        "P0087" to ("Fuel rail / system pressure too low" to H),
        "P0088" to ("Fuel rail / system pressure too high" to H),
        "P0093" to ("Fuel system large leak detected" to H),
        "P0100" to ("Mass air flow circuit malfunction" to M),
        "P0101" to ("Mass air flow circuit range / performance" to M),
        "P0102" to ("Mass air flow circuit low input" to M),
        "P0103" to ("Mass air flow circuit high input" to M),
        "P0106" to ("MAP / barometric pressure circuit range / performance" to M),
        "P0107" to ("MAP / barometric pressure circuit low input" to M),
        "P0108" to ("MAP / barometric pressure circuit high input" to M),
        "P0110" to ("Intake air temperature circuit malfunction" to L),
        "P0112" to ("Intake air temperature circuit low input" to L),
        "P0113" to ("Intake air temperature circuit high input" to L),
        "P0115" to ("Engine coolant temperature circuit malfunction" to M),
        "P0116" to ("Engine coolant temperature circuit range / performance" to M),
        "P0117" to ("Engine coolant temperature circuit low input" to M),
        "P0118" to ("Engine coolant temperature circuit high input" to M),
        "P0120" to ("Throttle position sensor A circuit malfunction" to H),
        "P0121" to ("Throttle position sensor A range / performance" to H),
        "P0122" to ("Throttle position sensor A circuit low input" to H),
        "P0123" to ("Throttle position sensor A circuit high input" to H),
        "P0125" to ("Insufficient coolant temperature for closed-loop fuel control" to L),
        "P0128" to ("Coolant thermostat (coolant temp below regulating temperature)" to L),
        "P0130" to ("O2 sensor circuit malfunction (Bank 1 Sensor 1)" to M),
        "P0131" to ("O2 sensor circuit low voltage (Bank 1 Sensor 1)" to M),
        "P0132" to ("O2 sensor circuit high voltage (Bank 1 Sensor 1)" to M),
        "P0133" to ("O2 sensor circuit slow response (Bank 1 Sensor 1)" to M),
        "P0134" to ("O2 sensor circuit no activity detected (Bank 1 Sensor 1)" to M),
        "P0135" to ("O2 sensor heater circuit malfunction (Bank 1 Sensor 1)" to L),
        "P0136" to ("O2 sensor circuit malfunction (Bank 1 Sensor 2)" to L),
        "P0137" to ("O2 sensor circuit low voltage (Bank 1 Sensor 2)" to L),
        "P0138" to ("O2 sensor circuit high voltage (Bank 1 Sensor 2)" to L),
        "P0139" to ("O2 sensor circuit slow response (Bank 1 Sensor 2)" to L),
        "P0140" to ("O2 sensor circuit no activity detected (Bank 1 Sensor 2)" to L),
        "P0141" to ("O2 sensor heater circuit malfunction (Bank 1 Sensor 2)" to L),
        "P0150" to ("O2 sensor circuit malfunction (Bank 2 Sensor 1)" to M),
        "P0155" to ("O2 sensor heater circuit malfunction (Bank 2 Sensor 1)" to L),
        "P0161" to ("O2 sensor heater circuit malfunction (Bank 2 Sensor 2)" to L),
        // Fuel trim
        "P0170" to ("Fuel trim malfunction (Bank 1)" to M),
        "P0171" to ("System too lean (Bank 1)" to M),
        "P0172" to ("System too rich (Bank 1)" to M),
        "P0173" to ("Fuel trim malfunction (Bank 2)" to M),
        "P0174" to ("System too lean (Bank 2)" to M),
        "P0175" to ("System too rich (Bank 2)" to M),
        // Injectors
        "P0200" to ("Injector circuit malfunction" to H),
        "P0201" to ("Injector circuit malfunction – cylinder 1" to H),
        "P0202" to ("Injector circuit malfunction – cylinder 2" to H),
        "P0203" to ("Injector circuit malfunction – cylinder 3" to H),
        "P0204" to ("Injector circuit malfunction – cylinder 4" to H),
        "P0205" to ("Injector circuit malfunction – cylinder 5" to H),
        "P0206" to ("Injector circuit malfunction – cylinder 6" to H),
        "P0217" to ("Engine over-temperature condition" to H),
        "P0218" to ("Transmission over-temperature condition" to H),
        "P0219" to ("Engine over-speed condition" to H),
        "P0220" to ("Throttle position sensor B circuit malfunction" to H),
        "P0230" to ("Fuel pump primary circuit malfunction" to H),
        "P0234" to ("Turbo / supercharger overboost condition" to H),
        "P0236" to ("Turbo boost sensor A range / performance" to M),
        "P0299" to ("Turbo / supercharger underboost" to M),
        // Misfire
        "P0300" to ("Random / multiple cylinder misfire detected" to H),
        "P0301" to ("Cylinder 1 misfire detected" to H),
        "P0302" to ("Cylinder 2 misfire detected" to H),
        "P0303" to ("Cylinder 3 misfire detected" to H),
        "P0304" to ("Cylinder 4 misfire detected" to H),
        "P0305" to ("Cylinder 5 misfire detected" to H),
        "P0306" to ("Cylinder 6 misfire detected" to H),
        "P0307" to ("Cylinder 7 misfire detected" to H),
        "P0308" to ("Cylinder 8 misfire detected" to H),
        "P0316" to ("Misfire detected on start-up (first 1000 revolutions)" to H),
        "P0320" to ("Ignition / distributor engine speed input circuit" to H),
        "P0325" to ("Knock sensor 1 circuit malfunction (Bank 1)" to M),
        "P0327" to ("Knock sensor 1 circuit low input (Bank 1)" to M),
        "P0328" to ("Knock sensor 1 circuit high input (Bank 1)" to M),
        "P0330" to ("Knock sensor 2 circuit malfunction (Bank 2)" to M),
        "P0335" to ("Crankshaft position sensor A circuit malfunction" to H),
        "P0336" to ("Crankshaft position sensor A range / performance" to H),
        "P0340" to ("Camshaft position sensor circuit malfunction" to H),
        "P0341" to ("Camshaft position sensor range / performance" to H),
        "P0345" to ("Camshaft position sensor A circuit (Bank 2)" to H),
        "P0351" to ("Ignition coil A primary / secondary circuit" to H),
        "P0352" to ("Ignition coil B primary / secondary circuit" to H),
        "P0353" to ("Ignition coil C primary / secondary circuit" to H),
        "P0354" to ("Ignition coil D primary / secondary circuit" to H),
        "P0380" to ("Glow plug / heater circuit A malfunction" to M),
        // Emissions
        "P0400" to ("Exhaust gas recirculation flow malfunction" to M),
        "P0401" to ("Exhaust gas recirculation flow insufficient" to M),
        "P0402" to ("Exhaust gas recirculation flow excessive" to M),
        "P0403" to ("EGR circuit malfunction" to M),
        "P0404" to ("EGR circuit range / performance" to M),
        "P0410" to ("Secondary air injection system malfunction" to L),
        "P0411" to ("Secondary air injection incorrect flow detected" to L),
        "P0420" to ("Catalyst system efficiency below threshold (Bank 1)" to M),
        "P0421" to ("Warm-up catalyst efficiency below threshold (Bank 1)" to M),
        "P0430" to ("Catalyst system efficiency below threshold (Bank 2)" to M),
        "P0440" to ("Evaporative emission control system malfunction" to L),
        "P0441" to ("Evaporative emission system incorrect purge flow" to L),
        "P0442" to ("Evaporative emission system small leak detected" to L),
        "P0443" to ("Evap purge control valve circuit malfunction" to L),
        "P0446" to ("Evap vent control circuit malfunction" to L),
        "P0449" to ("Evap vent valve / solenoid circuit malfunction" to L),
        "P0451" to ("Evap pressure sensor range / performance" to L),
        "P0455" to ("Evaporative emission system large leak detected" to L),
        "P0456" to ("Evaporative emission system very small leak detected" to L),
        "P0457" to ("Evaporative emission leak detected (fuel cap loose / off)" to L),
        "P0460" to ("Fuel level sensor circuit malfunction" to L),
        "P0480" to ("Cooling fan 1 control circuit malfunction" to H),
        "P0496" to ("Evap system high purge flow" to L),
        // Speed / idle
        "P0500" to ("Vehicle speed sensor malfunction" to M),
        "P0502" to ("Vehicle speed sensor circuit low input" to M),
        "P0505" to ("Idle air control system malfunction" to M),
        "P0506" to ("Idle control system RPM lower than expected" to L),
        "P0507" to ("Idle control system RPM higher than expected" to L),
        "P0520" to ("Engine oil pressure sensor / switch circuit" to H),
        "P0521" to ("Engine oil pressure sensor range / performance" to H),
        "P0524" to ("Engine oil pressure too low" to H),
        "P0530" to ("A/C refrigerant pressure sensor circuit" to L),
        "P0562" to ("System voltage low" to M),
        "P0563" to ("System voltage high" to M),
        "P0571" to ("Brake switch A circuit malfunction" to M),
        // Computer
        "P0600" to ("Serial communication link malfunction" to H),
        "P0601" to ("Internal control module memory checksum error" to H),
        "P0603" to ("Internal control module keep-alive memory (KAM) error" to M),
        "P0604" to ("Internal control module RAM error" to H),
        "P0606" to ("Control module processor fault" to H),
        "P0607" to ("Control module performance" to H),
        "P0620" to ("Generator control circuit malfunction" to M),
        "P0627" to ("Fuel pump control circuit / open" to H),
        "P0641" to ("Sensor reference voltage A circuit / open" to H),
        "P0650" to ("Malfunction indicator lamp (MIL) control circuit" to L),
        "P0670" to ("Glow plug control module circuit" to M),
        // Transmission
        "P0700" to ("Transmission control system malfunction (see TCM codes)" to H),
        "P0705" to ("Transmission range sensor circuit malfunction (PRNDL)" to M),
        "P0715" to ("Input / turbine speed sensor circuit malfunction" to M),
        "P0720" to ("Output speed sensor circuit malfunction" to M),
        "P0730" to ("Incorrect gear ratio" to H),
        "P0740" to ("Torque converter clutch circuit malfunction" to M),
        "P0741" to ("Torque converter clutch performance or stuck off" to M),
        "P0750" to ("Shift solenoid A malfunction" to M),
        "P0755" to ("Shift solenoid B malfunction" to M),
        "P0780" to ("Shift malfunction" to H),
        // Hybrid / misc
        "P0A80" to ("Replace hybrid battery pack" to H),
        "P2096" to ("Post-catalyst fuel trim system too lean (Bank 1)" to L),
        "P2097" to ("Post-catalyst fuel trim system too rich (Bank 1)" to L),
        "P2135" to ("Throttle / pedal position sensor A/B voltage correlation" to H),
        "P2138" to ("Throttle / pedal position sensor D/E voltage correlation" to H),
        "P2187" to ("System too lean at idle (Bank 1)" to M),
        "P2195" to ("O2 sensor signal stuck lean (Bank 1 Sensor 1)" to M),
        "P2196" to ("O2 sensor signal stuck rich (Bank 1 Sensor 1)" to M),
        "P2263" to ("Turbo / supercharger boost system performance" to M),
        "P242F" to ("Diesel particulate filter restriction – ash accumulation" to H),
        "P2002" to ("Diesel particulate filter efficiency below threshold" to M),
        "P2463" to ("Diesel particulate filter restriction – soot accumulation" to H),
        // Network
        "U0001" to ("High speed CAN communication bus" to H),
        "U0073" to ("Control module communication bus off" to H),
        "U0100" to ("Lost communication with ECM / PCM" to H),
        "U0101" to ("Lost communication with TCM" to H),
        "U0121" to ("Lost communication with ABS control module" to H),
        "U0140" to ("Lost communication with body control module" to M),
        "U0155" to ("Lost communication with instrument cluster" to M),
        // Chassis / body
        "C0035" to ("Left front wheel speed sensor circuit" to H),
        "C0040" to ("Right front wheel speed sensor circuit" to H),
        "C0045" to ("Left rear wheel speed sensor circuit" to H),
        "C0050" to ("Right rear wheel speed sensor circuit" to H),
        "B0001" to ("Driver frontal stage 1 airbag deployment control" to H),
        "B1000" to ("Body / airbag control module internal fault" to H),
    )

    private val causes: Map<String, List<String>> = mapOf(
        "P0171" to listOf("Vacuum leak (hoses, intake gasket)", "Dirty / faulty MAF sensor", "Weak fuel pump or clogged filter", "Leaking PCV valve"),
        "P0172" to listOf("Leaking injector", "Faulty MAF or O2 sensor", "High fuel pressure", "Dirty air filter"),
        "P0174" to listOf("Vacuum leak", "Dirty MAF sensor", "Low fuel pressure"),
        "P0300" to listOf("Worn spark plugs / coils", "Vacuum leak", "Low fuel pressure", "Low compression"),
        "P0301" to listOf("Spark plug or coil on cyl. 1", "Injector cyl. 1", "Compression (valves / rings)"),
        "P0302" to listOf("Spark plug or coil on cyl. 2", "Injector cyl. 2", "Compression (valves / rings)"),
        "P0303" to listOf("Spark plug or coil on cyl. 3", "Injector cyl. 3", "Compression (valves / rings)"),
        "P0304" to listOf("Spark plug or coil on cyl. 4", "Injector cyl. 4", "Compression (valves / rings)"),
        "P0420" to listOf("Worn catalytic converter", "Faulty downstream O2 sensor", "Exhaust leak", "Engine misfires damaging the cat"),
        "P0430" to listOf("Worn catalytic converter (Bank 2)", "Faulty downstream O2 sensor", "Exhaust leak"),
        "P0442" to listOf("Loose / cracked fuel cap", "Cracked evap hose", "Faulty purge or vent valve"),
        "P0455" to listOf("Fuel cap missing or loose", "Disconnected evap hose", "Faulty vent valve"),
        "P0457" to listOf("Fuel cap loose / missing – tighten and drive a few days"),
        "P0128" to listOf("Thermostat stuck open", "Coolant temperature sensor", "Low coolant"),
        "P0133" to listOf("Aging O2 sensor", "Exhaust leak before sensor", "Wiring"),
        "P0101" to listOf("Dirty MAF sensor (clean with MAF cleaner)", "Air leak after the MAF", "Clogged air filter"),
        "P0401" to listOf("Carbon-clogged EGR passages", "Faulty EGR valve", "Bad DPFE sensor"),
        "P0335" to listOf("Faulty crankshaft sensor", "Damaged reluctor ring", "Wiring / connector"),
        "P0340" to listOf("Faulty camshaft sensor", "Timing belt / chain stretched", "Wiring"),
        "P0016" to listOf("Stretched timing chain", "Faulty VVT solenoid", "Low / dirty oil"),
        "P0011" to listOf("Low or dirty engine oil", "Faulty VVT solenoid", "Timing chain wear"),
        "P0299" to listOf("Boost leak (hoses, intercooler)", "Sticking wastegate / VGT", "Worn turbo"),
        "P0562" to listOf("Weak battery", "Failing alternator", "Corroded battery terminals"),
        "P0700" to listOf("Read the transmission module codes for details"),
        "P0500" to listOf("Faulty speed sensor", "Wiring", "ABS wheel sensor fault"),
        "P0507" to listOf("Vacuum leak", "Dirty throttle body / IAC", "Needs idle relearn"),
        "U0100" to listOf("Wiring / CAN bus fault", "ECM power or ground", "Weak battery"),
        "P0087" to listOf("Weak fuel pump", "Clogged fuel filter", "Faulty pressure regulator / sensor"),
    )

    fun lookup(code: String): DtcInfo {
        val known = codes[code]
        val digit2 = code.getOrNull(1) ?: '0'
        val manufacturer = when (code.firstOrNull()) {
            'P' -> digit2 == '1' || digit2 == '3'
            'U', 'B', 'C' -> digit2 == '1' || digit2 == '2'
            else -> false
        }
        val system = systemOf(code)
        val description = known?.first ?: if (manufacturer)
            "Manufacturer-specific code – $system. Check your brand's service info for the exact meaning."
        else "$system fault (generic code not in the offline database)"
        val severity = known?.second ?: when (code.firstOrNull()) {
            'U' -> Severity.HIGH
            else -> Severity.MEDIUM
        }
        return DtcInfo(code, description, system, severity, causes[code] ?: emptyList(), manufacturer)
    }

    fun systemOf(code: String): String {
        if (code.length < 3) return "Unknown"
        return when (code[0]) {
            'P' -> when (code[2]) {
                '0' -> "Fuel / air metering & aux. emissions"
                '1', '2' -> "Fuel and air metering"
                '3' -> "Ignition system / misfire"
                '4' -> "Auxiliary emission controls"
                '5' -> "Speed, idle control & aux. inputs"
                '6' -> "Computer & output circuits"
                '7', '8', '9' -> "Transmission"
                'A', 'B', 'C' -> "Hybrid propulsion"
                else -> "Powertrain"
            }
            'C' -> "Chassis (ABS, steering, suspension)"
            'B' -> "Body (airbags, A/C, lighting, comfort)"
            'U' -> "Network / module communication"
            else -> "Unknown"
        }
    }
}

/** Tiny offline VIN decoder: manufacturer (WMI) and model year. */
object VinDecoder {
    private val wmi = mapOf(
        "WBA" to "BMW", "WBS" to "BMW M", "WBY" to "BMW i", "WDB" to "Mercedes-Benz", "WDD" to "Mercedes-Benz",
        "W1K" to "Mercedes-Benz", "WAU" to "Audi", "WUA" to "Audi Sport", "WVW" to "Volkswagen", "WV1" to "Volkswagen Commercial",
        "WV2" to "Volkswagen Bus", "WP0" to "Porsche", "WP1" to "Porsche SUV", "W0L" to "Opel / Vauxhall", "WF0" to "Ford Germany",
        "TMB" to "Škoda", "VSS" to "SEAT", "VF1" to "Renault", "VF3" to "Peugeot", "VF7" to "Citroën", "ZFA" to "Fiat",
        "ZAR" to "Alfa Romeo", "ZFF" to "Ferrari", "ZHW" to "Lamborghini", "SAL" to "Land Rover", "SAJ" to "Jaguar",
        "SCC" to "Lotus", "YV1" to "Volvo", "YS3" to "Saab", "JHM" to "Honda", "JHL" to "Honda", "JT2" to "Toyota", "JTD" to "Toyota",
        "JTE" to "Toyota", "JTH" to "Lexus", "JN1" to "Nissan", "JN8" to "Nissan", "JM1" to "Mazda", "JF1" to "Subaru", "JF2" to "Subaru",
        "JA3" to "Mitsubishi", "JS1" to "Suzuki", "KMH" to "Hyundai", "KNA" to "Kia", "KND" to "Kia", "1G1" to "Chevrolet",
        "1GC" to "Chevrolet Truck", "1GT" to "GMC Truck", "1G6" to "Cadillac", "1FA" to "Ford", "1FT" to "Ford Truck",
        "1FM" to "Ford SUV", "1C4" to "Chrysler / Jeep", "1C6" to "Ram", "2C3" to "Chrysler / Dodge (Canada)", "1J4" to "Jeep",
        "1HG" to "Honda (USA)", "2HG" to "Honda (Canada)", "4T1" to "Toyota (USA)", "5YJ" to "Tesla", "7SA" to "Tesla",
        "3VW" to "Volkswagen (Mexico)", "5UX" to "BMW (USA)", "4S3" to "Subaru (USA)", "5N1" to "Nissan (USA)", "LRW" to "Tesla (China)"
    )

    private const val YEAR_CHARS = "ABCDEFGHJKLMNPRSTVWXY123456789"

    fun manufacturer(vin: String): String? = wmi[vin.take(3)]

    /** Returns the most recent plausible model year for position 10. */
    fun modelYear(vin: String, currentYear: Int): Int? {
        if (vin.length != 17) return null
        val idx = YEAR_CHARS.indexOf(vin[9])
        if (idx < 0) return null
        var year = 1980 + idx
        while (year + 30 <= currentYear + 1) year += 30
        return year
    }
}
