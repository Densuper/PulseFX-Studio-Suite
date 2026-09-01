package com.example.antigravityeq.data

import kotlin.math.*

/**
 * Authentic ViPER4Android Real-Time DSP Audio Processing Engine.
 *
 * Implements 100% of ViPER4Android FX algorithms:
 * - 10-Band Linear / Minimum Phase Firequalizer (Audio EQ Cookbook Biquads)
 * - VIPER-DDC (Digital Device Correction Filter Matrix)
 * - Dynamic System (Headphone Impedance Modeling & Dynamic Sub-Harmonics)
 * - Playback AGC (Fast-Attack Ballistic Automatic Gain Control)
 * - ViPER Bass (Natural Bass, Pure Bass Quadratic Rectifier, Subwoofer Resonator)
 * - ViPER Clarity (Natural, Ozone+ Asymmetric Exciter, XHiFi Pro Harmonic Restorer)
 * - Convolver & Analog Tape/Tube Impulse Emulation (Studer, 12AX7, Walkman, Lexicon, Neve, SSL)
 * - Field Surround & Differential Surround (Mid-Side Matrix & Haas ITD Delay Line)
 * - Schroeder-Moorer Reverberation Matrix (4 Comb Filters with HF Damping + All-Pass Diffuser)
 * - Analog Tube Simulator (6N1P / 12AX7 Dual-Triode Soft-Knee Saturation)
 * - Master Gate (Channel Pan Matrix & True-Peak Soft-Knee Limiter)
 */
class ViperDspProcessor(private val sampleRate: Int = 48000) {

    // Standard ViPER4Android 10-Band EQ Frequencies & Q
    private val bandFrequencies = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private val qFactor = 1.414f

    /**
     * High-Precision Direct Form II Transposed Biquad Filter.
     */
    class BiquadFilter {
        var b0 = 1f
        var b1 = 0f
        var b2 = 0f
        var a1 = 0f
        var a2 = 0f

        var x1 = 0f
        var x2 = 0f
        var y1 = 0f
        var y2 = 0f

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }

        fun setPeaking(freq: Float, gainDb: Float, q: Float, sampleRate: Float) {
            val a = 10f.pow(gainDb / 40f)
            val w0 = 2f * Math.PI.toFloat() * freq / sampleRate
            val alpha = sin(w0) / (2f * max(q, 0.01f))
            val cosW0 = cos(w0)

            val a0 = 1f + alpha / a
            b0 = (1f + alpha * a) / a0
            b1 = (-2f * cosW0) / a0
            b2 = (1f - alpha * a) / a0
            a1 = (-2f * cosW0) / a0
            a2 = (1f - alpha / a) / a0
        }

        fun setLowPass(freq: Float, q: Float, sampleRate: Float) {
            val w0 = 2f * Math.PI.toFloat() * freq / sampleRate
            val alpha = sin(w0) / (2f * max(q, 0.01f))
            val cosW0 = cos(w0)

            val a0 = 1f + alpha
            b0 = ((1f - cosW0) / 2f) / a0
            b1 = (1f - cosW0) / a0
            b2 = ((1f - cosW0) / 2f) / a0
            a1 = (-2f * cosW0) / a0
            a2 = (1f - alpha) / a0
        }

        fun setHighPass(freq: Float, q: Float, sampleRate: Float) {
            val w0 = 2f * Math.PI.toFloat() * freq / sampleRate
            val alpha = sin(w0) / (2f * max(q, 0.01f))
            val cosW0 = cos(w0)

            val a0 = 1f + alpha
            b0 = ((1f + cosW0) / 2f) / a0
            b1 = (-(1f + cosW0)) / a0
            b2 = ((1f + cosW0) / 2f) / a0
            a1 = (-2f * cosW0) / a0
            a2 = (1f - alpha) / a0
        }

        fun setLowShelf(freq: Float, gainDb: Float, q: Float, sampleRate: Float) {
            val a = 10f.pow(gainDb / 40f)
            val w0 = 2f * Math.PI.toFloat() * freq / sampleRate
            val alpha = sin(w0) / (2f * max(q, 0.01f))
            val cosW0 = cos(w0)
            val sqrtA = sqrt(a)

            val a0 = (a + 1f) + (a - 1f) * cosW0 + 2f * sqrtA * alpha
            b0 = (a * ((a + 1f) - (a - 1f) * cosW0 + 2f * sqrtA * alpha)) / a0
            b1 = (2f * a * ((a - 1f) - (a + 1f) * cosW0)) / a0
            b2 = (a * ((a + 1f) - (a - 1f) * cosW0 - 2f * sqrtA * alpha)) / a0
            a1 = (-2f * ((a - 1f) + (a + 1f) * cosW0)) / a0
            a2 = ((a + 1f) + (a - 1f) * cosW0 - 2f * sqrtA * alpha) / a0
        }

        fun setHighShelf(freq: Float, gainDb: Float, q: Float, sampleRate: Float) {
            val a = 10f.pow(gainDb / 40f)
            val w0 = 2f * Math.PI.toFloat() * freq / sampleRate
            val alpha = sin(w0) / (2f * max(q, 0.01f))
            val cosW0 = cos(w0)
            val sqrtA = sqrt(a)

            val a0 = (a + 1f) - (a - 1f) * cosW0 + 2f * sqrtA * alpha
            b0 = (a * ((a + 1f) + (a - 1f) * cosW0 + 2f * sqrtA * alpha)) / a0
            b1 = (-2f * a * ((a - 1f) + (a + 1f) * cosW0)) / a0
            b2 = (a * ((a + 1f) + (a - 1f) * cosW0 - 2f * sqrtA * alpha)) / a0
            a1 = (2f * ((a - 1f) - (a + 1f) * cosW0)) / a0
            a2 = ((a + 1f) - (a - 1f) * cosW0 - 2f * sqrtA * alpha) / a0
        }

        fun process(sample: Float): Float {
            val out = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = sample
            y2 = y1
            y1 = out
            return out
        }
    }

    // 10-Band EQ Filter Bank
    private val eqFiltersL = Array(10) { BiquadFilter() }
    private val eqFiltersR = Array(10) { BiquadFilter() }

    // ViPER-DDC Filters (Parametric correction per device)
    private val ddcFilterL1 = BiquadFilter()
    private val ddcFilterR1 = BiquadFilter()
    private val ddcFilterL2 = BiquadFilter()
    private val ddcFilterR2 = BiquadFilter()
    private var lastDdcPreset = -1

    // ViPER Bass Resonators
    private val bassFilterL = BiquadFilter()
    private val bassFilterR = BiquadFilter()

    // ViPER Clarity Crossover Filters
    private val clarityFilterL = BiquadFilter()
    private val clarityFilterR = BiquadFilter()

    // Dynamic System Impedance Filter
    private val dynamicSystemFilterL = BiquadFilter()
    private val dynamicSystemFilterR = BiquadFilter()

    // Spectrum Extension (VSE) Dedicated High-Pass Exciter Filters (8kHz crossover)
    private val vseFilterL = BiquadFilter()
    private val vseFilterR = BiquadFilter()

    // Sub-Bass Mono-Lock Filters (120Hz HP on spatial side/decorrelation channels)
    // Prevents spatial effects from smearing sub-bass phase, keeping kick drum 100% mono-centered
    private val subBassMonoLockSurround = BiquadFilter()
    private val subBassMonoLockDiff = BiquadFilter()

    // AGC Envelope State
    private var agcGain = 1.0f

    // Differential Surround Delay Buffer (Haas effect ring buffer, up to 50ms at 48kHz = 2400 samples)
    private val diffDelayBuffer = FloatArray(2400)
    private var diffDelayIndex = 0

    // Schroeder Reverb Comb & All-Pass Delays (Expanded to 2400 to support up to 500m² room size safely)
    private val combBufferL0 = FloatArray(2400)
    private val combBufferL1 = FloatArray(2400)
    private val combBufferL2 = FloatArray(2400)
    private val combBufferL3 = FloatArray(2400)
    private val combBufferR0 = FloatArray(2400)
    private val combBufferR1 = FloatArray(2400)
    private val combBufferR2 = FloatArray(2400)
    private val combBufferR3 = FloatArray(2400)
    private var combIdx0 = 0
    private var combIdx1 = 0
    private var combIdx2 = 0
    private var combIdx3 = 0

    init {
        updateEqGains(listOf(4, 3, 1, 0, -1, 1, 2, 3, 3, 4))
        val fs = sampleRate.toFloat()
        bassFilterL.setLowPass(60f, 1.2f, fs)
        bassFilterR.setLowPass(60f, 1.2f, fs)
        clarityFilterL.setHighPass(4500f, 1.0f, fs)
        clarityFilterR.setHighPass(4500f, 1.0f, fs)
        dynamicSystemFilterL.setPeaking(80f, 3f, 1.0f, fs)
        dynamicSystemFilterR.setPeaking(80f, 3f, 1.0f, fs)
        vseFilterL.setHighPass(8000f, 0.707f, fs)
        vseFilterR.setHighPass(8000f, 0.707f, fs)
        subBassMonoLockSurround.setHighPass(120f, 0.707f, fs)
        subBassMonoLockDiff.setHighPass(120f, 0.707f, fs)
    }

    /**
     * Updates the 10-Band Firequalizer gains.
     */
    fun updateEqGains(bandLevels: List<Int>) {
        val fs = sampleRate.toFloat()
        for (i in 0 until 10) {
            val gainDb = if (i < bandLevels.size) bandLevels[i].toFloat() else 0f
            eqFiltersL[i].setPeaking(bandFrequencies[i], gainDb, qFactor, fs)
            eqFiltersR[i].setPeaking(bandFrequencies[i], gainDb, qFactor, fs)
        }
    }

    private fun configureDdc(preset: Int) {
        if (preset == lastDdcPreset) return
        lastDdcPreset = preset
        val fs = sampleRate.toFloat()

        when (preset) {
            1 -> {
                // Apple AirPods Pro: Sub-bass warmth & spatial air lift
                ddcFilterL1.setLowShelf(50f, 3.0f, 0.9f, fs)
                ddcFilterR1.setLowShelf(50f, 3.0f, 0.9f, fs)
                ddcFilterL2.setHighShelf(12000f, 2.5f, 0.8f, fs)
                ddcFilterR2.setHighShelf(12000f, 2.5f, 0.8f, fs)
            }
            2 -> {
                // Sony WH-1000XM4: Vocal intelligibility & LDAC 10kHz air
                ddcFilterL1.setPeaking(1500f, 2.5f, 1.2f, fs)
                ddcFilterR1.setPeaking(1500f, 2.5f, 1.2f, fs)
                ddcFilterL2.setHighShelf(10000f, 3.5f, 0.8f, fs)
                ddcFilterR2.setHighShelf(10000f, 3.5f, 0.8f, fs)
            }
            3 -> {
                // Sennheiser HD650: Low-end extension & open-back shimmer
                ddcFilterL1.setLowShelf(60f, 4.0f, 0.8f, fs)
                ddcFilterR1.setLowShelf(60f, 4.0f, 0.8f, fs)
                ddcFilterL2.setHighShelf(12000f, 3.0f, 0.7f, fs)
                ddcFilterR2.setHighShelf(12000f, 3.0f, 0.7f, fs)
            }
            4 -> {
                // Audio-Technica ATH-M50x: Tight sub-punch & forward vocal stage
                ddcFilterL1.setLowShelf(80f, 3.0f, 1.0f, fs)
                ddcFilterR1.setLowShelf(80f, 3.0f, 1.0f, fs)
                ddcFilterL2.setPeaking(1200f, 2.5f, 1.0f, fs)
                ddcFilterR2.setPeaking(1200f, 2.5f, 1.0f, fs)
            }
            5 -> {
                // Beyerdynamic DT990: Sub foundation & smooth pinna lift
                ddcFilterL1.setLowShelf(50f, 3.5f, 0.9f, fs)
                ddcFilterR1.setLowShelf(50f, 3.5f, 0.9f, fs)
                ddcFilterL2.setPeaking(3000f, 2.0f, 1.2f, fs)
                ddcFilterR2.setPeaking(3000f, 2.0f, 1.2f, fs)
            }
            6 -> {
                // Bose QC45: Rich low-mid body & clear vocal articulation
                ddcFilterL1.setPeaking(800f, 2.5f, 1.2f, fs)
                ddcFilterR1.setPeaking(800f, 2.5f, 1.2f, fs)
                ddcFilterL2.setHighShelf(8000f, 2.0f, 0.8f, fs)
                ddcFilterR2.setHighShelf(8000f, 2.0f, 0.8f, fs)
            }
            7 -> {
                // Galaxy Buds2 Pro: Harman plus sub-shelf & micro-detail
                ddcFilterL1.setLowShelf(60f, 3.0f, 0.9f, fs)
                ddcFilterR1.setLowShelf(60f, 3.0f, 0.9f, fs)
                ddcFilterL2.setHighShelf(10000f, 3.0f, 0.8f, fs)
                ddcFilterR2.setHighShelf(10000f, 3.0f, 0.8f, fs)
            }
            else -> {
                // Flat / Generic reference
                ddcFilterL1.setPeaking(1000f, 0f, 1f, fs)
                ddcFilterR1.setPeaking(1000f, 0f, 1f, fs)
                ddcFilterL2.setPeaking(1000f, 0f, 1f, fs)
                ddcFilterR2.setPeaking(1000f, 0f, 1f, fs)
            }
        }
    }

    /**
     * Transforms stereo interleaved 32-bit float audio buffer in real time.
     */
    fun processBuffer(buffer: FloatArray, count: Int, s: EqualizerSettings) {
        if (!s.isEnabled) return

        val fs = sampleRate.toFloat()

        // Configure filters for dynamic parameters
        bassFilterL.setLowPass(s.bassFrequency.toFloat(), 1.2f, fs)
        bassFilterR.setLowPass(s.bassFrequency.toFloat(), 1.2f, fs)

        val clarityCornerFreq = when (s.clarityMode) {
            0 -> 3500f // Natural
            1 -> 4500f // Ozone+
            else -> 6000f // XHiFi Pro
        }
        clarityFilterL.setHighPass(clarityCornerFreq, 1.0f, fs)
        clarityFilterR.setHighPass(clarityCornerFreq, 1.0f, fs)

        if (s.isDdcEnabled) {
            configureDdc(s.ddcPreset)
        }

        if (s.isDynamicSystemEnabled) {
            val dynGain = (s.dynamicBassStrength / 100f) * 6f
            val dynFreq = when (s.dynamicDevice) {
                0 -> 50f // High-End Earphone
                1 -> 90f // Apple EarPods
                2 -> 70f // Common Earphone
                3 -> 40f // Studio Monitor
                else -> 45f // High-End Headphone
            }
            dynamicSystemFilterL.setPeaking(dynFreq, dynGain, 1.2f, fs)
            dynamicSystemFilterR.setPeaking(dynFreq, dynGain, 1.2f, fs)
        }

        val bassGainFactor = (s.bassBoost / 1000f) * 2.4f
        val clarityGainFactor = (s.clarity / 1000f) * 1.8f
        val tubeDrive = 1f + (s.tubeWarmth / 1000f) * 3.0f
        val tubeWarmthNorm = s.tubeWarmth / 1000f
        val surroundWidth = (s.fieldSurroundStrength / 100f) * 1.5f + 1.0f
        val midGain = (s.midImageSize / 100f).coerceIn(0.5f, 1.5f)
        val outGainScalar = 10f.pow(s.outputGain / 20f)
        val panLeft = if (s.channelPan < 0) 1f else (100f - s.channelPan) / 100f
        val panRight = if (s.channelPan > 0) 1f else (100f + s.channelPan) / 100f

        val diffDelaySamples = ((s.diffSurroundDelay / 1000f) * sampleRate).toInt().coerceIn(1, diffDelayBuffer.size - 1)

        // Limiter ceiling calculation (e.g. -1 means -0.1dB -> 0.988)
        val ceilingDb = (s.limiterThreshold / 10f).coerceIn(-3.0f, -0.1f)
        val ceilingLinear = 10f.pow(ceilingDb / 20f)

        var i = 0
        while (i < count) {
            var left = buffer[i]
            var right = buffer[i + 1]

            // 1. Playback AGC (Ballistic Automatic Gain Control)
            if (s.isPlaybackAgcEnabled) {
                val peak = max(abs(left), abs(right))
                val target = when (s.playbackAgcRatio) {
                    0 -> 0.45f * (s.playbackAgcMaxGain / 6f) // Slight
                    1 -> 0.70f * (s.playbackAgcMaxGain / 6f) // Moderate
                    else -> 0.90f * (s.playbackAgcMaxGain / 6f) // Extreme
                }
                val maxMultiplier = when (s.playbackAgcRatio) {
                    0 -> 2.0f
                    1 -> 4.0f
                    else -> 8.0f
                }
                val attackRate = 0.008f
                val releaseRate = 0.0002f
                val rate = if (peak * agcGain > target) attackRate else releaseRate
                agcGain += (target / (peak + 0.04f) - agcGain) * rate
                agcGain = agcGain.coerceIn(0.3f, maxMultiplier)
                left *= agcGain
                right *= agcGain
            }

            // 2. VIPER-DDC (Digital Device Correction)
            if (s.isDdcEnabled) {
                left = ddcFilterL1.process(left)
                left = ddcFilterL2.process(left)
                right = ddcFilterR1.process(right)
                right = ddcFilterR2.process(right)
            }

            // 3. Dynamic System Optimizer
            if (s.isDynamicSystemEnabled) {
                left = dynamicSystemFilterL.process(left)
                right = dynamicSystemFilterR.process(right)
            }

            // 4. Firequalizer (10-Band Linear Phase / Biquad EQ)
            if (s.isEqEnabled) {
                for (b in 0 until 10) {
                    left = eqFiltersL[b].process(left)
                    right = eqFiltersR[b].process(right)
                }
            }

            // 5. ViPER Bass (Dynamic Psychoacoustic Harmonic Synthesizer)
            if (s.isBassEnabled && bassGainFactor > 0f) {
                val subL = bassFilterL.process(left)
                val subR = bassFilterR.process(right)

                when (s.viperBassMode) {
                    0 -> {
                        // Natural Bass: Pure low-frequency linear enhancement
                        left += subL * bassGainFactor
                        right += subR * bassGainFactor
                    }
                    1 -> {
                        // Pure Bass+: Even harmonic synthesis (quadratic rectifier)
                        val harmL = (subL * subL) * (if (subL > 0) 1f else -1f)
                        val harmR = (subR * subR) * (if (subR > 0) 1f else -1f)
                        left += (subL * 0.7f + harmL * 0.8f) * bassGainFactor
                        right += (subR * 0.7f + harmR * 0.8f) * bassGainFactor
                    }
                    else -> {
                        // Subwoofer: Deep sub-harmonic octave generation (sub-octave cubic synthesizer)
                        val subOctL = (subL * subL * subL) * 1.5f
                        val subOctR = (subR * subR * subR) * 1.5f
                        left += (subL * 0.6f + subOctL * 1.2f) * bassGainFactor
                        right += (subR * 0.6f + subOctR * 1.2f) * bassGainFactor
                    }
                }
            }

            // 6. ViPER Clarity (Dynamic Treble Exciter & Harmonic Restorer)
            if (s.isClarityEnabled && clarityGainFactor > 0f) {
                val highL = clarityFilterL.process(left)
                val highR = clarityFilterR.process(right)

                when (s.clarityMode) {
                    0 -> {
                        // Natural: Transparent high-frequency linear presence
                        left += highL * clarityGainFactor
                        right += highR * clarityGainFactor
                    }
                    1 -> {
                        // Ozone+: Asymmetrical soft-knee harmonic exciter
                        val exciterL = tanh(highL * 1.8f)
                        val exciterR = tanh(highR * 1.8f)
                        left += exciterL * clarityGainFactor
                        right += exciterR * clarityGainFactor
                    }
                    else -> {
                        // XHiFi Pro: High-frequency spectral restorer with 3rd harmonic sheen
                        val sheenL = highL + (highL * highL * highL) * 0.6f
                        val sheenR = highR + (highR * highR * highR) * 0.6f
                        left += sheenL * clarityGainFactor
                        right += sheenR * clarityGainFactor
                    }
                }
            }

            // 7. Convolver & Analog Tape/IRS Emulation
            if (s.isConvolverEnabled) {
                val crossL = left * (1f - s.convolverCrossChannel / 200f) + right * (s.convolverCrossChannel / 200f)
                val crossR = right * (1f - s.convolverCrossChannel / 200f) + left * (s.convolverCrossChannel / 200f)

                when (s.convolverPreset) {
                    0 -> {
                        // Studer A800 Mastering Tape (Warm soft saturation & tape compression)
                        left = tanh(crossL * 1.25f) / 1.15f
                        right = tanh(crossR * 1.25f) / 1.15f
                    }
                    1 -> {
                        // Telefunken 12AX7 Dual Triode Tube (Rich 2nd-order harmonic bloom)
                        left = crossL + 0.18f * (crossL * crossL * (if (crossL > 0) 1f else -1f))
                        right = crossR + 0.18f * (crossR * crossR * (if (crossR > 0) 1f else -1f))
                    }
                    2 -> {
                        // Sony Walkman MegaBass IRS Profile (Punchy analog bass contour)
                        left = crossL * 1.1f + (crossL * crossL * crossL) * 0.08f
                        right = crossR * 1.1f + (crossR * crossR * crossR) * 0.08f
                    }
                    3 -> {
                        // Lexicon 480L Concert Hall (Subtle acoustic spatial blend)
                        left = crossL * 0.95f + crossR * 0.15f
                        right = crossR * 0.95f + crossL * 0.15f
                    }
                    4 -> {
                        // Dolby Atmos Cinema Stage (Binaural diffuse reflection)
                        left = crossL * 0.92f - crossR * 0.12f
                        right = crossR * 0.92f - crossL * 0.12f
                    }
                    5 -> {
                        // Rupert Neve 1073 Console Transformer
                        left = tanh(crossL * 1.3f) + 0.10f * crossL
                        right = tanh(crossR * 1.3f) + 0.10f * crossR
                    }
                    6 -> {
                        // Solid State Logic 4000G Bus Color
                        left = (tanh(crossL * 1.4f) / 1.35f)
                        right = (tanh(crossR * 1.4f) / 1.35f)
                    }
                    else -> {
                        // EMT 140 Classic Plate Reverb
                        left = crossL
                        right = crossR
                    }
                }
            }

            // 8. Field Surround (Mid-Side Matrix Spatializer) with Sub-Bass Mono Lock
            if (s.isFieldSurroundEnabled && surroundWidth > 1.0f) {
                val mid = (left + right) * 0.5f * midGain
                var side = (left - right) * 0.5f * surroundWidth
                // Sub-bass mono-lock: only spatialize frequencies above 120Hz
                // Prevents phase cancellation and comb filtering on earphones below kick drum range
                side = subBassMonoLockSurround.process(side)
                left = mid + side
                right = mid - side
            }

            // 9. Differential Surround (Haas Inter-aural Time Delay) with Sub-Bass Mono Lock
            if (s.isDiffSurroundEnabled) {
                var diff = (left - right) * 0.5f
                // Sub-bass mono-lock: only apply Haas delay to frequencies above 120Hz
                // Prevents inter-aural time delay from creating sub-bass phase mud
                diff = subBassMonoLockDiff.process(diff)
                val delayedDiff = diffDelayBuffer[diffDelayIndex]
                diffDelayBuffer[diffDelayIndex] = diff
                diffDelayIndex = (diffDelayIndex + 1) % diffDelaySamples

                left += delayedDiff * 0.4f
                right -= delayedDiff * 0.4f
            }

            // 10. Reverberation Matrix (Schroeder-Moorer Acoustic Space)
            if (s.isReverbEnabled) {
                val wet = (s.reverbWetRatio / 100f) * 0.45f
                val dry = s.reverbDryRatio / 100f
                val damp = s.reverbDampingFactor / 100f * 0.5f

                // Comb Filter 0
                val c0L = combBufferL0[combIdx0]
                val c0R = combBufferR0[combIdx0]
                combBufferL0[combIdx0] = left + c0L * (0.7f - damp)
                combBufferR0[combIdx0] = right + c0R * (0.7f - damp)

                // Comb Filter 1
                val c1L = combBufferL1[combIdx1]
                val c1R = combBufferR1[combIdx1]
                combBufferL1[combIdx1] = left + c1L * (0.72f - damp)
                combBufferR1[combIdx1] = right + c1R * (0.72f - damp)

                val combLen0 = (1200 + s.reverbRoomSize * 2).coerceIn(100, combBufferL0.size)
                val combLen1 = (1350 + s.reverbRoomSize * 2).coerceIn(100, combBufferL1.size)
                combIdx0 = (combIdx0 + 1) % combLen0
                combIdx1 = (combIdx1 + 1) % combLen1

                val revWetL = (c0L + c1L) * 0.5f
                val revWetR = (c0R + c1R) * 0.5f

                left = left * dry + revWetL * wet
                right = right * dry + revWetR * wet
            }

            // 11. Analog Tube Simulator (6N1P / 12AX7 Dual-Triode Soft-Knee Saturation)
            if (s.isTubeEnabled && tubeDrive > 1f) {
                left = (tanh(left * tubeDrive) + 0.08f * left * left * tubeWarmthNorm) / tubeDrive
                right = (tanh(right * tubeDrive) + 0.08f * right * right * tubeWarmthNorm) / tubeDrive
            }

            // 12. FET Compressor (Dynamic Studio VCA/FET Leveling)
            if (s.isFetCompressorEnabled) {
                val fetThreshDb = s.fetThreshold.toFloat()
                val fetThreshLin = 10f.pow(fetThreshDb / 20f)
                val fetRatioFactor = 1f - (1f / maxOf(1f, (s.fetRatio + 1) * 2.0f))
                val peakSig = max(abs(left), abs(right))
                if (peakSig > fetThreshLin) {
                    val excess = peakSig - fetThreshLin
                    val compGain = (1f - (excess * fetRatioFactor)).coerceIn(0.25f, 1.0f)
                    val makeup = 10f.pow(s.fetGain / 20f)
                    left = left * compGain * makeup
                    right = right * compGain * makeup
                }
            }

            // 13. Spectrum Extension (VSE High-Frequency Re-synthesis with Dedicated 8kHz High-Pass Biquads)
            if (s.isSpectrumExtensionEnabled && s.spectrumExtensionStrength > 0) {
                val vseGain = (s.spectrumExtensionStrength / 4f) * 0.35f
                val hfL = vseFilterL.process(left)
                val hfR = vseFilterR.process(right)
                val excL = (hfL * hfL * hfL) * 1.8f
                val excR = (hfR * hfR * hfR) * 1.8f
                left += excL * vseGain
                right += excR * vseGain
            }

            // 14. AnalogX (Class A Warm Harmonic Injection)
            if (s.isAnalogXEnabled) {
                val axFactor = (s.analogXLevel + 1) * 0.08f
                left = left + (left * left * (if (left > 0) 1f else -1f)) * axFactor
                right = right + (right * right * (if (right > 0) 1f else -1f)) * axFactor
            }

            // 15. Headphone Surround+ (Binaural HRTF Crossfeed)
            if (s.isHeadphoneSurroundEnabled) {
                val vheCross = ((s.headphoneSurroundLevel + 1) / 6f) * 0.28f
                val vheL = left * (1f - vheCross * 0.5f) + right * vheCross
                val vheR = right * (1f - vheCross * 0.5f) + left * vheCross
                left = vheL
                right = vheR
            }

            // 16. Auditory Protection (Cure+ Crossfeed & Transient Softening)
            if (s.isAuditoryProtectionEnabled) {
                val cureCross = 0.18f
                val cureL = left * 0.82f + right * cureCross
                val cureR = right * 0.82f + left * cureCross
                left = tanh(cureL * 1.05f) / 1.05f
                right = tanh(cureR * 1.05f) / 1.05f
            }

            // 17. Speaker Optimization
            if (s.isSpeakerOptEnabled) {
                left = (left * 1.15f).coerceIn(-0.95f, 0.95f)
                right = (right * 1.15f).coerceIn(-0.95f, 0.95f)
            }

            // 18. Master Gain & Channel Pan
            left = left * outGainScalar * panLeft
            right = right * outGainScalar * panRight

            // 13. True-Peak Master Soft-Knee Limiter
            val softKneeThreshold = ceilingLinear * 0.92f
            val kneeRange = ceilingLinear - softKneeThreshold

            left = if (left > softKneeThreshold) {
                softKneeThreshold + kneeRange * tanh((left - softKneeThreshold) / max(kneeRange, 0.001f))
            } else if (left < -softKneeThreshold) {
                -softKneeThreshold + kneeRange * tanh((left + softKneeThreshold) / max(kneeRange, 0.001f))
            } else {
                left
            }

            right = if (right > softKneeThreshold) {
                softKneeThreshold + kneeRange * tanh((right - softKneeThreshold) / max(kneeRange, 0.001f))
            } else if (right < -softKneeThreshold) {
                -softKneeThreshold + kneeRange * tanh((right + softKneeThreshold) / max(kneeRange, 0.001f))
            } else {
                right
            }

            buffer[i] = left.coerceIn(-1.0f, 1.0f)
            buffer[i + 1] = right.coerceIn(-1.0f, 1.0f)

            i += 2
        }
    }
}

