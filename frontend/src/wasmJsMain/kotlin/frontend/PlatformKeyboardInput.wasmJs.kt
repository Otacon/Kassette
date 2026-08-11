package frontend

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import frontend.controllerSettings.ControllerInputMapper
import frontend.controllerSettings.NO_NES_BUTTON
import frontend.controllerSettings.inputButton
import nes.input.NesController

actual class PlatformKeyboardInput actual constructor(
    private val controller: NesController,
    private val inputMapper: ControllerInputMapper,
) : EmulatorInput {
    private var pressedButtons = 0

    actual fun onKeyEvent(event: KeyEvent): Boolean {
        val button = inputMapper.map(event.key.inputButton())
        if (button == NO_NES_BUTTON) return false
        val mask = 1 shl button
        when (event.type) {
            KeyEventType.KeyDown -> pressedButtons = pressedButtons or mask
            KeyEventType.KeyUp -> pressedButtons = pressedButtons and mask.inv()
        }
        return true
    }

    actual fun releaseAll() {
        pressedButtons = 0
    }

    actual override fun init() = Unit

    actual override fun poll() {
        controller.pressMask(pressedButtons)
    }

    override fun pause() {
        releaseAll()
    }

    override fun close() {
        pressedButtons = 0
    }
}
