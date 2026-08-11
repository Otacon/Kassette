package frontend

import androidx.compose.ui.input.key.*
import frontend.controllerSettings.ControllerInputMapper
import frontend.controllerSettings.NO_NES_BUTTON
import frontend.controllerSettings.inputButton
import nes.input.NesController

actual class PlatformKeyboardInput actual constructor(
    private val controller: NesController,
    private val inputMapper: ControllerInputMapper,
) : EmulatorInput {
    private var pressedButtons = 0

    @Synchronized
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

    @Synchronized
    actual override fun poll() {
        controller.pressMask(pressedButtons)
    }

    @Synchronized
    actual fun releaseAll() {
        pressedButtons = 0
    }

    @Synchronized
    override fun pause() {
        releaseAll()
    }

    @Synchronized
    override fun close() {
        pressedButtons = 0
    }
}
