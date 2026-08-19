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
    private val onNmi: () -> Unit,
    private val frameBuffer: FrameBuffer,
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
        renderPixel()
        shiftBackgroundRegisters()
        fetchBackground()
        updateScroll()
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
            state.status = state.status and STATUS_FLAGS.inv()
        }
    }

    private fun renderPixel() {
        val scanline = state.scanline
        val dot = state.dot

        val isVisibleScanline = scanline >= 0 && scanline <= 239
        val isVisibleDot = dot >= 1 && dot <= 256

        if (!isVisibleScanline || !isVisibleDot) {
            return
        }

        val x = dot - 1
        val y = scanline

        val backgroundVisible = isBackgroundEnabled() && (x >= 8 || isBackgroundEnabledInLeftmostPixels())

        val backgroundPixel = if (backgroundVisible) {
            getBackgroundPixel()
        } else {
            0
        }

        val pattern = backgroundPixel and 0x03
        val palette = (backgroundPixel shr 2) and 0x03

        val paletteAddress = if (pattern == 0) {
            0x3F00
        } else {
            0x3F00 + (palette shl 2) + pattern
        }

        val color = ppuBus.read(paletteAddress)

        frameBuffer.writePixel(
            x = x,
            y = y,
            color = color,
        )
    }

    private fun getBackgroundPixel(): Int {
        val bit = 0x8000 shr state.fineX

        val patternLow = if (state.patternLowShift and bit != 0) 1 else 0
        val patternHigh = if (state.patternHighShift and bit != 0) 1 else 0
        val attributeLow = if (state.attributeLowShift and bit != 0) 1 else 0
        val attributeHigh = if (state.attributeHighShift and bit != 0) 1 else 0

        val pattern = patternLow or (patternHigh shl 1)
        val palette = attributeLow or (attributeHigh shl 1)

        return pattern or (palette shl 2)
    }

    private fun shiftBackgroundRegisters() {
        if (!isBackgroundShiftDot()) {
            return
        }

        state.patternLowShift = (state.patternLowShift shl 1) and 0xFFFF
        state.patternHighShift = (state.patternHighShift shl 1) and 0xFFFF

        state.attributeLowShift = (state.attributeLowShift shl 1) and 0xFFFF
        state.attributeHighShift = (state.attributeHighShift shl 1) and 0xFFFF
    }

    private fun fetchBackground() {
        if (!isBackgroundFetchDot()) {
            return
        }

        when ((state.dot - 1) % 8) {
            0 -> fetchNametableByte()
            2 -> fetchAttributeByte()
            4 -> fetchPatternLowByte()
            6 -> fetchPatternHighByte()
            7 -> {
                loadBackgroundShiftRegisters()
                incrementCoarseX()
                if (state.dot == 256) {
                    incrementVerticalScroll()
                }
            }
        }
    }

    private fun isBackgroundShiftDot(): Boolean {
        val scanline = state.scanline
        val dot = state.dot

        val isRenderingScanline = (scanline >= 0 && scanline <= 239) || scanline == 261
        val isShiftDot = (dot >= 1 && dot <= 256) || (dot >= 321 && dot <= 336)

        return isRenderingScanline && isShiftDot
    }

    private fun isBackgroundFetchDot(): Boolean {
        val scanline = state.scanline
        val dot = state.dot

        val isRenderingScanline = (scanline >= 0 && scanline <= 239) || scanline == 261
        val isFetchDot = (dot >= 1 && dot <= 256) || (dot >= 321 && dot <= 336)

        return isRenderingScanline && isFetchDot
    }

    private fun isBackgroundEnabled(): Boolean {
        return state.mask and 0x08 != 0
    }

    private fun isBackgroundEnabledInLeftmostPixels(): Boolean {
        return state.mask and 0x02 != 0
    }

    private fun fetchNametableByte() {
        val address = 0x2000 or (state.v and 0x0FFF)

        state.nametableByte = ppuBus.read(address)
    }

    private fun fetchAttributeByte() {
        val nametable = state.v and 0x0C00
        val coarseY = (state.v shr 4) and 0x38
        val coarseX = (state.v shr 2) and 0x07

        val address = 0x23C0 or nametable or coarseY or coarseX

        state.attributeByte = ppuBus.read(address)
    }

    private fun fetchPatternLowByte() {
        val patternTable =
            if (state.control and 0x10 == 0) 0x0000
            else 0x1000

        val tile = state.nametableByte
        val fineY = (state.v shr 12) and 0x07

        val address = patternTable + (tile * 16) + fineY

        state.patternLowByte = ppuBus.read(address)
    }

    private fun fetchPatternHighByte() {
        val patternTable = if (state.control and 0x10 == 0) {
            0x0000
        } else {
            0x1000
        }

        val tile = state.nametableByte
        val fineY = (state.v shr 12) and 0x07

        val address = patternTable + (tile * 16) + fineY + 8

        state.patternHighByte = ppuBus.read(address)
    }

    private fun loadBackgroundShiftRegisters() {
        state.patternLowShift = (state.patternLowShift and 0xFF00) or state.patternLowByte
        state.patternHighShift = (state.patternHighShift and 0xFF00) or state.patternHighByte

        val attributePalette = extractAttributePalette()

        val attributeLow = if (attributePalette and 0x01 != 0) {
            0xFF
        } else {
            0x00
        }

        val attributeHigh = if (attributePalette and 0x02 != 0) {
            0xFF
        } else {
            0x00
        }

        state.attributeLowShift = (state.attributeLowShift and 0xFF00) or attributeLow
        state.attributeHighShift = (state.attributeHighShift and 0xFF00) or attributeHigh
    }

    private fun extractAttributePalette(): Int {
        val coarseX = state.v and 0x001F
        val coarseY = (state.v shr 5) and 0x001F

        val isRightQuadrant = coarseX and 0x02 != 0
        val isBottomQuadrant = coarseY and 0x02 != 0

        val shift = when {
            !isRightQuadrant && !isBottomQuadrant -> 0
            isRightQuadrant && !isBottomQuadrant -> 2
            !isRightQuadrant && isBottomQuadrant -> 4
            else -> 6
        }

        return (state.attributeByte shr shift) and 0x03
    }

    private fun incrementCoarseX() {
        val coarseX = state.v and 0x001F

        if (coarseX == 31) {
            // Wrap coarse X back to 0.
            state.v = state.v and 0x7FE0

            // Move to the horizontally adjacent nametable.
            state.v = state.v xor 0x0400
        } else {
            state.v++
        }
    }

    private fun incrementVerticalScroll() {
        val fineY = (state.v shr 12) and 0x07

        if (fineY < 7) {
            // Still inside the same tile row.
            state.v += 0x1000
            return
        }

        // Fine Y wraps back to 0.
        state.v = state.v and 0x0FFF

        val coarseY = (state.v shr 5) and 0x1F

        when (coarseY) {
            // Move from the bottom of one nametable to the top
            // of the vertically adjacent nametable.
            29 -> {
                state.v = state.v and 0x7C1F
                state.v = state.v xor 0x0800
            }
            // Coarse Y 30/31 are outside the normal visible nametable area.
            // 31 wraps to 0 without switching nametable.
            31 -> {
                state.v = state.v and 0x7C1F
            }

            else -> {
                state.v += 0x20
            }
        }
    }

    private fun updateScroll() {
        if (state.dot == 257) {
            val horizontalBits = state.t and 0x041F
            val vWithoutHorizontalBits = state.v and 0x7BE0

            state.v = vWithoutHorizontalBits or horizontalBits
        }

        if (state.scanline == 261 && state.dot in 280..304) {
            val verticalBits = state.t and 0x7BE0
            val vWithoutVerticalBits = state.v and 0x041F

            state.v = vWithoutVerticalBits or verticalBits
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
        const val SPRITE_OVERFLOW_FLAG = 0x20
        const val SPRITE_ZERO_HIT_FLAG = 0x40
        const val VBLANK_FLAG = 0x80
        const val STATUS_FLAGS = SPRITE_OVERFLOW_FLAG or SPRITE_ZERO_HIT_FLAG or VBLANK_FLAG

        const val NMI_ENABLED_FLAG = 0x80
        const val DOTS_PER_SCANLINE = 341
        const val SCANLINES_PER_FRAME = 262
    }
}