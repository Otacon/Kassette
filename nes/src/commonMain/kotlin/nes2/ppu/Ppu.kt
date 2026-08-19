package nes2.ppu

import nes.util.low8Bits
import nes2.ppuBus.PpuBus

interface Ppu {
    fun cpuReadRegister(address: Int): Int
    fun cpuWriteRegister(address: Int, value: Int)
    fun writeOamData(value: Int)
    fun tick()
}

class PpuNes(
    private val state: PpuState = PpuState(),
    private val ppuBus: PpuBus,
    private val onNmi: () -> Unit
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
            0x2000 -> writeControl(value)
            0x2001 -> state.mask = value.low8Bits()
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

    override fun tick() {
        updateStatusFlags()
        advanceTiming()
    }

    private fun updateStatusFlags() {
        if (state.scanline == 241 && state.dot == 1) {
            state.status = state.status or VBLANK_FLAG

            if (state.control and NMI_ENABLED_FLAG != 0) {
                onNmi()
            }
        }

        if (state.scanline == 261 && state.dot == 1) {
            state.status = state.status and VBLANK_FLAG.inv()
        }
    }

    private fun advanceTiming() {
        state.dot++

        if (state.dot >= DOTS_PER_SCANLINE) {
            state.dot = 0
            state.scanline++

            if (state.scanline >= SCANLINES_PER_FRAME) {
                state.scanline = 0
            }
        }
    }

    private fun writeControl(value: Int) {
        val previousControl = state.control
        val control = value.low8Bits()

        val nametable = control and 0x03

        // PPUCTRL bits 0-1 become nametable bits 10-11 in t.
        val tWithoutNametable = state.t and 0x73FF
        val nametableBits = nametable shl 10

        state.control = control
        state.t = tWithoutNametable or nametableBits

        val nmiWasDisabled = previousControl and NMI_ENABLED_FLAG == 0
        val nmiIsEnabled = control and NMI_ENABLED_FLAG != 0
        val vblankIsActive = state.status and VBLANK_FLAG != 0

        if (nmiWasDisabled && nmiIsEnabled && vblankIsActive) {
            onNmi()
        }
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
            // Palette reads are immediate, but still refresh the internal buffer
            // from the mirrored nametable address underneath.
            state.dataBuffer = ppuBus.read(address - 0x1000)
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
        const val NMI_ENABLED_FLAG = 0x80
        const val DOTS_PER_SCANLINE = 341
        const val SCANLINES_PER_FRAME = 262
    }
}