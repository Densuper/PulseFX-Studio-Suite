package com.example.antigravityeq.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * Interactive Touch Graph for ViPER Bass Harmonic Response.
 * Displays cutoff resonance bell curve and allows interactive frequency / boost drag sculpting.
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
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1016))
            .pointerInput(frequencyHz, gainBoost) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    
                    // Map X (0..width) to 30Hz..100Hz
                    val normX = (change.position.x / width).coerceIn(0f, 1f)
                    val newFreq = (30 + normX * (100 - 30)).roundToInt()
                    
                    // Map Y (height..0) to 0..1000 boost
                    val normY = (1f - (change.position.y / height)).coerceIn(0f, 1f)
                    val newGain = (normY * 1000).roundToInt()
                    
                    onFrequencyChange(newFreq)
                    onGainChange(newGain)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // Grid lines
            val hzMarkers = listOf(30, 40, 50, 60, 70, 80, 100)
            hzMarkers.forEach { hz ->
                val normX = (hz - 30f) / 70f
                val x = normX * w
                drawLine(
                    color = Color(0xFF1E2333),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${hz}Hz",
                    style = TextStyle(color = Color(0xFF6B7280), fontSize = 8.sp),
                    topLeft = Offset(x - 10f, h - 14f)
                )
            }
            
            // Resonant Bass Curve synthesis
            val centerNormX = (frequencyHz - 30f) / 70f
            val centerX = centerNormX * w
            val peakNormY = (gainBoost / 1000f).coerceIn(0.1f, 1f)
            val centerY = h - (peakNormY * (h - 35f)) - 15f
            
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
                val y = h - 20f - (bell * (h - 35f - centerY + 15f))
                
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
                        Color(0xFF00E5FF).copy(alpha = 0.30f),
                        Color(0xFF00E5FF).copy(alpha = 0.05f),
                        Color.Transparent
                    )
                )
            )
            
            // Stroke line
            drawPath(
                path = curvePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF00B0FF), Color(0xFF00E5FF))
                ),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
            
            // Peak handle circle
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                radius = 16f,
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = 7f,
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = 3.5f,
                center = Offset(centerX, centerY)
            )
            
            val readout = "${frequencyHz}Hz • +${gainBoost / 55}dB"
            drawText(
                textMeasurer = textMeasurer,
                text = readout,
                style = TextStyle(color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                topLeft = Offset(max(10f, min(centerX - 35f, w - 85f)), max(8f, centerY - 24f))
            )
        }
    }
}

/**
 * Interactive Touch Graph for ViPER Clarity Harmonic Exciter.
 * Displays high-shelf frequency excitation and interactive gain curve.
 */
@Composable
fun InteractiveClarityCurveGraph(
    clarityGain: Int,
    mode: Int, // 0 = Natural, 1 = Ozone+, 2 = XHiFi
    onGainChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1016))
            .pointerInput(clarityGain) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val height = size.height.toFloat()
                    val normY = (1f - (change.position.y / height)).coerceIn(0f, 1f)
                    val newGain = (normY * 1000).roundToInt()
                    onGainChange(newGain)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // Grid lines
            val kMarkers = listOf("2kHz", "4kHz", "8kHz", "12kHz", "16kHz", "20kHz")
            kMarkers.forEachIndexed { idx, label ->
                val x = (idx.toFloat() / (kMarkers.size - 1)) * w
                drawLine(
                    color = Color(0xFF1E2333),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = TextStyle(color = Color(0xFF6B7280), fontSize = 8.sp),
                    topLeft = Offset(max(4f, x - 12f), h - 14f)
                )
            }
            
            // High-shelf harmonic curve
            val shelfGainNorm = (clarityGain / 1000f).coerceIn(0.05f, 1f)
            val shelfHeight = shelfGainNorm * (h - 40f)
            val curveY = h - 20f - shelfHeight
            
            val curvePath = Path()
            val fillPath = Path()
            
            fillPath.moveTo(0f, h)
            val steps = 50
            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * w
                // Sigmoid / High-shelf rise from 3kHz (x=0.2) to 12kHz (x=0.7)
                val normX = (x / w)
                val shelfFactor = 1f / (1f + exp(- (normX - 0.45f) * 10f))
                val y = h - 20f - (shelfFactor * shelfHeight)
                
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
            
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF69F0AE).copy(alpha = 0.28f),
                        Color(0xFF69F0AE).copy(alpha = 0.05f),
                        Color.Transparent
                    )
                )
            )
            
            drawPath(
                path = curvePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF00E5FF), Color(0xFF69F0AE))
                ),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
            
            val handleX = w * 0.85f
            drawCircle(
                color = Color(0xFF69F0AE).copy(alpha = 0.35f),
                radius = 16f,
                center = Offset(handleX, curveY)
            )
            drawCircle(
                color = Color(0xFF69F0AE),
                radius = 7f,
                center = Offset(handleX, curveY)
            )
            
            val readout = "Clarity: +${clarityGain / 70}dB"
            drawText(
                textMeasurer = textMeasurer,
                text = readout,
                style = TextStyle(color = Color(0xFF69F0AE), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                topLeft = Offset(handleX - 70f, max(8f, curveY - 22f))
            )
        }
    }
}

/**
 * Interactive Touch Transfer Characteristic Graph for FET Compressor & Limiter.
 * Visualizes knee, threshold, ratio slope, and makeup gain.
 */
@Composable
fun InteractiveCompressorGraph(
    thresholdDb: Int,
    ratio: Int,
    makeupGainDb: Int,
    onThresholdChange: (Int) -> Unit,
    onRatioChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1016))
            .pointerInput(thresholdDb, ratio) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    
                    // Drag X controls Threshold (-40dB to 0dB)
                    val normX = (change.position.x / width).coerceIn(0f, 1f)
                    val newThresh = (-40 + normX * 40).roundToInt()
                    
                    // Drag Y controls Ratio (1:1 to 20:1)
                    val normY = (1f - (change.position.y / height)).coerceIn(0f, 1f)
                    val newRatio = max(1, (1 + normY * 19).roundToInt())
                    
                    onThresholdChange(newThresh)
                    onRatioChange(newRatio)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // Linear 1:1 Reference Line (Gray Dashed)
            drawLine(
                color = Color(0xFF282E40),
                start = Offset(0f, h),
                end = Offset(w, 0f),
                strokeWidth = 1.5f
            )
            
            // Threshold coordinate
            val threshNorm = ((thresholdDb - (-40f)) / 40f).coerceIn(0f, 1f)
            val threshX = threshNorm * w
            val threshY = h - (threshNorm * h)
            
            // Draw Threshold Guideline
            drawLine(
                color = Color(0xFFFFB74D).copy(alpha = 0.4f),
                start = Offset(threshX, 0f),
                end = Offset(threshX, h),
                strokeWidth = 1f
            )
            
            // Compression Transfer Curve with Makeup Gain
            val makeupOffset = (makeupGainDb / 18f) * (h * 0.25f)
            val compPath = Path()
            val fillPath = Path()
            
            compPath.moveTo(0f, h - makeupOffset)
            fillPath.moveTo(0f, h)
            fillPath.lineTo(0f, h - makeupOffset)
            
            // Up to threshold: 1:1 slope
            compPath.lineTo(threshX, threshY - makeupOffset)
            fillPath.lineTo(threshX, threshY - makeupOffset)
            
            // Above threshold: 1:Ratio slope
            val slope = 1f / max(1f, ratio.toFloat())
            val remainingW = w - threshX
            val endY = (threshY - (remainingW * slope)) - makeupOffset
            
            compPath.lineTo(w, endY)
            fillPath.lineTo(w, endY)
            fillPath.lineTo(w, h)
            fillPath.close()
            
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFB74D).copy(alpha = 0.25f),
                        Color(0xFFFFB74D).copy(alpha = 0.05f),
                        Color.Transparent
                    )
                )
            )
            
            drawPath(
                path = compPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFFB74D), Color(0xFFFF8A65))
                ),
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
            
            // Threshold Node Handle
            drawCircle(
                color = Color(0xFFFFB74D).copy(alpha = 0.35f),
                radius = 16f,
                center = Offset(threshX, threshY - makeupOffset)
            )
            drawCircle(
                color = Color(0xFFFFB74D),
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
                style = TextStyle(color = Color(0xFFFFB74D), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                topLeft = Offset(12f, 10f)
            )
        }
    }
}
