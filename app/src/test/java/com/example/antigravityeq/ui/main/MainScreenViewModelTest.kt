package com.example.antigravityeq.ui.main

import com.example.antigravityeq.data.EqualizerSettings
import com.example.antigravityeq.data.ViperDspProcessor
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ViperDspProcessorTest {

    @Test
    fun testBiquadPeakingFilterTransformation() {
        val filter = ViperDspProcessor.BiquadFilter()
        filter.setPeaking(1000f, 6.0f, 1.414f, 48000f)

        // Feed 1kHz sine wave sample
        var maxOut = 0f
        for (n in 0 until 480) {
            val sample = kotlin.math.sin(2.0 * Math.PI * 1000.0 * n / 48000.0).toFloat()
            val out = filter.process(sample)
            if (abs(out) > maxOut) maxOut = abs(out)
        }

        // At 1kHz with +6dB boost, output amplitude should exceed 1.5x (~2x = +6dB)
        assertTrue("Filter output should boost 1kHz by ~+6dB", maxOut > 1.5f)
    }

    @Test
    fun testViPERBassAndLimiterRealTimeProcessing() {
        val dsp = ViperDspProcessor(48000)
        val settings = EqualizerSettings(
            isEnabled = true,
            isBassEnabled = true,
            viperBassMode = 1, // Pure Bass
            bassFrequency = 60,
            bassBoost = 800, // +14.4dB
            outputGain = 0,
            limiterThreshold = -1 // -0.1dB
        )

        val buffer = FloatArray(512) { i ->
            if (i % 2 == 0) 0.5f * kotlin.math.sin(2.0 * Math.PI * 50.0 * (i / 2) / 48000.0).toFloat()
            else 0.5f * kotlin.math.cos(2.0 * Math.PI * 50.0 * (i / 2) / 48000.0).toFloat()
        }

        dsp.processBuffer(buffer, buffer.size, settings)

        // Limiter must strictly hold output within [-1.0, 1.0]
        for (sample in buffer) {
            assertTrue("Sample must not exceed true peak limiter ceiling", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun testViPERClarityHarmonicExciter() {
        val dsp = ViperDspProcessor(48000)
        val settings = EqualizerSettings(
            isEnabled = true,
            isClarityEnabled = true,
            clarityMode = 1, // Ozone+
            clarity = 700
        )

        val buffer = FloatArray(512) { i ->
            0.3f * kotlin.math.sin(2.0 * Math.PI * 8000.0 * (i / 2) / 48000.0).toFloat()
        }

        dsp.processBuffer(buffer, buffer.size, settings)

        for (sample in buffer) {
            assertTrue("Sample must remain stable within range", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun testEqualizerPresetsIntegrity() {
        assertEquals("Should have 21 EQ presets defined", 21, EqualizerSettings.EQ_PRESET_NAMES.size)
        assertEquals("Preset values should match names length", 21, EqualizerSettings.EQ_PRESET_VALUES.size)
        EqualizerSettings.EQ_PRESET_VALUES.forEach { bands ->
            assertEquals("Each preset must have exactly 10 bands", 10, bands.size)
        }
    }
}

