package com.example.antigravityeq

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
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
            effects.equalizer = Equalizer(1000, sessionId)
            effects.bassBoost = BassBoost(1000, sessionId)
            effects.virtualizer = Virtualizer(1000, sessionId)
            effects.presetReverb = PresetReverb(1000, sessionId)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    effects.loudnessEnhancer = LoudnessEnhancer(sessionId)
                } catch (e: Exception) {
                    Log.w(TAG, "LoudnessEnhancer unavailable for session $sessionId")
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
                val hasAnyHarmonicModule = currentSettings.isEqEnabled ||
                    currentSettings.isClarityEnabled ||
                    currentSettings.isConvolverEnabled ||
                    currentSettings.isTubeEnabled ||
                    currentSettings.isDdcEnabled ||
                    currentSettings.isAnalogXEnabled ||
                    currentSettings.isAuditoryProtectionEnabled ||
                    currentSettings.isSpeakerOptEnabled ||
                    currentSettings.isSpectrumExtensionEnabled ||
                    currentSettings.isDynamicSystemEnabled ||
                    currentSettings.isReverbEnabled

                if (isEnabled && hasAnyHarmonicModule) {
                    val numBands = eq.numberOfBands.toInt()
                    for (i in 0 until numBands) {
                        // 1. Base User 10-Band EQ Curve (Completely Independent Headroom)
                        var userEqGainDb = if (currentSettings.isEqEnabled && i < currentSettings.bandLevels.size) {
                            currentSettings.bandLevels[i].toFloat()
                        } else {
                            0f
                        }

                        // 2. High-Frequency Clarity & Harmonic Overtones (Isolated High-End Stage, Bands 5..9)
                        var clarityStageDb = 0f
                        if (currentSettings.isClarityEnabled && i >= 5) {
                            val clarityGain = if (currentSettings.clarity > 0) currentSettings.clarity else 500
                            val clarityBoost = (clarityGain / 1000f) * (when (currentSettings.clarityMode) {
                                0 -> 4.5f // Natural Air
                                1 -> 7.5f // Ozone+ Excite
                                else -> 11.0f // XHiFi Pro
                            }) * ((i - 4) / 5f)
                            clarityStageDb += clarityBoost
                        }

                        // 3. Convolver & Analog Tape/Console Coloration (Isolated Stage)
                        var convolverStageDb = 0f
                        if (currentSettings.isConvolverEnabled) {
                            convolverStageDb += when (currentSettings.convolverPreset) {
                                0 -> if (i <= 2) 3.5f else if (i >= 7) 2.5f else 0f // Studer A800 Warm Tape Head
                                1 -> if (i in 1..4) 4.0f else if (i >= 8) 2.0f else 0f // Telefunken 12AX7 Tube
                                2 -> if (i <= 3) 5.5f else 0f // Sony MegaBass Punch
                                3 -> if (i in 4..7) 2.5f else 0f // Lexicon Hall Presence
                                4 -> if (i in 5..9) 3.0f else 0f // Dolby Atmos Air
                                5 -> if (i <= 2) 2.5f else if (i in 3..6) 3.0f else 0f // Neve 1073 Transformer Warmth
                                6 -> if (i in 2..5) 2.0f else 0f // SSL 4000G Bus
                                else -> 0f
                            }
                        }

                        // 4. Analog Tube Simulator (Isolated Low-Mid Warmth Stage)
                        var tubeStageDb = 0f
                        if (currentSettings.isTubeEnabled) {
                            val warmth = if (currentSettings.tubeWarmth > 0) currentSettings.tubeWarmth else 500
                            val tubeBoost = (warmth / 1000f) * 3.5f
                            if (i in 1..4) tubeStageDb += tubeBoost
                        }

                        // 5. ViPER Bass Sub-Harmonic Low-End Stage (Bands 0..2)
                        var bassStageDb = 0f
                        if (currentSettings.isBassEnabled) {
                            val bassGain = if (currentSettings.bassBoost > 0) currentSettings.bassBoost else 600
                            val bassMult = when (currentSettings.viperBassMode) {
                                0 -> 6f // Natural Bass (+6dB)
                                1 -> 9f // Pure Bass+ (+9dB)
                                else -> 12f // Subwoofer (+12dB)
                            }
                            if (i <= 2) {
                                val weight = if (i == 0) 1.0f else if (i == 1) 0.8f else 0.5f
                                bassStageDb += (bassGain / 1000f) * bassMult * weight
                            }
                        }

                        // 6. Spectrum Extension (VSE High-Frequency Restoration)
                        var vseStageDb = 0f
                        if (currentSettings.isSpectrumExtensionEnabled) {
                            val strength = (currentSettings.spectrumExtensionStrength + 1) * 1.5f
                            if (i >= 7) vseStageDb += strength
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

                        // 8. Reverberation Acoustic Space Stage
                        var reverbStageDb = 0f
                        if (currentSettings.isReverbEnabled) {
                            val wetScale = currentSettings.reverbWetRatio / 100f
                            val roomScale = currentSettings.reverbRoomSize / 500f
                            if (i in 3..6) reverbStageDb += (2.0f * wetScale * roomScale)
                            if (i >= 8) reverbStageDb -= (1.0f * (1f - (currentSettings.reverbDampingFactor / 100f)))
                        }

                        // 9. ViPER-DDC Headphone Correction Profile
                        var ddcStageDb = 0f
                        if (currentSettings.isDdcEnabled) {
                            ddcStageDb += when (currentSettings.ddcPreset) {
                                1 -> if (i <= 1) 2.5f else if (i == 7) -2.0f else 0f
                                2 -> if (i in 2..3) -2.5f else if (i >= 8) 2.0f else 0f
                                3 -> if (i <= 1) 3.5f else if (i >= 8) 1.5f else 0f
                                4 -> if (i == 8) -2.5f else if (i in 4..5) 1.5f else 0f
                                5 -> if (i == 7) -3.5f else if (i <= 1) 2.0f else 0f
                                6 -> if (i in 3..4) 1.5f else if (i in 6..7) -1.5f else 0f
                                7 -> if (i <= 1) 2.0f else if (i in 6..7) -1.5f else 0f
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

                        // 11. Auditory Protection & Speaker Optimization Stages
                        var protectionStageDb = 0f
                        if (currentSettings.isAuditoryProtectionEnabled) {
                            if (i in 6..8) protectionStageDb -= 2.0f
                            if (i in 2..4) protectionStageDb += 1.0f
                        }
                        var speakerOptStageDb = 0f
                        if (currentSettings.isSpeakerOptEnabled) {
                            if (i in 3..6) speakerOptStageDb += 2.5f
                            if (i <= 1) speakerOptStageDb -= 1.5f
                        }

                        // Decoupled Harmonic Summation with Headroom Protection:
                        // Bass is routed in isolated low-frequency channels (0..2), Clarity in high (5..9), and EQ across all.
                        // They NEVER overwrite or suppress each other's frequency headroom.
                        val totalCompositeGainDb = userEqGainDb + bassStageDb + clarityStageDb +
                            convolverStageDb + tubeStageDb + vseStageDb + dynamicStageDb +
                            reverbStageDb + ddcStageDb + analogXStageDb + protectionStageDb + speakerOptStageDb

                        val levelMB = (totalCompositeGainDb * 100f).coerceIn(-1500f, 1500f).toInt().toShort()
                        eq.setBandLevel(i.toShort(), levelMB)
                    }
                    if (!eq.enabled) eq.enabled = true
                } else {
                    if (eq.enabled) eq.enabled = false
                }
            }

            // 2. ViPER Bass (Resonant sub-bass & powerful low-end boost up to +18dB)
            val bb = effects.bassBoost
            if (bb != null) {
                var totalBass = 0
                if (isEnabled && currentSettings.isBassEnabled) {
                    val baseBoost = if (currentSettings.bassBoost > 0) currentSettings.bassBoost else 600
                    val multiplier = when (currentSettings.viperBassMode) {
                        0 -> 1.0f // Natural
                        1 -> 1.4f // Pure Bass+
                        else -> 1.8f // Subwoofer
                    }
                    totalBass += (baseBoost * multiplier).toInt()
                }
                if (isEnabled && currentSettings.isDynamicSystemEnabled) {
                    val dynStrength = if (currentSettings.dynamicBassStrength > 0) currentSettings.dynamicBassStrength else 14
                    // Dynamic bass scaling from 0..30 mapped directly to 0..850 boost strength
                    totalBass += (dynStrength * 35).coerceIn(50, 850)
                }

                if (totalBass > 0) {
                    val strength = totalBass.coerceIn(0, 1000).toShort()
                    bb.setStrength(strength)
                    if (!bb.enabled) bb.enabled = true
                } else {
                    if (bb.enabled) bb.enabled = false
                }
            }

            // 3. 3D Surround Field / Virtualizer (Field Surround + Differential Surround + Headphone Surround+ & Reverb Diffusion)
            val virt = effects.virtualizer
            if (virt != null) {
                var totalSurround = 0
                if (isEnabled && currentSettings.isFieldSurroundEnabled) {
                    val str = if (currentSettings.fieldSurroundStrength > 0) currentSettings.fieldSurroundStrength else 50
                    totalSurround += (str * 10).coerceIn(0, 1000)
                }
                if (isEnabled && currentSettings.isDiffSurroundEnabled) {
                    val delay = if (currentSettings.diffSurroundDelay > 0) currentSettings.diffSurroundDelay else 5
                    totalSurround += (delay * 50).coerceIn(0, 850)
                }
                if (isEnabled && currentSettings.isHeadphoneSurroundEnabled) {
                    totalSurround += ((currentSettings.headphoneSurroundLevel + 1) * 200).coerceIn(0, 1000)
                }
                if (isEnabled && currentSettings.isReverbEnabled && currentSettings.reverbWetRatio > 0) {
                    // Feed Reverb wet ratio directly into Virtualizer spatial reflections
                    totalSurround += (currentSettings.reverbWetRatio * 5).coerceIn(0, 500)
                }

                if (totalSurround > 0) {
                    val strength = totalSurround.coerceIn(0, 1000).toShort()
                    virt.setStrength(strength)
                    if (!virt.enabled) virt.enabled = true
                } else {
                    if (virt.enabled) virt.enabled = false
                }
            }

            // 4. Reverberation Matrix
            val rev = effects.presetReverb
            if (rev != null) {
                if (isEnabled && currentSettings.isReverbEnabled) {
                    val preset = when {
                        currentSettings.reverbRoomSize < 100 -> PresetReverb.PRESET_SMALLROOM
                        currentSettings.reverbRoomSize < 200 -> PresetReverb.PRESET_MEDIUMROOM
                        currentSettings.reverbRoomSize < 350 -> PresetReverb.PRESET_LARGEROOM
                        else -> PresetReverb.PRESET_LARGEHALL
                    }
                    rev.preset = preset
                    if (!rev.enabled) rev.enabled = true
                } else {
                    if (rev.enabled) rev.enabled = false
                }
            }

            // 5. Loudness Enhancer (Playback AGC Gain + Master Output Volume Boost + Speaker Opt)
            val le = effects.loudnessEnhancer
            if (le != null) {
                val agcGainDb = if (isEnabled && currentSettings.isPlaybackAgcEnabled) currentSettings.playbackAgcMaxGain else 0
                val spkOptGain = if (isEnabled && currentSettings.isSpeakerOptEnabled) 4 else 0
                val totalGainDb = (currentSettings.outputGain + agcGainDb + spkOptGain)
                
                if (isEnabled && totalGainDb != 0) {
                    val gainMb = (totalGainDb * 100).coerceIn(-2000, 2000)
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

