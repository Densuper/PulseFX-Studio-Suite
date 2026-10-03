package com.example.antigravityeq.data

import android.media.audiofx.AudioEffect
import java.lang.reflect.Method
import java.util.UUID

/**
 * Controller for the PulseFX native AudioEffect implementation.
 *
 * The effect is selected by UUID. The native library must be registered with
 * AudioFlinger (see native/README.md); an APK cannot register a new AudioEffect
 * implementation on an unmodified device by itself.
 */
class NativeDistortionEffect(sessionId: Int) : AutoCloseable {
    companion object {
        val EFFECT_UUID: UUID = UUID.fromString("7e4e3b7a-1e20-4f74-8f4e-9e9d5f3e9c01")

        const val PARAM_DRIVE_DB = 1
        const val PARAM_MIX_PERCENT = 2
        const val PARAM_TONE_HZ = 3
        const val PARAM_OUTPUT_DB = 4
        const val PARAM_BIAS_PERCENT = 5
        const val PARAM_MODE = 6
        const val PARAM_DYNAMICS_PERCENT = 7
        const val PARAM_SPREAD_PERCENT = 8

        private val NULL_EFFECT_TYPE: UUID =
            UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210")
    }

    private val effect = AudioEffect(NULL_EFFECT_TYPE, EFFECT_UUID, 0, sessionId)

    // AudioEffect's generic setParameter overload is hidden from the public
    // SDK stubs. The native effect needs it, so resolve the stable framework
    // method once instead of depending on a hidden compile-time API.
    private val setParameterMethod: Method = AudioEffect::class.java.getMethod(
        "setParameter",
        Int::class.javaPrimitiveType!!,
        Int::class.javaPrimitiveType!!
    )

    val isEnabled: Boolean
        get() = effect.enabled

    fun configure(settings: EqualizerSettings, masterEnabled: Boolean) {
        setParameter(PARAM_DRIVE_DB, settings.distortionDrive)
        setParameter(PARAM_MIX_PERCENT, settings.distortionMix)
        setParameter(PARAM_TONE_HZ, settings.distortionTone)
        setParameter(PARAM_OUTPUT_DB, settings.distortionOutput)
        setParameter(PARAM_BIAS_PERCENT, settings.distortionAsymmetry)
        setParameter(PARAM_MODE, settings.distortionMode)
        setParameter(PARAM_DYNAMICS_PERCENT, settings.distortionDynamics)
        setParameter(PARAM_SPREAD_PERCENT, settings.distortionSpread)

        val shouldRun = masterEnabled &&
            settings.isDistortionEnabled &&
            settings.distortionDrive > 0 &&
            settings.distortionMix > 0
        effect.enabled = shouldRun
    }

    private fun setParameter(parameter: Int, value: Int) {
        setParameterMethod.invoke(effect, parameter, value)
    }

    override fun close() {
        if (effect.enabled) effect.enabled = false
        effect.release()
    }
}
