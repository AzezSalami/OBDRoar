package com.obdroar.app.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

enum class EngineState { OFF, CRANKING, RUNNING, STOPPING }

/**
 * Real-time procedural engine synthesizer (no samples).
 *
 * Every cylinder firing launches a decaying exhaust pressure pulse tuned to its bank's resonance.
 * Summing those pulses at the right crank angles produces the characteristic sound of each layout
 * (e.g. the cross-plane V8 burble comes from uneven bank pulses). On top of that: combustion noise,
 * an exhaust low-pass that opens with load, soft-clipping, rev limiter, overrun pops, turbo whistle
 * and blow-off valve.
 */
class EngineSoundEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        private const val BLOCK = 256
        private const val TWO_PI = (2 * PI).toFloat()
    }

    // ---- controls (written from UI / OBD thread) ----
    @Volatile var profile: EngineProfile = EngineProfiles.all.first()
        set(value) { field = value; profileChanged = true }

    /** 0..1, user pedal or car throttle. */
    @Volatile var throttle: Float = 0f

    /** When non-null the engine follows this RPM (from the real car) instead of free-revving. */
    @Volatile var externalRpm: Float? = null
        set(value) {
            val now = System.nanoTime()
            val prev = field
            val gap = (now - extTime) / 1e9f
            if (value != null && prev != null && gap in 0.02f..1f) {
                // rev rate (rpm/s), lightly damped so one noisy reading doesn't swing the pitch
                extSlope = extSlope * 0.3f + (value - prev) / gap * 0.7f
                extInterval = extInterval * 0.7f + gap * 0.3f
            } else {
                extSlope = 0f
            }
            extTime = now
            field = value
        }
    @Volatile private var extTime = 0L
    @Volatile private var extSlope = 0f
    @Volatile private var extInterval = 0.15f
    @Volatile var volume: Float = 0.8f
    @Volatile var turbo: Boolean = false
    @Volatile var popsAndBangs: Boolean = true

    // ---- read by UI ----
    @Volatile var rpm: Float = 0f; private set
    @Volatile var state: EngineState = EngineState.OFF; private set
    @Volatile var boost: Float = 0f; private set

    @Volatile private var wantOn = false
    @Volatile private var audioActive = false
    @Volatile private var profileChanged = true
    private var thread: Thread? = null

    fun setIgnition(on: Boolean) { wantOn = on }

    @Synchronized
    fun startAudio() {
        if (thread != null) return
        audioActive = true
        thread = Thread({ runLoop() }, "EngineSynth").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    fun stopAudio() {
        audioActive = false
        thread?.join(600)
        thread = null
        wantOn = false
        state = EngineState.OFF
        rpm = 0f
    }

    // ===================================================================== synth state
    private val rnd = Random(42)
    private var p: EngineProfile = profile
    private var n = 0
    private var cylGain = FloatArray(0)
    private var vEnv = FloatArray(0)
    private var vT = FloatArray(0)
    private var vAmp = FloatArray(0)
    private var vPop = BooleanArray(0)

    private var phase = 0.0
    private var rpmSmooth = 0f
    private var load = 0.15f
    private var lastThrottle = 0f
    private var stateTime = 0f
    private var flare = 0f
    private var limiter = false
    private var popTimer = 0f
    private var spool = 0f
    private var bovEnv = 0f
    private var turboPhase = 0f
    private var starterT = 0f
    private var masterVol = 0f
    private var ignitionGain = 0f

    // filters
    private var svfLow = 0f
    private var svfBand = 0f
    private var dcX = 0f
    private var dcY = 0f
    private var noiseLp = 0f
    private var intakeLp = 0f

    private fun applyProfile() {
        p = profile
        n = p.cylinders
        val r = Random(p.id.hashCode())
        cylGain = FloatArray(n) { 1f + (r.nextFloat() * 2f - 1f) * p.unevenness }
        vEnv = FloatArray(n); vT = FloatArray(n); vAmp = FloatArray(n); vPop = BooleanArray(n)
        profileChanged = false
    }

    private fun runLoop() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuf, BLOCK * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        val buf = FloatArray(BLOCK)
        try {
            track.play()
            while (audioActive) {
                if (profileChanged) applyProfile()
                updatePhysics(BLOCK / SAMPLE_RATE.toFloat())
                render(buf)
                track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
            }
        } catch (e: Throwable) {
            android.util.Log.w("EngineSynth", "audio loop stopped", e)
        } finally {
            runCatching { track.pause(); track.flush(); track.stop() }
            track.release()
        }
    }

    // ===================================================================== physics (per block)
    private fun approach(v: Float, target: Float, step: Float): Float =
        if (v < target) min(target, v + step) else max(target, v - step)

    private var rpmInternal = 0f

    private fun updatePhysics(dt: Float) {
        val thr = throttle.coerceIn(0f, 1f)
        val ext = externalRpm

        // state transitions
        if (wantOn && state == EngineState.OFF) { state = EngineState.CRANKING; stateTime = 0f }
        if (!wantOn && (state == EngineState.RUNNING || state == EngineState.CRANKING)) state = EngineState.STOPPING
        stateTime += dt

        when (state) {
            EngineState.OFF -> rpmInternal = 0f
            EngineState.CRANKING -> {
                rpmInternal = approach(rpmInternal, 240f, 1600f * dt)
                if (stateTime > p.crankTime) {
                    state = EngineState.RUNNING; stateTime = 0f; flare = 1f
                }
            }
            EngineState.RUNNING -> {
                if (ext != null) {
                    // The car only reports RPM every ~0.1–0.3 s. Between readings, keep moving in the
                    // direction the revs were heading (at most one reading interval ahead) so the
                    // sound doesn't lag behind, then ease quickly toward that estimate.
                    val since = (System.nanoTime() - extTime) / 1e9f
                    val ahead = min(since, min(extInterval, 0.3f))
                    val predicted = (ext + extSlope * ahead).coerceIn(0f, p.redline * 1.1f)
                    rpmInternal += (predicted - rpmInternal) * min(1f, dt * 14f)
                    limiter = false
                } else {
                    val shaped = thr.pow(1.15f)
                    var target = p.idleRpm + shaped * (p.redline * 1.06f - p.idleRpm)
                    target += flare * 900f
                    if (rpmInternal >= p.redline) limiter = true
                    if (limiter && rpmInternal < p.redline - 280f) limiter = false
                    rpmInternal = if (limiter) {
                        approach(rpmInternal, p.idleRpm, p.revDown * 1.3f * dt)
                    } else {
                        val rate = if (target > rpmInternal) p.revUp * (0.25f + thr) else p.revDown
                        approach(rpmInternal, target, rate * dt)
                    }
                }
                flare = max(0f, flare - dt / 1.4f)
            }
            EngineState.STOPPING -> {
                rpmInternal = approach(rpmInternal, 0f, 900f * dt)
                if (rpmInternal < 40f) { state = EngineState.OFF; rpmInternal = 0f }
            }
        }

        // load drives loudness/brightness
        val accel = if (ext == null && state == EngineState.RUNNING && !limiter) thr else thr * 0.9f
        val loadTarget = when (state) {
            EngineState.RUNNING -> 0.12f + 0.88f * accel
            EngineState.STOPPING -> 0.05f
            else -> 0.1f
        }
        load += (loadTarget - load) * min(1f, dt * 8f)

        // overrun pops after lifting off at high rpm
        if (popsAndBangs && lastThrottle - thr > 0.3f && rpmInternal > 2800f && state == EngineState.RUNNING) {
            popTimer = 0.5f + rnd.nextFloat() * 0.9f
        }
        popTimer = max(0f, popTimer - dt)

        // turbo
        val boostTarget = if (turbo && state == EngineState.RUNNING)
            thr * ((rpmInternal - 1500f) / 2500f).coerceIn(0f, 1f) else 0f
        spool += (boostTarget - spool) * min(1f, dt * (if (boostTarget > spool) 1.6f else 4f))
        if (turbo && lastThrottle - thr > 0.35f && spool > 0.35f) bovEnv = min(1f, spool * 1.2f)

        lastThrottle = thr
        rpm = rpmInternal
        boost = spool
    }

    // ===================================================================== audio (per sample)
    private fun noise(): Float = rnd.nextFloat() * 2f - 1f

    private fun render(out: FloatArray) {
        val sr = SAMPLE_RATE.toFloat()
        val invSr = 1f / sr
        val running = state == EngineState.RUNNING || state == EngineState.STOPPING
        val firing = running && !(state == EngineState.STOPPING && rpmInternal < 260f)
        val decay = p.decay * (1f + rpmSmooth / 3500f)
        val envMul = exp(-decay * invSr)
        val popEnvMul = exp(-22f * invSr)
        val cut = (p.brightness * (0.55f + 0.9f * load) + rpmSmooth * 0.35f).coerceAtMost(6500f)
        val f = 2f * sin(PI.toFloat() * cut / sr)
        val q = 0.75f
        val targetVol = volume.coerceIn(0f, 1f)
        val ampNorm = 1f / kotlin.math.sqrt(max(1f, n / 4f))
        val ignitionTarget = if (firing) 1f else 0f

        for (i in out.indices) {
            rpmSmooth += (rpmInternal - rpmSmooth) * 0.003f
            masterVol += (targetVol - masterVol) * 0.002f
            ignitionGain += (ignitionTarget - ignitionGain) * 0.004f

            // ---- crank angle & firing events
            val prev = phase
            phase += rpmSmooth / 120.0 / sr
            val wrapped = phase >= 1.0
            if (wrapped) phase -= 1.0
            if (firing) {
                for (c in 0 until n) {
                    val a = p.angles[c].toDouble()
                    val crossed = if (!wrapped) prev < a && phase >= a else prev < a || phase >= a
                    if (crossed) fire(c)
                }
            }

            // ---- exhaust pulses
            var sum = 0f
            var envSum = 0f
            for (c in 0 until n) {
                val e = vEnv[c]
                if (e < 0.0005f) continue
                val t = vT[c]
                val fb = p.bankFreq[p.banks[c] % p.bankFreq.size]
                var s: Float
                if (vPop[c]) {
                    s = e * (0.9f * noise() + sin(TWO_PI * 85f * t))
                    vEnv[c] = e * popEnvMul
                } else {
                    s = e * (sin(TWO_PI * fb * t) + 0.32f * (0.4f + load) * sin(TWO_PI * p.raspFreq * t + 0.7f))
                    if (p.diesel) s += exp(-t * 900f) * noise() * 0.7f
                    vEnv[c] = e * envMul
                }
                sum += s * vAmp[c]
                envSum += e
                vT[c] = t + invSr
            }
            sum *= ampNorm

            // combustion rasp + intake roar
            noiseLp += (noise() - noiseLp) * 0.35f
            sum += envSum * noiseLp * p.noise * (0.25f + load) * ampNorm
            intakeLp += (noise() - intakeLp) * 0.05f
            sum += intakeLp * load * load * 0.35f * min(1f, rpmSmooth / 3000f) * ignitionGain

            // starter motor
            if (state == EngineState.CRANKING || (state == EngineState.RUNNING && stateTime < 0.15f)) {
                starterT += invSr
                val comp = 0.55f + 0.45f * sin(TWO_PI * (phase * n).toFloat())
                sum += comp * (0.16f * sin(TWO_PI * 165f * starterT) + 0.10f * sin(TWO_PI * 330f * starterT) +
                    0.05f * noise())
            }

            // ---- exhaust low-pass (Chamberlin SVF)
            svfLow += f * svfBand
            val high = sum - svfLow - q * svfBand
            svfBand += f * high
            var y = svfLow * 0.8f + sum * 0.2f

            // DC blocker
            val dc = y - dcX + 0.995f * dcY
            dcX = y; dcY = dc
            y = dc

            // turbo whistle + blow-off valve
            if (spool > 0.01f) {
                turboPhase += TWO_PI * (1800f + 8500f * spool) * invSr
                if (turboPhase > TWO_PI) turboPhase -= TWO_PI
                y += sin(turboPhase) * spool * spool * 0.05f
            }
            if (bovEnv > 0.001f) {
                val nz = noise()
                y += (nz - noiseLp) * bovEnv * 0.22f
                bovEnv *= 0.99982f
            }

            // saturation
            y = tanh(y * p.drive * 0.5f) * 0.9f
            out[i] = y * masterVol
        }
    }

    private fun fire(c: Int) {
        // rev limiter: cut most ignition events
        if (limiter && rnd.nextFloat() < 0.75f) {
            if (popsAndBangs && rnd.nextFloat() < 0.15f) startPop(c)
            return
        }
        if (popTimer > 0f && throttle < 0.15f && rnd.nextFloat() < 0.22f) {
            startPop(c); return
        }
        val jitter = 1f + (rnd.nextFloat() - 0.5f) * 0.12f
        val idleLope = if (rpmSmooth < p.idleRpm * 1.6f) 1f + p.unevenness * 0.6f * (cylGain[c] - 1f) * 3f else 1f
        vAmp[c] = cylGain[c] * jitter * idleLope * (0.38f + 0.62f * load) * ignitionGain.coerceAtLeast(0.3f)
        vEnv[c] = 1f
        vT[c] = 0f
        vPop[c] = false
    }

    private fun startPop(c: Int) {
        vAmp[c] = 1.7f + rnd.nextFloat()
        vEnv[c] = 1f
        vT[c] = 0f
        vPop[c] = true
    }
}
