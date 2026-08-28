package com.smartspend.ai.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import com.smartspend.ai.R

@Composable
fun AiLoadingIndicator(isClockwiseRotation: Boolean = false, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "GeminiLoading")

    // ë¶€?œëŸ½ê²?ì»¤ì¡Œ?¤ê? ?‘ì•„ì§€???„ìŠ¤ ? ë‹ˆë©”ì´??
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val targetValue = if (isClockwiseRotation) 360f else -360f
    // ?€?€?˜ê²Œ ?Œì „?˜ëŠ” ? ë‹ˆë©”ì´??(? íƒ ?¬í•­, ?„ìš” ?†ìœ¼ë©??œê±° ê°€??
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "Rotation"
    )

    Box(
        modifier = modifier.scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_gemini_color),
            contentDescription = "Gemini Loading",
            modifier = Modifier.fillMaxSize().rotate(rotation)
        )
    }
}