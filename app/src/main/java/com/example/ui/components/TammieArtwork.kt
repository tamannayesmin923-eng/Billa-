package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.OnlineGreen

fun parseColorSafe(hex: String, fallback: Color = Color(0xFF0D7C7B)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun UserAvatar(
    name: String,
    photoUrl: String? = null,
    avatarColorHex: String = "#0D7C7B",
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    isGroup: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = parseColorSafe(avatarColorHex, MaterialTheme.colorScheme.primary)
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "T"

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(bgColor, bgColor.copy(alpha = 0.78f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrBlank() && !isGroup) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isGroup) {
                Icon(
                    imageVector = Icons.Rounded.Group,
                    contentDescription = name,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.52f)
                )
            } else {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
        if (isOnline && !isGroup) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.28f).dp.coerceAtLeast(11.dp))
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun Base64ImagePreview(
    base64: String,
    contentDescription: String,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(base64) {
        try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

@Composable
fun TammieHeroIllustration(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface

    val transition = rememberInfiniteTransition(label = "hero_float")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Ambient radial teal glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = minOf(w, h) * 0.56f
            ),
            radius = minOf(w, h) * 0.56f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // Concentric calm rings
        drawCircle(
            color = primary.copy(alpha = 0.14f + 0.06f * pulse),
            radius = minOf(w, h) * (0.36f + 0.02f * pulse),
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            color = tertiary.copy(alpha = 0.18f),
            radius = minOf(w, h) * 0.45f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = 2.dp.toPx())
        )

        // Back bubble (incoming slate)
        drawRoundRect(
            color = primaryContainer,
            topLeft = Offset(w * 0.16f, h * 0.24f),
            size = Size(w * 0.46f, h * 0.24f),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
        )

        // Front bubble (outgoing calm teal)
        drawRoundRect(
            color = primary,
            topLeft = Offset(w * 0.36f, h * (0.44f - 0.02f * pulse)),
            size = Size(w * 0.50f, h * 0.27f),
            cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
        )

        // Message lines inside front bubble
        drawRoundRect(
            color = surface.copy(alpha = 0.9f),
            topLeft = Offset(w * 0.43f, h * (0.52f - 0.02f * pulse)),
            size = Size(w * 0.28f, 6.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        drawRoundRect(
            color = surface.copy(alpha = 0.65f),
            topLeft = Offset(w * 0.43f, h * (0.59f - 0.02f * pulse)),
            size = Size(w * 0.19f, 5.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )

        // Encryption amber orb badge
        drawCircle(
            color = tertiary,
            radius = 12.dp.toPx(),
            center = Offset(w * 0.78f, h * 0.32f)
        )
        drawCircle(
            color = surface,
            radius = 4.5.dp.toPx(),
            center = Offset(w * 0.78f, h * 0.32f)
        )
    }
}

@Composable
fun AcousticCallRings(isRinging: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "call_rings")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRinging) 1400 else 2800),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = minOf(size.width, size.height) * 0.48f
        for (i in 0..2) {
            val p = (phase + i / 3f) % 1f
            drawCircle(
                color = primary.copy(alpha = (1f - p) * 0.32f),
                radius = maxR * (0.45f + 0.55f * p),
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
    }
}

@Composable
fun LanguagePillToggle(
    isBengali: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onToggle,
        shape = CircleShape,
        modifier = modifier
    ) {
        Text(
            text = if (isBengali) "EN / বাংলা" else "বাংলা / EN",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
