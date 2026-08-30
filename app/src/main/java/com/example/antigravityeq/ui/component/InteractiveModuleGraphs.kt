package com.example.antigravityeq.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

private val bassHzMarkers = listOf(30, 40, 50, 60, 70, 80, 100)
private val clarityKMarkers = listOf("2kHz", "4kHz", "8kHz", "12kHz", "16kHz", "20kHz")

/**
 * Interactive Bass Resonance Graph with External Frequency Axis.
 */
@Composable
fun InteractiveBassCurveGraph(
    frequencyHz: Int,
    gainBoost: Int,
    mode: Int, // 0 = Natural, 1 = Pure, 2 = Subwoofer
    onFrequencyChange: (Int) -> Unit,
    onGainChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Internal Graph Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceVariantColor)
                .border(1.dp, outlineColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .pointerInput(frequencyHz, gainBoost) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        
                        val normX = (down.position.x / width).coerceIn(0f, 1f)
                        val newFreq = (30 + normX * (100 - 30)).roundToInt()
                        val normY = (1f - (down.position.y / height)).coerceIn(0f, 1f)
                        val newGain = (normY * 1000).roundToInt()
                        
                        onFrequencyChange(newFreq)
                        onGainChange(newGain)
                        
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    change.consume()
                                    val moveNormX = (change.position.x / width).coerceIn(0f, 1f)
                                    val moveFreq = (30 + moveNormX * (100 - 30)).roundToInt()
                                    val moveNormY = (1f - (change.position.y / height)).coerceIn(0f, 1f)
                                    val moveGain = (moveNormY * 1000).roundToInt()
                                    onFrequencyChange(moveFreq)
                                    onGainChange(moveGain)
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                
                // Internal Grid Lines
                bassHzMarkers.forEach { hz ->
                    val normX = (hz - 30f) / 70f
                    val x = normX * (w - 32f) + 16f
                    drawLine(
                        color = outlineColor.copy(alpha = 0.15f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                }
                
                // Resonant Bass Curve synthesis
                val centerNormX = (frequencyHz - 30f) / 70f
                val centerX = centerNormX * (w - 32f) + 16f
                val peakNormY = (gainBoost / 1000f).coerceIn(0.08f, 1f)
                val centerY = h - (peakNormY * (h - 30f)) - 15f
                
                val curvePath = Path()
                val fillPath = Path()
                val qWidth = when (mode) {
                    0 -> 0.35f * w // Natural - wide
                    1 -> 0.22f * w // Pure - focused
                    else -> 0.15f * w // Subwoofer - steep sub peak
                }
                
                val steps = 60
                fillPath.moveTo(0f, h)
                
                for (i in 0..steps) {
                    val x = (i.toFloat() / steps) * w
                    val dist = abs(x - centerX)
                    val bell = exp(- (dist * dist) / (2f * qWidth * qWidth))
                    val y = h - 10f - (bell * (h - 30f - centerY + 15f))
                    
                    if (i == 0) {
                        curvePath.moveTo(x, y)
                        fillPath.lineTo(x, y)
                    } else {
                        curvePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                
                fillPath.lineTo(w, h)
                fillPath.close()
                
                // Area gradient
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.30f),
                            primaryColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h
                    )
                )
                
                // Curve stroke
                drawPath(
                    path = curvePath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.7f),
                            primaryColor,
                            primaryColor.copy(alpha = 0.7f)
                        )
                    ),
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )
                
                // Active Center Node
                drawCircle(
                    color = primaryColor.copy(alpha = 0.35f),
                    radius = 16f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = primaryColor,
                    radius = 7f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5f,
                    center = Offset(centerX, centerY)
                )
                
                // Active Readout Info bubble
                val readout = "${frequencyHz}Hz • +${gainBoost / 70}dB"
                val readY = if (centerY < 30f) centerY + 12f else centerY - 22f
                drawText(
                    textMeasurer = textMeasurer,
                    text = readout,
                    style = TextStyle(color = primaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    topLeft = Offset((centerX - 45f).coerceIn(8f, w - 95f), readY)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // External Frequency Numbers Row (Outside Graph Box)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bassHzMarkers.forEach { hz ->
                Text(
                    text = "${hz}Hz",
                    color = onSurfaceVariantColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Interactive Clarity High-Shelf Treble Graph with External Frequency Axis.
 */
@Composable
fun InteractiveClarityCurveGraph(
    clarityGain: Int,
    mode: Int, // 0 = Natural, 1 = Ozone+, 2 = XHiFi
    onGainChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Internal Graph Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceVariantColor)
                .border(1.dp, outlineColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .pointerInput(clarityGain) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val height = size.height.toFloat()
                        val normY = (1f - (down.position.y / height)).coerceIn(0f, 1f)
                        val newGain = (normY * 1000).roundToInt()
                        onGainChange(newGain)
                        
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    change.consume()
                                    val moveNormY = (1f - (change.position.y / height)).coerceIn(0f, 1f)
                                    val moveGain = (moveNormY * 1000).roundToInt()
                                    onGainChange(moveGain)
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                
                // Internal Grid Lines
                clarityKMarkers.forEachIndexed { idx, _ ->
                    val x = idx.toFloat() / (clarityKMarkers.size - 1) * (w - 32f) + 16f
                    drawLine(
                        color = outlineColor.copy(alpha = 0.15f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                }
                
                // High-shelf harmonic curve
                val shelfGainNorm = (clarityGain / 1000f).coerceIn(0.05f, 1f)
                val shelfHeight = shelfGainNorm * (h - 35f)
                val curveY = h - 15f - shelfHeight
                
                val curvePath = Path()
                val fillPath = Path()
                
                fillPath.moveTo(0f, h)
                val steps = 50
                for (i in 0..steps) {
                    val x = (i.toFloat() / steps) * w
                    val normX = (x / w)
                    val shelfFactor = 1f / (1f + exp(- (normX - 0.45f) * 10f))
                    val y = h - 15f - (shelfFactor * shelfHeight)
                    
                    if (i == 0) {
                        curvePath.moveTo(x, y)
                        fillPath.lineTo(x, y)
                    } else {
                        curvePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                
                fillPath.lineTo(w, h)
                fillPath.close()
                
                // Area gradient
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.30f),
                            primaryColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h
                    )
                )
                
                drawPath(
                    path = curvePath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.8f), primaryColor)
                    ),
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )
                
                val handleX = (w - 32f) * 0.85f + 16f
                val handleY = curveY
                drawCircle(
                    color = primaryColor.copy(alpha = 0.35f),
                    radius = 16f,
                    center = Offset(handleX, handleY)
                )
                drawCircle(
                    color = primaryColor,
                    radius = 7f,
                    center = Offset(handleX, handleY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5f,
                    center = Offset(handleX, handleY)
                )
                
                val readout = "Clarity: +${clarityGain / 70}dB"
                val readY = if (handleY < 30f) handleY + 12f else handleY - 22f
                drawText(
                    textMeasurer = textMeasurer,
                    text = readout,
                    style = TextStyle(color = primaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    topLeft = Offset((handleX - 60f).coerceIn(8f, w - 90f), readY)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // External Frequency Numbers Row (Outside Graph Box)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            clarityKMarkers.forEach { label ->
                Text(
                    text = label,
                    color = onSurfaceVariantColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Interactive FET Compressor Dynamic Transfer Curve with External dB Scale.
 */
@Composable
fun InteractiveCompressorGraph(
    thresholdDb: Int,      // -40 dB to 0 dB
    ratio: Int,            // 1:1 to 20:1
    makeupGainDb: Int,     // 0 dB to 18 dB
    onThresholdChange: (Int) -> Unit,
    onRatioChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // External Left dB Scale (0dB down to -40dB)
            Column(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(text = "0 dB", color = tertiaryColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(text = "-10", color = onSurfaceVariantColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                Text(text = "-20", color = onSurfaceVariantColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                Text(text = "-30", color = onSurfaceVariantColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                Text(text = "-40", color = onSurfaceVariantColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Internal Transfer Box (Pure Curve & Interactive Node)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceVariantColor)
                    .border(1.dp, outlineColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .pointerInput(thresholdDb, ratio) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        
                        val normX = (down.position.x / width).coerceIn(0f, 1f)
                        val newThresh = (-40 + normX * 40).roundToInt()
                        val normY = (1f - (down.position.y / height)).coerceIn(0f, 1f)
                        val newRatio = max(1, (1 + normY * 19).roundToInt())
                        
                        onThresholdChange(newThresh)
                        onRatioChange(newRatio)
                        
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    change.consume()
                                    val moveNormX = (change.position.x / width).coerceIn(0f, 1f)
                                    val moveThresh = (-40 + moveNormX * 40).roundToInt()
                                    val moveNormY = (1f - (change.position.y / height)).coerceIn(0f, 1f)
                                    val moveRatio = max(1, (1 + moveNormY * 19).roundToInt())
                                    onThresholdChange(moveThresh)
                                    onRatioChange(moveRatio)
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    // Linear 1:1 Reference Line (Gray Dashed)
                    drawLine(
                        color = outlineColor.copy(alpha = 0.25f),
                        start = Offset(12f, h - 12f),
                        end = Offset(w - 12f, 12f),
                        strokeWidth = 1.5f
                    )
                    
                    // Threshold coordinate
                    val threshNorm = ((thresholdDb - (-40f)) / 40f).coerceIn(0f, 1f)
                    val threshX = threshNorm * (w - 24f) + 12f
                    val threshY = (h - 12f) - (threshNorm * (h - 24f))
                    
                    // Threshold Guideline
                    drawLine(
                        color = tertiaryColor.copy(alpha = 0.35f),
                        start = Offset(threshX, 0f),
                        end = Offset(threshX, h),
                        strokeWidth = 1f
                    )
                    
                    // Compression Transfer Curve with Makeup Gain
                    val makeupOffset = (makeupGainDb / 18f) * (h * 0.15f)
                    val compPath = Path()
                    val fillPath = Path()
                    
                    compPath.moveTo(12f, h - 12f - makeupOffset)
                    fillPath.moveTo(12f, h - 12f)
                    fillPath.lineTo(12f, h - 12f - makeupOffset)
                    
                    compPath.lineTo(threshX, threshY - makeupOffset)
                    fillPath.lineTo(threshX, threshY - makeupOffset)
                    
                    val slope = 1f / max(1f, ratio.toFloat())
                    val remainingW = (w - 12f) - threshX
                    val endY = (threshY - (remainingW * slope)) - makeupOffset
                    
                    compPath.lineTo(w - 12f, endY)
                    fillPath.lineTo(w - 12f, endY)
                    fillPath.lineTo(w - 12f, h - 12f)
                    fillPath.close()
                    
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                tertiaryColor.copy(alpha = 0.25f),
                                tertiaryColor.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = h
                        )
                    )
                    
                    drawPath(
                        path = compPath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(tertiaryColor, tertiaryColor.copy(alpha = 0.85f))
                        ),
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )
                    
                    // Threshold Node Handle
                    drawCircle(
                        color = tertiaryColor.copy(alpha = 0.35f),
                        radius = 16f,
                        center = Offset(threshX, threshY - makeupOffset)
                    )
                    drawCircle(
                        color = tertiaryColor,
                        radius = 7f,
                        center = Offset(threshX, threshY - makeupOffset)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.5f,
                        center = Offset(threshX, threshY - makeupOffset)
                    )
                    
                    // Readout info
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "Thresh: ${thresholdDb}dB • ${ratio}:1 Ratio",
                        style = TextStyle(color = tertiaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        topLeft = Offset(12f, 10f)
                    )
                }
            }
        }
    }
}
