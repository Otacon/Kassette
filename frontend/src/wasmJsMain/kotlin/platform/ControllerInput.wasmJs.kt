@file:OptIn(ExperimentalWasmJsInterop::class)

package platform

import frontend.EmulatorInput
import frontend.controllerSettings.AXIS_NEGATIVE
import frontend.controllerSettings.AXIS_POSITIVE
import frontend.controllerSettings.ControllerInputMapper
import frontend.controllerSettings.GAMEPAD_AXIS_COUNT
import frontend.controllerSettings.GAMEPAD_BUTTON_COUNT
import frontend.controllerSettings.InputButton
import frontend.controllerSettings.NO_NES_BUTTON
import frontend.controllerSettings.gamepadAxis
import frontend.controllerSettings.gamepadButton
import nes.input.NesController

actual class ControllerInput actual constructor(
    private val controller: NesController,
    private val inputMapper: ControllerInputMapper,
) : EmulatorInput {

    private var ignoredBindings = emptySet<Int>()

    actual override fun init() = Unit

    actual override fun poll() {
        val gamepad = firstGamepad() ?: return
        var nesButtons = 0

        var buttonMask = gamepadButtonMask(gamepad, GAMEPAD_BUTTON_COUNT)
        var index = 0
        while (buttonMask != 0) {
            if ((buttonMask and 1) != 0) nesButtons = nesButtons or mappedMask(gamepadButton(index))
            buttonMask = buttonMask ushr 1
            index++
        }

        var axisMask = gamepadAxisMask(gamepad, GAMEPAD_AXIS_COUNT, AXIS_THRESHOLD)
        index = 0
        while (axisMask != 0) {
            if ((axisMask and 1) != 0) nesButtons = nesButtons or mappedMask(gamepadAxis(index, AXIS_NEGATIVE))
            if ((axisMask and 2) != 0) nesButtons = nesButtons or mappedMask(gamepadAxis(index, AXIS_POSITIVE))
            axisMask = axisMask ushr 2
            index++
        }

        controller.pressMask(nesButtons)
    }

    actual fun pressedButtons(): Set<InputButton> {
        val current = currentPressedBindings()
        if (current.none { it.id in ignoredBindings }) ignoredBindings = emptySet()
        return current.filterTo(mutableSetOf()) { it.id !in ignoredBindings }
    }

    actual fun clearPressedBindings() {
        ignoredBindings = emptySet()
        ignoredBindings = currentPressedBindings().mapTo(mutableSetOf()) { it.id }
    }

    override fun pause() = Unit

    override fun close() = Unit

    private fun mappedMask(button: InputButton): Int {
        val nesButton = inputMapper.map(button)
        return if (nesButton != NO_NES_BUTTON) 1 shl nesButton else 0
    }

    private fun currentPressedBindings(): Set<InputButton> {
        val gamepad = firstGamepad() ?: return emptySet()
        val current = mutableSetOf<InputButton>()

        var buttonMask = gamepadButtonMask(gamepad, GAMEPAD_BUTTON_COUNT)
        var index = 0
        while (buttonMask != 0) {
            if ((buttonMask and 1) != 0) current.add(gamepadButton(index))
            buttonMask = buttonMask ushr 1
            index++
        }

        var axisMask = gamepadAxisMask(gamepad, GAMEPAD_AXIS_COUNT, AXIS_THRESHOLD)
        index = 0
        while (axisMask != 0) {
            if ((axisMask and 1) != 0) current.add(gamepadAxis(index, AXIS_NEGATIVE))
            if ((axisMask and 2) != 0) current.add(gamepadAxis(index, AXIS_POSITIVE))
            axisMask = axisMask ushr 2
            index++
        }

        return current
    }

    private companion object {
        const val AXIS_THRESHOLD = 0.5
    }
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

@JsFun(
    """
    (pad, maxButtons) => {
        let mask = 0;
        const count = Math.min(pad.buttons.length, maxButtons, 31);
        for (let index = 0; index < count; index++) {
            if (pad.buttons[index] && pad.buttons[index].pressed) mask |= 1 << index;
        }
        return mask;
    }
    """
)
private external fun gamepadButtonMask(pad: JsAny, maxButtons: Int): Int

@JsFun(
    """
    (pad, maxAxes, threshold) => {
        let mask = 0;
        const count = Math.min(pad.axes.length, maxAxes, 16);
        for (let index = 0; index < count; index++) {
            const value = pad.axes[index] || 0;
            if (value < -threshold) mask |= 1 << (index * 2);
            if (value > threshold) mask |= 1 << (index * 2 + 1);
        }
        return mask;
    }
    """
)
private external fun gamepadAxisMask(pad: JsAny, maxAxes: Int, threshold: Double): Int
