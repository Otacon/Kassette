package frontend.controllerSettings

import androidx.compose.ui.input.key.Key
import io.ControllerMappings
import io.DEFAULT_CONTROLLER_MAPPINGS
import io.DeviceMappings
import kotlin.jvm.JvmInline

@JvmInline
value class InputButton(val id: Int)

enum class InputDevice { Keyboard, Gamepad }

class ControllerInputMapper(initialMappings: ControllerMappings?) {
    var mappings: ControllerMappings = initialMappings ?: DEFAULT_CONTROLLER_MAPPINGS
        private set

    private val keyboardLookup = IntArray(INPUT_BUTTON_COUNT) { NO_NES_BUTTON }
    private val gamepadLookup = IntArray(INPUT_BUTTON_COUNT) { NO_NES_BUTTON }

    init {
        rebuildLookups()
    }

    fun updateMappings(mappings: ControllerMappings) {
        this.mappings = mappings
        rebuildLookups()
    }

    fun map(device: InputDevice, button: InputButton): Int = when (device) {
        InputDevice.Keyboard -> keyboardLookup.valueAt(button.id)
        InputDevice.Gamepad -> gamepadLookup.valueAt(button.id)
    }

    private fun rebuildLookups() {
        keyboardLookup.fill(NO_NES_BUTTON)
        gamepadLookup.fill(NO_NES_BUTTON)
        mappings.keyboard.installInto(keyboardLookup)
        mappings.controller.installInto(gamepadLookup)
    }
}

fun Key.inputButton(): InputButton = InputButton(keyCode.toInt())

fun gamepadButton(index: Int): InputButton = InputButton(GAMEPAD_BUTTON_OFFSET + index)

fun gamepadAxis(index: Int, direction: Int): InputButton = InputButton(GAMEPAD_AXIS_OFFSET + index * 2 + direction)

fun gamepadPov(direction: Int): InputButton = InputButton(GAMEPAD_POV_OFFSET + direction)

private fun DeviceMappings.installInto(lookup: IntArray) {
    buttons.forEachIndexed { nesButton, inputButton ->
        if (inputButton in lookup.indices) lookup[inputButton] = nesButton
    }
}

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
