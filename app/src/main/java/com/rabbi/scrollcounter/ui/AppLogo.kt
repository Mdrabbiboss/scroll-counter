package com.rabbi.scrollcounter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rabbi.scrollcounter.face.FaceRenderer
import com.rabbi.scrollcounter.face.Mood

/** The Mood Mascot app icon, drawn in Compose so it stays sharp at any size (matches the launcher icon). */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val u = s / 512f
        // Tile
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF0B4F47), Color(0xFF041513)), Offset.Zero, Offset(s, s)),
            cornerRadius = CornerRadius(116 * u, 116 * u)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            topLeft = Offset(2 * u, 2 * u),
            size = Size(s - 4 * u, s - 4 * u),
            cornerRadius = CornerRadius(114 * u, 114 * u),
            style = Stroke(4 * u)
        )
        // Arc track + mint→gold progress
        val c = Offset(256 * u, 262 * u)
        val r = 196 * u
        val tl = Offset(c.x - r, c.y - r)
        val box = Size(2 * r, 2 * r)
        drawArc(Color.White.copy(alpha = 0.10f), 0f, 360f, false, tl, box, style = Stroke(18 * u))
        drawArc(
            Brush.linearGradient(listOf(Color(0xFF6EE7D2), Color(0xFFF8C33A)), Offset(c.x, c.y - r), Offset(c.x + r * 0.3f, c.y + r)),
            -90f, 164f, false, tl, box, style = Stroke(18 * u, cap = StrokeCap.Round)
        )
        // Face
        drawIntoCanvas { FaceRenderer.draw(it.nativeCanvas, c.x, c.y, 146 * u, Mood.FRESH) }
    }
}
