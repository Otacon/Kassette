package nes2.fakes

import nes2.ppu.Ppu

class FakePpu(
    private val registers: IntArray = IntArray(8),
) : Ppu {

    override var onNmi: () -> Unit = {}

    override var frame: Long = 0
    var scanlinesPerFrame = 262
        private set
    var nmiScanline = 241
        private set
    var skipsOddFrameDot = true
        private set
    var ticks = 0
    var ticksUntilNextFrame: Int? = null
    val oamWrites = mutableListOf<Int>()

    override fun reset() {
        frame = 0
        ticks = 0
        ticksUntilNextFrame = null
        oamWrites.clear()
    }

    override fun configureTiming(scanlinesPerFrame: Int, nmiScanline: Int, skipsOddFrameDot: Boolean) {
        this.scanlinesPerFrame = scanlinesPerFrame
        this.nmiScanline = nmiScanline
        this.skipsOddFrameDot = skipsOddFrameDot
    }

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
