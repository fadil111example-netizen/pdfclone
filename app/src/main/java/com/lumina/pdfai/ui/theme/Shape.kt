package com.lumina.pdfai.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Signature violet -> ember gradient of the app. */
val BrandGradient = Brush.linearGradient(listOf(Violet500, Ember500))

fun brandGradient(reverse: Boolean = false): Brush =
    Brush.linearGradient(
        colors = if (reverse) listOf(Ember500, Violet500) else listOf(Violet500, Ember500)
    )

/** Colour pairs for generated document covers. */
private val coverPalettes = listOf(
    listOf(Violet600, Violet500),
    listOf(Color(0xFF1F6FEB), Color(0xFF37C6FF)),
    listOf(Color(0xFF0E7C5A), Color(0xFF3DD9B0)),
    listOf(Color(0xFFB3341C), Color(0xFFFF9559)),
    listOf(Color(0xFF6D2FB8), Color(0xFFE45CFF)),
    listOf(Color(0xFF1B2A5B), Color(0xFF4C6FFF))
)

fun paperGradient(seed: Int): Brush {
    val palette = coverPalettes[seed.mod(coverPalettes.size)]
    return Brush.linearGradient(palette)
}

fun softGradient(dark: Boolean): Brush =
    Brush.verticalGradient(
        if (dark) listOf(Ink800, Ink900) else listOf(Color.White, Mist100)
    )

val CardShape: Shape = RoundedCornerShape(22.dp)
val PillShape: Shape = RoundedCornerShape(50)
val SmallShape: Shape = RoundedCornerShape(14.dp)

@Composable
fun GradientSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    brush: Brush = BrandGradient,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
        content = content
    )
}

/**
 * True when a colour is dark.
 *
 * Lives here rather than in a screen so any surface can decide whether it needs
 * the light or dark variant of the aurora background.
 */
fun Color.isDark(): Boolean = luminance() < 0.5f

/** Gentle press/focus scale used on tappable cards. */
@Composable
fun animatedScale(pressed: Boolean, from: Float = 1f, to: Float = 0.97f): Float {
    val value by animateFloatAsState(
        targetValue = if (pressed) to else from,
        animationSpec = tween(140),
        label = "scale"
    )
    return value
}
