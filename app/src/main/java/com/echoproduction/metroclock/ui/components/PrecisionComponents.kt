package com.echoproduction.metroclock.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoproduction.metroclock.ui.theme.*

// ── FlipDigit ────────────────────────────────────────────────────────────────

/**
 * A single digit card for the flip clock.
 * Animates a scaleY pinch when the digit changes.
 */
@Composable
fun FlipDigit(
    digit: Int,
    width: Dp = 68.dp,
    height: Dp = 84.dp,
    fontSize: Int = 60,
    cornerRadius: Dp = 8.dp
) {
    var currentDigit by remember { mutableIntStateOf(digit) }
    var targetScale by remember { mutableFloatStateOf(1f) }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        finishedListener = {
            if (targetScale < 0.1f) {
                currentDigit = digit
                targetScale = 1f
            }
        },
        label = "flipScale"
    )

    LaunchedEffect(digit) {
        if (digit != currentDigit) {
            targetScale = 0.03f
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(LocalMcColors.current.surface)
            .border(1.dp, LocalMcColors.current.border, RoundedCornerShape(cornerRadius))
            .graphicsLayer { scaleY = animatedScale }
    ) {
        Text(
            text = "$currentDigit",
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = McOrange,
            textAlign = TextAlign.Center
        )
        // Centre divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.Black.copy(alpha = 0.65f))
        )
    }
}

// ── FlipClockRow ─────────────────────────────────────────────────────────────

/**
 * HH:mm flip clock row.
 * Pass currentTime as "HH:mm" string, updated every second.
 */
@Composable
fun FlipClockRow(
    currentTime: String,
    digitWidth: Dp = 68.dp,
    digitHeight: Dp = 84.dp,
    fontSize: Int = 60,
    cornerRadius: Dp = 8.dp
) {
    val parts = currentTime.split(":")
    val h = (parts.getOrNull(0) ?: "00").padStart(2, '0')
    val m = (parts.getOrNull(1) ?: "00").padStart(2, '0')

    val h1 = h[0].digitToInt()
    val h2 = h[1].digitToInt()
    val m1 = m[0].digitToInt()
    val m2 = m[1].digitToInt()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        FlipDigit(h1, digitWidth, digitHeight, fontSize, cornerRadius)
        FlipDigit(h2, digitWidth, digitHeight, fontSize, cornerRadius)

        Text(
            text = ":",
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = McOrange,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlipDigit(m1, digitWidth, digitHeight, fontSize, cornerRadius)
        FlipDigit(m2, digitWidth, digitHeight, fontSize, cornerRadius)
    }
}

// ── HolographicButton ────────────────────────────────────────────────────────

private val holoColors = listOf(
    Color(0xFFEA4500),
    Color(0xFFFF7030),
    Color(0xFFFFB347),
    Color(0xFFFF4500),
    Color(0xFFC13A00),
    Color(0xFF6B1E00),
    Color(0xFFEA4500)
)

/**
 * Holographic rotating-ring clock-in / clock-out button.
 */
@Composable
fun HolographicButton(
    isClockedIn: Boolean,
    isEnabled: Boolean,
    isLoading: Boolean,
    size: Dp = 176.dp,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "holo")

    // Ring rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "holoRotation"
    )

    // Glow pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "holoGlow"
    )

    val glowSize = size + 60.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(glowSize)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            McOrange.copy(alpha = glowAlpha * 0.55f),
                            McOrange.copy(alpha = glowAlpha * 0.18f),
                            Color.Transparent
                        ),
                        radius = glowSize.toPx() / 2
                    )
                )
            }
            .graphicsLayer { alpha = if (isEnabled) 1f else 0.38f }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .drawBehind {
                    rotate(rotation) {
                        drawCircle(
                            brush = Brush.sweepGradient(holoColors)
                        )
                    }
                }
                .then(
                    if (isEnabled && !isLoading)
                        Modifier.clickable { onClick() }
                    else
                        Modifier
                )
        ) {
            // Inner dark core
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size - 8.dp)
                    .clip(CircleShape)
                    .background(LocalMcColors.current.background)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = McOrange,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isClockedIn) "CLOCK OUT" else "CLOCK IN",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.5.sp
                        )
                        Text(
                            text = if (isClockedIn) "TAP TO STOP" else "TAP TO START",
                            color = LocalMcColors.current.textTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 2.4.sp
                        )
                    }
                }
            }
        }
    }
}

// ── StatusChip ───────────────────────────────────────────────────────────────

/**
 * Pill chip showing clocked-in / clocked-out state.
 */
@Composable
fun StatusChip(isClockedIn: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isClockedIn) 1f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    val dotColor = if (isClockedIn) Color.White.copy(alpha = 0.6f) else McOrange
    val textColor = if (isClockedIn) Color.White.copy(alpha = 0.7f) else McOrange.copy(alpha = 0.9f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(LocalMcColors.current.surface)
            .border(1.dp, Color(0xFF191922), RoundedCornerShape(100.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = if (isClockedIn) 0.6f else dotAlpha))
        )
        Text(
            text = if (isClockedIn) "CLOCKED IN" else "CLOCKED OUT",
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.0.sp
        )
    }
}

// ── LocationIndicator ────────────────────────────────────────────────────────

@Composable
fun LocationIndicator(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(LocalMcColors.current.textFaint)
        )
        Text(
            text = text.uppercase(),
            color = LocalMcColors.current.textFaint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 1.4.sp
        )
    }
}
