package frontend.controllerSettings

import androidx.compose.ui.input.key.Key
import kotlin.jvm.JvmInline

@JvmInline
value class InputButton(val id: Int)

data class InputMappings(
    val buttons: List<Pair<Int, Int>>,
)

class ControllerInputMapper(
    private var mappings: InputMappings,
) {
    private val lookup = IntArray(INPUT_BUTTON_COUNT) { NO_NES_BUTTON }

    init {
        rebuildLookup()
    }

    fun updateMappings(mappings: InputMappings) {
        this.mappings = mappings
        rebuildLookup()
    }

    fun map(button: InputButton): Int = lookup.valueAt(button.id)

    private fun rebuildLookup() {
        lookup.fill(NO_NES_BUTTON)
        mappings.buttons.forEachIndexed { nesButton, (primary, secondary) ->
            if (primary in lookup.indices) lookup[primary] = nesButton
            if (secondary in lookup.indices) lookup[secondary] = nesButton
        }
    }
}

fun Key.inputButton(): InputButton = InputButton(keyCode.toInt())

fun gamepadButton(index: Int): InputButton = InputButton(GAMEPAD_BUTTON_OFFSET + index)

fun gamepadAxis(index: Int, direction: Int): InputButton = InputButton(GAMEPAD_AXIS_OFFSET + index * 2 + direction)

fun gamepadPov(direction: Int): InputButton = InputButton(GAMEPAD_POV_OFFSET + direction)

private fun IntArray.valueAt(index: Int): Int = if (index in indices) this[index] else NO_NES_BUTTON

const val NO_NES_BUTTON = -1
const val AXIS_NEGATIVE = 0
const val AXIS_POSITIVE = 1
const val POV_UP = 0
const val POV_DOWN = 1
const val POV_LEFT = 2
const val POV_RIGHT = 3
const val KEYBOARD_BUTTON_COUNT = 65536
const val GAMEPAD_BUTTON_OFFSET = KEYBOARD_BUTTON_COUNT
const val GAMEPAD_BUTTON_COUNT = 64
const val GAMEPAD_AXIS_OFFSET = GAMEPAD_BUTTON_OFFSET + GAMEPAD_BUTTON_COUNT
const val GAMEPAD_AXIS_COUNT = 16
const val GAMEPAD_POV_OFFSET = GAMEPAD_AXIS_OFFSET + GAMEPAD_AXIS_COUNT * 2
const val INPUT_BUTTON_COUNT = GAMEPAD_POV_OFFSET + 4
