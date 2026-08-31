package com.example.antigravityeq.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var showExpandedModal by remember { mutableStateOf(false) }
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
                .height(220.dp),
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(surfaceVariantColor)
                    .border(1.2.dp, outlineColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .pointerInput(displayLevels, isEqEnabled) {
                        detectTapGestures(
                            onTap = {
                                showExpandedModal = true
                            }
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
                    
                    // LAYER 1.5: LIVE REAL-TIME AUDIO SPECTRUM WAVEFORM UNDERLAY
                    val liveFft = com.example.antigravityeq.AudioEffectsService.liveFftLevels
                    val fftWavePath = Path()
                    val fftFillPath = Path()
                    fftFillPath.moveTo(0f, canvasHeight)

                    val fftPoints = mutableListOf<Offset>()
                    fftPoints.add(Offset(0f, dbToY(liveFft.firstOrNull()?.coerceIn(MIN_DB, MAX_DB) ?: -12f, canvasHeight)))
                    frequencies.forEachIndexed { idx, freq ->
                        val magDb = liveFft.getOrElse(idx) { -12f }.coerceIn(MIN_DB, MAX_DB)
                        val x = freqToX(freq.toDouble(), canvasWidth)
                        val y = dbToY(magDb, canvasHeight)
                        fftPoints.add(Offset(x, y))
                    }
                    fftPoints.add(Offset(canvasWidth, dbToY(liveFft.lastOrNull()?.coerceIn(MIN_DB, MAX_DB) ?: -12f, canvasHeight)))

                    if (fftPoints.size >= 2) {
                        fftWavePath.moveTo(fftPoints[0].x, fftPoints[0].y)
                        fftFillPath.lineTo(fftPoints[0].x, fftPoints[0].y)
                        for (i in 0 until fftPoints.size - 1) {
                            val p0 = if (i > 0) fftPoints[i - 1] else fftPoints[i]
                            val p1 = fftPoints[i]
                            val p2 = fftPoints[i + 1]
                            val p3 = if (i + 2 < fftPoints.size) fftPoints[i + 2] else p2
                            val cp1x = p1.x + (p2.x - p0.x) / 6f
                            val cp1y = p1.y + (p2.y - p0.y) / 6f
                            val cp2x = p2.x - (p3.x - p1.x) / 6f
                            val cp2y = p2.y - (p3.y - p1.y) / 6f
                            fftWavePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                            fftFillPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                        }
                        fftFillPath.lineTo(canvasWidth, canvasHeight)
                        fftFillPath.close()

                        // Translucent Living Spectral Shadow Fill
                        drawPath(
                            path = fftFillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.22f),
                                    tertiaryColor.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = canvasHeight
                            )
                        )
                        // Glowing Waveform Contour
                        drawPath(
                            path = fftWavePath,
                            color = primaryColor.copy(alpha = 0.40f),
                            style = Stroke(width = 1.8f, cap = StrokeCap.Round)
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
                text = "Tap graph to expand precision studio",
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

        // Animated Precision Studio Modal Dialog for 10-Band Equalizer (Instant Auto-Save)
        if (showExpandedModal) {
            Dialog(
                onDismissRequest = { showExpandedModal = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "10-Band Studio Equalizer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                                Text(
                                    "High-Precision Paragraphic Mastering Studio",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariantColor
                                )
                            }
                            IconButton(
                                onClick = { showExpandedModal = false },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(surfaceVariantColor, CircleShape)
                            ) {
                                Text("✕", color = onSurfaceVariantColor, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Large 280dp Expanded Paragraphic Equalizer Canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(surfaceVariantColor)
                                .border(1.5.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .pointerInput(displayLevels, isEqEnabled) {
                                    if (isEqEnabled) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            down.consume()
                                            val width = size.width.toFloat()
                                            val height = size.height.toFloat()
                                            
                                            val tappedFreq = xToFreq(down.position.x, width)
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
                                            
                                            val newDb = yToDb(down.position.y, height).roundToInt().coerceIn(MIN_DB.toInt(), MAX_DB.toInt())
                                            val updated = bandLevels.toMutableList()
                                            if (closestIdx in updated.indices) {
                                                updated[closestIdx] = newDb
                                                onBandLevelsChange(updated)
                                            }
                                            
                                            do {
                                                val event = awaitPointerEvent()
                                                event.changes.forEach { change ->
                                                    if (change.pressed) {
                                                        change.consume()
                                                        val idx = selectedNodeIndex ?: closestIdx
                                                        val gainDb = yToDb(change.position.y, height).roundToInt()
                                                        val currentUpdated = bandLevels.toMutableList()
                                                        if (idx in currentUpdated.indices) {
                                                            currentUpdated[idx] = gainDb.coerceIn(MIN_DB.toInt(), MAX_DB.toInt())
                                                            onBandLevelsChange(currentUpdated)
                                                        }
                                                    }
                                                }
                                            } while (event.changes.any { it.pressed })
                                            
                                            selectedNodeIndex = null
                                        }
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                
                                val dbGuidelines = listOf(12f, 6f, 0f, -6f, -12f)
                                dbGuidelines.forEach { db ->
                                    val y = dbToY(db, canvasHeight)
                                    val isZero = db == 0f
                                    drawLine(
                                        color = if (isZero) primaryColor.copy(alpha = 0.4f) else outlineColor.copy(alpha = 0.15f),
                                        start = Offset(0f, y),
                                        end = Offset(canvasWidth, y),
                                        strokeWidth = if (isZero) 1.5f else 1f
                                    )
                                }
                                
                                val points = mutableListOf<Offset>()
                                points.add(Offset(0f, dbToY(bandLevels.firstOrNull()?.toFloat()?.coerceIn(MIN_DB, MAX_DB) ?: 0f, canvasHeight)))
                                
                                frequencies.forEachIndexed { idx, freq ->
                                    val gain = bandLevels.getOrElse(idx) { 0 }.toFloat().coerceIn(MIN_DB, MAX_DB)
                                    val x = freqToX(freq.toDouble(), canvasWidth)
                                    val y = dbToY(gain, canvasHeight)
                                    points.add(Offset(x, y))
                                }
                                points.add(Offset(canvasWidth, dbToY(bandLevels.lastOrNull()?.toFloat()?.coerceIn(MIN_DB, MAX_DB) ?: 0f, canvasHeight)))
                                
                                val sampledSpline = computeCatmullRomSpline(points, steps = 24)
                                if (sampledSpline.isNotEmpty()) {
                                    val splineCurvePath = Path()
                                    val fillPath = Path()
                                    splineCurvePath.moveTo(sampledSpline[0].x, sampledSpline[0].y)
                                    fillPath.moveTo(sampledSpline[0].x, canvasHeight)
                                    fillPath.lineTo(sampledSpline[0].x, sampledSpline[0].y)
                                    
                                    for (i in 1 until sampledSpline.size) {
                                        splineCurvePath.lineTo(sampledSpline[i].x, sampledSpline[i].y)
                                        fillPath.lineTo(sampledSpline[i].x, sampledSpline[i].y)
                                    }
                                    fillPath.lineTo(canvasWidth, canvasHeight)
                                    fillPath.close()
                                    
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                primaryColor.copy(alpha = 0.35f),
                                                primaryColor.copy(alpha = 0.08f),
                                                Color.Transparent
                                            ),
                                            startY = 0f,
                                            endY = canvasHeight
                                        )
                                    )
                                    drawPath(
                                        path = splineCurvePath,
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                primaryColor.copy(alpha = 0.8f),
                                                primaryColor,
                                                primaryColor.copy(alpha = 0.9f)
                                            )
                                        ),
                                        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                                    )
                                }
                                
                                frequencies.forEachIndexed { idx, freq ->
                                    val gain = bandLevels.getOrElse(idx) { 0 }.toFloat().coerceIn(MIN_DB, MAX_DB)
                                    val x = freqToX(freq.toDouble(), canvasWidth)
                                    val y = dbToY(gain, canvasHeight)
                                    val isSelected = selectedNodeIndex == idx
                                    
                                    drawCircle(
                                        color = if (isSelected) primaryColor else primaryColor.copy(alpha = 0.4f),
                                        radius = if (isSelected) 22f else 14f,
                                        center = Offset(x, y)
                                    )
                                    drawCircle(
                                        color = if (isSelected) Color.White else primaryColor,
                                        radius = if (isSelected) 10f else 6f,
                                        center = Offset(x, y)
                                    )
                                    
                                    val valueLabel = if (gain > 0) "+${gain.toInt()}dB" else "${gain.toInt()}dB"
                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = valueLabel,
                                        style = TextStyle(
                                            color = if (isSelected) primaryColor else onSurfaceColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        topLeft = Offset(x - 16f, if (y < 40f) y + 16f else y - 28f)
                                    )
                                }
                            }
                        }

                        // Frequency Labels for Expanded Modal
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            freqLabels.forEach { label ->
                                Text(text = label, color = onSurfaceVariantColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onBandLevelsChange(List(10) { 0 }) }) {
                                Text("Reset Flat (0 dB)", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "⚡ Auto-Saved • Tap outside to close",
                                style = MaterialTheme.typography.bodySmall,
                                color = onSurfaceVariantColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
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
