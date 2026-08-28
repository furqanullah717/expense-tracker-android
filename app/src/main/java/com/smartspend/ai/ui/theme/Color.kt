package com.smartspend.ai.ui.theme

import androidx.compose.ui.graphics.Color

val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
val Zinc = Color(0xFF2F7E79)
val LightGrey = Color(0xFF666666)
val Navy = Color(0xFF1B1B1F)
val Navy80 = Color(0xCCFFFFFF)
val Teal = Color(0xFF00AEAE)
val Teal80 = Color(0xCCFFFFFF)
val Indigo = Color(0xFF4B0082)
val Indigo80 = Color(0xCCFFFFFF)
val Amethyst = Color(0xFF9370DB)
val Amethyst80 = Color(0xCCFFFFFF)
val White = Color(0xFFFFFFFF)
val White80 = Color(0xCCFFFFFF)
val Red = Color(0xFFFF0000)
val Green = Color(0xFF00FF00)

sealed class ThemeColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val text: Color
) {
    data object Night : ThemeColors(
        background = Navy,
        surface = Teal,
        primary = Zinc,
        secondary = Indigo,
        tertiary = Amethyst,
        text = White
    )

    data object Day : ThemeColors(
        background = Color.White,
        surface = Color.White,
        primary = Zinc,
        secondary = PurpleGrey40,
        tertiary = Pink40,
        text = Color.Black
    )
}