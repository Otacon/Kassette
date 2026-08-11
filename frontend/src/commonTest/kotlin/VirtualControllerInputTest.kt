package frontend

import nes.input.NesController
import kotlin.test.Test
import kotlin.test.assertEquals

class VirtualControllerInputTest {

    @Test
    fun `pressed buttons are submitted and released`() {
        val controller = NesController()
        val input = VirtualControllerInput(controller)

        input.press(NesController.BUTTON_A)
        input.press(NesController.BUTTON_LEFT)
        assertEquals(
            (1 shl NesController.BUTTON_A) or (1 shl NesController.BUTTON_LEFT),
            input.pollAndRead(controller),
        )

        input.release(NesController.BUTTON_A)
        assertEquals(1 shl NesController.BUTTON_LEFT, input.pollAndRead(controller))

        input.pause()
        assertEquals(0, input.pollAndRead(controller))
    }

    @Test
    fun `horizontal and vertical directions combine diagonally`() {
        val controller = NesController()
        val input = VirtualControllerInput(controller)

        input.setDirections(horizontal = 1, vertical = -1)
        assertEquals(
            (1 shl NesController.BUTTON_RIGHT) or (1 shl NesController.BUTTON_UP),
            input.pollAndRead(controller),
        )

        input.setDirections(horizontal = -1, vertical = 1)
        assertEquals(
            (1 shl NesController.BUTTON_LEFT) or (1 shl NesController.BUTTON_DOWN),
            input.pollAndRead(controller),
        )
    }

    private fun VirtualControllerInput.pollAndRead(controller: NesController): Int {
        poll()
        controller.poll()
        controller.write(1)
        controller.write(0)
        return NesController.NES_BUTTONS.fold(0) { buttons, button ->
            buttons or ((controller.read() and 1) shl button)
        }
    }
}
