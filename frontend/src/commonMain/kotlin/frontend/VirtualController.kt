package frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nes.input.NesController

@Composable
fun VirtualController(
    input: VirtualControllerInput,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(input) {
        onDispose(input::releaseAll)
    }

    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 36.dp),
    ) {
        Box(Modifier.align(Alignment.BottomStart)) {
            DirectionPad(input)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControllerButton("SELECT", NesController.BUTTON_SELECT, input, system = true)
                ControllerButton("START", NesController.BUTTON_START, input, system = true)
            }
            Spacer(Modifier.size(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                ControllerButton("B", NesController.BUTTON_B, input)
                ControllerButton("A", NesController.BUTTON_A, input)
            }
        }
    }
}

@Composable
private fun DirectionPad(input: VirtualControllerInput) {
    Box(
        modifier = Modifier
            .size(DPAD_SIZE)
            .pointerInput(input) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    input.updateDirection(down.position, size.width, size.height)
                    try {
                        do {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (change?.pressed == true) {
                                input.updateDirection(change.position, size.width, size.height)
                            }
                        } while (change?.pressed == true)
                    } finally {
                        input.setDirections(horizontal = 0, vertical = 0)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(DPAD_SIZE, DIRECTION_SIZE)
                .background(CONTROL_COLOR, RoundedCornerShape(8.dp)),
        )
        Box(
            Modifier
                .size(DIRECTION_SIZE, DPAD_SIZE)
                .background(CONTROL_COLOR, RoundedCornerShape(8.dp)),
        )
        Box(
            Modifier
                .size(42.dp)
                .background(DPAD_CENTER_COLOR, CircleShape),
        )
    }
}

@Composable
private fun ControllerButton(
    label: String,
    button: Int,
    input: VirtualControllerInput,
    system: Boolean = false,
) {
    val shape = if (system) RoundedCornerShape(12.dp) else CircleShape
    val color = if (button == NesController.BUTTON_A || button == NesController.BUTTON_B) {
        ACTION_COLOR
    } else {
        CONTROL_COLOR
    }
    Box(
        modifier = Modifier
            .then(
                if (system) Modifier.size(width = 58.dp, height = 24.dp)
                else Modifier.size(BUTTON_SIZE)
            )
            .background(color, shape)
            .pointerInput(input, button) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    input.press(button)
                    try {
                        waitForRelease(down.id)
                    } finally {
                        input.release(button)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = Color.White,
                fontSize = if (system) 9.sp else 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForRelease(pointerId: PointerId) {
    do {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId }
    } while (change?.pressed == true)
}

private fun VirtualControllerInput.updateDirection(position: Offset, width: Int, height: Int) {
    val horizontal = when {
        position.x < width * DIRECTION_LOW_THRESHOLD -> -1
        position.x > width * DIRECTION_HIGH_THRESHOLD -> 1
        else -> 0
    }
    val vertical = when {
        position.y < height * DIRECTION_LOW_THRESHOLD -> -1
        position.y > height * DIRECTION_HIGH_THRESHOLD -> 1
        else -> 0
    }
    setDirections(horizontal, vertical)
}

private val BUTTON_SIZE = 58.dp
private val DIRECTION_SIZE = 46.dp
private val DPAD_SIZE = DIRECTION_SIZE * 3
private val CONTROL_COLOR = Color(0xAA30343B)
private val DPAD_CENTER_COLOR = Color(0xAA454A53)
private val ACTION_COLOR = Color(0xCCD32F2F)
private const val DIRECTION_LOW_THRESHOLD = 0.4f
private const val DIRECTION_HIGH_THRESHOLD = 0.6f
