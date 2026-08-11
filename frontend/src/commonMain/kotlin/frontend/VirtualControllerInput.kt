package frontend

import nes.input.NesController

class VirtualControllerInput(
    private val controller: NesController,
) : EmulatorInput {
    private var pressedButtons = 0

    fun press(button: Int) {
        pressedButtons = pressedButtons or (1 shl button)
    }

    fun release(button: Int) {
        pressedButtons = pressedButtons and (1 shl button).inv()
    }

    fun setDirections(horizontal: Int, vertical: Int) {
        pressedButtons = pressedButtons and DIRECTION_MASK.inv()
        if (horizontal < 0) pressedButtons = pressedButtons or (1 shl NesController.BUTTON_LEFT)
        if (horizontal > 0) pressedButtons = pressedButtons or (1 shl NesController.BUTTON_RIGHT)
        if (vertical < 0) pressedButtons = pressedButtons or (1 shl NesController.BUTTON_UP)
        if (vertical > 0) pressedButtons = pressedButtons or (1 shl NesController.BUTTON_DOWN)
    }

    fun releaseAll() {
        pressedButtons = 0
    }

    override fun init() = Unit

    override fun poll() {
        controller.pressMask(pressedButtons)
    }

    override fun pause() = releaseAll()

    override fun close() = releaseAll()

    private companion object {
        const val DIRECTION_MASK =
            (1 shl NesController.BUTTON_UP) or
                (1 shl NesController.BUTTON_DOWN) or
                (1 shl NesController.BUTTON_LEFT) or
                (1 shl NesController.BUTTON_RIGHT)
    }
}
