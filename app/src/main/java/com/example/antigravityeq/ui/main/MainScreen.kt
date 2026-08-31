package com.example.antigravityeq.ui.main

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.antigravityeq.MainActivity
import com.example.antigravityeq.data.EqualizerSettings
import com.example.antigravityeq.ui.component.EffectCard
import com.example.antigravityeq.ui.component.InteractiveBassCurveGraph
import com.example.antigravityeq.ui.component.InteractiveClarityCurveGraph
import com.example.antigravityeq.ui.component.InteractiveCompressorGraph
import com.example.antigravityeq.ui.component.InteractiveFirequalizerCurve
import com.example.antigravityeq.ui.component.ValuePicker
import com.example.antigravityeq.ui.component.ValueSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    var showAboutDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "P",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Column {
                        Text(
                            text = "PulseFX Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "v1.6.0 • Sovereign DSP Audio Suite",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PulseFX Studio is a sovereign, 32-bit floating-point DSP audio mastering suite engineered for Android 15 & modern devices. Works system-wide across YouTube, Spotify, games, and web media with zero intrusive permissions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "🎨 Design Philosophy",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Google Material 3 Dynamic Theming (adapts to wallpaper palettes)\n• Externalized dB & Hz graph axes for clean visual curves\n• Brushed titanium rotary dial interface & tactile haptics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "👥 Authors & Core Team",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Denver Colaco: Lead Architect, Creator & Project Vision\n• J.A.R.V.I.S.: Lead Architect & Systems Integrity\n• VECTOR: UI/UX, Motion & Adaptive Icon Design\n• CIPHER: 32-bit DSP Engine & Shizuku Interception",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "🌟 Tributes & Heritage",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Inspired by ViPER's Audio (Euphony & ZhuHang), Team DeWitt, and the Shizuku & Shevery non-root ecosystem.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val liveFftLevels by viewModel.liveFftLevels.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PulseFX Studio", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("v1.6.5 • Sovereign DSP Audio Suite", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                },
                actions = {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var isRefreshing by remember { mutableStateOf(false) }

                    // Rounded Refresh Audio Stream Button
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            try {
                                val rebootIntent = android.content.Intent(context, com.example.antigravityeq.AudioEffectsService::class.java).apply {
                                    action = com.example.antigravityeq.AudioEffectsService.ACTION_REBOOT_ENGINE
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    context.startForegroundService(rebootIntent)
                                } else {
                                    context.startService(rebootIntent)
                                }
                                android.widget.Toast.makeText(context, "⚡ Audio Engine & Stream Hook Refreshed!", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.util.Log.e("MainScreen", "Error triggering audio stream reboot", e)
                            }
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ isRefreshing = false }, 800)
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↻",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(onClick = { showAboutDialog = true }) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "i",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = settings.isEnabled,
                        onCheckedChange = { viewModel.updateSettings { s -> s.copy(isEnabled = it) } }
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Connected Earphones / Sources Card (Real Hardware Name Detection)
            val context = androidx.compose.ui.platform.LocalContext.current
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            
            var realDeviceName = "Internal Stereo Speakers"
            var routeBadge = "SPEAKER"
            var isBluetoothOn = false
            var isHeadsetOn = false
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
                val outputDevices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                val activeOutput = outputDevices.firstOrNull { dev ->
                    dev.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE
                } ?: outputDevices.firstOrNull { dev ->
                    dev.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }

                if (activeOutput != null) {
                    val rawName = activeOutput.productName?.toString()?.trim() ?: ""
                    when (activeOutput.type) {
                        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                            isBluetoothOn = true
                            routeBadge = "BT A2DP"
                            realDeviceName = if (rawName.isNotEmpty() && !rawName.equals("Bluetooth", ignoreCase = true)) rawName else "Bluetooth Headset"
                        }
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                            isHeadsetOn = true
                            routeBadge = "3.5mm"
                            realDeviceName = if (rawName.isNotEmpty()) rawName else "Wired Headphones"
                        }
                        android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                        android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> {
                            isHeadsetOn = true
                            routeBadge = "USB-DAC"
                            realDeviceName = if (rawName.isNotEmpty()) rawName else "USB-C Audio DAC"
                        }
                        else -> {
                            routeBadge = "SPEAKER"
                            realDeviceName = if (rawName.isNotEmpty()) rawName else "Internal Stereo Speakers"
                        }
                    }
                }
            } else {
                isBluetoothOn = audioManager?.isBluetoothA2dpOn == true || audioManager?.isBluetoothScoOn == true
                isHeadsetOn = audioManager?.isWiredHeadsetOn == true
                realDeviceName = when {
                    isBluetoothOn -> "Bluetooth Audio Device"
                    isHeadsetOn -> "Wired Headset / USB-C DAC"
                    else -> "Internal Stereo Speakers"
                }
                routeBadge = when {
                    isBluetoothOn -> "BT A2DP"
                    isHeadsetOn -> "USB/DAC"
                    else -> "SPEAKER"
                }
            }
            val connectedDeviceName = realDeviceName

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (settings.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isBluetoothOn) "🎧" else if (isHeadsetOn) "🎚️" else "🔊",
                                fontSize = 15.sp
                            )
                        }
                        Column {
                            Text(
                                text = connectedDeviceName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "48 kHz / 24-bit PCM • 32-Bit Float Engine Active",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                if (settings.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = routeBadge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (settings.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Master Limiter (Original #1 with Dedicated Module Toggle Switch)
            EffectCard(
                badgeText = "OUT",
                name = "Master limiter",
                enabled = settings.isLimiterEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isLimiterEnabled = it) } }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ValueSlider(
                        title = "Output gain",
                        summary = "${settings.outputGain} dB",
                        value = settings.outputGain,
                        onValueChange = { v -> viewModel.updateSettings { s -> s.copy(outputGain = v) } },
                        valueRange = -20..10
                    )
                    ValueSlider(
                        title = "Output pan",
                        summary = "${settings.channelPan}",
                        value = settings.channelPan,
                        onValueChange = { v -> viewModel.updateSettings { s -> s.copy(channelPan = v) } },
                        valueRange = -100..100
                    )
                    ValueSlider(
                        title = "Threshold limit",
                        summary = "${settings.limiterThreshold} dB",
                        value = settings.limiterThreshold,
                        onValueChange = { v -> viewModel.updateSettings { s -> s.copy(limiterThreshold = v) } },
                        valueRange = -3..0
                    )
                }
            }

            // Playback Gain Control (Original #2)
            EffectCard(
                badgeText = "AGC",
                name = "Playback gain control",
                enabled = settings.isPlaybackAgcEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isPlaybackAgcEnabled = it) } }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ValuePicker(
                        title = "Ratio",
                        values = arrayOf("Slight", "Moderate", "Extreme"),
                        selectedIndex = settings.playbackAgcRatio,
                        onSelectedIndexChange = { idx -> viewModel.updateSettings { s -> s.copy(playbackAgcRatio = idx) } }
                    )
                    ValueSlider(
                        title = "Max gain",
                        summary = "${settings.playbackAgcMaxGain}x",
                        value = settings.playbackAgcMaxGain,
                        onValueChange = { v -> viewModel.updateSettings { s -> s.copy(playbackAgcMaxGain = v) } },
                        valueRange = 0..18
                    )
                }
            }

            // FET Compressor (Original #3)
            EffectCard(
                badgeText = "FET",
                name = "FET compressor",
                enabled = settings.isFetCompressorEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isFetCompressorEnabled = it) } }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    InteractiveCompressorGraph(
                        thresholdDb = settings.fetThreshold,
                        ratio = settings.fetRatio,
                        makeupGainDb = settings.fetGain,
                        onThresholdChange = { th -> viewModel.updateSettings { s -> s.copy(fetThreshold = th) } },
                        onRatioChange = { rt -> viewModel.updateSettings { s -> s.copy(fetRatio = rt) } }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ValueSlider(
                        title = "Makeup gain",
                        summary = "+${settings.fetGain} dB",
                        value = settings.fetGain,
                        onValueChange = { v -> viewModel.updateSettings { s -> s.copy(fetGain = v) } },
                        valueRange = 0..18
                    )
                }
            }

            // ViPER-DDC (Original #4)
            EffectCard(
                badgeText = "DDC",
                name = "ViPER-DDC",
                enabled = settings.isDdcEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isDdcEnabled = it) } }
            ) {
                ValuePicker(
                    title = "Preset",
                    values = EqualizerSettings.DDC_PRESET_NAMES.toTypedArray(),
                    selectedIndex = settings.ddcPreset,
                    onSelectedIndexChange = { idx -> viewModel.updateSettings { s -> s.copy(ddcPreset = idx) } }
                )
            }

            // Spectrum Extension (Original #5)
            EffectCard(
                badgeText = "VSE",
                name = "Spectrum extension",
                enabled = settings.isSpectrumExtensionEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isSpectrumExtensionEnabled = it) } }
            ) {
                ValueSlider(
                    title = "Strength",
                    summary = "${settings.spectrumExtensionStrength}",
                    value = settings.spectrumExtensionStrength,
                    onValueChange = { v -> viewModel.updateSettings { s -> s.copy(spectrumExtensionStrength = v) } },
                    valueRange = 0..10
                )
            }

            // FIR Equalizer (Original #6 - Pure Clean Layout with Smooth Natural Real-Time Movement)
            EffectCard(
                badgeText = "EQ",
                name = "FIR equalizer",
                enabled = settings.isEqEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isEqEnabled = it) } }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ValuePicker(
                        title = "Preset",
                        values = EqualizerSettings.EQ_PRESET_NAMES.toTypedArray(),
                        selectedIndex = settings.eqPreset,
                        onSelectedIndexChange = { idx ->
                            val presetValues = EqualizerSettings.EQ_PRESET_VALUES.getOrElse(idx) { List(10) { 0 } }
                            viewModel.updateSettings { s ->
                                s.copy(
                                    eqPreset = idx,
                                    bandLevels = presetValues
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    InteractiveFirequalizerCurve(
                        bandLevels = settings.bandLevels,
                        onBandLevelsChange = { updatedLevels ->
                            viewModel.updateSettings { s ->
                                s.copy(
                                    bandLevels = updatedLevels,
                                    eqPreset = 0 // Custom
                                )
                            }
                        },
                        isEqEnabled = settings.isEqEnabled,
                        liveLevels = liveFftLevels
                    )
                }
            }

            // 8. Convolver
            EffectCard(
                badgeText = "IRS",
                name = "Convolver",
                enabled = settings.isConvolverEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isConvolverEnabled = it) } }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ValuePicker(
                        title = "Impulse Response",
                        values = EqualizerSettings.CONVOLVER_PRESET_NAMES.toTypedArray(),
                        selectedIndex = settings.convolverPreset,
                        onSelectedIndexChange = { idx -> viewModel.updateSettings { s -> s.copy(convolverPreset = idx) } }
                    )
                    ValueSlider(
                        title = "Cross channel",
                        summary = "${settings.convolverCrossChannel}%",
                        value = settings.convolverCrossChannel,
                        onValueChange = { v -> viewModel.updateSettings { s -> s.copy(convolverCrossChannel = v) } },
                        valueRange = 0..100
                    )
                }
            }

            // Field Surround
            EffectCard(
                badgeText = "FS",
                name = "Field surround",
                enabled = settings.isFieldSurroundEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isFieldSurroundEnabled = it) } }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ValueSlider(
                        title = "Surround strength",
                        summary = "${settings.fieldSurroundStrength}",
                        value = settings.fieldSurroundStrength,
                        onValueChange = { v -> viewModel.updateSettings { s -> s.copy(fieldSurroundStrength = v) } },
                        valueRange = 0..100
                    )
                    ValueSlider(
                        title = "Mid image strength",
                        summary = "${settings.midImageSize}",
                        value = settings.midImageSize,
                        onValueChange = { v -> viewModel.updateSettings { s -> s.copy(midImageSize = v) } },
                        valueRange = 0..100
                    )
                }
            }

            // Differential Surround
            EffectCard(
                badgeText = "DS",
                name = "Differential surround",
                enabled = settings.isDiffSurroundEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isDiffSurroundEnabled = it) } }
            ) {
                ValueSlider(
                    title = "Delay",
                    summary = "${settings.diffSurroundDelay} ms",
                    value = settings.diffSurroundDelay,
                    onValueChange = { v -> viewModel.updateSettings { s -> s.copy(diffSurroundDelay = v) } },
                    valueRange = 0..20
                )
            }

            // Headphone Surround+
            EffectCard(
                badgeText = "VHE",
                name = "Headphone surround+",
                enabled = settings.isHeadphoneSurroundEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isHeadphoneSurroundEnabled = it) } }
            ) {
                ValueSlider(
                    title = "Effect level",
                    summary = "${settings.headphoneSurroundLevel}",
                    value = settings.headphoneSurroundLevel,
                    onValueChange = { v -> viewModel.updateSettings { s -> s.copy(headphoneSurroundLevel = v) } },
                    valueRange = 0..5
                )
            }

            // Reverberation
            EffectCard(
                badgeText = "REV",
                name = "Reverberation",
                enabled = settings.isReverbEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isReverbEnabled = it) } }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ValueSlider(
                        title = "Room size",
                        summary = "${settings.reverbRoomSize} m²",
                        value = settings.reverbRoomSize,
                        onValueChange = { v -> viewModel.updateSettings { s -> s.copy(reverbRoomSize = v) } },
                        valueRange = 25..500
                    )
                    ValueSlider(
                        title = "Wet ratio",
                        summary = "${settings.reverbWetRatio}%",
                        value = settings.reverbWetRatio,
                        onValueChange = { v -> viewModel.updateSettings { s -> s.copy(reverbWetRatio = v) } },
                        valueRange = 0..100
                    )
                }
            }

            // Dynamic System
            EffectCard(
                badgeText = "DYN",
                name = "Dynamic system",
                enabled = settings.isDynamicSystemEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isDynamicSystemEnabled = it) } }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ValuePicker(
                        title = "Listening device",
                        values = EqualizerSettings.DYNAMIC_DEVICE_NAMES.toTypedArray(),
                        selectedIndex = settings.dynamicDevice,
                        onSelectedIndexChange = { idx -> viewModel.updateSettings { s -> s.copy(dynamicDevice = idx) } }
                    )
                    ValueSlider(
                        title = "Dynamic bass",
                        summary = "${settings.dynamicBassStrength}",
                        value = settings.dynamicBassStrength,
                        onValueChange = { v -> viewModel.updateSettings { s -> s.copy(dynamicBassStrength = v) } },
                        valueRange = 0..30
                    )
                }
            }

            // Tube Simulator
            EffectCard(
                badgeText = "TUBE",
                name = "Tube simulator (6N1P)",
                enabled = settings.isTubeEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isTubeEnabled = it) } }
            ) {
                ValueSlider(
                    title = "Tube warmth",
                    summary = "${settings.tubeWarmth / 10}%",
                    value = settings.tubeWarmth,
                    onValueChange = { v -> viewModel.updateSettings { s -> s.copy(tubeWarmth = v) } },
                    valueRange = 0..1000
                )
            }

            // ViPER Bass
            EffectCard(
                badgeText = "BASS",
                name = "ViPER bass",
                enabled = settings.isBassEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isBassEnabled = it) } }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ValuePicker(
                        title = "Bass mode",
                        values = arrayOf("Natural bass", "Pure bass +", "Subwoofer"),
                        selectedIndex = settings.viperBassMode,
                        onSelectedIndexChange = { idx -> 
                            val anchoredFreq = when (idx) {
                                0 -> 80 // Natural Bass: Warm organic low foundation (80Hz)
                                1 -> 60 // Pure Bass+: Chest-thumping kick punch (60Hz)
                                else -> 45 // Subwoofer: Deep physical sub-octave rumble (45Hz)
                            }
                            viewModel.updateSettings { s -> s.copy(viperBassMode = idx, bassFrequency = anchoredFreq) } 
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InteractiveBassCurveGraph(
                        frequencyHz = settings.bassFrequency,
                        gainBoost = settings.bassBoost,
                        mode = settings.viperBassMode,
                        onFrequencyChange = { f -> viewModel.updateSettings { s -> s.copy(bassFrequency = f) } },
                        onGainChange = { g -> viewModel.updateSettings { s -> s.copy(bassBoost = g) } }
                    )
                }
            }

            // ViPER Clarity
            EffectCard(
                badgeText = "CLR",
                name = "ViPER clarity",
                enabled = settings.isClarityEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isClarityEnabled = it) } }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ValuePicker(
                        title = "Clarity mode",
                        values = arrayOf("Natural", "Ozone+", "XHiFi"),
                        selectedIndex = settings.clarityMode,
                        onSelectedIndexChange = { idx -> viewModel.updateSettings { s -> s.copy(clarityMode = idx) } }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InteractiveClarityCurveGraph(
                        clarityGain = settings.clarity,
                        mode = settings.clarityMode,
                        onGainChange = { g -> viewModel.updateSettings { s -> s.copy(clarity = g) } }
                    )
                }
            }

            // Auditory System Protection
            EffectCard(
                badgeText = "CURE",
                name = "Auditory system protection",
                enabled = settings.isAuditoryProtectionEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isAuditoryProtectionEnabled = it) } }
            ) {
                Text(
                    text = "Cure+ crossfeed reduces ear fatigue during extended listening sessions.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }

            // AnalogX
            EffectCard(
                badgeText = "AX",
                name = "AnalogX",
                enabled = settings.isAnalogXEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isAnalogXEnabled = it) } }
            ) {
                ValuePicker(
                    title = "Mode level",
                    values = arrayOf("Level 1 (Subtle)", "Level 2 (Moderate)", "Level 3 (Extreme)"),
                    selectedIndex = settings.analogXLevel.coerceIn(0, 2),
                    onSelectedIndexChange = { idx -> viewModel.updateSettings { s -> s.copy(analogXLevel = idx) } }
                )
            }

            // Speaker Optimization
            EffectCard(
                badgeText = "SPK",
                name = "Speaker optimization",
                enabled = settings.isSpeakerOptEnabled,
                onEnabledChange = { viewModel.updateSettings { s -> s.copy(isSpeakerOptEnabled = it) } }
            ) {
                Text(
                    text = "Acoustic correction profile optimized for device external speakers.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
    }
}
