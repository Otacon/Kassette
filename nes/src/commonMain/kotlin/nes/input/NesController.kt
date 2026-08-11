package nes.input

import co.touchlab.kermit.Logger

class NesController {
    companion object {
        const val BUTTON_A = 0
        const val BUTTON_B = 1
        const val BUTTON_SELECT = 2
        const val BUTTON_START = 3
        const val BUTTON_UP = 4
        const val BUTTON_DOWN = 5
        const val BUTTON_LEFT = 6
        const val BUTTON_RIGHT = 7

        val NES_BUTTONS = (BUTTON_A..BUTTON_RIGHT).toList()
    }

    private var live = 0
    private var latched = 0
    private var index = 0
    private var strobe = false
    private var buffered = 0
    private val log = Logger.withTag("NesController")

    fun reset() {
        latched = live
        index = 0
        strobe = false
        buffered = 0
    }

    fun poll() {
        commit(buffered)
        buffered = 0
    }

    fun press(button: Int) {
        require(button in BUTTON_A..BUTTON_RIGHT) { "Invalid controller button: $button" }
        buffered = buffered or (1 shl button)
    }

    fun pressMask(buttons: Int) {
        buffered = buffered or (buttons and 0xFF)
    }

    fun write(value: Int) {
        strobe = (value and 1) != 0
        if (strobe) {
            latched = live
            index = 0
        }
    }

    fun read(): Int {
        val bit = if (index < 8) (latched shr index) and 1 else 1
        if (!strobe && index < 8) index++
        return 0x40 or bit
    }

    private fun commit(buttons: Int) {
        val previous = live
        live = buttons and 0xFF
        if ((live and (1 shl BUTTON_LEFT)) != 0 && (live and (1 shl BUTTON_RIGHT)) != 0) {
            live = live and (1 shl BUTTON_RIGHT).inv()
        }
        if ((live and (1 shl BUTTON_UP)) != 0 && (live and (1 shl BUTTON_DOWN)) != 0) {
            live = live and (1 shl BUTTON_DOWN).inv()
        }
        logButtonEdges(previous, live)
        if (strobe) latched = live
    }

    private fun logButtonEdges(previous: Int, current: Int) {
        val pressed = current and previous.inv()
        val released = previous and current.inv()
        logEdges(pressed, "pressed")
        logEdges(released, "released")
    }

    private fun logEdges(buttons: Int, action: String) {
        if ((buttons and (1 shl BUTTON_START)) != 0) log.d { "START $action" }
        if ((buttons and (1 shl BUTTON_A)) != 0) log.d { "A $action" }
        if ((buttons and (1 shl BUTTON_B)) != 0) log.d { "B $action" }
        if ((buttons and (1 shl BUTTON_SELECT)) != 0) log.d { "SELECT $action" }
        if ((buttons and (1 shl BUTTON_UP)) != 0) log.d { "UP $action" }
        if ((buttons and (1 shl BUTTON_DOWN)) != 0) log.d { "DOWN $action" }
        if ((buttons and (1 shl BUTTON_LEFT)) != 0) log.d { "LEFT $action" }
        if ((buttons and (1 shl BUTTON_RIGHT)) != 0) log.d { "RIGHT $action" }
    }
}
