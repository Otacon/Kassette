package nes2.fakes

import nes2.ppu.Ppu

class FakePpu(
    private val registers: IntArray = IntArray(8),
) : Ppu {

    override var frame: Long = 0

    var ticks = 0
        private set

    var ticksUntilNextFrame: Int? = null
    val oamWrites = mutableListOf<Int>()

    override fun cpuReadRegister(address: Int): Int {
        return registers[address - 0x2000]
    }

    override fun cpuWriteRegister(address: Int, value: Int) {
        registers[address - 0x2000] = value and 0xFF
    }

    override fun writeOamData(value: Int) {
        oamWrites += value and 0xFF
    }

    override fun tick() {
        ticks++

        val remaining = ticksUntilNextFrame ?: return

        if (ticks >= remaining) {
            frame++
            ticksUntilNextFrame = null
        }
    }
}