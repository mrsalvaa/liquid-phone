// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.incall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.liquidphone.R
import dev.liquidphone.ui.glass.GlassSurface
import dev.liquidphone.ui.glass.LocalFloating
import dev.liquidphone.ui.glass.LocalGlass
import dev.liquidphone.ui.glass.LocalReducedMotion
import dev.liquidphone.ui.glass.pillVeil
import dev.liquidphone.ui.glass.readableOn
import dev.liquidphone.ui.glass.styled
import dev.liquidphone.ui.screens.CallGreenFrame
import dev.liquidphone.ui.screens.CallRedFrame
import dev.liquidphone.util.str
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class SlideSide { REJECT, ANSWER }

private val ThumbSize = 72.dp

/** Alto del control entero, con los textos de abajo: la pantalla le reserva este lugar. */
internal val SlideHeight = ThumbSize + 32.dp

/** Qué parte del recorrido hay que hacer para que soltar cuente. */
private const val ARM_AT = 0.65f

/** Si la acción no cambió la pantalla en este tiempo, los botones vuelven a estar disponibles. */
private const val RETRY_AFTER_MS = 2500L

/**
 * Rechazar a la izquierda y atender a la derecha. Un toque no alcanza: al apoyar el dedo en uno
 * aparece la pista y hay que deslizar hasta pasar [ARM_AT]. Así el celu no atiende solo en el
 * bolsillo. Mientras tanto el otro botón se va. Al soltar antes, todo vuelve enseguida, para no
 * confundirse de botón. Con TalkBack no se puede deslizar: tocar dos veces hace la
 * acción directo. La pista es una píldora flotante: va en el slot `floating` de GlassRoot.
 */
@Composable
internal fun SlideToAnswer(onReject: () -> Unit, onAnswer: () -> Unit, modifier: Modifier = Modifier) {
    // Las cuentas de la pista son de izquierda a derecha: atender va siempre a la derecha.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        SlideTrack(onReject, onAnswer, modifier)
    }
}

@Composable
private fun SlideTrack(onReject: () -> Unit, onAnswer: () -> Unit, modifier: Modifier) {
    val p = LocalGlass.current
    val haptics = LocalHapticFeedback.current
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val reject by rememberUpdatedState(onReject)
    val answer by rememberUpdatedState(onAnswer)
    val rejectLabel = str(R.string.call_reject)
    val answerLabel = str(R.string.call_answer)

    var side by remember { mutableStateOf<SlideSide?>(null) }
    var lastSide by remember { mutableStateOf(SlideSide.ANSWER) }
    var done by remember { mutableStateOf(false) }
    val reveal = remember { Animatable(0f) }
    val travelled = remember { Animatable(0f) }

    LaunchedEffect(side) {
        reveal.animateTo(if (side != null) 1f else 0f, if (reduced) snap() else tween(if (side != null) 150 else 90))
    }

    fun close() {
        side = null
        scope.launch { travelled.animateTo(0f, if (reduced) snap() else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow * 2)) }
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(SlideHeight)) {
        val fullWidth = maxWidth
        val thumbPx = ThumbSize.value * androidx.compose.ui.platform.LocalDensity.current.density
        val travelPx = (constraints.maxWidth - thumbPx).coerceAtLeast(1f)
        val slop = 12f * androidx.compose.ui.platform.LocalDensity.current.density
        val shown = side ?: lastSide
        val color = if (shown == SlideSide.ANSWER) AnswerGreen else HangupRed

        Box(
            Modifier
                .fillMaxWidth()
                .height(ThumbSize)
                .pointerInput(travelPx, thumbPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (done) return@awaitEachGesture
                        val y = down.position.y
                        val x = down.position.x
                        val onLeft = x <= thumbPx + slop && y in -slop..(thumbPx + slop)
                        val onRight = x >= travelPx - slop && y in -slop..(thumbPx + slop)
                        val pressed = when {
                            onLeft -> SlideSide.REJECT
                            onRight -> SlideSide.ANSWER
                            else -> return@awaitEachGesture
                        }
                        down.consume()
                        lastSide = pressed
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        side = pressed
                        var drag = 0f
                        var armed = false
                        var released = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    released = true
                                    break
                                }
                                val dx = change.position.x - down.position.x
                                drag = (if (pressed == SlideSide.REJECT) dx else -dx).coerceIn(0f, travelPx)
                                change.consume()
                                val value = drag
                                scope.launch { travelled.snapTo(value) }
                                val nowArmed = drag >= travelPx * ARM_AT
                                if (nowArmed != armed) {
                                    armed = nowArmed
                                    if (armed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        } finally {
                            if (!released) close()
                        }
                        if (released && armed) {
                            done = true
                            scope.launch {
                                travelled.animateTo(travelPx, if (reduced) snap() else tween(110))
                                if (pressed == SlideSide.ANSWER) answer() else reject()
                                delay(RETRY_AFTER_MS)
                                done = false
                                close()
                            }
                        } else if (released) {
                            close()
                        }
                    }
                },
        ) {
            // La pista crece desde el botón apretado hacia el otro lado.
            if (reveal.value > 0f) {
                val trackWidth = ThumbSize + (fullWidth - ThumbSize) * reveal.value
                val trackModifier = Modifier
                    .align(if (shown == SlideSide.ANSWER) Alignment.CenterEnd else Alignment.CenterStart)
                    .width(trackWidth)
                    .fillMaxHeight()
                    .clearAndSetSemantics { }
                val inside: @Composable BoxScope.() -> Unit = {
                    // Lo recorrido se va llenando con el color de la acción.
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .drawBehind {
                                val filled = travelled.value + size.height
                                drawRoundRect(
                                    color = color.copy(alpha = 0.45f),
                                    topLeft = if (shown == SlideSide.REJECT) Offset.Zero else Offset(size.width - filled, 0f),
                                    size = Size(filled, size.height),
                                    cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                                )
                            },
                    )
                    SlideHint(
                        shown,
                        reduced,
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = ThumbSize)
                            .graphicsLayer { alpha = reveal.value * (1f - travelled.value / (travelPx * ARM_AT)).coerceIn(0f, 1f) },
                    )
                }
                if (p.solid) {
                    // GlassSurface en sólido recorta las formas largas a esquinas chicas; acá tiene que
                    // ser redonda, como el botón. Mismos colores que las superficies sólidas.
                    Box(
                        trackModifier
                            .clip(CircleShape)
                            .background(p.levels.first())
                            .border(1.dp, p.outline, CircleShape),
                        content = inside,
                    )
                } else {
                    GlassSurface(
                        trackModifier,
                        shape = CircleShape,
                        strong = true,
                        // Los valores de las píldoras flotantes de la suite: ver DESIGN.md de Contacts.
                        tint = p.pillVeil(),
                        frost = 4.dp,
                        content = inside,
                    )
                }
            }
            SlideThumb(
                rejectLabel,
                Icons.Rounded.CallEnd,
                HangupRed,
                pulse = false,
                reduced = reduced,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    // El que se desliza pasa por encima del otro.
                    .zIndex(if (shown == SlideSide.REJECT) 1f else 0f)
                    .offset { IntOffset(if (shown == SlideSide.REJECT) travelled.value.roundToInt() else 0, 0) }
                    .graphicsLayer {
                        val gone = if (shown == SlideSide.ANSWER) reveal.value else 0f
                        alpha = 1f - gone
                        scaleX = 1f - gone * 0.5f
                        scaleY = 1f - gone * 0.5f
                    },
            ) { reject() }
            SlideThumb(
                answerLabel,
                Icons.Rounded.Call,
                AnswerGreen,
                pulse = side == null,
                reduced = reduced,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .zIndex(if (shown == SlideSide.ANSWER) 1f else 0f)
                    .offset { IntOffset(if (shown == SlideSide.ANSWER) -travelled.value.roundToInt() else 0, 0) }
                    .graphicsLayer {
                        val gone = if (shown == SlideSide.REJECT) reveal.value else 0f
                        alpha = 1f - gone
                        scaleX = 1f - gone * 0.5f
                        scaleY = 1f - gone * 0.5f
                    },
            ) { answer() }
        }
        val labelStyle = Modifier.graphicsLayer { alpha = 1f - reveal.value }.clearAndSetSemantics { }
        Text(
            rejectLabel,
            Modifier.align(Alignment.BottomStart).width(ThumbSize).wrapContentWidth(unbounded = true).then(labelStyle),
            fontSize = 15.sp,
            color = p.content,
            maxLines = 1,
        )
        Text(
            answerLabel,
            Modifier.align(Alignment.BottomEnd).width(ThumbSize).wrapContentWidth(unbounded = true).then(labelStyle),
            fontSize = 15.sp,
            color = p.content,
            maxLines = 1,
        )
    }
}

/** El botón redondo. El gesto lo maneja la pista; este solo atiende el doble toque de TalkBack. */
@Composable
private fun SlideThumb(label: String, icon: ImageVector, color: Color, pulse: Boolean, reduced: Boolean, modifier: Modifier, onAccessibilityClick: () -> Unit) {
    val beat = if (pulse && !reduced) {
        val transition = rememberInfiniteTransition(label = "latido")
        transition.animateFloat(1f, 1.07f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "escala")
    } else {
        null
    }
    CallCircle(
        icon,
        color,
        modifier
            .graphicsLayer {
                val scale = beat?.value ?: 1f
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick(label) {
                    onAccessibilityClick()
                    true
                }
            },
    )
}

/**
 * Botón de llamada del color fijo de su acción, con la superficie del estilo elegido: cristal con
 * el color como tinte, u opaco en sólido. Flota: refracta también con fondo liso.
 */
@Composable
internal fun CallCircle(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = ThumbSize, onClick: (() -> Unit)? = null) {
    val p = LocalGlass.current
    CompositionLocalProvider(LocalFloating provides true) {
        GlassSurface(
            modifier
                .size(size)
                // Solo en sólido lleva borde: en Liquid Glass lo da el brillo del propio cristal.
                .then(if (p.solid) Modifier.border(1.dp, if (color == HangupRed) CallRedFrame else CallGreenFrame, CircleShape) else Modifier),
            shape = CircleShape,
            strong = true,
            tint = when {
                p.solid -> color
                p.dark -> color.copy(alpha = 0.6f)
                else -> color.copy(alpha = 0.78f)
            },
            frost = 4.dp,
            shadowRadius = 12.dp,
            onClick = onClick,
        ) {
            Icon(styled(icon), null, Modifier.align(Alignment.Center).size(size * 0.44f), tint = readableOn(color))
        }
    }
}

@Composable
private fun SlideHint(side: SlideSide, reduced: Boolean, modifier: Modifier) {
    val p = LocalGlass.current
    // Las flechas se mueven hacia donde hay que deslizar.
    val shift = if (reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "flechas").animateFloat(0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "corrimiento")
    }
    val towardsLeft = side == SlideSide.ANSWER
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (towardsLeft) {
            Icon(
                Icons.Rounded.KeyboardDoubleArrowLeft,
                null,
                Modifier.size(22.dp).offset { IntOffset(-((shift?.value ?: 0f) * abs(4.dp.toPx())).roundToInt(), 0) },
                tint = p.content,
            )
        }
        Text(
            str(if (side == SlideSide.ANSWER) R.string.slide_answer else R.string.slide_reject),
            Modifier.padding(horizontal = 4.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = p.content,
            maxLines = 1,
        )
        if (!towardsLeft) {
            Icon(
                Icons.Rounded.KeyboardDoubleArrowRight,
                null,
                Modifier.size(22.dp).offset { IntOffset(((shift?.value ?: 0f) * abs(4.dp.toPx())).roundToInt(), 0) },
                tint = p.content,
            )
        }
    }
}
