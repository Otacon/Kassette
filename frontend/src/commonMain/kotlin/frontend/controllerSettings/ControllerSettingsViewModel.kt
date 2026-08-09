package frontend.controllerSettings

import androidx.lifecycle.ViewModel
import androidx.compose.ui.input.key.Key
import dev.zacsweers.metro.Inject
import io.ControllerMappings
import io.DEFAULT_CONTROLLER_MAPPINGS
import io.DeviceMappings
import io.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nes.input.NesController.Companion.NES_BUTTONS

@Inject
class ControllerSettingsViewModel(
    private val preferences: Preferences,
    private val inputMapper: ControllerInputMapper,
) : ViewModel() {
    private val _state = MutableStateFlow(ControllerSettingsState())
    val state = _state.asStateFlow()

    fun onCreate() {
        val mappings = preferences.mappings ?: DEFAULT_CONTROLLER_MAPPINGS
        _state.value = ControllerSettingsState(mappings = mappings).withRows()
    }

    fun onCaptureStarted(button: Int, device: InputDevice) {
        _state.update { it.copy(captureTarget = CaptureTarget(button, device)).withRows() }
    }

    fun onKeyboardInputCaptured(inputButton: InputButton) {
        onInputCaptured(InputDevice.Keyboard, inputButton)
    }

    fun onGamepadInputCaptured(inputButton: InputButton) {
        onInputCaptured(InputDevice.Gamepad, inputButton)
    }

    private fun onInputCaptured(device: InputDevice, inputButton: InputButton) {
        _state.update { state ->
            val target = state.captureTarget?.takeIf { it.device == device } ?: return@update state
            state.copy(
                mappings = state.mappings.withValue(device, target.button, inputButton),
                captureTarget = null,
            ).withRows()
        }
    }

    fun onCaptureCancelled() {
        _state.update { it.copy(captureTarget = null).withRows() }
    }

    fun onSave() {
        val mappings = _state.value.mappings
        preferences.mappings = mappings
        inputMapper.updateMappings(mappings)
    }

    private fun ControllerSettingsState.withRows(): ControllerSettingsState = copy(rows = rowsFor(this))

    private fun rowsFor(state: ControllerSettingsState): List<ButtonRow> = NES_BUTTONS.map { button ->
        ButtonRow(
            button = button,
            label = button.asButtonLabel(),
            keyboardBinding = if (state.captureTarget == CaptureTarget(button, InputDevice.Keyboard)) {
                "Press keyboard input..."
            } else {
                labelFor(InputDevice.Keyboard, state.mappings.valueFor(InputDevice.Keyboard, button))
            },
            gamepadBinding = if (state.captureTarget == CaptureTarget(button, InputDevice.Gamepad)) {
                "Press gamepad input..."
            } else {
                labelFor(InputDevice.Gamepad, state.mappings.valueFor(InputDevice.Gamepad, button))
            },
        )
    }

    private fun labelFor(device: InputDevice, button: InputButton): String = button.label(device)

    private fun ControllerMappings.valueFor(device: InputDevice, nesButton: Int): InputButton = when (device) {
        InputDevice.Keyboard -> keyboard[nesButton]
        InputDevice.Gamepad -> controller[nesButton]
    }

    private fun ControllerMappings.withValue(
        device: InputDevice,
        nesButton: Int,
        value: InputButton,
    ): ControllerMappings = when (device) {
        InputDevice.Keyboard -> copy(keyboard = keyboard.with(nesButton, value))
        InputDevice.Gamepad -> copy(controller = controller.with(nesButton, value))
    }

    private operator fun DeviceMappings.get(nesButton: Int): InputButton = InputButton(buttons.getOrElse(nesButton) {
        throw IllegalArgumentException("Button $nesButton is not supported")
    })

    private fun DeviceMappings.with(nesButton: Int, value: InputButton): DeviceMappings {
        require(nesButton in buttons.indices) { "Button $nesButton is not supported" }
        return DeviceMappings(buttons.toMutableList().also { it[nesButton] = value.id })
    }

    private fun Int.asButtonLabel(): String = NES_BUTTON_LABELS.getOrElse(this) {
        throw IllegalArgumentException("Button type not supported: $this")
    }

    private fun InputButton.label(device: InputDevice): String = when (device) {
        InputDevice.Keyboard -> Key(id.toLong()).toString().substringAfterLast('.')
        InputDevice.Gamepad -> gamepadLabel()
    }

    private fun InputButton.gamepadLabel(): String = when (id) {
        in GAMEPAD_BUTTON_OFFSET until GAMEPAD_AXIS_OFFSET -> "Button ${id - GAMEPAD_BUTTON_OFFSET}"
        in GAMEPAD_AXIS_OFFSET until GAMEPAD_POV_OFFSET -> "Axis ${(id - GAMEPAD_AXIS_OFFSET) / 2} ${if ((id - GAMEPAD_AXIS_OFFSET) % 2 == 0) "-" else "+"}"
        in GAMEPAD_POV_OFFSET until INPUT_BUTTON_COUNT -> "D-pad ${povLabel(id - GAMEPAD_POV_OFFSET)}"
        else -> "Input $id"
    }

    private fun povLabel(direction: Int): String = when (direction) {
        POV_UP -> "Up"
        POV_DOWN -> "Down"
        POV_LEFT -> "Left"
        POV_RIGHT -> "Right"
        else -> direction.toString()
    }

    private companion object {
        val NES_BUTTON_LABELS = arrayOf("A", "B", "Select", "Start", "Up", "Down", "Left", "Right")
    }
}

data class ControllerSettingsState(
    val mappings: ControllerMappings = DEFAULT_CONTROLLER_MAPPINGS,
    val captureTarget: CaptureTarget? = null,
    val rows: List<ButtonRow> = emptyList(),
)

data class CaptureTarget(
    val button: Int,
    val device: InputDevice,
)
