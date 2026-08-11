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

    fun releaseAll() {
        pressedButtons = 0
    }

    override fun init() = Unit

    override fun poll() {
        controller.pressMask(pressedButtons)
    }

    override fun pause() = releaseAll()

    override fun close() = releaseAll()
}
