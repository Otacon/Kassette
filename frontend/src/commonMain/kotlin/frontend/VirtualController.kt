package frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Box(Modifier.align(Alignment.BottomStart)) {
            DirectionPad(input)
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (maxWidth < 600.dp) 184.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ControllerButton("SELECT", NesController.BUTTON_SELECT, input, small = true)
            ControllerButton("START", NesController.BUTTON_START, input, small = true)
        }
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ControllerButton("B", NesController.BUTTON_B, input)
            ControllerButton("A", NesController.BUTTON_A, input)
        }
    }
}

@Composable
private fun DirectionPad(input: VirtualControllerInput) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ControllerButton("U", NesController.BUTTON_UP, input)
        Row {
            ControllerButton("L", NesController.BUTTON_LEFT, input)
            Spacer(Modifier.size(BUTTON_SIZE))
            ControllerButton("R", NesController.BUTTON_RIGHT, input)
        }
        ControllerButton("D", NesController.BUTTON_DOWN, input)
    }
}

@Composable
private fun ControllerButton(
    label: String,
    button: Int,
    input: VirtualControllerInput,
    small: Boolean = false,
) {
    val size = if (small) 52.dp else BUTTON_SIZE
    Box(
        modifier = Modifier
            .size(size)
            .background(CONTROL_COLOR, CircleShape)
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
                fontSize = if (small) 10.sp else 18.sp,
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

private val BUTTON_SIZE = 58.dp
private val CONTROL_COLOR = Color(0xAA30343B)
