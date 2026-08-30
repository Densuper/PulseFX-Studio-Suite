package com.example.antigravityeq.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ValuePicker(
    title: String,
    values: Array<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
        onClick = { showDialog = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = values.getOrElse(selectedIndex) { "" },
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Text(
                text = "CHANGE ▾",
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }

    if (showDialog) {
        ValuePickerDialog(
            title,
            values,
            selectedIndex,
            onSelectedIndexChange,
            onDismissRequest = { showDialog = false })
    }
}

@Composable
private fun ValuePickerDialog(
    title: String,
    values: Array<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var internalSelectedIndex by rememberSaveable { mutableStateOf(selectedIndex) }
    AlertDialog(
        onDismissRequest = {
            onDismissRequest()
        },
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        textContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                values.forEachIndexed { index, value ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { internalSelectedIndex = index }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == internalSelectedIndex,
                            onClick = { internalSelectedIndex = index }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = value, 
                            color = if (index == internalSelectedIndex) 
                                androidx.compose.material3.MaterialTheme.colorScheme.primary 
                            else 
                                androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelectedIndexChange(internalSelectedIndex)
                    onDismissRequest()
                }
            ) {
                Text("Select", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}
