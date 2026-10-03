package com.example.antigravityeq.data

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.math.pow

/**
 * Dedicated nonlinear distortion stage.
 *
 * Clean-room implementation of a production-style waveshaper chain:
 * pre-gain -> dynamics/spread -> bias -> nonlinear transfer -> DC blocker
 * -> tone filter -> output trim -> wet/dry.
 *
 * A real 2x half-band FIR oversampling stage surrounds the nonlinear transfer
 * to reduce alias products. Transfer curves are independently written from
 * documented DSP behavior; no source code is copied from Tryptify.
 */
class DistortionProcessor(private val sampleRate: Int = 48000) {

    enum class Mode(val label: String) {
        SOFT_CLIP("Soft clip"),
        HARD_CLIP("Hard clip"),
        TANH("Tanh saturation"),
        FOLDBACK("Foldback"),
        RECTIFY("Rectify"),
        ASYMMETRIC("Asymmetric")
    }

    internal class HalfBand2x {
        // Each polyphase component sums to 0.5. Zero-stuffing interpolation
        // therefore needs 2x compensation; decimation stays at unity gain.
        private val hDecimation = floatArrayOf(
            -3f / 632f, 0f, 15f / 632f, 0f, -48f / 632f, 0f,
            194f / 632f, 316f / 632f, 194f / 632f, 0f, -48f / 632f,
            0f, 15f / 632f, 0f, -3f / 632f
        )
        private val hInterpolation = hDecimation.map { it * 2f }.toFloatArray()
        private val inHistory = FloatArray(hInterpolation.size)
        private val outHistory = FloatArray(hDecimation.size)
        private var inPos = 0
        private var outPos = 0

        private fun push(history: FloatArray, sample: Float, position: Int, coefficients: FloatArray): Pair<Float, Int> {
            history[position] = sample
            var sum = 0f
            var index = position
            for (k in coefficients.indices) {
                sum += coefficients[k] * history[index]
                index--
                if (index < 0) index = coefficients.lastIndex
            }
            return sum to ((position + 1) % coefficients.size)
        }

        fun process(input: Float, shape: (Float) -> Float): Float {
            val (hi0, nextIn0) = push(inHistory, input, inPos, hInterpolation)
            inPos = nextIn0
            val (hi1, nextIn1) = push(inHistory, 0f, inPos, hInterpolation)
            inPos = nextIn1

            val shaped0 = shape(hi0)
            val shaped1 = shape(hi1)

            val (lo0, nextOut0) = push(outHistory, shaped0, outPos, hDecimation)
            outPos = nextOut0
            push(outHistory, shaped1, outPos, hDecimation)
            return lo0
        }

        fun reset() {
            java.util.Arrays.fill(inHistory, 0f)
            java.util.Arrays.fill(outHistory, 0f)
            inPos = 0
            outPos = 0
        }
    }

    private var envelopeL = 0f
    private var envelopeR = 0f
    private var dcX1L = 0f
    private var dcX1R = 0f
    private var dcY1L = 0f
    private var dcY1R = 0f
    private var toneStateL = 0f
    private var toneStateR = 0f

    private val oversamplerL = HalfBand2x()
    private val oversamplerR = HalfBand2x()
    private val dryDelayL = FloatArray(7)
    private val dryDelayR = FloatArray(7)
    private var dryDelayPos = 0

    fun reset() {
        envelopeL = 0f
        envelopeR = 0f
        dcX1L = 0f
        dcX1R = 0f
        dcY1L = 0f
        dcY1R = 0f
        toneStateL = 0f
        toneStateR = 0f
        oversamplerL.reset()
        oversamplerR.reset()
        java.util.Arrays.fill(dryDelayL, 0f)
        java.util.Arrays.fill(dryDelayR, 0f)
        dryDelayPos = 0
    }

    private fun waveshape(x: Float, mode: Mode): Float = when (mode) {
        Mode.SOFT_CLIP -> {
            val ax = abs(x)
            when {
                ax > 1.5f -> if (x > 0f) 1f else -1f
                ax > 1f -> {
                    val t = (3f - (2f - ax) * (2f - ax)) / 3f
                    if (x > 0f) t else -t
                }
                else -> x - (x * x * x) / 3f
            }
        }
        Mode.HARD_CLIP -> x.coerceIn(-1f, 1f)
        Mode.TANH -> tanh(x)
        Mode.FOLDBACK -> sin(x * Math.PI.toFloat())
        Mode.RECTIFY -> abs(x).coerceIn(0f, 1f)
        Mode.ASYMMETRIC -> if (x >= 0f) 1f - exp(-x) else -tanh(-x * 1.5f)
    }

    private fun envelope(input: Float, previous: Float): Float {
        val magnitude = abs(input)
        val coeff = if (magnitude > previous) {
            exp(-1f / (0.005f * sampleRate))
        } else {
            exp(-1f / (0.050f * sampleRate))
        }
        return coeff * previous + (1f - coeff) * magnitude
    }

    private fun dcBlock(sample: Float, previousX: Float, previousY: Float): Float {
        return sample - previousX + 0.995f * previousY
    }

    private fun tone(sample: Float, cutoffHz: Float, state: Float): Float {
        val cutoff = cutoffHz.coerceIn(200f, (sampleRate * 0.45f).coerceAtLeast(200f))
        val alpha = (1f - exp(-2f * Math.PI.toFloat() * cutoff / sampleRate.toFloat()))
            .coerceIn(0.001f, 1f)
        return state + alpha * (sample - state)
    }

    fun processStereo(
        left: Float,
        right: Float,
        driveDb: Float,
        mixPercent: Float,
        toneHz: Float,
        outputDb: Float,
        asymmetryPercent: Float,
        mode: Mode,
        dynamicsPercent: Float = 0f,
        spreadPercent: Float = 0f
    ): Pair<Float, Float> {
        val wetAmount = mixPercent.coerceIn(0f, 100f) / 100f

        // Keep the dry delay advancing even while bypassed so re-enabling the
        // effect cannot expose stale samples at the wet/dry crossover.
        val dryL = dryDelayL[dryDelayPos]
        val dryR = dryDelayR[dryDelayPos]
        dryDelayL[dryDelayPos] = left
        dryDelayR[dryDelayPos] = right
        dryDelayPos = (dryDelayPos + 1) % dryDelayL.size

        if (wetAmount <= 0f || driveDb <= 0f) return left to right

        envelopeL = envelope(left, envelopeL)
        envelopeR = envelope(right, envelopeR)

        val baseDrive = 10f.pow(driveDb.coerceIn(0f, 48f) / 20f)
        val dynamics = dynamicsPercent.coerceIn(0f, 100f) / 100f
        val spread = spreadPercent.coerceIn(0f, 100f) / 100f * 0.5f
        val driveL = baseDrive * (1f + dynamics * envelopeL * 4f) * (1f + spread)
        val driveR = baseDrive * (1f + dynamics * envelopeR * 4f) * (1f - spread)
        val bias = asymmetryPercent.coerceIn(-100f, 100f) / 100f * 0.12f

        val wetL = oversamplerL.process(left * driveL + bias) { x -> waveshape(x, mode) }
        val wetR = oversamplerR.process(right * driveR + bias) { x -> waveshape(x, mode) }

        val blockedL = dcBlock(wetL, dcX1L, dcY1L)
        val blockedR = dcBlock(wetR, dcX1R, dcY1R)
        dcX1L = wetL
        dcX1R = wetR
        dcY1L = blockedL
        dcY1R = blockedR

        toneStateL = tone(blockedL, toneHz, toneStateL)
        toneStateR = tone(blockedR, toneHz, toneStateR)

        val output = 10f.pow(outputDb.coerceIn(-12f, 6f) / 20f)
        val outL = (dryL * (1f - wetAmount) + toneStateL * wetAmount) * output
        val outR = (dryR * (1f - wetAmount) + toneStateR * wetAmount) * output
        return outL.coerceIn(-1.5f, 1.5f) to outR.coerceIn(-1.5f, 1.5f)
    }
}
