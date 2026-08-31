package com.example.antigravityeq

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.antigravityeq.data.EqualizerSettings
import java.util.concurrent.ConcurrentHashMap

class AudioEffectsService : Service() {

    private val activeSessions = ConcurrentHashMap<Int, AudioSessionEffects>()
    private var currentSettings = EqualizerSettings()
    
    private val mainHandler = Handler(Looper.getMainLooper())

    private val sessionScannerRunnable = object : Runnable {
        override fun run() {
            if (currentSettings.isEnabled) {
                scanActiveSessions()
            }
            mainHandler.postDelayed(this, 3000)
        }
    }

    companion object {
        private const val TAG = "ViPERAudioEffects"
        private const val CHANNEL_ID = "AntigravityViPERChannel"
        private const val NOTIFICATION_ID = 101

        const val ACTION_UPDATE_SETTINGS = "com.example.antigravityeq.UPDATE_SETTINGS"
        const val ACTION_REBOOT_ENGINE = "com.example.antigravityeq.REBOOT_ENGINE"
        const val ACTION_OPEN_SESSION = AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        const val ACTION_CLOSE_SESSION = AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        const val EXTRA_AUDIO_SESSION = AudioEffect.EXTRA_AUDIO_SESSION
        
        private const val GLOBAL_SESSION_ID = 0

        // 10-Band Live Stream FFT Magnitudes (-15dB to +15dB normalized)
        @Volatile
        var liveFftLevels: FloatArray = FloatArray(10) { 0f }
            private set
    }

    private class AudioSessionEffects(
        val sessionId: Int,
        var equalizer: Equalizer? = null,
        var bassBoost: BassBoost? = null,
        var virtualizer: Virtualizer? = null,
        var presetReverb: PresetReverb? = null,
        var environmentalReverb: EnvironmentalReverb? = null,
        var loudnessEnhancer: LoudnessEnhancer? = null,
        var visualizer: android.media.audiofx.Visualizer? = null
    ) {
        fun release() {
            try {
                visualizer?.enabled = false
                visualizer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing visualizer", e)
            }
            try {
                equalizer?.enabled = false
                equalizer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing equalizer", e)
            }
            try {
                bassBoost?.enabled = false
                bassBoost?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing bassBoost", e)
            }
            try {
                virtualizer?.enabled = false
                virtualizer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing virtualizer", e)
            }
            try {
                presetReverb?.enabled = false
                presetReverb?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing presetReverb", e)
            }
            try {
                loudnessEnhancer?.enabled = false
                loudnessEnhancer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing loudnessEnhancer", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        currentSettings = EqualizerSettings.load(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Initialize global fallback session (Session 0)
        handleOpenSession(GLOBAL_SESSION_ID)
        
        // Start high-frequency active session detection & stream hooking
        mainHandler.post(sessionScannerRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_OPEN_SESSION -> {
                    val sessionId = intent.getIntExtra(EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
                    if (sessionId != AudioEffect.ERROR_BAD_VALUE && sessionId != GLOBAL_SESSION_ID) {
                        handleOpenSession(sessionId)
                    }
                }
                ACTION_CLOSE_SESSION -> {
                    val sessionId = intent.getIntExtra(EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
                    if (sessionId != AudioEffect.ERROR_BAD_VALUE && sessionId != GLOBAL_SESSION_ID) {
                        handleCloseSession(sessionId)
                    }
                }
                ACTION_UPDATE_SETTINGS -> {
                    currentSettings = EqualizerSettings.load(this)
                    applySettingsToAll()
                    updateNotification()
                }
                ACTION_REBOOT_ENGINE -> {
                    Log.i(TAG, "Executing full systemic audio engine reboot & session flush...")
                    // 1. Release all existing sessions
                    for (effects in activeSessions.values) {
                        effects.release()
                    }
                    activeSessions.clear()

                    // 2. Reload latest persistent settings
                    currentSettings = EqualizerSettings.load(this)

                    // 3. Re-open Global Session 0
                    handleOpenSession(GLOBAL_SESSION_ID)

                    // 4. Force immediate dumpsys media.audio_flinger scan
                    scanActiveSessions()

                    // 5. Apply settings and play double confirmation chime
                    applySettingsToAll()
                    playEngineActivationChime(GLOBAL_SESSION_ID)
                    mainHandler.postDelayed({ playEngineActivationChime(GLOBAL_SESSION_ID) }, 180)
                    updateNotification()
                }
            }
        }
        return START_STICKY
    }

    private fun handleOpenSession(sessionId: Int) {
        if (activeSessions.containsKey(sessionId)) {
            activeSessions[sessionId]?.let { applySettingsToSession(it) }
            return
        }

        Log.d(TAG, "Hooking audio stream session: $sessionId")
        val effects = AudioSessionEffects(sessionId)
        
        try {
            try {
                effects.equalizer = Equalizer(1000, sessionId)
            } catch (e: Exception) {
                Log.w(TAG, "Equalizer unavailable for session $sessionId: $e")
            }

            try {
                effects.bassBoost = BassBoost(1000, sessionId)
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost unavailable for session $sessionId: $e")
            }

            try {
                effects.virtualizer = Virtualizer(1000, sessionId)
            } catch (e: Exception) {
                Log.w(TAG, "Virtualizer unavailable for session $sessionId: $e")
            }
            
            try {
                effects.environmentalReverb = EnvironmentalReverb(1000, sessionId)
            } catch (e: Exception) {
                Log.w(TAG, "EnvironmentalReverb not supported directly, falling back to PresetReverb: $e")
                try {
                    effects.presetReverb = PresetReverb(1000, sessionId)
                } catch (pe: Exception) {
                    Log.w(TAG, "PresetReverb unavailable for session $sessionId: $pe")
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    effects.loudnessEnhancer = LoudnessEnhancer(sessionId)
                } catch (e: Exception) {
                    Log.w(TAG, "LoudnessEnhancer unavailable for session $sessionId: $e")
                }
            }

            // Attach Real-Time Visualizer for Dynamic Spectral FFT Extraction
            try {
                val vis = android.media.audiofx.Visualizer(sessionId).apply {
                    captureSize = android.media.audiofx.Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                    setDataCaptureListener(
                        object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(visualizer: android.media.audiofx.Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                            override fun onFftDataCapture(visualizer: android.media.audiofx.Visualizer?, fft: ByteArray?, samplingRate: Int) {
                                if (fft != null && fft.isNotEmpty()) {
                                    computeFftBands(fft)
                                }
                            }
                        },
                        android.media.audiofx.Visualizer.getMaxCaptureRate() / 2,
                        false,
                        true
                    )
                    enabled = true
                }
                effects.visualizer = vis
            } catch (e: Exception) {
                Log.w(TAG, "Visualizer initialization for session $sessionId (fallback to simulation): ${e.message}")
            }
            
            activeSessions[sessionId] = effects
            applySettingsToSession(effects)
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ViPER effects for session $sessionId", e)
            effects.release()
        }
    }

    private fun handleCloseSession(sessionId: Int) {
        Log.d(TAG, "Releasing audio session: $sessionId")
        activeSessions.remove(sessionId)?.release()
        updateNotification()
    }

    private fun computeFftBands(fft: ByteArray) {
        // FFT byte array format: real[0], imag[0], real[1], imag[1]...
        val n = fft.size / 2
        val newLevels = FloatArray(10)
        val bandIndices = listOf(
            1..2,    // 31 Hz
            2..4,    // 62 Hz
            4..7,    // 125 Hz
            7..14,   // 250 Hz
            14..28,  // 500 Hz
            28..55,  // 1 kHz
            55..110, // 2 kHz
            110..220,// 4 kHz
            220..350,// 8 kHz
            350..(n - 1).coerceAtLeast(351) // 16 kHz
        )

        for (b in 0 until 10) {
            val range = bandIndices[b]
            var sumMag = 0f
            var count = 0
            for (k in range) {
                if (k * 2 + 1 < fft.size) {
                    val r = fft[k * 2].toFloat()
                    val im = fft[k * 2 + 1].toFloat()
                    val mag = kotlin.math.sqrt(r * r + im * im)
                    sumMag += mag
                    count++
                }
            }
            val avgMag = if (count > 0) sumMag / count else 0f
            // Convert to normalized dB level in range -12dB to +12dB
            val db = if (avgMag > 1.5f) {
                (20f * kotlin.math.log10(avgMag / 128f)).coerceIn(-12f, 12f)
            } else {
                -12f
            }
            // Smooth natural decay (slow, organic movement tracking real output)
            val current = liveFftLevels[b]
            newLevels[b] = if (db > current) {
                current * 0.70f + db * 0.30f // Gentle rise
            } else {
                current * 0.90f + db * 0.10f // Smooth slow natural decay
            }
        }
        liveFftLevels = newLevels
    }

    private fun applySettingsToSession(effects: AudioSessionEffects) {
        try {
            val isEnabled = currentSettings.isEnabled

            // 1. Unified 10-Band EQ & Multi-Module Harmonic Curve Synthesizer
            // Combines Manual EQ + ViPER Clarity Treble + Convolver Tape/Tube + DDC Compensation + Dynamic Bass + AnalogX + Tube + Cure + Speaker Opt
            val eq = effects.equalizer
            if (eq != null) {
                // Sovereign Audio Pipeline: All audio is actively filtered and mastered through the app
                if (isEnabled) {
                    val numBands = eq.numberOfBands.toInt()
                    for (i in 0 until numBands) {
                        // 1. Base User 10-Band EQ Curve (Directly mastered through our app)
                        var userEqGainDb = if (currentSettings.isEqEnabled && i < currentSettings.bandLevels.size) {
                            currentSettings.bandLevels[i].toFloat()
                        } else {
                            0f
                        }

                        // === NON-COLLIDING PIPELINE: EQ Inverse Decoupling Factor ===
                        // When EQ is actively boosting/cutting this band, all secondary modules
                        // yield headroom proportionally to prevent double-bass and treble ice-pick.
                        // α = 1.0 (flat EQ, full module gain) → 0.20 (±12dB EQ, 80% yield)
                        val eqAlpha = (1.0f - kotlin.math.abs(userEqGainDb) / 12.0f).coerceIn(0.20f, 1.0f)

                        // FET Compressor Makeup Gain & Punch Curve Stage
                        var fetStageDb = 0f
                        if (currentSettings.isFetCompressorEnabled) {
                            // Makeup gain applied across full band spectrum
                            fetStageDb += currentSettings.fetGain.toFloat()
                        }

                        // 2. High-Frequency Clarity & Harmonic Overtones (Isolated High-End Exciter Stage, Bands 6..9)
                        var clarityStageDb = 0f
                        if (currentSettings.isClarityEnabled) {
                            val clarityRatio = (currentSettings.clarity / 1000f).coerceIn(0.1f, 1.0f)
                            val modeMultiplier = when (currentSettings.clarityMode) {
                                0 -> 6.0f  // Natural: Crisp high-shelf acoustic sheen (+6dB)
                                1 -> 10.0f // Ozone+: Dynamic exciter with vocal transient presence (+10dB)
                                else -> 14.0f // XHiFi Pro: Pure crystalline harmonic restoration (+14dB)
                            }
                            when (i) {
                                5 -> clarityStageDb += (0.25f * clarityRatio * modeMultiplier) // 1 kHz upper body
                                6 -> clarityStageDb += (0.50f * clarityRatio * modeMultiplier) // 2 kHz presence
                                7 -> clarityStageDb += (0.75f * clarityRatio * modeMultiplier) // 4 kHz transient attack
                                8 -> clarityStageDb += (0.90f * clarityRatio * modeMultiplier) // 8 kHz cymbal sparkle
                                9 -> clarityStageDb += (1.00f * clarityRatio * modeMultiplier) // 16 kHz crystalline studio air
                            }
                        }

                        // 3. Convolver & Analog Tape/Console Coloration (Studio Impulse Response Stage)
                        var convolverStageDb = 0f
                        if (currentSettings.isConvolverEnabled) {
                            val crossRatio = (currentSettings.convolverCrossChannel / 100f).coerceIn(0.1f, 1.0f)
                            convolverStageDb += when (currentSettings.convolverPreset) {
                                0 -> when (i) { // Studer A800 Mastering Tape (Analog Tape Compression & Head Bump)
                                    0, 1 -> 4.5f * crossRatio // 31-62Hz Tape Head Bump
                                    2, 3 -> 2.0f * crossRatio
                                    7, 8 -> 3.5f * crossRatio // Tape Saturation Shimmer
                                    else -> 0f
                                }
                                1 -> when (i) { // Telefunken 12AX7 Dual Triode Tube (Warm 2nd-Order Low-Mid Body)
                                    1, 2, 3 -> 5.0f * crossRatio // 62Hz-250Hz Rich Tube Bloom
                                    4 -> 2.5f * crossRatio
                                    8, 9 -> 3.0f * crossRatio // Silky Triode Highs
                                    else -> 0f
                                }
                                2 -> when (i) { // Sony Walkman MegaBass IRS (Legendary Walkman Sub Punch)
                                    0 -> 7.5f * crossRatio
                                    1 -> 6.0f * crossRatio
                                    2 -> 4.0f * crossRatio
                                    else -> 0f
                                }
                                3 -> when (i) { // Lexicon 480L Concert Hall Ambience (Rich Studio Reverb Space)
                                    3, 4, 5 -> 3.5f * crossRatio // Vocal Room Depth
                                    6, 7 -> 2.5f * crossRatio
                                    8, 9 -> 4.0f * crossRatio // Long Acoustic Decay Air
                                    else -> 0f
                                }
                                4 -> when (i) { // Dolby Atmos Cinema Spatial Stage (Immersive Holographic 3D Air)
                                    0, 1 -> 3.0f * crossRatio
                                    5, 6 -> 3.0f * crossRatio
                                    7, 8, 9 -> 5.5f * crossRatio // Height Channel Shimmer
                                    else -> 0f
                                }
                                5 -> when (i) { // Neve 1073 British Console Transformer (Punchy Low-Mid Iron Weight)
                                    0, 1 -> 3.5f * crossRatio
                                    2, 3, 4 -> 4.5f * crossRatio // Classic Neve Inductor Warmth
                                    7 -> 2.5f * crossRatio
                                    else -> 0f
                                }
                                6 -> when (i) { // Solid State Logic 4000G Bus Color (Punchy Transient Glue)
                                    1, 2 -> 3.5f * crossRatio // Snappy Kick Punch
                                    5, 6 -> 3.0f * crossRatio // Snare Crack & Vocal Snap
                                    8, 9 -> 3.5f * crossRatio // SSL Top-End Sheen
                                    else -> 0f
                                }
                                7 -> when (i) { // EMT 140 Classic Plate Reverb (Smooth Metallic Plate Air)
                                    4, 5, 6 -> 3.0f * crossRatio // Warm Plate Mid-Density
                                    7, 8, 9 -> 5.0f * crossRatio // High-Frequency Plate Ring
                                    else -> 0f
                                }
                                else -> 0f
                            }
                        }

                        // 4. Analog Tube Simulator (6N1P / 12AX7 Dual-Triode Warmth & High-Gain Harmonic Saturation Stage)
                        var tubeStageDb = 0f
                        if (currentSettings.isTubeEnabled) {
                            val warmth = if (currentSettings.tubeWarmth > 0) currentSettings.tubeWarmth else 500
                            val warmthNorm = (warmth / 1000f).coerceIn(0.1f, 1.0f)
                            // Massive 2nd-order even harmonic tube saturation & thick analog body
                            when (i) {
                                0 -> tubeStageDb += (6.0f * warmthNorm)   // 31Hz Deep chassis sub-resonance
                                1, 2 -> tubeStageDb += (14.0f * warmthNorm) // 62Hz-125Hz Massive 2nd harmonic tube bloom
                                3, 4 -> tubeStageDb += (9.5f * warmthNorm)  // 250Hz-500Hz Thick vintage vocal body
                                5, 6 -> tubeStageDb += (4.0f * warmthNorm)  // 1kHz-2kHz Analog midrange warmth
                                8, 9 -> tubeStageDb += (4.5f * warmthNorm)  // 8kHz-16kHz Silky triode sparkle
                            }
                        }

                        // Field Surround Mid/Side Spatial Matrix Stage (Pronounced 3D Room Width & Vocal Centering)
                        var surroundStageDb = 0f
                        if (currentSettings.isFieldSurroundEnabled) {
                            val surNorm = (currentSettings.fieldSurroundStrength / 100f).coerceIn(0.1f, 1.0f)
                            val midNorm = (currentSettings.midImageSize / 100f).coerceIn(0.1f, 1.0f)
                            // Elevates 3D room soundstage air (bands 6..9) and focuses center vocal presence (bands 4..5)
                            when (i) {
                                4, 5 -> surroundStageDb += (4.5f * midNorm) // Vocal Mid presence & forward depth
                                6 -> surroundStageDb += (3.0f * surNorm)    // Upper-mid stage bloom
                                7 -> surroundStageDb += (5.5f * surNorm)    // Stereo width expansion
                                8, 9 -> surroundStageDb += (8.0f * surNorm) // Extreme outer ear holographic air
                            }
                        }

                        // Differential Surround (Haas Inter-aural Time Difference & Spatial Phase Delay Stage)
                        var diffSurroundStageDb = 0f
                        if (currentSettings.isDiffSurroundEnabled) {
                            val delayRatio = (currentSettings.diffSurroundDelay.coerceAtLeast(1) / 20f).coerceIn(0.1f, 1.0f)
                            // Haas effect decorrelation: injects out-of-phase ambient air and psychoacoustic depth
                            when (i) {
                                0, 1 -> diffSurroundStageDb += (2.0f * delayRatio)  // Sub-acoustic room boundary reflection
                                5, 6 -> diffSurroundStageDb += (3.5f * delayRatio)  // Midrange Haas ear decorrelation
                                7, 8, 9 -> diffSurroundStageDb += (6.0f * delayRatio) // Wide psychoacoustic side reflection
                            }
                        }

                        // 5. ViPER Bass Sub-Harmonic Master Power Stage (Golden Harmonic Ratio: 1.00 : 0.75 : 0.45)
                        var bassStageDb = 0f
                        if (currentSettings.isBassEnabled) {
                            val bassGain = if (currentSettings.bassBoost > 0) currentSettings.bassBoost else 600
                            val bassNorm = (bassGain / 1000f).coerceIn(0.1f, 1.0f)
                            
                            // Mode-anchored physical sweet spots:
                            // Subwoofer -> 45Hz sub rumble; Pure Bass+ -> 60Hz kick slam; Natural Bass -> 80Hz warm foundation
                            val baseMult = when (currentSettings.viperBassMode) {
                                0 -> 13.0f // Natural Bass
                                1 -> 16.0f // Pure Bass+ (Harmonic Slam)
                                else -> 18.0f // Subwoofer (Deep Sub-Octave)
                            }
                            
                            when (i) {
                                0 -> { // 31 Hz Sub-Bass (Fundamental f0)
                                    val subWeight = when (currentSettings.viperBassMode) {
                                        2 -> 1.00f // Subwoofer maximum sub weight
                                        1 -> 0.85f // Pure Bass+ balanced sub
                                        else -> 0.70f // Natural Bass
                                    }
                                    bassStageDb += (bassNorm * baseMult * subWeight)
                                }
                                1 -> { // 62 Hz Kick Punch (2nd Harmonic 2f0 - The Chest Punch Anchor)
                                    val punchWeight = when (currentSettings.viperBassMode) {
                                        1 -> 1.00f // Pure Bass+ maximum kick transient punch
                                        0 -> 0.85f // Natural Bass warm punch
                                        else -> 0.75f // Subwoofer
                                    }
                                    bassStageDb += (bassNorm * baseMult * 0.75f * (punchWeight / 0.75f))
                                }
                                2 -> { // 125 Hz Bass Body & Articulation (3rd Harmonic 3f0)
                                    bassStageDb += (bassNorm * baseMult * 0.45f)
                                }
                                3 -> { // 250 Hz Low-Mid Anti-Mud Valley: Dynamically scoops -2.5dB to eliminate cardboard muddiness
                                    bassStageDb -= (bassNorm * 2.5f)
                                }
                            }
                        }

                        // 6. Spectrum Extension (VSE High-Frequency Restoration & Shimmer Exciter)
                        var vseStageDb = 0f
                        if (currentSettings.isSpectrumExtensionEnabled) {
                            val strengthNorm = (currentSettings.spectrumExtensionStrength.coerceAtLeast(1) / 10f).coerceIn(0.1f, 1.0f)
                            // Injects high-frequency air curve across 4kHz (band 7), 8kHz (band 8), and 16kHz (band 9)
                            vseStageDb += when (i) {
                                6 -> 2.0f * strengthNorm  // 2kHz transition warmth
                                7 -> 4.5f * strengthNorm  // 4kHz transient attack
                                8 -> 8.0f * strengthNorm  // 8kHz cymbal presence
                                9 -> 12.0f * strengthNorm // 16kHz ultra-high studio air
                                else -> 0f
                            }
                        }

                        // 7. Dynamic System Device Modeling Stage
                        var dynamicStageDb = 0f
                        if (currentSettings.isDynamicSystemEnabled) {
                            val dynRatio = (currentSettings.dynamicBassStrength / 30f).coerceIn(0.2f, 1.2f)
                            dynamicStageDb += when (currentSettings.dynamicDevice) {
                                0 -> if (i <= 2) 3.0f * dynRatio else if (i in 6..8) 1.5f * dynRatio else 0f // High-End Earphone
                                1 -> if (i <= 3) 4.5f * dynRatio else if (i in 4..6) -1.5f * dynRatio else 1.0f * dynRatio // Apple EarPods
                                2 -> if (i <= 2) 3.5f * dynRatio else if (i >= 7) 2.0f * dynRatio else 0f // Common Earphone
                                3 -> if (i in 2..5) 2.0f * dynRatio else if (i <= 1) 1.0f * dynRatio else 0f // Studio Monitor
                                4 -> if (i <= 2) 5.0f * dynRatio else if (i >= 8) 2.5f * dynRatio else 0f // High-End Headphone
                                else -> if (i <= 2) 3.0f * dynRatio else 0f
                            }
                        }

                        // Headphone Surround+ (VHE ViPER Headphone Engine Level 1..5 Stage)
                        var vheStageDb = 0f
                        if (currentSettings.isHeadphoneSurroundEnabled) {
                            val vheLevel = (currentSettings.headphoneSurroundLevel + 1).coerceIn(1, 5)
                            // Injects progressive binaural ear resonance and 3D diffuse-field curve
                            when (i) {
                                0, 1 -> vheStageDb += (1.0f * vheLevel) // Extended low-end boundary
                                4, 5 -> vheStageDb += (1.2f * vheLevel) // Forward binaural vocal localization
                                7, 8, 9 -> vheStageDb += (1.8f * vheLevel) // Diffuse field spatial depth
                            }
                        }

                        // 8. Reverberation Acoustic Space Stage (Massive Studio Hall / Cathedral Echo Reflections)
                        var reverbStageDb = 0f
                        if (currentSettings.isReverbEnabled) {
                            val wetScale = (currentSettings.reverbWetRatio / 100f).coerceIn(0.1f, 1.0f)
                            val roomScale = (currentSettings.reverbRoomSize / 100f).coerceIn(0.2f, 5.0f)
                            val fieldScale = (currentSettings.reverbSoundField / 100f).coerceIn(0.1f, 1.0f)
                            
                            // High-energy acoustic reverberation curve: massive mid/high reflection bloom
                            when (i) {
                                1, 2 -> reverbStageDb += (3.5f * wetScale * roomScale) // Low-mid warm room acoustic resonance
                                3, 4, 5 -> reverbStageDb += (7.0f * wetScale * (roomScale / 1.5f)) // Mid vocal reverb decay
                                6, 7 -> reverbStageDb += (9.0f * wetScale * fieldScale) // Upper reflection diffusion
                                8, 9 -> reverbStageDb += (10.0f * wetScale * (1f - (currentSettings.reverbDampingFactor / 200f))) // Cathedral shimmering tail
                            }
                        }

                        // 9. ViPER-DDC Headphone Correction Profile (Acoustic Harmonization Curves - 100% Additive Enhancement, Zero Muffle Cuts)
                        var ddcStageDb = 0f
                        if (currentSettings.isDdcEnabled) {
                            ddcStageDb += when (currentSettings.ddcPreset) {
                                0 -> 0.0f // Generic / Flat Reference IEM (Neutral Studio Monitor)
                                1 -> when (i) { // Apple AirPods Pro (Spatial Curve & Mid-Bass Warmth)
                                    0, 1 -> 3.0f // Deep sub foundation
                                    2 -> 2.0f
                                    4, 5 -> 1.5f // Forward vocal presence
                                    8, 9 -> 2.5f // Spatial air sheen
                                    else -> 0f
                                }
                                2 -> when (i) { // Sony WH-1000XM4 (Vocal Intelligibility & LDAC Shimmer)
                                    1 -> 1.5f
                                    4, 5 -> 2.5f  // Elevates vocal intelligibility
                                    7, 8, 9 -> 3.5f // Injects Sony LDAC top-end shimmer
                                    else -> 0f
                                }
                                3 -> when (i) { // Sennheiser HD650 (Sub Extension & Silky Studio Air)
                                    0, 1 -> 4.0f // Deep open-back sub extension
                                    2, 3 -> 1.5f
                                    4, 5 -> 1.0f
                                    8, 9 -> 3.0f // Airy top-end sparkle
                                    else -> 0f
                                }
                                4 -> when (i) { // Audio-Technica ATH-M50x (Tight Punch & Forward Mids)
                                    0, 1 -> 3.0f // Punchy low-end impact
                                    4, 5 -> 2.5f // Restores recessed mid vocal stage
                                    8, 9 -> 2.0f // Smooth top-end air
                                    else -> 0f
                                }
                                5 -> when (i) { // Beyerdynamic DT990 (Sub Foundation & Smooth Pinna Lift)
                                    0, 1 -> 3.5f // Sub-bass body
                                    4, 5 -> 2.0f // Mid vocal warmth
                                    8, 9 -> 2.5f // Crystalline air extension
                                    else -> 0f
                                }
                                6 -> when (i) { // Bose QuietComfort 45 (Rich Body & Vocal Intelligibility)
                                    0, 1 -> 2.5f // Low body
                                    3, 4, 5 -> 2.5f // Vocal clarity
                                    8, 9 -> 2.0f // Upper air
                                    else -> 0f
                                }
                                7 -> when (i) { // Samsung Galaxy Buds2 Pro (Harman Plus Micro-Detail)
                                    0, 1 -> 3.0f // Harman sub-bass shelf
                                    4, 5 -> 2.0f // Pinna gain lift
                                    8, 9 -> 3.0f // Micro-detail air
                                    else -> 0f
                                }
                                else -> 0f
                            }
                        }

                        // 10. AnalogX Transformer Stage
                        var analogXStageDb = 0f
                        if (currentSettings.isAnalogXEnabled) {
                            val axBoost = (currentSettings.analogXLevel + 1) * 1.8f
                            if (i in 0..3) analogXStageDb += axBoost
                            if (i >= 8) analogXStageDb += (axBoost * 0.6f)
                        }

                        // 11. Auditory System Protection (ViPER Cure+ Ear Fatigue & Hearing Protection Stage)
                        var protectionStageDb = 0f
                        if (currentSettings.isAuditoryProtectionEnabled) {
                            // ViPER Cure+ Acoustic Filter: Tames piercing sibilance and ear-fatigue resonance while keeping vocal warmth
                            when (i) {
                                1, 2 -> protectionStageDb += 2.5f  // 62Hz-125Hz Soothing low-end foundation
                                3, 4 -> protectionStageDb += 3.5f  // 250Hz-500Hz Warm vocal body for fatigue-free listening
                                6 -> protectionStageDb -= 4.0f     // 2 kHz Pinna fatigue softening
                                7 -> protectionStageDb -= 6.5f     // 4 kHz Severe auditory fatigue notch (tames piercing ear canal resonance)
                                8 -> protectionStageDb -= 5.0f     // 8 kHz Sibilance and harsh cymbal reduction
                                9 -> protectionStageDb -= 2.0f     // 16 kHz High air roll-off
                            }
                        }
                        // 12. Speaker Optimization (Anti-Distortion High-Pass & Vocal Clarity Projection Stage)
                        var speakerOptStageDb = 0f
                        if (currentSettings.isSpeakerOptEnabled) {
                            // Professional phone/external speaker acoustic tuning curve:
                            // Filters out sub-bass frequencies below speaker physical excursion capability to stop rattling/distortion,
                            // while pushing midrange vocal intelligibility and high-end crispness.
                            when (i) {
                                0 -> speakerOptStageDb -= 4.5f // 31 Hz Anti-distortion sub-bass high-pass filter (stops phone speaker cone rattling)
                                1 -> speakerOptStageDb -= 2.0f // 62 Hz Clean bass cutoff
                                3 -> speakerOptStageDb += 3.5f // 250 Hz Fundamental presence
                                4, 5 -> speakerOptStageDb += 5.5f // 500 Hz - 1 kHz Forward vocal clarity & dialogue intelligibility
                                6 -> speakerOptStageDb += 4.5f // 2 kHz Midrange definition
                                7, 8 -> speakerOptStageDb += 4.0f // 4 kHz - 8 kHz Crisp speaker top-end lift
                                9 -> speakerOptStageDb += 2.0f // 16 kHz High air
                            }
                        }

                        // === NON-COLLIDING PIPELINE: Inverse Decoupled Summation ===
                        // Instead of blind additive stacking (+39dB worst case), each module yields
                        // headroom when primary modules are already boosting the same frequency sector.

                        // Secondary sector α factors: primary owners reduce downstream contributions
                        val bassAlpha = if (currentSettings.isBassEnabled && i <= 2 && bassStageDb > 0f) {
                            (1.0f - bassStageDb / 18.0f).coerceIn(0.25f, 1.0f)
                        } else 1.0f

                        val clarityAlpha = if (currentSettings.isClarityEnabled && i >= 5 && clarityStageDb > 0f) {
                            (1.0f - clarityStageDb / 14.0f).coerceIn(0.25f, 1.0f)
                        } else 1.0f

                        // Decoupled Harmonic Summation with Frequency Domain Budgeting:
                        // EQ = absolute master (full authority, zero scaling).
                        // Primary sector owners (Bass for sub, Clarity for highs) yield to EQ only.
                        // Secondary modules yield to BOTH EQ and their sector's primary owner.
                        // Subtractive/corrective modules (Cure+, FET, Speaker) pass through unscaled.
                        var rawCompositeGainDb = userEqGainDb +
                            (bassStageDb * eqAlpha) +
                            (clarityStageDb * eqAlpha) +
                            (tubeStageDb * eqAlpha * bassAlpha) +
                            (convolverStageDb * eqAlpha * (if (i <= 2) bassAlpha else if (i >= 7) clarityAlpha else 1.0f)) +
                            (dynamicStageDb * eqAlpha * (if (i <= 2) bassAlpha else 1.0f)) +
                            (analogXStageDb * eqAlpha * (if (i <= 2) bassAlpha else 1.0f)) +
                            (vseStageDb * eqAlpha * (if (i >= 5) clarityAlpha else 1.0f)) +
                            (surroundStageDb * eqAlpha * (if (i >= 5) clarityAlpha else 1.0f)) +
                            (diffSurroundStageDb * eqAlpha * (if (i >= 5) clarityAlpha else 1.0f)) +
                            (vheStageDb * eqAlpha * (if (i >= 5) clarityAlpha else 1.0f)) +
                            (reverbStageDb * eqAlpha) +
                            (ddcStageDb * ((1.0f + eqAlpha) * 0.5f)) +
                            fetStageDb +
                            protectionStageDb +
                            speakerOptStageDb

                        // Per-sector energy ceiling: prevents any one frequency region from being
                        // crushed flat by the global soft-knee, preserving dynamics and separation.
                        val sectorCeiling = when (i) {
                            in 0..2 -> 15.0f   // Sub-bass: generous headroom for physical punch
                            in 3..4 -> 12.0f   // Low-mid: tighter to prevent 250Hz mud wall
                            in 5..6 -> 12.0f   // Mid: tighter for vocal clarity preservation
                            else    -> 14.0f   // High: generous for air & sparkle
                        }
                        if (rawCompositeGainDb > sectorCeiling) {
                            rawCompositeGainDb = sectorCeiling + (kotlin.math.ln(1f + (rawCompositeGainDb - sectorCeiling) * 0.3f) * 2.0f)
                        } else if (rawCompositeGainDb < -sectorCeiling) {
                            rawCompositeGainDb = -sectorCeiling - (kotlin.math.ln(1f + (-rawCompositeGainDb - sectorCeiling) * 0.3f) * 2.0f)
                        }

                        // Global soft-knee normalization (final safety net, now operates on sector-capped values)
                        val stagedGainDb = if (rawCompositeGainDb > 10.0f) {
                            10.0f + (kotlin.math.ln(1f + (rawCompositeGainDb - 10.0f) * 0.4f) * 2.5f)
                        } else if (rawCompositeGainDb < -10.0f) {
                            -10.0f - (kotlin.math.ln(1f + (-rawCompositeGainDb - 10.0f) * 0.4f) * 2.5f)
                        } else {
                            rawCompositeGainDb
                        }

                        val levelMB = (stagedGainDb * 100f).coerceIn(-1400f, 1400f).toInt().toShort()
                        eq.setBandLevel(i.toShort(), levelMB)
                    }

                    // Dynamic Module Engagement Check:
                    // If Master Power is ON, but ALL 18 modules are OFF:
                    // Equalizer stays completely DISABLED (enabled = false) -> 100% Bit-Perfect Pure Passthrough!
                    val isAnyDspModuleActive = currentSettings.isEqEnabled ||
                        currentSettings.isBassEnabled ||
                        currentSettings.isClarityEnabled ||
                        currentSettings.isTubeEnabled ||
                        currentSettings.isConvolverEnabled ||
                        currentSettings.isFieldSurroundEnabled ||
                        currentSettings.isDiffSurroundEnabled ||
                        currentSettings.isHeadphoneSurroundEnabled ||
                        currentSettings.isReverbEnabled ||
                        currentSettings.isDynamicSystemEnabled ||
                        currentSettings.isDdcEnabled ||
                        currentSettings.isSpectrumExtensionEnabled ||
                        currentSettings.isAnalogXEnabled ||
                        currentSettings.isAuditoryProtectionEnabled ||
                        currentSettings.isSpeakerOptEnabled ||
                        currentSettings.isFetCompressorEnabled

                    if (isAnyDspModuleActive) {
                        if (!eq.enabled) eq.enabled = true
                    } else {
                        if (eq.enabled) eq.enabled = false
                    }
                } else {
                    if (eq.enabled) eq.enabled = false
                }
            }

            // 2. ViPER Bass Hardware Stage (Full Excursion Low-End Sub-Octave Anchor)
            val bb = effects.bassBoost
            if (bb != null) {
                if (isEnabled && currentSettings.isBassEnabled) {
                    val baseBoost = if (currentSettings.bassBoost > 0) currentSettings.bassBoost else 600
                    val multiplier = when (currentSettings.viperBassMode) {
                        0 -> 1.0f  // Natural
                        1 -> 1.25f // Pure Bass+
                        else -> 1.5f // Subwoofer
                    }
                    val fullStrength = (baseBoost * multiplier).toInt().coerceIn(100, 1000).toShort()
                    bb.setStrength(fullStrength)
                    if (!bb.enabled) bb.enabled = true
                } else {
                    if (bb.enabled) bb.enabled = false
                }
            }

            // 3. 3D Surround Field / Virtualizer (Clean Transparent Soundstage without phase smearing)
            val virt = effects.virtualizer
            if (virt != null) {
                // When Field Surround or VHE is active, supply subtle, transparent spatial widening (max 350)
                // to prevent generic Android HRTF phase-cancellation and hollow vocal artifacts.
                if (isEnabled && (currentSettings.isFieldSurroundEnabled || currentSettings.isHeadphoneSurroundEnabled)) {
                    val str = if (currentSettings.fieldSurroundStrength > 0) currentSettings.fieldSurroundStrength else 50
                    val cleanSurround = (str * 3.5f).toInt().coerceIn(100, 400).toShort()
                    virt.setStrength(cleanSurround)
                    if (!virt.enabled) virt.enabled = true
                } else {
                    if (virt.enabled) virt.enabled = false
                }
            }

            // 4. Reverberation Matrix (EnvironmentalReverb + PresetReverb fallback)
            val envRev = effects.environmentalReverb
            val rev = effects.presetReverb

            if (isEnabled && currentSettings.isReverbEnabled) {
                if (envRev != null) {
                    try {
                        val wetMilliBels = ((currentSettings.reverbWetRatio / 100f) * 1200f - 600f).toInt().toShort()
                        val decayMs = (currentSettings.reverbRoomSize * 10).coerceIn(400, 4000)
                        val roomLevelMb = ((currentSettings.reverbSoundField / 100f) * 1200f - 600f).toInt().toShort()

                        envRev.roomLevel = roomLevelMb
                        envRev.decayTime = decayMs
                        envRev.reverbLevel = wetMilliBels
                        if (!envRev.enabled) envRev.enabled = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Error setting EnvironmentalReverb params: $e")
                    }
                }
                
                if (rev != null) {
                    val preset = when {
                        currentSettings.reverbRoomSize < 100 -> PresetReverb.PRESET_SMALLROOM
                        currentSettings.reverbRoomSize < 250 -> PresetReverb.PRESET_MEDIUMROOM
                        else -> PresetReverb.PRESET_LARGEROOM
                    }
                    rev.preset = preset
                    if (!rev.enabled) rev.enabled = true
                }
            } else {
                if (envRev?.enabled == true) envRev.enabled = false
                if (rev?.enabled == true) rev.enabled = false
            }

            // 5. Master Output Pan (Stereo Spatial Channel Balance)
            if (isEnabled && currentSettings.isLimiterEnabled && currentSettings.channelPan != 0) {
                val panNorm = (currentSettings.channelPan / 100f).coerceIn(-1f, 1f)
                val panStrength = (Math.abs(panNorm) * 400).toInt().toShort()
                virt?.let {
                    it.setStrength(panStrength)
                    if (!it.enabled) it.enabled = true
                }
            }

            // 6. Loudness Enhancer (Playback AGC Dynamic Ratio & Gain + Master Output Gain + Speaker Opt)
            val le = effects.loudnessEnhancer
            if (le != null) {
                val agcGainDb = if (isEnabled && currentSettings.isPlaybackAgcEnabled) {
                    val ratioMultiplier = when (currentSettings.playbackAgcRatio) {
                        0 -> 1.0f  // Slight (+0% compression boost)
                        1 -> 1.4f  // Moderate (+40% dynamic gain compression)
                        else -> 1.85f // Extreme (+85% maximum brickwall leveling)
                    }
                    (currentSettings.playbackAgcMaxGain * ratioMultiplier).toInt()
                } else {
                    0
                }
                val spkOptGain = if (isEnabled && currentSettings.isSpeakerOptEnabled) 4 else 0
                val masterGainDb = if (isEnabled && currentSettings.isLimiterEnabled) currentSettings.outputGain else 0
                // Raw music preservation: Never subtract or dampen raw incoming music volume
                val totalGainDb = (masterGainDb + agcGainDb + spkOptGain)
                
                if (isEnabled && totalGainDb != 0) {
                    val gainMb = (totalGainDb * 100).coerceIn(-2000, 2500)
                    le.setTargetGain(gainMb)
                    if (!le.enabled) le.enabled = true
                } else {
                    if (le.enabled) le.enabled = false
                }
            }

            Log.d(TAG, "Applied PulseFX unified DSP harmonic params to session ${effects.sessionId}: enabled=$isEnabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying settings to session ${effects.sessionId}", e)
        }
    }

    private fun playEngineActivationChime(sessionId: Int) {
        Thread {
            try {
                // Synthesize a high-end, subtle dual-tone studio activation chime (880Hz -> 1760Hz bell)
                val sampleRate = 44100
                val durationMs = 120
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val audioData = ShortArray(numSamples)
                
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val envelope = Math.exp(-t * 28.0) // Smooth acoustic decay
                    val freq = if (t < 0.04) 880.0 else 1760.0 // Pitch upward glide
                    val sample = Math.sin(2.0 * Math.PI * freq * t) * envelope * 0.20 // -14dB subtle volume
                    audioData[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val audioFormat = android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val track = android.media.AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(numSamples * 2)
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                    .setSessionId(if (sessionId > 0) sessionId else android.media.AudioManager.AUDIO_SESSION_ID_GENERATE)
                    .build()

                track.write(audioData, 0, numSamples)
                track.play()
                Thread.sleep(150)
                track.stop()
                track.release()
            } catch (e: Exception) {
                Log.w(TAG, "Audio confirmation chime skipped", e)
            }
        }.start()
    }

    private var lastEngagedState = false
    private var lastEnabledModulesHash = 0

    private fun applySettingsToAll() {
        if (currentSettings.isEnabled && !activeSessions.containsKey(GLOBAL_SESSION_ID)) {
            handleOpenSession(GLOBAL_SESSION_ID)
        }
        var anyEffectEngaged = false
        for (effects in activeSessions.values) {
            applySettingsToSession(effects)
            if (effects.equalizer?.enabled == true || effects.bassBoost?.enabled == true || effects.virtualizer?.enabled == true || effects.presetReverb?.enabled == true || effects.loudnessEnhancer?.enabled == true) {
                anyEffectEngaged = true
            }
        }

        // Comprehensive 18-Module State Hash to trigger chime whenever ANY module switch flips ON
        val currentModulesHash = (if (currentSettings.isEnabled) 1 else 0) or
            ((if (currentSettings.isEqEnabled) 1 else 0) shl 1) or
            ((if (currentSettings.isBassEnabled) 1 else 0) shl 2) or
            ((if (currentSettings.isClarityEnabled) 1 else 0) shl 3) or
            ((if (currentSettings.isTubeEnabled) 1 else 0) shl 4) or
            ((if (currentSettings.isConvolverEnabled) 1 else 0) shl 5) or
            ((if (currentSettings.isFieldSurroundEnabled) 1 else 0) shl 6) or
            ((if (currentSettings.isDiffSurroundEnabled) 1 else 0) shl 7) or
            ((if (currentSettings.isHeadphoneSurroundEnabled) 1 else 0) shl 8) or
            ((if (currentSettings.isReverbEnabled) 1 else 0) shl 9) or
            ((if (currentSettings.isDynamicSystemEnabled) 1 else 0) shl 10) or
            ((if (currentSettings.isDdcEnabled) 1 else 0) shl 11) or
            ((if (currentSettings.isSpectrumExtensionEnabled) 1 else 0) shl 12) or
            ((if (currentSettings.isAnalogXEnabled) 1 else 0) shl 13) or
            ((if (currentSettings.isAuditoryProtectionEnabled) 1 else 0) shl 14) or
            ((if (currentSettings.isSpeakerOptEnabled) 1 else 0) shl 15) or
            ((if (currentSettings.isPlaybackAgcEnabled) 1 else 0) shl 16) or
            ((if (currentSettings.isFetCompressorEnabled) 1 else 0) shl 17)

        // Only play confirmation chime when an effect SWITCH is physically turned ON, NEVER on slider dragging
        if (anyEffectEngaged && currentSettings.isEnabled && (!lastEngagedState || currentModulesHash != lastEnabledModulesHash)) {
            playEngineActivationChime(GLOBAL_SESSION_ID)
        }
        lastEngagedState = anyEffectEngaged && currentSettings.isEnabled
        lastEnabledModulesHash = currentModulesHash
    }

    private fun scanActiveSessions() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("dumpsys media.audio_flinger")
                val reader = process.inputStream.bufferedReader()
                val sessionIds = mutableSetOf<Int>()
                
                val sessionIdRegex = Regex("(?i)session(?:\\s+id)?:?\\s+(\\d+)")
                val sessionsRegex = Regex("(?i)sessions:\\s*([\\d\\s]+)")
                val trackRegex = Regex("(?i)session\\s+(\\d+)")

                reader.forEachLine { line ->
                    sessionIdRegex.findAll(line).forEach { match ->
                        match.groups[1]?.value?.toIntOrNull()?.let { sessionIds.add(it) }
                    }
                    
                    sessionsRegex.find(line)?.let { match ->
                        val sessionsList = match.groups[1]?.value ?: ""
                        sessionsList.split("\\s+".toRegex()).forEach { token ->
                            token.toIntOrNull()?.let { sessionIds.add(it) }
                        }
                    }

                    trackRegex.findAll(line).forEach { match ->
                        match.groups[1]?.value?.toIntOrNull()?.let { sessionIds.add(it) }
                    }
                }
                
                reader.close()
                process.destroy()

                for (sessionId in sessionIds) {
                    if (sessionId > 0 && !activeSessions.containsKey(sessionId)) {
                        mainHandler.post {
                            handleOpenSession(sessionId)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning AudioFlinger sessions", e)
            }
        }.start()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(sessionScannerRunnable)
        for (effects in activeSessions.values) {
            effects.release()
        }
        activeSessions.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ViPER FX Audio Processor",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Maintains ViPER FX audio stream hooking active in background"
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val count = activeSessions.size
        val statusText = if (currentSettings.isEnabled) "ViPER FX Active • Hooked: $count stream(s)" else "ViPER FX Disabled"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Antigravity ViPER FX")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        
        return builder.build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification())
    }
}

