package nes2.ppu

import nes.util.low8Bits
import nes2.ppuBus.PpuBus

interface Ppu {
    fun cpuReadRegister(address: Int): Int
    fun cpuWriteRegister(address: Int, value: Int)
    fun writeOamData(value: Int)
}

class PpuNes(
    private val state: PpuState = PpuState(),
    private val ppuBus: PpuBus,
) : Ppu {

    override fun cpuReadRegister(address: Int): Int {
        return when (address) {
            0x2002 -> readStatus()
            0x2004 -> state.oam[state.oamAddress]
            0x2007 -> readData()
            else -> 0
        }
    }

    override fun cpuWriteRegister(address: Int, value: Int) {
        when (address) {
            0x2000 -> state.control = value.low8Bits()
            0x2003 -> state.oamAddress = value.low8Bits()
            0x2004 -> writeOamData(value)
            0x2005 -> writeScroll(value)
            0x2006 -> writeAddress(value)
            0x2007 -> writeData(value)
        }
    }

    override fun writeOamData(value: Int) {
        state.oam[state.oamAddress] = value.low8Bits()
        state.oamAddress = (state.oamAddress + 1).low8Bits()
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
            state.t = tWithoutCoarseYAndFineY or coarseYBits or fineYBits
            state.writeToggle = false
        }
    }

    private fun writeAddress(value: Int) {
        val address = value.low8Bits()

        if (!state.writeToggle) {
            // First write: high 6 bits of the PPU address
            val highAddress = (address and 0x3F) shl 8
            val lowAddress = state.t and 0x00FF

            state.t = highAddress or lowAddress
            state.writeToggle = true
        } else {
            // Second write: low 8 bits of the PPU address
            val highAddress = state.t and 0x7F00

            state.t = highAddress or address
            state.v = state.t
            state.writeToggle = false
        }
    }

    private fun readData(): Int {
        val address = state.v
        val value = ppuBus.read(address)
        val result = if (address < 0x3F00) {
            val buffered = state.dataBuffer
            state.dataBuffer = value
            buffered
        } else {
            value
        }
        incrementVramAddress()
        return result
    }

    private fun writeData(value: Int) {
        ppuBus.write(state.v, value)
        incrementVramAddress()
    }

    private fun incrementVramAddress() {
        val increment = if (state.control and 0x04 == 0) 1 else 32
        state.v = (state.v + increment) and 0x3FFF
    }

    private companion object {
        const val VBLANK_FLAG = 0x80
    }
}