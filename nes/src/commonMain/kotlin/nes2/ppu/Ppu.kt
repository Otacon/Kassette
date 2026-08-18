package nes2.ppu

import nes.util.low8Bits

interface Ppu {
    fun cpuReadRegister(address: Int): Int
    fun cpuWriteRegister(address: Int, value: Int)
    fun writeOamData(value: Int)
}

class PpuNes(
    private val state: PpuState = PpuState(),
) : Ppu {

    override fun cpuReadRegister(address: Int): Int {
        return when (address) {
            0x2002 -> readStatus()
            0x2004 -> state.oam[state.oamAddress]
            else -> 0
        }
    }

    override fun cpuWriteRegister(address: Int, value: Int) {
        when (address) {
            0x2000 -> state.control = value and 0xFF
            0x2003 -> state.oamAddress = value and 0xFF
            0x2004 -> writeOamData(value)
            0x2005 -> writeScroll(value)
        }
    }

    override fun writeOamData(value: Int) {
        state.oam[state.oamAddress] = value and 0xFF
        state.oamAddress = (state.oamAddress + 1) and 0xFF
    }

    private fun readStatus(): Int {
        val value = state.status

        state.status = state.status and VBLANK_FLAG.inv()
        state.writeToggle = false

        return value
    }

    private fun writeScroll(value: Int) {
        val scroll = value.low8Bits()

        if (!state.writeToggle) {
            // First write: horizontal scroll
            val coarseX = scroll shr 3
            val fineX = scroll and 0x07

            val tWithoutCoarseX = state.t and 0x7FE0

            state.t = tWithoutCoarseX or coarseX
            state.fineX = fineX
            state.writeToggle = true
        } else {
            // Second write: vertical scroll
            val coarseY = scroll shr 3
            val fineY = scroll and 0x07

            val tWithoutCoarseYAndFineY = state.t and 0x0C1F
            val coarseYBits = coarseY shl 5
            val fineYBits = fineY shl 12

            state.t =
                tWithoutCoarseYAndFineY or
                        coarseYBits or
                        fineYBits

            state.writeToggle = false
        }
    }

    private companion object {
        const val VBLANK_FLAG = 0x80
    }
}