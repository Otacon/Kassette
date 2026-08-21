package nes2

import nes2.ppu.Ppu

interface OamDma {
    var cpuBusRead: (Int) -> Int
    val isActive: Boolean
    fun reset()
    fun start(page: Int)
    fun transfer()
}

data class OamDmaState(
    var active: Boolean = false,
    var page: Int = 0,
)

class OamDmaNes(
    private val ppu: Ppu,
    private var state: OamDmaState = OamDmaState(),
) : OamDma {

    override lateinit var cpuBusRead: (Int) -> Int

    override val isActive
        get() = state.active

    override fun reset() {
        state = OamDmaState()
    }

    override fun start(page: Int) {
        state.page = page and 0xFF
        state.active = true
    }

    override fun transfer() {
        val baseAddress = state.page shl 8

        var offset = 0
        while (offset < 0x100) {
            val value = cpuBusRead(baseAddress + offset)
            ppu.writeOamData(value)
            offset++
        }

        state.active = false
    }
}
