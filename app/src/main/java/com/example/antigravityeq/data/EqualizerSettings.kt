package com.example.antigravityeq.data

import android.content.Context
import android.content.SharedPreferences

data class EqualizerSettings(
    // Global & Device Profile
    val selectedTab: Int = 0, // 0 = Headset, 1 = Speaker, 2 = Bluetooth, 3 = USB
    val isEnabled: Boolean = true, // Master Power

    // Playback AGC (Automatic Gain Control)
    val isPlaybackAgcEnabled: Boolean = false,
    val playbackAgcRatio: Int = 1, // 0 = Slight (1.5x), 1 = Moderate (3.0x), 2 = Extreme (6.0x)
    val playbackAgcMaxGain: Int = 6, // +0dB to +18dB (1x to 8x)
    val playbackAgcMaxOutput: Int = -1, // -0.1dB to -3.0dB target ceiling

    // VIPER-DDC (Digital Device Correction)
    val isDdcEnabled: Boolean = false,
    val ddcPreset: Int = 0, // Profile index for headphone correction curves

    // Firequalizer (10-Band Linear / Minimum Phase Graphic EQ)
    val isEqEnabled: Boolean = false,
    val eqPreset: Int = 0, // Preset index (Custom, Acoustic, Bass Booster, etc.)
    val bandLevels: List<Int> = listOf(4, 3, 1, 0, -1, 1, 2, 3, 3, 4),

    // Convolver (IRS / Analog Tape & Tube Impulses)
    val isConvolverEnabled: Boolean = false,
    val convolverPreset: Int = 0, // Impulse response index
    val convolverCrossChannel: Int = 20, // 0% to 100% stereo crossfeed

    // Field Surround & Differential Surround
    val isFieldSurroundEnabled: Boolean = false,
    val fieldSurroundStrength: Int = 50, // 0 to 100 (0% to 100% stereo width)
    val midImageSize: Int = 50, // 0 to 100 (Center vocal presence)
    val isDiffSurroundEnabled: Boolean = false,
    val diffSurroundDelay: Int = 5, // ms (1 to 20ms Haas inter-aural delay)

    // Reverberation Matrix (Schroeder-Moorer Acoustic Space)
    val isReverbEnabled: Boolean = false,
    val reverbRoomSize: Int = 100, // 25 to 500 m2
    val reverbSoundField: Int = 12, // 5 to 30 m width
    val reverbDampingFactor: Int = 40, // 0% to 100% HF damping
    val reverbWetRatio: Int = 30, // 0% to 100% wet signal
    val reverbDryRatio: Int = 90, // 0% to 100% dry signal

    // Dynamic System Optimizer (Bass & Earphone Resonance)
    val isDynamicSystemEnabled: Boolean = false,
    val dynamicDevice: Int = 0, // 0 = High-End Earphone, 1 = Apple EarPods, 2 = Common Earphone, 3 = Studio Monitor, 4 = High-End Headphone
    val dynamicBassStrength: Int = 14, // 0% to 30% dynamic bass synthesis

    // ViPER Bass (Dynamic Sub-Harmonic Resonator)
    val isBassEnabled: Boolean = false,
    val viperBassMode: Int = 1, // 0 = Natural Bass, 1 = Pure Bass, 2 = Subwoofer
    val bassFrequency: Int = 60, // 30Hz, 40Hz, 50Hz, 60Hz, 70Hz, 80Hz, 100Hz
    val bassBoost: Int = 600, // 0 to 1000 (0 to +18dB)

    // ViPER Clarity (High-Frequency Harmonic Exciter & Transient Restorer)
    val isClarityEnabled: Boolean = false,
    val clarityMode: Int = 1, // 0 = Natural, 1 = Ozone+, 2 = XHiFi Pro
    val clarity: Int = 500, // 0 to 1000 (0 to +14dB)

    // Analog Tube Simulator (6N1P / 12AX7 Non-Linear Saturation)
    val isTubeEnabled: Boolean = false,
    val tubeWarmth: Int = 350, // 0 to 1000 (Warm triode even harmonics)

    // Master Gate (Output Gain, Pan & True-Peak Limiter)
    val isLimiterEnabled: Boolean = false,
    val outputGain: Int = 0, // -20dB to +6dB
    val channelPan: Int = 0, // -100 (Left) to +100 (Right)
    val limiterThreshold: Int = -1, // -0.1dB to -3.0dB ceiling

    // FET Compressor
    val isFetCompressorEnabled: Boolean = false,
    val fetThreshold: Int = 0,
    val fetRatio: Int = 0,
    val fetAttack: Int = 0,
    val fetRelease: Int = 0,
    val fetGain: Int = 0,

    // Spectrum Extension (VSE)
    val isSpectrumExtensionEnabled: Boolean = false,
    val spectrumExtensionStrength: Int = 0,

    // Headphone Surround+ (VHE)
    val isHeadphoneSurroundEnabled: Boolean = false,
    val headphoneSurroundLevel: Int = 0,

    // AnalogX
    val isAnalogXEnabled: Boolean = false,
    val analogXLevel: Int = 0,

    // Auditory System Protection (Cure Crossfeed)
    val isAuditoryProtectionEnabled: Boolean = false,

    // Speaker Optimization
    val isSpeakerOptEnabled: Boolean = false
) {
    companion object {
        private const val PREFS_NAME = "antigravity_viper_v4a_prefs"

        // 10 Standard ViPER4Android Band Frequencies
        val EQ_FREQUENCIES = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

        // Complete Authentic ViPER4Android EQ Presets
        val EQ_PRESET_NAMES = listOf(
            "Custom",
            "Acoustic",
            "Bass Booster",
            "Bass Reducer",
            "Classical",
            "Dance",
            "Deep",
            "Electronic",
            "Flat",
            "Hip-Hop",
            "Jazz",
            "Latin",
            "Loudness",
            "Pop",
            "R&B",
            "Rock",
            "Small Speakers",
            "Spoken Word",
            "Treble Booster",
            "Treble Reducer",
            "Vocal Booster"
        )

        val EQ_PRESET_VALUES = listOf(
            listOf(4, 3, 1, 0, -1, 1, 2, 3, 3, 4),      // Custom (Default)
            listOf(4, 3, 2, 1, 2, 2, 3, 3, 4, 3),      // Acoustic
            listOf(8, 7, 5, 2, 0, 0, 1, 2, 3, 4),      // Bass Booster
            listOf(-7, -5, -3, -1, 0, 0, 0, 0, 0, 0),  // Bass Reducer
            listOf(5, 3, 2, 2, -1, -1, 0, 2, 4, 5),    // Classical
            listOf(6, 5, 2, 0, 0, 2, 4, 4, 5, 3),      // Dance
            listOf(7, 5, 3, 1, 2, 0, 1, 2, -2, -4),    // Deep
            listOf(6, 4, 0, 1, -2, 2, 1, 2, 5, 6),     // Electronic
            listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),      // Flat
            listOf(7, 6, 1, 2, -1, -1, 1, -1, 2, 3),    // Hip-Hop
            listOf(4, 2, 1, 2, -2, -2, 0, 1, 3, 4),    // Jazz
            listOf(3, 2, 0, 0, -1, -1, -1, 0, 2, 4),   // Latin
            listOf(8, 5, 2, -1, -3, -2, 1, 4, 7, 8),   // Loudness
            listOf(-1, 1, 3, 4, 4, 3, 1, -1, -1, -1),  // Pop
            listOf(3, 7, 5, 1, -2, -1, 2, 3, 4, 4),    // R&B
            listOf(7, 4, -3, -5, -2, 1, 5, 7, 8, 8),   // Rock
            listOf(8, 4, 2, -1, -2, 1, 3, 5, 6, 7),    // Small Speakers
            listOf(-3, 0, 1, 3, 5, 4, 3, 1, -1, -2),   // Spoken Word
            listOf(0, 0, 0, 0, 0, 1, 3, 5, 7, 9),      // Treble Booster
            listOf(0, 0, 0, 0, 0, -1, -3, -5, -7, -9), // Treble Reducer
            listOf(-2, -3, -3, 1, 4, 4, 3, 2, 0, -2)   // Vocal Booster
        )

        // VIPER-DDC Profile Definitions
        val DDC_PRESET_NAMES = listOf(
            "Generic / Flat Reference IEM",
            "Apple AirPods Pro (Spatial Curve)",
            "Sony WH-1000XM4 (Clarity & Sub)",
            "Sennheiser HD650 (Diffuse Reference)",
            "Audio-Technica ATH-M50x (Tamed Highs)",
            "Beyerdynamic DT990 (Anti-Sibilance)",
            "Bose QuietComfort 45 (Linear Balanced)",
            "Samsung Galaxy Buds2 Pro (Harman Target)"
        )

        // Convolver Impulse Response Presets
        val CONVOLVER_PRESET_NAMES = listOf(
            "Studer A800 Mastering Tape (Warm Saturation)",
            "Telefunken 12AX7 Dual Triode Tube",
            "Sony Walkman MegaBass IRS",
            "Lexicon 480L Concert Hall Ambience",
            "Dolby Atmos Cinema Spatial Stage",
            "Neve 1073 British Console Transformer",
            "Solid State Logic 4000G Bus Color",
            "EMT 140 Classic Plate Reverb"
        )

        // Dynamic System Device Profiles
        val DYNAMIC_DEVICE_NAMES = listOf(
            "High-End In-Ear Earphone",
            "Apple EarPods / Semi-Open",
            "Common In-Ear / Earbud",
            "Studio Monitor Headphones",
            "High-End Open-Back Audiophile"
        )

        fun load(context: Context): EqualizerSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("v4a_master_enabled", true)
            val selectedTab = prefs.getInt("v4a_tab", 0)

            val isPlaybackAgcEnabled = prefs.getBoolean("v4a_agc_enabled", false)
            val playbackAgcRatio = prefs.getInt("v4a_agc_ratio", 1)
            val playbackAgcMaxGain = prefs.getInt("v4a_agc_gain", 6)
            val playbackAgcMaxOutput = prefs.getInt("v4a_agc_out", -1)

            val isDdcEnabled = prefs.getBoolean("v4a_ddc_enabled", false)
            val ddcPreset = prefs.getInt("v4a_ddc_preset", 0)

            val isEqEnabled = prefs.getBoolean("v4a_eq_enabled", true)
            val eqPreset = prefs.getInt("v4a_eq_preset", 0)
            val bandLevels = (0 until 10).map { i ->
                prefs.getInt("v4a_eq_band_$i", if (i == 0) 4 else if (i == 1) 3 else if (i >= 8) 3 else 0)
            }

            val isConvolverEnabled = prefs.getBoolean("v4a_convolver_enabled", false)
            val convolverPreset = prefs.getInt("v4a_convolver_preset", 0)
            val convolverCrossChannel = prefs.getInt("v4a_convolver_cross", 20)

            val isFieldSurroundEnabled = prefs.getBoolean("v4a_surround_enabled", false)
            val fieldSurroundStrength = prefs.getInt("v4a_surround_strength", 50)
            val midImageSize = prefs.getInt("v4a_surround_mid", 50)
            val isDiffSurroundEnabled = prefs.getBoolean("v4a_diff_surround_enabled", false)
            val diffSurroundDelay = prefs.getInt("v4a_diff_surround_delay", 5)

            val isReverbEnabled = prefs.getBoolean("v4a_reverb_enabled", false)
            val reverbRoomSize = prefs.getInt("v4a_reverb_room", 100)
            val reverbSoundField = prefs.getInt("v4a_reverb_field", 12)
            val reverbDampingFactor = prefs.getInt("v4a_reverb_damp", 40)
            val reverbWetRatio = prefs.getInt("v4a_reverb_wet", 30)
            val reverbDryRatio = prefs.getInt("v4a_reverb_dry", 90)

            val isDynamicSystemEnabled = prefs.getBoolean("v4a_dynamic_enabled", false)
            val dynamicDevice = prefs.getInt("v4a_dynamic_dev", 0)
            val dynamicBassStrength = prefs.getInt("v4a_dynamic_bass", 14)

            val isBassEnabled = prefs.getBoolean("v4a_bass_enabled", false)
            val viperBassMode = prefs.getInt("v4a_bass_mode", 1)
            val bassFrequency = prefs.getInt("v4a_bass_freq", 60)
            val bassBoost = prefs.getInt("v4a_bass_boost", 600)

            val isClarityEnabled = prefs.getBoolean("v4a_clarity_enabled", false)
            val clarityMode = prefs.getInt("v4a_clarity_mode", 1)
            val clarity = prefs.getInt("v4a_clarity_gain", 500)

            val isTubeEnabled = prefs.getBoolean("v4a_tube_enabled", false)
            val tubeWarmth = prefs.getInt("v4a_tube_warmth", 350)

            val isLimiterEnabled = prefs.getBoolean("v4a_limiter_enabled", false)
            val outputGain = prefs.getInt("v4a_out_gain", 0)
            val channelPan = prefs.getInt("v4a_pan", 0)
            val limiterThreshold = prefs.getInt("v4a_limiter", -1)

            val isFetCompressorEnabled = prefs.getBoolean("v4a_fet_enabled", false)
            val fetThreshold = prefs.getInt("v4a_fet_threshold", 0)
            val fetRatio = prefs.getInt("v4a_fet_ratio", 0)
            val fetAttack = prefs.getInt("v4a_fet_attack", 0)
            val fetRelease = prefs.getInt("v4a_fet_release", 0)
            val fetGain = prefs.getInt("v4a_fet_gain", 0)

            val isSpectrumExtensionEnabled = prefs.getBoolean("v4a_vse_enabled", false)
            val spectrumExtensionStrength = prefs.getInt("v4a_vse_strength", 0)

            val isHeadphoneSurroundEnabled = prefs.getBoolean("v4a_vhe_enabled", false)
            val headphoneSurroundLevel = prefs.getInt("v4a_vhe_level", 0)

            val isAnalogXEnabled = prefs.getBoolean("v4a_analogx_enabled", false)
            val analogXLevel = prefs.getInt("v4a_analogx_level", 0)

            val isAuditoryProtectionEnabled = prefs.getBoolean("v4a_cure_enabled", false)
            val isSpeakerOptEnabled = prefs.getBoolean("v4a_spk_enabled", false)

            return EqualizerSettings(
                selectedTab = selectedTab,
                isEnabled = isEnabled,
                isPlaybackAgcEnabled = isPlaybackAgcEnabled,
                playbackAgcRatio = playbackAgcRatio,
                playbackAgcMaxGain = playbackAgcMaxGain,
                playbackAgcMaxOutput = playbackAgcMaxOutput,
                isDdcEnabled = isDdcEnabled,
                ddcPreset = ddcPreset,
                isEqEnabled = isEqEnabled,
                eqPreset = eqPreset,
                bandLevels = bandLevels,
                isConvolverEnabled = isConvolverEnabled,
                convolverPreset = convolverPreset,
                convolverCrossChannel = convolverCrossChannel,
                isFieldSurroundEnabled = isFieldSurroundEnabled,
                fieldSurroundStrength = fieldSurroundStrength,
                midImageSize = midImageSize,
                isDiffSurroundEnabled = isDiffSurroundEnabled,
                diffSurroundDelay = diffSurroundDelay,
                isReverbEnabled = isReverbEnabled,
                reverbRoomSize = reverbRoomSize,
                reverbSoundField = reverbSoundField,
                reverbDampingFactor = reverbDampingFactor,
                reverbWetRatio = reverbWetRatio,
                reverbDryRatio = reverbDryRatio,
                isDynamicSystemEnabled = isDynamicSystemEnabled,
                dynamicDevice = dynamicDevice,
                dynamicBassStrength = dynamicBassStrength,
                isBassEnabled = isBassEnabled,
                viperBassMode = viperBassMode,
                bassFrequency = bassFrequency,
                bassBoost = bassBoost,
                isClarityEnabled = isClarityEnabled,
                clarityMode = clarityMode,
                clarity = clarity,
                isTubeEnabled = isTubeEnabled,
                tubeWarmth = tubeWarmth,
                isLimiterEnabled = isLimiterEnabled,
                outputGain = outputGain,
                channelPan = channelPan,
                limiterThreshold = limiterThreshold,
                isFetCompressorEnabled = isFetCompressorEnabled,
                fetThreshold = fetThreshold,
                fetRatio = fetRatio,
                fetAttack = fetAttack,
                fetRelease = fetRelease,
                fetGain = fetGain,
                isSpectrumExtensionEnabled = isSpectrumExtensionEnabled,
                spectrumExtensionStrength = spectrumExtensionStrength,
                isHeadphoneSurroundEnabled = isHeadphoneSurroundEnabled,
                headphoneSurroundLevel = headphoneSurroundLevel,
                isAnalogXEnabled = isAnalogXEnabled,
                analogXLevel = analogXLevel,
                isAuditoryProtectionEnabled = isAuditoryProtectionEnabled,
                isSpeakerOptEnabled = isSpeakerOptEnabled
            )
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("v4a_master_enabled", isEnabled)
            putInt("v4a_tab", selectedTab)
            putBoolean("v4a_agc_enabled", isPlaybackAgcEnabled)
            putInt("v4a_agc_ratio", playbackAgcRatio)
            putInt("v4a_agc_gain", playbackAgcMaxGain)
            putInt("v4a_agc_out", playbackAgcMaxOutput)
            putBoolean("v4a_ddc_enabled", isDdcEnabled)
            putInt("v4a_ddc_preset", ddcPreset)
            putBoolean("v4a_eq_enabled", isEqEnabled)
            putInt("v4a_eq_preset", eqPreset)
            bandLevels.forEachIndexed { i, level -> putInt("v4a_eq_band_$i", level) }
            putBoolean("v4a_convolver_enabled", isConvolverEnabled)
            putInt("v4a_convolver_preset", convolverPreset)
            putInt("v4a_convolver_cross", convolverCrossChannel)
            putBoolean("v4a_surround_enabled", isFieldSurroundEnabled)
            putInt("v4a_surround_strength", fieldSurroundStrength)
            putInt("v4a_surround_mid", midImageSize)
            putBoolean("v4a_diff_surround_enabled", isDiffSurroundEnabled)
            putInt("v4a_diff_surround_delay", diffSurroundDelay)
            putBoolean("v4a_reverb_enabled", isReverbEnabled)
            putInt("v4a_reverb_room", reverbRoomSize)
            putInt("v4a_reverb_field", reverbSoundField)
            putInt("v4a_reverb_damp", reverbDampingFactor)
            putInt("v4a_reverb_wet", reverbWetRatio)
            putInt("v4a_reverb_dry", reverbDryRatio)
            putBoolean("v4a_dynamic_enabled", isDynamicSystemEnabled)
            putInt("v4a_dynamic_dev", dynamicDevice)
            putInt("v4a_dynamic_bass", dynamicBassStrength)
            putBoolean("v4a_bass_enabled", isBassEnabled)
            putInt("v4a_bass_mode", viperBassMode)
            putInt("v4a_bass_freq", bassFrequency)
            putInt("v4a_bass_boost", bassBoost)
            putBoolean("v4a_clarity_enabled", isClarityEnabled)
            putInt("v4a_clarity_mode", clarityMode)
            putInt("v4a_clarity_gain", clarity)
            putBoolean("v4a_tube_enabled", isTubeEnabled)
            putInt("v4a_tube_warmth", tubeWarmth)
            putBoolean("v4a_limiter_enabled", isLimiterEnabled)
            putInt("v4a_out_gain", outputGain)
            putInt("v4a_pan", channelPan)
            putInt("v4a_limiter", limiterThreshold)

            putBoolean("v4a_fet_enabled", isFetCompressorEnabled)
            putInt("v4a_fet_threshold", fetThreshold)
            putInt("v4a_fet_ratio", fetRatio)
            putInt("v4a_fet_attack", fetAttack)
            putInt("v4a_fet_release", fetRelease)
            putInt("v4a_fet_gain", fetGain)

            putBoolean("v4a_vse_enabled", isSpectrumExtensionEnabled)
            putInt("v4a_vse_strength", spectrumExtensionStrength)

            putBoolean("v4a_vhe_enabled", isHeadphoneSurroundEnabled)
            putInt("v4a_vhe_level", headphoneSurroundLevel)

            putBoolean("v4a_analogx_enabled", isAnalogXEnabled)
            putInt("v4a_analogx_level", analogXLevel)

            putBoolean("v4a_cure_enabled", isAuditoryProtectionEnabled)
            putBoolean("v4a_spk_enabled", isSpeakerOptEnabled)
            apply()
        }
    }
}
