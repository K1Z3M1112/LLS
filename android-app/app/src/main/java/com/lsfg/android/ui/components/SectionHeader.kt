package com.lsfg.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsfg.android.ui.theme.*

/**
 * Bold uppercase section title with a red left-bar accent — RedMagic style.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    accentColor: Color = RedCore,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(accentColor),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

/** Row for a labelled setting with a trailing composable (switch, value text, etc.). */
@Composable
fun SettingRow(
    label: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = TextPrimary,
            )
            if (description != null) {
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}
