package nes2.controller

interface ControllerPort {
    fun reset()
    fun read(): Int
    fun write(value: Int)
}

class ControllerPortNes : ControllerPort {

    private var state = 0
    private var shiftRegister = 0
    private var strobe = false

    fun update(buttons: Int) {
        state = buttons and 0xFF
    }

    override fun reset() {
        shiftRegister = 0
        strobe = false
    }

    override fun write(value: Int) {
        val nextStrobe = value and 1 != 0

        if (strobe && !nextStrobe) {
            shiftRegister = state
        }

        strobe = nextStrobe
    }

    override fun read(): Int {
        val bit = if (strobe) {
            state and 1
        } else {
            shiftRegister and 1
        }

        if (!strobe) {
            shiftRegister = (shiftRegister ushr 1) or 0x80
        }

        return bit
    }
}
