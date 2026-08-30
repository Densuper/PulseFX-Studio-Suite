package com.example.antigravityeq.ui.main

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.antigravityeq.AudioEffectsService
import com.example.antigravityeq.data.EqualizerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val _uiState = MutableStateFlow(EqualizerSettings.load(context))
    val uiState: StateFlow<EqualizerSettings> = _uiState.asStateFlow()

    private val _liveFftLevels = MutableStateFlow(FloatArray(10) { 0f })
    val liveFftLevels: StateFlow<FloatArray> = _liveFftLevels.asStateFlow()

    init {
        // Polling loop for smooth 60 FPS live spectrum animation in UI
        androidx.lifecycle.viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            while (kotlinx.coroutines.isActive) {
                _liveFftLevels.value = AudioEffectsService.liveFftLevels
                kotlinx.coroutines.delay(16L)
            }
        }
    }

    fun updateSettings(transform: (EqualizerSettings) -> EqualizerSettings) {
        _uiState.update(transform)
        saveAndBroadcast()
    }

    // 1. Global & Tab Control
    fun setMasterEnabled(enabled: Boolean) = updateSettings { it.copy(isEnabled = enabled) }
    fun setSelectedTab(tab: Int) = updateSettings { it.copy(selectedTab = tab) }

    // 2. Playback AGC
    fun setPlaybackAgc(
        enabled: Boolean,
        ratio: Int = _uiState.value.playbackAgcRatio,
        gain: Int = _uiState.value.playbackAgcMaxGain,
        maxOutput: Int = _uiState.value.playbackAgcMaxOutput
    ) = updateSettings {
        it.copy(
            isPlaybackAgcEnabled = enabled,
            playbackAgcRatio = ratio,
            playbackAgcMaxGain = gain,
            playbackAgcMaxOutput = maxOutput
        )
    }

    // 3. VIPER-DDC
    fun setDdc(enabled: Boolean, preset: Int = _uiState.value.ddcPreset) =
        updateSettings { it.copy(isDdcEnabled = enabled, ddcPreset = preset) }

    // 4. Firequalizer (10-Band Graphic EQ)
    fun setEq(enabled: Boolean, preset: Int = _uiState.value.eqPreset) =
        updateSettings { it.copy(isEqEnabled = enabled, eqPreset = preset) }

    fun updateBand(index: Int, level: Int) = updateSettings { state ->
        val newBands = state.bandLevels.toMutableList()
        if (index in newBands.indices) {
            newBands[index] = level.coerceIn(-15, 15)
        }
        state.copy(bandLevels = newBands, eqPreset = 0)
    }

    fun applyEqPreset(presetIndex: Int) = updateSettings { state ->
        val bands = if (presetIndex in EqualizerSettings.EQ_PRESET_VALUES.indices) {
            EqualizerSettings.EQ_PRESET_VALUES[presetIndex]
        } else {
            state.bandLevels
        }
        state.copy(eqPreset = presetIndex, bandLevels = bands)
    }

    // 5. Convolver / IRS Coloration
    fun setConvolver(
        enabled: Boolean,
        preset: Int = _uiState.value.convolverPreset,
        cross: Int = _uiState.value.convolverCrossChannel,
        crossChannel: Int = cross
    ) = updateSettings {
        it.copy(isConvolverEnabled = enabled, convolverPreset = preset, convolverCrossChannel = if (cross != _uiState.value.convolverCrossChannel) cross else crossChannel)
    }

    // 6. Field Surround & Differential Surround
    fun setFieldSurround(
        enabled: Boolean,
        strength: Int = _uiState.value.fieldSurroundStrength,
        mid: Int = _uiState.value.midImageSize,
        midImageSize: Int = mid
    ) = updateSettings {
        it.copy(isFieldSurroundEnabled = enabled, fieldSurroundStrength = strength, midImageSize = if (mid != _uiState.value.midImageSize) mid else midImageSize)
    }

    fun setDiffSurround(
        enabled: Boolean,
        delay: Int = _uiState.value.diffSurroundDelay,
        delayMs: Int = delay
    ) = updateSettings {
        it.copy(isDiffSurroundEnabled = enabled, diffSurroundDelay = if (delay != _uiState.value.diffSurroundDelay) delay else delayMs)
    }

    // 7. Reverberation Matrix
    fun setReverberation(
        enabled: Boolean,
        room: Int = _uiState.value.reverbRoomSize,
        roomSize: Int = room,
        field: Int = _uiState.value.reverbSoundField,
        soundField: Int = field,
        damp: Int = _uiState.value.reverbDampingFactor,
        damping: Int = damp,
        wet: Int = _uiState.value.reverbWetRatio,
        wetRatio: Int = wet,
        dry: Int = _uiState.value.reverbDryRatio,
        dryRatio: Int = dry
    ) = updateSettings {
        it.copy(
            isReverbEnabled = enabled,
            reverbRoomSize = if (room != _uiState.value.reverbRoomSize) room else roomSize,
            reverbSoundField = if (field != _uiState.value.reverbSoundField) field else soundField,
            reverbDampingFactor = if (damp != _uiState.value.reverbDampingFactor) damp else damping,
            reverbWetRatio = if (wet != _uiState.value.reverbWetRatio) wet else wetRatio,
            reverbDryRatio = if (dry != _uiState.value.reverbDryRatio) dry else dryRatio
        )
    }

    // 8. Dynamic System Optimizer
    fun setDynamicSystem(
        enabled: Boolean,
        device: Int = _uiState.value.dynamicDevice,
        bass: Int = _uiState.value.dynamicBassStrength,
        bassStrength: Int = bass
    ) = updateSettings {
        it.copy(isDynamicSystemEnabled = enabled, dynamicDevice = device, dynamicBassStrength = if (bass != _uiState.value.dynamicBassStrength) bass else bassStrength)
    }

    // 9. ViPER Bass
    fun setViperBass(
        enabled: Boolean,
        mode: Int = _uiState.value.viperBassMode,
        freq: Int = _uiState.value.bassFrequency,
        frequency: Int = freq,
        boost: Int = _uiState.value.bassBoost
    ) = updateSettings {
        it.copy(isBassEnabled = enabled, viperBassMode = mode, bassFrequency = if (freq != _uiState.value.bassFrequency) freq else frequency, bassBoost = boost)
    }

    // 10. ViPER Clarity
    fun setViperClarity(
        enabled: Boolean,
        mode: Int = _uiState.value.clarityMode,
        gain: Int = _uiState.value.clarity,
        clarity: Int = gain
    ) = updateSettings {
        it.copy(isClarityEnabled = enabled, clarityMode = mode, clarity = if (gain != _uiState.value.clarity) gain else clarity)
    }

    // 11. Analog Tube Simulator
    fun setTubeSimulator(
        enabled: Boolean,
        warmth: Int = _uiState.value.tubeWarmth,
        drive: Int = warmth
    ) = updateSettings {
        it.copy(isTubeEnabled = enabled, tubeWarmth = if (drive != _uiState.value.tubeWarmth) drive else warmth)
    }

    // 12. Master Gate & Limiter
    fun setMasterOutput(
        gain: Int = _uiState.value.outputGain,
        pan: Int = _uiState.value.channelPan,
        limiter: Int = _uiState.value.limiterThreshold,
        limiterThreshold: Int = limiter
    ) = updateSettings {
        it.copy(outputGain = gain, channelPan = pan, limiterThreshold = if (limiter != _uiState.value.limiterThreshold) limiter else limiterThreshold)
    }

    // 13. FET Compressor
    fun setFetCompressor(
        enabled: Boolean,
        threshold: Int = _uiState.value.fetThreshold,
        ratio: Int = _uiState.value.fetRatio,
        attack: Int = _uiState.value.fetAttack,
        release: Int = _uiState.value.fetRelease,
        gain: Int = _uiState.value.fetGain
    ) = updateSettings {
        it.copy(
            isFetCompressorEnabled = enabled,
            fetThreshold = threshold,
            fetRatio = ratio,
            fetAttack = attack,
            fetRelease = release,
            fetGain = gain
        )
    }

    // 14. Spectrum Extension (VSE)
    fun setSpectrumExtension(
        enabled: Boolean,
        strength: Int = _uiState.value.spectrumExtensionStrength
    ) = updateSettings {
        it.copy(isSpectrumExtensionEnabled = enabled, spectrumExtensionStrength = strength)
    }

    // 15. Headphone Surround+ (VHE)
    fun setHeadphoneSurround(
        enabled: Boolean,
        level: Int = _uiState.value.headphoneSurroundLevel
    ) = updateSettings {
        it.copy(isHeadphoneSurroundEnabled = enabled, headphoneSurroundLevel = level)
    }

    // 16. AnalogX
    fun setAnalogX(
        enabled: Boolean,
        level: Int = _uiState.value.analogXLevel
    ) = updateSettings {
        it.copy(isAnalogXEnabled = enabled, analogXLevel = level)
    }

    // 17. Auditory System Protection (Cure Crossfeed)
    fun setAuditoryProtection(enabled: Boolean) = updateSettings {
        it.copy(isAuditoryProtectionEnabled = enabled)
    }

    // 18. Speaker Optimization
    fun setSpeakerOptimization(enabled: Boolean) = updateSettings {
        it.copy(isSpeakerOptEnabled = enabled)
    }

    // 19. Factory Reset
    fun resetToDefaults() = updateSettings { EqualizerSettings() }

    private fun saveAndBroadcast() {
        val s = _uiState.value
        s.save(context)

        // 1. AudioEffectsService (Broadcast AudioFlinger / Android Framework Session Hook)
        try {
            val intent = Intent(context, AudioEffectsService::class.java).apply {
                action = AudioEffectsService.ACTION_UPDATE_SETTINGS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {}
    }
}

