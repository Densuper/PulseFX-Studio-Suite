package com.example.antigravityeq

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.antigravityeq.data.EqualizerSettings
import com.example.antigravityeq.data.ViperDspProcessor
import java.util.concurrent.atomic.AtomicBoolean

class AudioStreamCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isCapturing = AtomicBoolean(false)
    private var captureThread: Thread? = null

    private lateinit var dspProcessor: ViperDspProcessor
    private var currentSettings = EqualizerSettings()

    companion object {
        private const val TAG = "ViPERStreamCapture"
        private const val CHANNEL_ID = "ViPEROutputCatchChannel"
        private const val NOTIFICATION_ID = 202

        const val ACTION_START_CAPTURE = "com.example.antigravityeq.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.example.antigravityeq.STOP_CAPTURE"
        const val ACTION_UPDATE_SETTINGS = "com.example.antigravityeq.UPDATE_DSP_SETTINGS"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"

        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_STEREO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        private const val BUFFER_FRAMES = 512
    }

    override fun onCreate() {
        super.onCreate()
        dspProcessor = ViperDspProcessor(SAMPLE_RATE)
        currentSettings = EqualizerSettings.load(this)
        dspProcessor.updateEqGains(currentSettings.bandLevels)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START_CAPTURE -> {
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultData != null && !isCapturing.get()) {
                    startForegroundServiceWithNotification()
                    initMediaProjectionAndCapture(resultData)
                }
            }
            ACTION_STOP_CAPTURE -> {
                stopCapture()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE_SETTINGS -> {
                currentSettings = EqualizerSettings.load(this)
                dspProcessor.updateEqGains(currentSettings.bandLevels)
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notification = createNotification("Catching and Processing Master Audio Stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initMediaProjectionAndCapture(resultData: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(android.app.Activity.RESULT_OK, resultData)

        if (mediaProjection == null) {
            Log.e(TAG, "Failed to acquire MediaProjection token")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .build()

                val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
                val bufferSize = maxOf(minBufSize, BUFFER_FRAMES * 2 * 4 * 4)

                audioRecord = AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG_IN)
                            .build()
                    )
                    .setAudioPlaybackCaptureConfig(config)
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                val outBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG_OUT)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(outBufSize, bufferSize))
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioRecord?.startRecording()
                audioTrack?.play()
                isCapturing.set(true)

                startProcessingLoop()
                Log.d(TAG, "Direct Master Audio Stream Capture successfully started at 48kHz Stereo!")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AudioPlaybackCapture", e)
            }
        }
    }

    private fun startProcessingLoop() {
        captureThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buffer = FloatArray(BUFFER_FRAMES * 2)

            while (isCapturing.get()) {
                val record = audioRecord ?: break
                val track = audioTrack ?: break

                val readCount = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (readCount > 0) {
                    // Apply Real ViPER DSP (EQ, Bass, Clarity, Tube, Surround, Limiter)
                    dspProcessor.processBuffer(buffer, readCount, currentSettings)

                    // Write directly to master output track
                    track.write(buffer, 0, readCount, AudioTrack.WRITE_BLOCKING)
                }
            }
        }.apply {
            name = "ViPER_DSP_Core_Thread"
            start()
        }
    }

    private fun stopCapture() {
        isCapturing.set(false)
        try {
            captureThread?.interrupt()
            captureThread = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing capture resources", e)
        }
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ViPER Stream Processing Core",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Catches and transforms all device audio through ViPER DSP"
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ViPER Master Stream Hook")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
