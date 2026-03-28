package com.echoproduction.metroclock.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Precision accent (same in both modes) ──────────────────────────────────
val McOrange = Color(0xFFEA4500)
val McGreen  = Color(0xFF2DD47E)
val McRed    = Color(0xFFF55252)

// ── Color scheme data class ────────────────────────────────────────────────
data class McColorScheme(
    val background:    Color,
    val surface:       Color,
    val border:        Color,
    val text:          Color,
    val textSecondary: Color,
    val textTertiary:  Color,
    val textFaint:     Color,
    val navBorder:     Color,
)

// ── Dark palette ───────────────────────────────────────────────────────────
val darkMcColors = McColorScheme(
    background    = Color(0xFF07070A),
    surface       = Color(0xFF0D0D13),
    border        = Color(0xFF181820),
    text          = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF888899),
    textTertiary  = Color(0xFF555566),
    textFaint     = Color(0xFF3A3A4A),
    navBorder     = Color(0xFF101018),
)

// ── Light palette ──────────────────────────────────────────────────────────
val lightMcColors = McColorScheme(
    background    = Color(0xFFF2F2F7),
    surface       = Color(0xFFFFFFFF),
    border        = Color(0xFFE5E5EA),
    text          = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF6E6E73),
    textTertiary  = Color(0xFF8E8E93),
    textFaint     = Color(0xFFAEAEB2),
    navBorder     = Color(0xFFD1D1D6),
)

// ── CompositionLocal ───────────────────────────────────────────────────────
val LocalMcColors = staticCompositionLocalOf { darkMcColors }
