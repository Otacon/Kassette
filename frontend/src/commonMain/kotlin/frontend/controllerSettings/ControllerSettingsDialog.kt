package frontend.controllerSettings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import frontend.components.Dialog
import frontend.components.HorizontalDivider
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ControllerSettingsDialog(
    viewModel: ControllerSettingsViewModel,
    controllerInput: frontend.PlatformControllerInput,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onCreate()
    }

    val gamepadCaptureTarget = state.captureTarget?.takeIf { it.device == InputDevice.Gamepad }
    LaunchedEffect(gamepadCaptureTarget) {
        controllerInput.clearPressedBindings()
        while (true) {
            controllerInput.pressedButtons().firstOrNull()?.let { button ->
                viewModel.onGamepadInputCaptured(button)
                return@LaunchedEffect
            }
            delay(50.milliseconds)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        title = "Controller Settings",
        onPositive = {
            viewModel.onSave()
            onDismiss()
        },
        onNegative = { onDismiss() },
        positiveText = "OK",
        negativeText = "Cancel",
    ) {
        ButtonTable(
            rows = state.rows,
            captureTarget = state.captureTarget,
            onCaptureStarted = viewModel::onCaptureStarted,
            onKeyboardCaptured = viewModel::onKeyboardInputCaptured,
            onCaptureCancelled = viewModel::onCaptureCancelled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 16.dp)
        )
    }
}

@Composable
fun ButtonTable(
    rows: List<ButtonRow>,
    captureTarget: CaptureTarget?,
    onCaptureStarted: (Int, InputDevice) -> Unit,
    onKeyboardCaptured: (InputButton) -> Unit,
    onCaptureCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(captureTarget) {
        if (captureTarget != null) focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val target = captureTarget ?: return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                if (event.key == Key.Escape) {
                    onCaptureCancelled()
                } else if (target.device == InputDevice.Keyboard) {
                    onKeyboardCaptured(event.key.inputButton())
                }
                true
            }
            .focusable(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            TableHeaderCell(modifier = Modifier.weight(1.0f), text = "Button")
            TableHeaderCell(modifier = Modifier.weight(1.0f), text = "Keyboard")
            TableHeaderCell(modifier = Modifier.weight(1.0f), text = "Gamepad")
        }

        HorizontalDivider()

        // Rows
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                TableCell(modifier = Modifier.weight(1.0f)) {
                    BasicText(row.label)
                }

                TableCell(
                    modifier = Modifier
                        .weight(1.0f)
                        .clickable { onCaptureStarted(row.button, InputDevice.Keyboard) },
                ) {
                    BindingText(
                        value = row.keyboardBinding,
                        modifier = Modifier
                            .fillMaxWidth(),
                    )
                }

                TableCell(
                    modifier = Modifier
                        .weight(1.0f)
                        .clickable { onCaptureStarted(row.button, InputDevice.Gamepad) },
                ) {
                    BindingText(
                        value = row.gamepadBinding,
                        modifier = Modifier
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BindingText(
    value: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = value,
        modifier = modifier
            .background(Color.White)
            .border(
                width = 1.dp,
                color = Color.Gray,
            )
            .padding(
                horizontal = 8.dp,
                vertical = 6.dp,
            ),
    )
}

@Composable
private fun TableHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
) {
    TableCell(modifier) {
        BasicText(text)
    }
}

@Composable
private fun TableCell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(
                horizontal = 8.dp,
                vertical = 6.dp,
            ),
    ) {
        content()
    }
}

data class ButtonRow(
    val button: Int,
    val label: String,
    val keyboardBinding: String,
    val gamepadBinding: String,
)
