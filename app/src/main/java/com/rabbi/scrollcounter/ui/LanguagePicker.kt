package com.rabbi.scrollcounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rabbi.scrollcounter.ui.theme.Brand

/** Small glass chip showing the current language; tap to change it. */
@Composable
fun LanguageChip(current: Lang, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Language, null, tint = Brand.Gold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(current.native, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
    }
}

/** Bottom sheet listing every language by its own name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(current: Lang, onPick: (Lang) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF071012), contentColor = Color.White,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0B2A2B), Color(0xFF071012))))
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(bottom = 16.dp)
        ) {
            Text(tr("Choose your language"), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Lang.entries.forEach { l ->
                    val on = l == current
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(if (on) Brand.Gold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                            .border(1.dp, if (on) Brand.Gold.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                            .clickable { onPick(l) }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) { Text(l.code.uppercase(), style = MaterialTheme.typography.labelMedium, color = Brand.Gold, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.native, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                            if (l.native != l.english) Text(l.english, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                        }
                        if (on) Icon(Icons.Rounded.Check, null, tint = Brand.Gold)
                    }
                }
            }
        }
    }
}
