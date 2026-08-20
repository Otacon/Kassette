package nes2.fakes

import nes.cartridge.Mirroring
import nes2.cartridge.CartridgePort

class FakeCartridgePort : CartridgePort {

    override var mirroring: Mirroring = Mirroring.VERTICAL
    val memory = IntArray(0x2000)

    var lastReadAddress: Int? = null
    var lastWriteAddress: Int? = null
    var lastWriteValue: Int? = null
    var irqPending = false

    override fun ppuRead(address: Int): Int {
        lastReadAddress = address
        return memory[address]
    }

    override fun ppuWrite(address: Int, value: Int) {
        lastWriteAddress = address
        lastWriteValue = value
        memory[address] = value
    }

    override fun irqPending(): Boolean = irqPending

}
