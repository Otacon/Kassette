package nes2.fakes

import nes2.controller.ControllerPort

class FakeController : ControllerPort {

    var value: Int = 0

    var lastWrittenValue: Int? = null
        private set

    override fun read(): Int {
        return value
    }

    override fun write(value: Int) {
        lastWrittenValue = value and 0xFF
    }
}