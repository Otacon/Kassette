@file:OptIn(ExperimentalWasmJsInterop::class)

package frontend

import frontend.controllerSettings.ControllerInputMapper
import frontend.controllerSettings.AXIS_NEGATIVE
import frontend.controllerSettings.AXIS_POSITIVE
import frontend.controllerSettings.GAMEPAD_AXIS_COUNT
import frontend.controllerSettings.GAMEPAD_BUTTON_COUNT
import frontend.controllerSettings.InputButton
import frontend.controllerSettings.InputDevice
import frontend.controllerSettings.NO_NES_BUTTON
import frontend.controllerSettings.gamepadAxis
import frontend.controllerSettings.gamepadButton
import nes.input.NesController

actual class PlatformControllerInput actual constructor(
    private val controller: NesController,
    private val inputMapper: ControllerInputMapper,
) : EmulatorInput {

    private var ignoredBindings = emptySet<Int>()

    actual override fun init() = Unit

    actual override fun poll() {
        val gamepad = firstGamepad() ?: return
        var index = 0
        val buttonCount = minOf(gamepadButtonCount(gamepad), GAMEPAD_BUTTON_COUNT)
        while (index < buttonCount) {
            if (gamepadButton(gamepad, index)) pressMapped(gamepadButton(index))
            index++
        }

        index = 0
        val axisCount = minOf(gamepadAxisCount(gamepad), GAMEPAD_AXIS_COUNT)
        while (index < axisCount) {
            val value = gamepadAxis(gamepad, index)
            if (value < -0.5) pressMapped(gamepadAxis(index, AXIS_NEGATIVE))
            if (value > 0.5) pressMapped(gamepadAxis(index, AXIS_POSITIVE))
            index++
        }
    }

    private fun pressMapped(button: InputButton) {
        val nesButton = inputMapper.map(InputDevice.Gamepad, button)
        if (nesButton != NO_NES_BUTTON) controller.press(nesButton)
    }

    actual fun pressedButtons(): Set<InputButton> {
        val current = currentPressedBindings()
        if (current.none { it.id in ignoredBindings }) ignoredBindings = emptySet()
        return current.filterTo(mutableSetOf()) { it.id !in ignoredBindings }
    }

    private fun currentPressedBindings(): Set<InputButton> {
        val gamepad = firstGamepad() ?: return emptySet()
        return buildSet {
            for (index in 0 until minOf(gamepadButtonCount(gamepad), GAMEPAD_BUTTON_COUNT)) {
                if (gamepadButton(gamepad, index)) add(gamepadButton(index))
            }
            for (index in 0 until minOf(gamepadAxisCount(gamepad), GAMEPAD_AXIS_COUNT)) {
                val value = gamepadAxis(gamepad, index)
                if (value < -0.5) add(gamepadAxis(index, AXIS_NEGATIVE))
                if (value > 0.5) add(gamepadAxis(index, AXIS_POSITIVE))
            }
        }
    }

    actual fun clearPressedBindings() {
        ignoredBindings = emptySet()
        ignoredBindings = currentPressedBindings().mapTo(mutableSetOf()) { it.id }
    }

    override fun pause() = Unit

    override fun close() = Unit
}

@JsFun(
    """
    () => {
        const pads = navigator.getGamepads ? navigator.getGamepads() : [];
        for (const pad of pads) {
            if (pad && pad.connected) return pad;
        }
        return null;
    }
    """
)
private external fun firstGamepad(): JsAny?

@JsFun("(pad, index) => !!(pad.buttons[index] && pad.buttons[index].pressed)")
private external fun gamepadButton(pad: JsAny, index: Int): Boolean

@JsFun("(pad) => pad.buttons.length")
private external fun gamepadButtonCount(pad: JsAny): Int

@JsFun("(pad) => pad.axes.length")
private external fun gamepadAxisCount(pad: JsAny): Int

@JsFun("(pad, index) => pad.axes[index] || 0")
private external fun gamepadAxis(pad: JsAny, index: Int): Double
