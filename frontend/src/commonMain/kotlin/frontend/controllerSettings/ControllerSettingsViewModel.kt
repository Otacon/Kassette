package frontend.controllerSettings

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.Inject
import io.Preferences
import io.toControllerMappings
import io.toInputMappings
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
        val mappings = preferences.mappings.toInputMappings()
        _state.value = ControllerSettingsState(mappings = mappings).withRows()
    }

    fun onCaptureStarted(button: Int, primary: Boolean) {
        _state.update { it.copy(captureButton = button, capturePrimary = primary).withRows() }
    }

    fun onInputCaptured(inputButton: InputButton) {
        _state.update { state ->
            val button = state.captureButton ?: return@update state
            state.copy(
                mappings = state.mappings.withValue(state.capturePrimary, button, inputButton),
                captureButton = null,
            ).withRows()
        }
    }

    fun onCaptureCancelled() {
        _state.update { it.copy(captureButton = null).withRows() }
    }

    fun onSave() {
        val mappings = _state.value.mappings
        preferences.mappings = mappings.toControllerMappings()
        inputMapper.updateMappings(mappings)
    }

    private fun ControllerSettingsState.withRows(): ControllerSettingsState {
        val rows = NES_BUTTONS.map { button ->
            ButtonRow(
                button = button,
                label = button.asButtonLabel(),
                primaryBinding = if (captureButton == button && capturePrimary) {
                    "Press input..."
                } else {
                    mappings.valueFor(primary = true, button).label()
                },
                secondaryBinding = if (captureButton == button && !capturePrimary) {
                    "Press input..."
                } else {
                    mappings.valueFor(primary = false, button).label()
                },
            )

        }
        return copy(rows = rows)
    }

    private fun InputMappings.valueFor(primary: Boolean, nesButton: Int): InputButton {
        val pair = buttons.getOrElse(nesButton) { throw IllegalArgumentException("Button $nesButton is not supported") }
        return InputButton(if (primary) pair.first else pair.second)
    }

    private fun InputMappings.withValue(primary: Boolean, nesButton: Int, value: InputButton): InputMappings {
        require(nesButton in buttons.indices) { "Button $nesButton is not supported" }
        return copy(buttons = buttons.toMutableList().also { mappings ->
            val current = mappings[nesButton]
            mappings[nesButton] = if (primary) {
                value.id to current.second
            } else {
                current.first to value.id
            }
        })
    }

    private fun Int.asButtonLabel(): String = NES_BUTTON_LABELS.getOrElse(this) {
        throw IllegalArgumentException("Button type not supported: $this")
    }

    private fun InputButton.label(): String = when (id) {
        in GAMEPAD_BUTTON_OFFSET until GAMEPAD_AXIS_OFFSET -> "Button ${id - GAMEPAD_BUTTON_OFFSET}"
        in GAMEPAD_AXIS_OFFSET until GAMEPAD_POV_OFFSET -> "Axis ${(id - GAMEPAD_AXIS_OFFSET) / 2} ${if ((id - GAMEPAD_AXIS_OFFSET) % 2 == 0) "-" else "+"}"
        in GAMEPAD_POV_OFFSET until INPUT_BUTTON_COUNT -> "D-pad ${povLabel(id - GAMEPAD_POV_OFFSET)}"
        else -> keyboardLabel(id)
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
    val mappings: InputMappings = InputMappings(emptyList()),
    val captureButton: Int? = null,
    val capturePrimary: Boolean = true,
    val rows: List<ButtonRow> = emptyList(),
)
