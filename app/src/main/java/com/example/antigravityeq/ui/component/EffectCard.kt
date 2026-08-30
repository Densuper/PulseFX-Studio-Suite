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
import androidx.compose.material3.MaterialTheme
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

    val glowAlpha by animateFloatAsState(
        targetValue = if (enabled == true) 1f else 0f,
        label = "glow_alpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Straight Glowing Electric Connector Line
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (glowAlpha > 0.05f)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f * glowAlpha)
                    else
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )
        )

        Spacer(modifier = Modifier.width(6.dp))

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (enabled == true) 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) 
                else 
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
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
                            if (enabled == true) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeText,
                        color = if (enabled == true) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = name,
                    color = if (enabled == true) 
                        MaterialTheme.colorScheme.onSurface 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (enabled == true) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    fontSize = 15.sp
                )

                if (expandedContent != null) {
                    Text(
                        text = "▼",
                        color = if (expanded) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
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
                        }
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

    Spacer(modifier = Modifier.width(6.dp))

    // Right Straight Glowing Electric Connector Line
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                if (glowAlpha > 0.05f)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f * glowAlpha)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )
    )
}
}

