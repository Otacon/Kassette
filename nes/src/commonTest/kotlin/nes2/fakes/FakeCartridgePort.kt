package nes2.fakes

import nes2.cartridgePort.CartridgePort

class FakeCartridgePort : CartridgePort {

    val memory = IntArray(0x2000)

    var lastReadAddress: Int? = null
    var lastWriteAddress: Int? = null
    var lastWriteValue: Int? = null

    override fun ppuRead(address: Int): Int {
        lastReadAddress = address
        return memory[address]
    }

    override fun ppuWrite(address: Int, value: Int) {
        lastWriteAddress = address
        lastWriteValue = value
        memory[address] = value
    }

}