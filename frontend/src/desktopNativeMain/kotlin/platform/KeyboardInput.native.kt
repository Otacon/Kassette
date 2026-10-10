package platform

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import frontend.EmulatorInput
import frontend.controllerSettings.ControllerInputMapper
import frontend.controllerSettings.NO_NES_BUTTON
import frontend.controllerSettings.inputButton
import nes.input.NesController

actual class KeyboardInput actual constructor(
    private val controller: NesController,
    private val inputMapper: ControllerInputMapper,
) : EmulatorInput {
    private var pressedButtons = 0

    actual fun onKeyEvent(event: KeyEvent): Boolean {
        val button = inputMapper.map(event.key.inputButton())
        if (button == NO_NES_BUTTON) return false
        val mask = 1 shl button
        pressedButtons = when (event.type) {
            KeyEventType.KeyDown -> pressedButtons or mask
            KeyEventType.KeyUp -> pressedButtons and mask.inv()
            else -> return false
        }
        return true
    }

    actual override fun init() = Unit

    actual override fun poll() {
        controller.pressMask(pressedButtons)
    }

    actual fun releaseAll() {
        pressedButtons = 0
    }

    override fun pause() = releaseAll()

    override fun close() = releaseAll()
}
