package com.example.antigravityeq.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DistortionProcessorTest {


    @Test
    fun twoXHalfBandPathPreservesUnityGainWithLinearShaper() {
        val oversampler = DistortionProcessor.HalfBand2x()
        var output = 0f

        repeat(100) {
            output = oversampler.process(0.25f) { it }
        }

        // The interpolation FIR has 2x compensation; the decimation FIR
        // remains unity gain. After settling, DC should return at unity.
        assertEquals(0.25f, output, 0.0001f)
    }

    @Test
    fun tanhDriveProducesNonlinearOutput() {
        val processor = DistortionProcessor(48000)
        val (left, right) = processor.processStereo(
            left = 0.5f,
            right = -0.5f,
            driveDb = 18f,
            mixPercent = 100f,
            toneHz = 20000f,
            outputDb = 0f,
            asymmetryPercent = 0f,
            mode = DistortionProcessor.Mode.TANH
        )

        assertTrue(abs(left) < 1f)
        assertTrue(abs(right) < 1f)
        assertTrue(left > 0f)
        assertTrue(right < 0f)
    }

    @Test
    fun hardClipCreatesNonlinearityAtHighDrive() {
        val processor = DistortionProcessor(48000)
        val (left, right) = processor.processStereo(
            left = 0.25f,
            right = -0.25f,
            driveDb = 24f,
            mixPercent = 100f,
            toneHz = 20000f,
            outputDb = 0f,
            asymmetryPercent = 0f,
            mode = DistortionProcessor.Mode.HARD_CLIP
        )
        assertTrue(left != 0.25f)
        assertTrue(right != -0.25f)
    }

    @Test
    fun rectifyProducesNonNegativeOutput() {
        val processor = DistortionProcessor(48000)
        val (left, right) = processor.processStereo(
            left = -0.8f,
            right = -0.4f,
            driveDb = 18f,
            mixPercent = 100f,
            toneHz = 20000f,
            outputDb = 0f,
            asymmetryPercent = 0f,
            mode = DistortionProcessor.Mode.RECTIFY
        )
        assertTrue(left >= 0f)
        assertTrue(right >= 0f)
    }

    @Test
    fun dryMixPreservesInput() {
        val processor = DistortionProcessor(48000)
        val (left, right) = processor.processStereo(
            left = 0.37f,
            right = -0.21f,
            driveDb = 48f,
            mixPercent = 0f,
            toneHz = 200f,
            outputDb = 0f,
            asymmetryPercent = 100f,
            mode = DistortionProcessor.Mode.HARD_CLIP
        )

        assertTrue(abs(left - 0.37f) < 0.0001f)
        assertTrue(abs(right + 0.21f) < 0.0001f)
    }
}
