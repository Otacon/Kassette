package nes2.fakes

import nes.cartridge.Cartridge
import nes.cartridge.Mapper
import nes.cartridge.Mirroring
import nes2.cartridge.CartridgePort

class FakeCartridgePort : CartridgePort {

    override var mirroring: Mirroring = Mirroring.VERTICAL
    val memory = IntArray(0x2000)

    var lastReadAddress: Int? = null
    var lastWriteAddress: Int? = null
    var lastWriteValue: Int? = null
    var irqPending = false
    var scanlineClocks = 0
    private var mapper: Mapper? = null

    override fun insert(cartridge: Cartridge) {
        mapper = cartridge.mapper
        mirroring = mapper?.mirroring() ?: cartridge.mirroring
    }

    override fun cpuRead(address: Int): Int {
        return mapper?.cpuRead(address) ?: 0
    }

    override fun cpuRead(address: Int, openBus: Int): Int {
        return mapper?.cpuRead(address, openBus) ?: 0
    }

    override fun cpuWrite(address: Int, value: Int) {
        mapper?.cpuWrite(address, value)
    }

    override fun ppuRead(address: Int): Int {
        lastReadAddress = address
        return memory[address]
    }

    override fun ppuWrite(address: Int, value: Int) {
        lastWriteAddress = address
        lastWriteValue = value
        memory[address] = value
    }

    override fun clockScanline() {
        scanlineClocks++
    }

    override fun irqPending(): Boolean = irqPending

}
