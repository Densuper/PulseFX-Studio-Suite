package com.example.antigravityeq.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun ValueSlider(
    title: String,
    summary: String? = null,
    summaryUnit: String = "",
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedRange<Int> = 0..1,
    steps: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color(0xFFEDF1F8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = (summary ?: value.toString()) + summaryUnit,
                color = Color(0xFF00E5FF),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange.let { it.start.toFloat()..it.endInclusive.toFloat() },
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00E5FF),
                activeTrackColor = Color(0xFF00E5FF),
                inactiveTrackColor = Color(0xFF1E2433)
            )
        )
    }
}

@Composable
fun ValueSlider(
    title: String,
    summary: String? = null,
    summaryUnit: String = "",
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color(0xFFEDF1F8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = (summary ?: String.format("%.1f", value)) + summaryUnit,
                color = Color(0xFF00E5FF),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Slider(
            value = value, 
            onValueChange = onValueChange, 
            valueRange = valueRange, 
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00E5FF),
                activeTrackColor = Color(0xFF00E5FF),
                inactiveTrackColor = Color(0xFF1E2433)
            )
        )
    }
}

