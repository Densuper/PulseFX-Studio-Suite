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
import com.example.antigravityeq.data.EqualizerSettings
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private const val MIN_FREQ = 20.0
private const val MAX_FREQ = 20000.0
private const val MIN_DB = -12f
private const val MAX_DB = 12f

@Composable
fun InteractiveFirequalizerCurve(
    bandLevels: List<Int>,
    onBandLevelsChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val frequencies = EqualizerSettings.EQ_FREQUENCIES
    var selectedNodeIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(bandLevels) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            
                            var closestIdx: Int? = null
                            var minDistance = 50f
                            
                            frequencies.forEachIndexed { idx, freq ->
                                val nodeX = freqToX(freq.toDouble(), width)
                                val gain = bandLevels.getOrElse(idx) { 0 }.toFloat().coerceIn(MIN_DB, MAX_DB)
                                val nodeY = dbToY(gain, height)
                                
                                val distance = (offset.x - nodeX).let { dx -> (offset.y - nodeY).let { dy -> kotlin.math.sqrt(dx * dx + dy * dy) } }
                                if (distance < minDistance) {
                                    minDistance = distance
                                    closestIdx = idx
                                }
                            }
                            
                            selectedNodeIndex = closestIdx
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            
                            val idx = selectedNodeIndex ?: run {
                                var closest: Int = 0
                                var minDist = Float.MAX_VALUE
                                frequencies.forEachIndexed { i, freq ->
                                    val nx = freqToX(freq.toDouble(), width)
                                    val dist = kotlin.math.abs(change.position.x - nx)
                                    if (dist < minDist) {
                                        minDist = dist
                                        closest = i
                                    }
                                }
                                closest
                            }
                            
                            val normY = (change.position.y / height).coerceIn(0f, 1f)
                            val gainDb = (MAX_DB - normY * (MAX_DB - MIN_DB)).roundToInt()
                            
                            val updated = bandLevels.toMutableList()
                            if (idx in updated.indices) {
                                updated[idx] = gainDb.coerceIn(MIN_DB.toInt(), MAX_DB.toInt())
                            }
                            onBandLevelsChange(updated)
                        },
                        onDragEnd = {
                            selectedNodeIndex = null
                        },
                        onDragCancel = {
                            selectedNodeIndex = null
                        }
                    )
                }
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
            val outlineColor = MaterialTheme.colorScheme.outline

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                
                val dbGuidelines = listOf(12f, 6f, 0f, -6f, -12f)
                dbGuidelines.forEach { db ->
                    val y = dbToY(db, canvasHeight)
                    val isZero = db == 0f
                    
                    drawLine(
                        color = if (isZero) primaryColor.copy(alpha = 0.45f) else outlineColor.copy(alpha = 0.25f),
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = if (isZero) 1.5f else 1f
                    )
                    
                    val label = if (db > 0) "+${db.toInt()}dB" else if (db < 0) "${db.toInt()}dB" else "0 dB"
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        style = TextStyle(
                            color = if (isZero) primaryColor else onSurfaceVariantColor,
                            fontSize = 8.5.sp,
                            fontWeight = if (isZero) FontWeight.Bold else FontWeight.Normal
                        ),
                        topLeft = Offset(8f, (y - 12f).coerceIn(4f, canvasHeight - 32f))
                    )
                }
                
                val gridFreqs = listOf(50.0, 100.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
                gridFreqs.forEach { freq ->
                    val x = freqToX(freq, canvasWidth)
                    drawLine(
                        color = outlineColor.copy(alpha = 0.15f),
                        start = Offset(x, 0f),
                        end = Offset(x, canvasHeight - 22f),
                        strokeWidth = 1f
                    )
                }
                
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
                        fillPath.moveTo(sampledSpline[0].x, canvasHeight - 20f)
                        fillPath.lineTo(sampledSpline[0].x, sampledSpline[0].y)
                        
                        for (i in 1 until sampledSpline.size) {
                            splineCurvePath.lineTo(sampledSpline[i].x, sampledSpline[i].y)
                            fillPath.lineTo(sampledSpline[i].x, sampledSpline[i].y)
                        }
                        
                        fillPath.lineTo(canvasWidth, canvasHeight - 20f)
                        fillPath.close()
                    }
                }
                
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.32f),
                            primaryColor.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = canvasHeight - 20f
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
                    style = Stroke(
                        width = 3.5f,
                        cap = StrokeCap.Round
                    )
                )
                
                val labels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
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
                        radius = 8f,
                        center = Offset(x, y)
                    )
                    
                    drawCircle(
                        color = if (isSelected) Color.White else primaryColor,
                        radius = 4.5f,
                        center = Offset(x, y)
                    )
                    
                    if (gain != 0f || isSelected) {
                        val valueLabel = if (gain > 0) "+${gain.toInt()}dB" else "${gain.toInt()}dB"
                        val valYOffset = if (y < 45f) y + 12f else y - 20f
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
                    
                    drawText(
                        textMeasurer = textMeasurer,
                        text = labels.getOrElse(idx) { "" },
                        style = TextStyle(
                            color = if (isSelected) primaryColor else onSurfaceVariantColor,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        topLeft = Offset(x - 8f, canvasHeight - 24f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Touch & drag nodes to sculpt response curve",
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
    val topPad = 30f
    val bottomPad = 42f
    val effectiveHeight = height - (topPad + bottomPad)
    val norm = (db - MIN_DB) / (MAX_DB - MIN_DB)
    return height - bottomPad - (norm * effectiveHeight)
}

private fun yToDb(y: Float, height: Float): Float {
    val topPad = 30f
    val bottomPad = 42f
    val effectiveHeight = height - (topPad + bottomPad)
    val norm = 1f - ((y - topPad) / effectiveHeight).coerceIn(0f, 1f)
    return MIN_DB + norm * (MAX_DB - MIN_DB)
}

private fun findNearestBandIndex(freq: Double, bands: FloatArray): Int {
    var minDiff = Double.MAX_VALUE
    var nearestIdx = 0
    bands.forEachIndexed { index, bandFreq ->
        val diff = kotlin.math.abs(log10(freq) - log10(bandFreq.toDouble()))
        if (diff < minDiff) {
            minDiff = diff
            nearestIdx = index
        }
    }
    return nearestIdx
}

private fun computeCatmullRomSpline(points: List<Offset>, steps: Int = 16): List<Offset> {
    if (points.size < 2) return points
    val result = mutableListOf<Offset>()
    
    val p = mutableListOf<Offset>()
    p.add(points.first())
    p.addAll(points)
    p.add(points.last())
    
    for (i in 0 until p.size - 3) {
        val p0 = p[i]
        val p1 = p[i + 1]
        val p2 = p[i + 2]
        val p3 = p[i + 3]
        
        for (step in 0..steps) {
            val t = step.toFloat() / steps
            val t2 = t * t
            val t3 = t2 * t
            
            val x = 0.5f * ((2f * p1.x) +
                    (-p0.x + p2.x) * t +
                    (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
                    (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3)
                    
            val y = 0.5f * ((2f * p1.y) +
                    (-p0.y + p2.y) * t +
                    (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                    (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3)
                    
            result.add(Offset(x, y))
        }
    }
    return result
}
