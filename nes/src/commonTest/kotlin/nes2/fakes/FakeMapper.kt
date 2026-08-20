package nes2.fakes

import nes.cartridge.Mapper
import nes.cartridge.MapperState
import nes.cartridge.Mirroring

class FakeMapper : Mapper {
    var cpuReadValue = 0
    var useOpenBusForCpuRead = false
    var lastCpuReadAddress: Int? = null
    var lastCpuReadOpenBus: Int? = null
    var lastCpuWriteAddress: Int? = null
    var lastCpuWriteValue: Int? = null
    var ppuReadValue = 0
    var lastPpuReadAddress: Int? = null
    var lastPpuWriteAddress: Int? = null
    var lastPpuWriteValue: Int? = null
    var irqPending = false
    var mirroring: Mirroring? = null
    var scanlineClocks = 0
    var resets = 0

    override fun cpuRead(address: Int): Int {
        lastCpuReadAddress = address
        return cpuReadValue
    }

    override fun cpuRead(address: Int, openBus: Int): Int {
        lastCpuReadAddress = address
        lastCpuReadOpenBus = openBus
        return if (useOpenBusForCpuRead) openBus else cpuReadValue
    }

    override fun cpuWrite(address: Int, value: Int) {
        lastCpuWriteAddress = address
        lastCpuWriteValue = value
    }

    override fun ppuRead(address: Int): Int {
        lastPpuReadAddress = address
        return ppuReadValue
    }

    override fun ppuWrite(address: Int, value: Int) {
        lastPpuWriteAddress = address
        lastPpuWriteValue = value
    }

    override fun reset() {
        resets++
    }

    override fun clockScanline() {
        scanlineClocks++
    }

    override fun irqPending(): Boolean = irqPending

    override fun mirroring(): Mirroring? = mirroring

    override fun captureState(): MapperState = error("Not needed")
}
