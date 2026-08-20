package nes2.fakes

import nes2.apu.Apu

class FakeApu : Apu {
    var readValue = 0
    var lastReadAddress: Int? = null
    var lastWriteAddress: Int? = null
    var lastWriteValue: Int? = null

    override fun read(address: Int): Int {
        lastReadAddress = address
        return readValue
    }

    override fun write(address: Int, value: Int) {
        lastWriteAddress = address
        lastWriteValue = value and 0xFF
    }
}
