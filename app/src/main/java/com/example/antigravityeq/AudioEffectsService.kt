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
        const val ACTION_OPEN_SESSION = AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        const val ACTION_CLOSE_SESSION = AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        const val EXTRA_AUDIO_SESSION = AudioEffect.EXTRA_AUDIO_SESSION
        
        private const val GLOBAL_SESSION_ID = 0
    }

    private class AudioSessionEffects(
        val sessionId: Int,
        var equalizer: Equalizer? = null,
        var bassBoost: BassBoost? = null,
        var virtualizer: Virtualizer? = null,
        var presetReverb: PresetReverb? = null,
        var loudnessEnhancer: LoudnessEnhancer? = null
    ) {
        fun release() {
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

    private fun applySettingsToSession(effects: AudioSessionEffects) {
        try {
            val isEnabled = currentSettings.isEnabled

            // 1. Equalizer & Frequency Shaping
            val eq = effects.equalizer
            if (eq != null) {
                eq.enabled = false
                if (isEnabled) {
                    val numBands = eq.numberOfBands.toInt()
                    for (i in 0 until numBands) {
                        if (i < currentSettings.bandLevels.size) {
                            val levelMB = (currentSettings.bandLevels[i] * 100).coerceIn(-1500, 1500).toShort()
                            eq.setBandLevel(i.toShort(), levelMB)
                        }
                    }
                    eq.enabled = true
                }
            }

            // 2. ViPER Bass (Resonant sub-bass & natural boost)
            val bb = effects.bassBoost
            if (bb != null) {
                bb.enabled = false
                if (isEnabled && currentSettings.bassBoost > 0) {
                    val strength = currentSettings.bassBoost.coerceIn(0, 1000).toShort()
                    bb.setStrength(strength)
                    bb.enabled = true
                }
            }

            // 3. 3D Surround Field / Virtualizer
            val virt = effects.virtualizer
            if (virt != null) {
                virt.enabled = false
                if (isEnabled && currentSettings.isFieldSurroundEnabled && currentSettings.fieldSurroundStrength > 0) {
                    val strength = currentSettings.fieldSurroundStrength.coerceIn(0, 1000).toShort()
                    virt.setStrength(strength)
                    virt.enabled = true
                }
            }

            // 4. Reverberation Matrix
            val rev = effects.presetReverb
            if (rev != null) {
                rev.enabled = false
                if (isEnabled && currentSettings.isReverbEnabled) {
                    val preset = when {
                        currentSettings.reverbRoomSize < 100 -> PresetReverb.PRESET_SMALLROOM
                        currentSettings.reverbRoomSize < 200 -> PresetReverb.PRESET_MEDIUMROOM
                        currentSettings.reverbRoomSize < 350 -> PresetReverb.PRESET_LARGEROOM
                        else -> PresetReverb.PRESET_LARGEHALL
                    }
                    rev.preset = preset
                    rev.enabled = true
                }
            }

            // 5. ViPER Clarity & Loudness Master
            val le = effects.loudnessEnhancer
            if (le != null) {
                le.enabled = false
                if (isEnabled && (currentSettings.clarity > 0 || currentSettings.outputGain > 0)) {
                    val gainMb = ((currentSettings.clarity / 2) + (maxOf(0, currentSettings.outputGain) * 50)).coerceIn(0, 1000)
                    le.setTargetGain(gainMb)
                    le.enabled = true
                }
            }

            Log.d(TAG, "Applied ViPER DSP params to session ${effects.sessionId}: enabled=$isEnabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying settings to session ${effects.sessionId}", e)
        }
    }

    private fun applySettingsToAll() {
        if (currentSettings.isEnabled && !activeSessions.containsKey(GLOBAL_SESSION_ID)) {
            handleOpenSession(GLOBAL_SESSION_ID)
        }
        for (effects in activeSessions.values) {
            applySettingsToSession(effects)
        }
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

