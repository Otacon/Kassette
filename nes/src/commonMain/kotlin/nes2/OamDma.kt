package nes2

import nes2.ppu.Ppu

interface OamDma {
    val page: Int
    val active: Boolean
    fun start(page: Int)
    fun transfer()
}

class OamDmaNes(
    private val cpuBus: CpuBus,
    private val ppu: Ppu,
) : OamDma {
    override var active = false
        private set

    override var page = 0
        private set

    override fun start(page: Int) {
        this.page = page and 0xFF
        active = true
    }

    override fun transfer() {
        val baseAddress = page shl 8

        var offset = 0
        while (offset < 0x100) {
            ppu.writeOamData(cpuBus.read(baseAddress + offset))
            offset++
        }

        active = false
    }
}