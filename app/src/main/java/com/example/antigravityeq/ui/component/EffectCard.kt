package com.example.antigravityeq.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EffectCard(
    badgeText: String,
    name: String,
    enabled: Boolean?,
    onEnabledChange: ((Boolean) -> Unit)?,
    expandedContent: (@Composable BoxScope.() -> Unit)? = null
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    
    // Animate arrow rotation
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "expand_rotation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled == true) Color(0xFF1F222E) else Color(0xFF1A1C24)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = expandedContent != null) { 
                        expanded = !expanded 
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Colored badge circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled == true) Color(0xFF00E5FF).copy(alpha = 0.22f) 
                            else Color(0xFF2B3042)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeText,
                        color = if (enabled == true) Color(0xFF00E5FF) else Color(0xFF9AA5B8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = name,
                    color = if (enabled == true) Color(0xFFEDF1F8) else Color(0xFFB0B8C8),
                    fontWeight = if (enabled == true) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    fontSize = 15.sp
                )

                if (expandedContent != null) {
                    Text(
                        text = "▼",
                        color = if (expanded) Color(0xFF00E5FF) else Color(0xFF6B7280),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .rotate(rotationAngle)
                            .padding(end = 4.dp)
                    )
                }

                if (enabled != null && onEnabledChange != null) {
                    Switch(
                        checked = enabled, 
                        onCheckedChange = { isChecked ->
                            onEnabledChange(isChecked)
                            if (isChecked && expandedContent != null) {
                                expanded = true
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF1A1C24),
                            checkedTrackColor = Color(0xFF00E5FF),
                            uncheckedThumbColor = Color(0xFF9AA5B8),
                            uncheckedTrackColor = Color(0xFF12131A)
                        )
                    )
                }
            }

            expandedContent?.let {
                AnimatedVisibility(visible = expanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp, top = 2.dp)
                    ) {
                        it()
                    }
                }
            }
        }
    }
}

