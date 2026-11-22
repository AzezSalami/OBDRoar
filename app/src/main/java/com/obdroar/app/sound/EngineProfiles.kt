package com.obdroar.app.sound

/**
 * Describes how an engine "sounds": when each cylinder fires inside the 720° four-stroke cycle,
 * which exhaust bank it belongs to and the resonance of each bank.
 */
class EngineProfile(
    val id: String,
    val name: String,
    val tagline: String,
    /** Firing points as a fraction (0..1) of one 720° cycle. Size == cylinder count. */
    val angles: FloatArray,
    /** Exhaust bank of each cylinder (0 or 1). */
    val banks: IntArray,
    val idleRpm: Float,
    val redline: Float,
    /** Exhaust body resonance per bank, Hz. Two different values = burble. */
    val bankFreq: FloatArray,
    /** Higher "rasp" partial, Hz. */
    val raspFreq: Float,
    /** Pulse decay, 1/s. Lower = boomier. */
    val decay: Float,
    /** Cylinder-to-cylinder loudness variation (lope). */
    val unevenness: Float,
    val noise: Float,
    val drive: Float,
    /** Base cutoff of the exhaust filter, Hz. */
    val brightness: Float,
    val revUp: Float,
    val revDown: Float,
    val crankTime: Float = 0.9f,
    val diesel: Boolean = false,
) {
    val cylinders: Int get() = angles.size
}

object EngineProfiles {
    private fun even(n: Int) = FloatArray(n) { it / n.toFloat() }
    private fun alt(n: Int) = IntArray(n) { it % 2 }

    val all: List<EngineProfile> = listOf(
        EngineProfile(
            "v8_muscle", "V8 Muscle", "Cross-plane, lumpy burble",
            even(8), intArrayOf(0, 1, 1, 0, 1, 0, 0, 1),
            idleRpm = 700f, redline = 6800f,
            bankFreq = floatArrayOf(118f, 151f), raspFreq = 620f,
            decay = 55f, unevenness = 0.38f, noise = 0.30f, drive = 2.2f, brightness = 900f,
            revUp = 7000f, revDown = 4200f
        ),
        EngineProfile(
            "v8_flat", "V8 Flat-plane", "Italian supercar scream",
            even(8), alt(8),
            idleRpm = 1000f, redline = 9000f,
            bankFreq = floatArrayOf(210f, 230f), raspFreq = 1150f,
            decay = 80f, unevenness = 0.10f, noise = 0.28f, drive = 2.6f, brightness = 1500f,
            revUp = 12000f, revDown = 7500f
        ),
        EngineProfile(
            "i4_hot", "Inline-4 Hot Hatch", "Raspy turbo four",
            even(4), IntArray(4),
            idleRpm = 850f, redline = 7200f,
            bankFreq = floatArrayOf(190f), raspFreq = 900f,
            decay = 70f, unevenness = 0.08f, noise = 0.42f, drive = 2.4f, brightness = 1300f,
            revUp = 9500f, revDown = 6500f
        ),
        EngineProfile(
            "i6", "Inline-6", "Smooth German straight six",
            even(6), IntArray(6),
            idleRpm = 700f, redline = 7500f,
            bankFreq = floatArrayOf(170f), raspFreq = 980f,
            decay = 65f, unevenness = 0.05f, noise = 0.22f, drive = 1.8f, brightness = 1200f,
            revUp = 8500f, revDown = 5500f
        ),
        EngineProfile(
            "v6", "V6 Sport", "Throaty six",
            even(6), alt(6),
            idleRpm = 750f, redline = 7000f,
            bankFreq = floatArrayOf(150f, 172f), raspFreq = 800f,
            decay = 62f, unevenness = 0.14f, noise = 0.30f, drive = 2.0f, brightness = 1050f,
            revUp = 8000f, revDown = 5200f
        ),
        EngineProfile(
            "boxer", "Boxer-4 Rumble", "Unequal-length header thrum",
            even(4), intArrayOf(0, 0, 1, 1),
            idleRpm = 800f, redline = 7000f,
            bankFreq = floatArrayOf(130f, 175f), raspFreq = 700f,
            decay = 58f, unevenness = 0.30f, noise = 0.30f, drive = 2.1f, brightness = 950f,
            revUp = 8500f, revDown = 5800f
        ),
        EngineProfile(
            "v12", "V12 Grand Tourer", "Twelve-cylinder wail",
            even(12), alt(12),
            idleRpm = 800f, redline = 8500f,
            bankFreq = floatArrayOf(240f, 255f), raspFreq = 1400f,
            decay = 95f, unevenness = 0.04f, noise = 0.20f, drive = 2.0f, brightness = 1700f,
            revUp = 10000f, revDown = 6500f
        ),
        EngineProfile(
            "vtwin", "V-Twin Cruiser", "Potato-potato 45° twin",
            floatArrayOf(0f, 315f / 720f), intArrayOf(0, 0),
            idleRpm = 900f, redline = 5800f,
            bankFreq = floatArrayOf(95f), raspFreq = 480f,
            decay = 32f, unevenness = 0.12f, noise = 0.35f, drive = 2.6f, brightness = 700f,
            revUp = 7500f, revDown = 5000f, crankTime = 0.7f
        ),
        EngineProfile(
            "single", "Single Dirt Bike", "Braaap thumper",
            floatArrayOf(0f), intArrayOf(0),
            idleRpm = 1500f, redline = 11000f,
            bankFreq = floatArrayOf(160f), raspFreq = 1100f,
            decay = 42f, unevenness = 0.10f, noise = 0.45f, drive = 3.0f, brightness = 1100f,
            revUp = 20000f, revDown = 12000f, crankTime = 0.5f
        ),
        EngineProfile(
            "diesel", "Diesel Truck", "Clattering turbo-diesel six",
            even(6), IntArray(6),
            idleRpm = 650f, redline = 4300f,
            bankFreq = floatArrayOf(105f), raspFreq = 520f,
            decay = 45f, unevenness = 0.12f, noise = 0.35f, drive = 1.9f, brightness = 650f,
            revUp = 3500f, revDown = 2200f, crankTime = 1.5f, diesel = true
        ),
    )

    fun byId(id: String): EngineProfile = all.firstOrNull { it.id == id } ?: all.first()
}
