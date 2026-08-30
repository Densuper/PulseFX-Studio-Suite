package com.example.antigravityeq.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val frequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
private val freqLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
private const val MIN_FREQ = 25.0
private const val MAX_FREQ = 20000.0
private const val MIN_DB = -12.0f
private const val MAX_DB = 12.0f

@Composable
fun InteractiveFirequalizerCurve(
    bandLevels: List<Int>,
    onBandLevelsChange: (List<Int>) -> Unit,
    isEqEnabled: Boolean = true,
    liveLevels: FloatArray? = null,
    modifier: Modifier = Modifier
) {
    var selectedNodeIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    // Effective display levels: either dancing RTA live levels (when OFF) or user target (when ON)
    val displayLevels = if (!isEqEnabled && liveLevels != null) {
        liveLevels.map { it.roundToInt().coerceIn(-12, 12) }
    } else {
        bandLevels
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Main Graph Row: Vertical External dB scale + Graph Box Canvas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // External Left dB Scale (Outside Graph Box)
            Column(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(text = "+12", color = onSurfaceVariantColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                Text(text = "+6", color = onSurfaceVariantColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                Text(text = "0 dB", color = primaryColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(text = "-6", color = onSurfaceVariantColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                Text(text = "-12", color = onSurfaceVariantColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Graph Box Surface (Internal Lines, Spline & Nodes Only)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceVariantColor)
                    .border(1.dp, outlineColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .pointerInput(displayLevels, isEqEnabled) {
                        if (isEqEnabled) {
                            detectTapGestures { offset ->
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                val tappedFreq = xToFreq(offset.x, width)
                                
                                var closestIdx = 0
                                var minDistance = Double.MAX_VALUE
                                for (i in frequencies.indices) {
                                    val dist = abs(log10(frequencies[i].toDouble()) - log10(tappedFreq))
                                    if (dist < minDistance) {
                                        minDistance = dist
                                        closestIdx = i
                                    }
                                }
                                
                                val newDb = yToDb(offset.y, height).roundToInt().coerceIn(MIN_DB.toInt(), MAX_DB.toInt())
                                val updated = bandLevels.toMutableList()
                                updated[closestIdx] = newDb
                                onBandLevelsChange(updated)
                                selectedNodeIndex = closestIdx
                            }
                        }
                    }
                    .pointerInput(displayLevels, isEqEnabled) {
                        if (isEqEnabled) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val width = size.width.toFloat()
                                    val tappedFreq = xToFreq(offset.x, width)
                                    var closestIdx = 0
                                    var minDistance = Double.MAX_VALUE
                                    for (i in frequencies.indices) {
                                        val dist = abs(log10(frequencies[i].toDouble()) - log10(tappedFreq))
                                        if (dist < minDistance) {
                                            minDistance = dist
                                            closestIdx = i
                                        }
                                    }
                                    selectedNodeIndex = closestIdx
                                },
                                onDragEnd = { selectedNodeIndex = null },
                                onDragCancel = { selectedNodeIndex = null }
                            ) { change, _ ->
                                change.consume()
                                val idx = selectedNodeIndex ?: return@detectDragGestures
                                val height = size.height.toFloat()
                                val gainDb = yToDb(change.position.y, height).roundToInt()
                                
                                val updated = bandLevels.toMutableList()
                                if (idx in updated.indices) {
                                    updated[idx] = gainDb.coerceIn(MIN_DB.toInt(), MAX_DB.toInt())
                                }
                                onBandLevelsChange(updated)
                            },
                            onDragEnd = { selectedNodeIndex = null },
                            onDragCancel = { selectedNodeIndex = null }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    
                    // Internal Horizontal dB Guidelines (Clean lines inside box)
                    val dbGuidelines = listOf(12f, 6f, 0f, -6f, -12f)
                    dbGuidelines.forEach { db ->
                        val y = dbToY(db, canvasHeight)
                        val isZero = db == 0f
                        
                        drawLine(
                            color = if (isZero) primaryColor.copy(alpha = 0.45f) else outlineColor.copy(alpha = 0.20f),
                            start = Offset(0f, y),
                            end = Offset(canvasWidth, y),
                            strokeWidth = if (isZero) 1.5f else 1f
                        )
                    }
                    
                    // Internal Vertical Frequency Grid Lines
                    val gridFreqs = listOf(50.0, 100.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
                    gridFreqs.forEach { freq ->
                        val x = freqToX(freq, canvasWidth)
                        drawLine(
                            color = outlineColor.copy(alpha = 0.12f),
                            start = Offset(x, 0f),
                            end = Offset(x, canvasHeight),
                            strokeWidth = 1f
                        )
                    }
                    
                    // Spline Curve Calculation
                    val points = mutableListOf<Offset>()
                    val firstGain = bandLevels.firstOrNull()?.toFloat()?.coerceIn(MIN_DB, MAX_DB) ?: 0f
                    points.add(Offset(0f, dbToY(firstGain, canvasHeight)))
                    
                    frequencies.forEachIndexed { idx, freq ->
                        val gain = bandLevels.getOrElse(idx) { 0 }.toFloat().coerceIn(MIN_DB, MAX_DB)
                        val x = freqToX(freq.toDouble(), canvasWidth)
                        val y = dbToY(gain, canvasHeight)
                        points.add(Offset(x, y))
                    }
                    
                    val lastGain = bandLevels.lastOrNull()?.toFloat()?.coerceIn(MIN_DB, MAX_DB) ?: 0f
                    points.add(Offset(canvasWidth, dbToY(lastGain, canvasHeight)))
                    
                    val splineCurvePath = Path()
                    val fillPath = Path()
                    
                    if (points.isNotEmpty()) {
                        val sampledSpline = computeCatmullRomSpline(points, steps = 16)
                        if (sampledSpline.isNotEmpty()) {
                            splineCurvePath.moveTo(sampledSpline[0].x, sampledSpline[0].y)
                            fillPath.moveTo(sampledSpline[0].x, canvasHeight)
                            fillPath.lineTo(sampledSpline[0].x, sampledSpline[0].y)
                            
                            for (i in 1 until sampledSpline.size) {
                                splineCurvePath.lineTo(sampledSpline[i].x, sampledSpline[i].y)
                                fillPath.lineTo(sampledSpline[i].x, sampledSpline[i].y)
                            }
                            
                            fillPath.lineTo(canvasWidth, canvasHeight)
                            fillPath.close()
                        }
                    }
                    
                    // Gradient Fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.28f),
                                primaryColor.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = canvasHeight
                        )
                    )
                    
                    // Spline Stroke
                    drawPath(
                        path = splineCurvePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.8f),
                                primaryColor,
                                primaryColor.copy(alpha = 0.9f)
                            )
                        ),
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )
                    
                    // Interactive EQ Nodes & Active Readouts
                    frequencies.forEachIndexed { idx, freq ->
                        val gain = bandLevels.getOrElse(idx) { 0 }.toFloat().coerceIn(MIN_DB, MAX_DB)
                        val x = freqToX(freq.toDouble(), canvasWidth)
                        val y = dbToY(gain, canvasHeight)
                        val isSelected = selectedNodeIndex == idx
                        
                        if (isSelected) {
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.35f),
                                radius = 16f,
                                center = Offset(x, y)
                            )
                        }
                        
                        drawCircle(
                            color = if (isSelected) primaryColor else outlineColor.copy(alpha = 0.6f),
                            radius = 7f,
                            center = Offset(x, y)
                        )
                        
                        drawCircle(
                            color = if (isSelected) Color.White else primaryColor,
                            radius = 4f,
                            center = Offset(x, y)
                        )
                        
                        // Active Gain Bubble
                        if (gain != 0f || isSelected) {
                            val valueLabel = if (gain > 0) "+${gain.toInt()}dB" else "${gain.toInt()}dB"
                            val valYOffset = if (y < 35f) y + 12f else y - 20f
                            drawText(
                                textMeasurer = textMeasurer,
                                text = valueLabel,
                                style = TextStyle(
                                    color = if (isSelected) primaryColor else onSurfaceColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                topLeft = Offset(x - 12f, valYOffset)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // External Horizontal Frequency Row (Completely Outside Graph Box)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 42.dp, end = 4.dp), // Align precisely with graph box width
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            freqLabels.forEachIndexed { idx, label ->
                val isSelected = selectedNodeIndex == idx
                Text(
                    text = label,
                    color = if (isSelected) primaryColor else onSurfaceVariantColor,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Touch & drag nodes to sculpt curve",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            
            TextButton(
                onClick = {
                    onBandLevelsChange(List(10) { 0 })
                }
            ) {
                Text(
                    text = "Reset Flat (0 dB)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun freqToX(freq: Double, width: Float): Float {
    val minLog = log10(MIN_FREQ)
    val maxLog = log10(MAX_FREQ)
    val currentLog = log10(freq.coerceIn(MIN_FREQ, MAX_FREQ))
    return (((currentLog - minLog) / (maxLog - minLog)) * width).toFloat()
}

private fun xToFreq(x: Float, width: Float): Double {
    val norm = (x / width).coerceIn(0f, 1f).toDouble()
    val minLog = log10(MIN_FREQ)
    val maxLog = log10(MAX_FREQ)
    return 10.0.pow(minLog + norm * (maxLog - minLog))
}

private fun dbToY(db: Float, height: Float): Float {
    val norm = ((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)
    val topPad = 16f
    val bottomPad = 16f
    val usableH = height - topPad - bottomPad
    return height - bottomPad - (norm * usableH)
}

private fun yToDb(y: Float, height: Float): Float {
    val topPad = 16f
    val bottomPad = 16f
    val usableH = height - topPad - bottomPad
    val norm = (1.0f - ((y - topPad) / usableH)).coerceIn(0f, 1f)
    return MIN_DB + norm * (MAX_DB - MIN_DB)
}

private fun computeCatmullRomSpline(points: List<Offset>, steps: Int = 16): List<Offset> {
    if (points.size < 2) return points
    val result = mutableListOf<Offset>()
    
    for (i in 0 until points.size - 1) {
        val p0 = if (i > 0) points[i - 1] else points[i]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i + 2 < points.size) points[i + 2] else p2
        
        for (step in 0..steps) {
            val t = step.toFloat() / steps
            val t2 = t * t
            val t3 = t2 * t
            
            val x = 0.5f * (
                (2 * p1.x) +
                (-p0.x + p2.x) * t +
                (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3
            )
            val y = 0.5f * (
                (2 * p1.y) +
                (-p0.y + p2.y) * t +
                (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3
            )
            result.add(Offset(x, y))
        }
    }
    return result
}
