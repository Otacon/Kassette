package nes.ppu

import nes.Timing
import nes.util.low8Bits
import nes.util.toUnsignedInt

class Ppu(
    private val bus: PpuBus,
) {
    private val framebuffers = Array(2) { IntArray(SCREEN_WIDTH * SCREEN_HEIGHT) }
    private var renderFramebufferIndex = 0
    private var completedFramebufferIndex = 0

    val framebuffer: IntArray
        get() = framebuffers[renderFramebufferIndex]
    val completedFramebuffer: IntArray
        get() = framebuffers[completedFramebufferIndex]
    val oam = ByteArray(256)

    var ctrl = 0
        private set
    var mask = 0
        private set
    var status = 0
        private set
    var oamAddress = 0
        private set
    var v = 0
        private set
    var t = 0
        private set
    var fineX = 0
        private set
    var writeLatch = false
        private set
    var scanline = -1
        private set
    var cycle = LAST_DOT
        private set
    var frameComplete = false
        private set
    var nmiRequested = false
        private set
    var nmiLine = false
        private set

    var timing: Timing = Timing.DEFAULT

    private val secondaryOam = ByteArray(32) { 0xFF.toByte() }
    private val activeSpriteX = IntArray(8)
    private val activeSpriteAttributes = IntArray(8)
    private val activeSpriteLow = IntArray(8)
    private val activeSpriteHigh = IntArray(8)
    private val fetchedSpriteX = IntArray(8)
    private val fetchedSpriteAttributes = IntArray(8)
    private val fetchedSpriteLow = IntArray(8)
    private val fetchedSpriteHigh = IntArray(8)

    private var activeSpriteCount = 0
    private var activeSpriteZero = false
    private var fetchedSpriteCount = 0
    private var fetchedSpriteZero = false
    private var evaluatedSpriteZero = false
    private var secondaryOamAddress = 0
    private var oamCopyBuffer = 0xFF
    private var evalSprite = 0
    private var evalByte = 0
    private var evalInRange = false
    private var evalDone = false

    private var bgPatternLow = 0
    private var bgPatternHigh = 0
    private var bgAttributeLow = 0
    private var bgAttributeHigh = 0
    private var nextTile = 0
    private var nextAttribute = 0
    private var nextPatternLow = 0
    private var nextPatternHigh = 0

    private var openBus = 0
    private var ppuBusAddress = 0
    private var renderingMask = 0
    private var readBuffer = 0
    private var preventVblank = false
    private var frameNumber = 1
    private var pendingVAddress = 0
    private var pendingVAddressDelay = 0
    private var pendingDataReadDelay = 0
    private var pendingDataWriteValue = 0
    private var pendingDataWriteDelay = 0
    private var pendingVramIncrement = false
    private val paletteCache = IntArray(32)
    private var paletteCacheMask = -1

    fun reset() = reset(softReset = false)

    fun reset(softReset: Boolean) {
        val retainedStatus = status
        val retainedAddress = v
        ctrl = 0
        mask = 0
        status = if (softReset) retainedStatus else 0
        oamAddress = 0
        v = if (softReset) retainedAddress else 0
        t = 0
        fineX = 0
        writeLatch = false
        scanline = -1
        cycle = LAST_DOT
        frameComplete = false
        nmiRequested = false
        nmiLine = false
        openBus = 0
        ppuBusAddress = 0
        renderingMask = 0
        readBuffer = 0
        preventVblank = false
        frameNumber = 1
        pendingVAddressDelay = 0
        pendingDataReadDelay = 0
        pendingDataWriteDelay = 0
        pendingVramIncrement = false
        paletteCacheMask = -1
        activeSpriteCount = 0
        fetchedSpriteCount = 0
        activeSpriteZero = false
        fetchedSpriteZero = false
        evaluatedSpriteZero = false
        secondaryOamAddress = 0
        oamCopyBuffer = 0xFF
        evalSprite = 0
        evalByte = 0
        evalInRange = false
        evalDone = false
        bgPatternLow = 0
        bgPatternHigh = 0
        bgAttributeLow = 0
        bgAttributeHigh = 0
        nextTile = 0
        nextAttribute = 0
        nextPatternLow = 0
        nextPatternHigh = 0
        renderFramebufferIndex = 0
        completedFramebufferIndex = 0
        framebuffers.forEach { it.fill(0) }
    }

    fun pollNmi(): Boolean {
        val value = nmiRequested
        nmiRequested = false
        return value
    }

    fun clearFrameComplete() {
        frameComplete = false
    }

    fun step() {
        if (cycle == LAST_DOT) {
            cycle = 0
            scanline++
            if (scanline > timing.scanlinesPerFrame - 2) scanline = -1
            processScanlineStart()
        } else {
            cycle++
            processDot()
        }
        processDelayedRegisterOperations()
    }

    fun cpuRead(register: Int): Int = when (register and 7) {
        2 -> readStatus()
        4 -> readOamData()
        7 -> readData()
        else -> openBus
    }

    fun cpuWrite(register: Int, value: Int) {
        val data = value.low8Bits()
        openBus = data
        when (register and 7) {
            0 -> writeControl(data)
            1 -> mask = data
            3 -> oamAddress = data
            4 -> writeOamData(data)
            5 -> writeScroll(data)
            6 -> writeAddress(data)
            7 -> scheduleDataWrite(data)
        }
    }

    fun writeOamDma(page: ByteArray) {
        var source = 0
        var destination = oamAddress
        while (source < page.size && source < 256) {
            var value = page[source].toUnsignedInt()
            if ((destination and 3) == 2) value = value and 0xE3
            oam[destination] = value.toByte()
            destination = (destination + 1).low8Bits()
            source++
        }
    }

    fun ppuRead(address: Int): Int = bus.read(address)

    fun ppuWrite(address: Int, value: Int) = bus.write(address, value)

    private fun processScanlineStart() {
        if (scanline in 0 until SCREEN_HEIGHT) {
            activeSpriteCount = fetchedSpriteCount
            activeSpriteZero = fetchedSpriteZero
            fetchedSpriteCount = 0
            fetchedSpriteZero = false
            fetchedSpriteX.copyInto(activeSpriteX)
            fetchedSpriteAttributes.copyInto(activeSpriteAttributes)
            fetchedSpriteLow.copyInto(activeSpriteLow)
            fetchedSpriteHigh.copyInto(activeSpriteHigh)
        }

        if (scanline == -1) {
            renderFramebufferIndex = (renderFramebufferIndex + 1) % framebuffers.size
            status = status and STATUS_SPRITE_ZERO_HIT.inv() and STATUS_SPRITE_OVERFLOW.inv()
            fetchedSpriteCount = 0
        } else if (scanline == SCREEN_HEIGHT) {
            completedFramebufferIndex = renderFramebufferIndex
            frameComplete = true
            frameNumber++
            fetchedSpriteCount = 0
            fetchedSpriteZero = false
        }
    }

    private fun processDot() {
        if (scanline == timing.nmiScanline && cycle == 1) {
            if (!preventVblank) {
                status = status or STATUS_VBLANK
                updateNmiLine()
            }
            preventVblank = false
        }

        if (
            timing.scanlinesPerFrame == 312 && timing.nmiScanline == 241 &&
            scanline in 265..310 && cycle in 2..340 && (cycle and 1) == 0
        ) {
            oamAddress = (oamAddress + 1).low8Bits()
        }

        if (scanline !in -1 until SCREEN_HEIGHT) return

        if (cycle in 1..256) {
            if (scanline >= 0) drawPixel()
            if (renderingEnabled()) {
                if (scanline >= 0) processSpriteEvaluation()
                shiftBackground()
                fetchBackground()
                if ((cycle and 7) == 0) {
                    incrementCoarseX()
                    if (cycle == 256) incrementY()
                }
            }
        } else if (cycle in 257..320) {
            if (cycle == 257) {
                if (scanline == -1) {
                    fetchedSpriteCount = 0
                    fetchedSpriteZero = false
                } else {
                    fetchedSpriteCount = secondaryOamAddress.coerceAtMost(32) / 4
                    fetchedSpriteZero = evaluatedSpriteZero
                }
                if (renderingEnabled()) transferHorizontalAddress()
            }
            if (renderingEnabled()) {
                oamAddress = 0
                val phase = (cycle - 257) and 7
                when (phase) {
                    0, 2 -> readVram(0x2000 or (v and 0x0FFF))
                    4 -> fetchSprite((cycle - 257) shr 3)
                }
                if (cycle == 257) {
                    secondaryOamAddress = 0
                } else if (((cycle - 1) and 4) == 0) {
                    secondaryOamAddress++
                }
                if (scanline == -1 && cycle in 280..304) transferVerticalAddress()
            }
        } else if (cycle in 321..336 && renderingEnabled()) {
            if (cycle == 321) secondaryOamAddress++
            shiftBackground()
            fetchBackground()
            if (cycle == 328 || cycle == 336) incrementCoarseX()
        } else if ((cycle == 337 || cycle == 339) && renderingEnabled()) {
            readVram(0x2000 or (v and 0x0FFF))
            if (
                cycle == 339 && scanline == -1 && timing.skipsOddFrameDot &&
                (frameNumber and 1) != 0
            ) {
                cycle = LAST_DOT
            }
        }

        if (cycle == MAPPER_SCANLINE_DOT && renderingEnabled()) bus.clockScanline()
        if (scanline == -1 && cycle == 1) {
            status = status and STATUS_VBLANK.inv()
            setNmiLine(false)
        }
    }

    private fun drawPixel() {
        val x = cycle - 1
        val index = scanline * SCREEN_WIDTH + x
        if (!renderingEnabled()) {
            val paletteIndex = if ((v and 0x3F00) == 0x3F00) v and 0x1F else 0
            framebuffer[index] = paletteColor(paletteIndex)
            return
        }
        var backgroundColor = 0
        var backgroundPalette = 0
        if ((mask and MASK_BACKGROUND) != 0 && (x >= 8 || (mask and MASK_BACKGROUND_LEFT) != 0)) {
            val bit = 0x8000 shr fineX
            backgroundColor =
                (if ((bgPatternLow and bit) != 0) 1 else 0) or
                    (if ((bgPatternHigh and bit) != 0) 2 else 0)
            backgroundPalette =
                (if ((bgAttributeLow and bit) != 0) 1 else 0) or
                    (if ((bgAttributeHigh and bit) != 0) 2 else 0)
        }

        val backgroundPaletteIndex = if (backgroundColor == 0) 0 else backgroundPalette * 4 + backgroundColor
        framebuffer[index] = paletteColor(backgroundPaletteIndex)

        if ((mask and MASK_SPRITES) == 0 || (x < 8 && (mask and MASK_SPRITES_LEFT) == 0)) return

        var sprite = 0
        while (sprite < activeSpriteCount) {
            val offset = x - activeSpriteX[sprite]
            if (offset in 0..7) {
                val attributes = activeSpriteAttributes[sprite]
                val bit = if ((attributes and 0x40) != 0) offset else 7 - offset
                val spriteColor =
                    ((activeSpriteLow[sprite] shr bit) and 1) or
                        (((activeSpriteHigh[sprite] shr bit) and 1) shl 1)
                if (spriteColor != 0) {
                    if (
                        sprite == 0 && activeSpriteZero && backgroundColor != 0 && x != 255 &&
                        (mask and MASK_BACKGROUND) != 0
                    ) {
                        status = status or STATUS_SPRITE_ZERO_HIT
                    }
                    if (backgroundColor == 0 || (attributes and 0x20) == 0) {
                        framebuffer[index] = paletteColor(0x10 + (attributes and 3) * 4 + spriteColor)
                    }
                    return
                }
            }
            sprite++
        }
    }

    private fun shiftBackground() {
        bgPatternLow = (bgPatternLow shl 1) and 0xFFFF
        bgPatternHigh = (bgPatternHigh shl 1) and 0xFFFF
        bgAttributeLow = (bgAttributeLow shl 1) and 0xFFFF
        bgAttributeHigh = (bgAttributeHigh shl 1) and 0xFFFF
    }

    private fun fetchBackground() {
        when (cycle and 7) {
            0 -> {
                bgPatternLow = (bgPatternLow and 0xFF00) or nextPatternLow
                bgPatternHigh = (bgPatternHigh and 0xFF00) or nextPatternHigh
                bgAttributeLow = (bgAttributeLow and 0xFF00) or if ((nextAttribute and 1) != 0) 0xFF else 0
                bgAttributeHigh = (bgAttributeHigh and 0xFF00) or if ((nextAttribute and 2) != 0) 0xFF else 0
            }

            1 -> nextTile = readVram(0x2000 or (v and 0x0FFF))
            3 -> {
                val address = 0x23C0 or (v and 0x0C00) or ((v shr 4) and 0x38) or ((v shr 2) and 7)
                val shift = ((v shr 4) and 4) or (v and 2)
                nextAttribute = (readVram(address) shr shift) and 3
            }

            5 -> nextPatternLow = readVram(backgroundPatternAddress())
            7 -> nextPatternHigh = readVram(backgroundPatternAddress() + 8)
        }
    }

    private fun backgroundPatternAddress(): Int =
        (if ((ctrl and 0x10) != 0) 0x1000 else 0) + nextTile * 16 + ((v shr 12) and 7)

    private fun processSpriteEvaluation() {
        if (cycle <= 64) {
            if (cycle == 1) {
                secondaryOam.fill(0xFF.toByte())
                secondaryOamAddress = 0
                evaluatedSpriteZero = false
            }
            oamCopyBuffer = 0xFF
            return
        }

        if (cycle == 65) {
            evalSprite = oamAddress shr 2
            evalByte = oamAddress and 3
            evalInRange = false
            evalDone = false
            secondaryOamAddress = 0
        }
        if (evalDone) return

        if ((cycle and 1) != 0) {
            oamCopyBuffer = oam[((evalSprite shl 2) or evalByte) and 0xFF].toUnsignedInt()
            return
        }

        if (secondaryOamAddress < 32) {
            if (!evalInRange) {
                if (spriteInRange(oamCopyBuffer)) {
                    evalInRange = true
                    if (secondaryOamAddress == 0 && evalSprite == 0 && evalByte == 0) evaluatedSpriteZero = true
                    secondaryOam[secondaryOamAddress++] = oamCopyBuffer.toByte()
                    evalByte = (evalByte + 1) and 3
                } else {
                    advanceEvaluationSprite()
                }
            } else {
                secondaryOam[secondaryOamAddress++] = oamCopyBuffer.toByte()
                evalByte++
                if (evalByte == 4) {
                    evalByte = 0
                    evalInRange = false
                    advanceEvaluationSprite()
                }
            }
        } else {
            if (spriteInRange(oamCopyBuffer)) {
                status = status or STATUS_SPRITE_OVERFLOW
                evalByte++
                if (evalByte == 4) {
                    evalByte = 0
                    evalSprite = (evalSprite + 1) and 0x3F
                }
            } else {
                evalSprite = (evalSprite + 1) and 0x3F
                evalByte = (evalByte + 1) and 3
            }
            if (evalSprite == 0) evalDone = true
        }
        oamAddress = ((evalSprite shl 2) or evalByte) and 0xFF
    }

    private fun advanceEvaluationSprite() {
        evalSprite = (evalSprite + 1) and 0x3F
        evalByte = 0
        if (evalSprite == 0) evalDone = true
    }

    private fun spriteInRange(spriteY: Int): Boolean {
        val height = if ((ctrl and 0x20) != 0) 16 else 8
        return scanline >= spriteY && scanline < spriteY + height
    }

    private fun fetchSprite(slot: Int) {
        val base = slot * 4
        val spriteY = secondaryOam[base].toUnsignedInt()
        val tile = secondaryOam[base + 1].toUnsignedInt()
        val attributes = secondaryOam[base + 2].toUnsignedInt()
        var row = scanline - spriteY
        val height = if ((ctrl and 0x20) != 0) 16 else 8
        if ((attributes and 0x80) != 0) row = height - 1 - row
        val address = if (height == 16) {
            ((tile and 1) shl 12) + ((tile and 0xFE) shl 4) + ((row and 8) shl 1) + (row and 7)
        } else {
            (if ((ctrl and 0x08) != 0) 0x1000 else 0) + tile * 16 + (row and 7)
        }
        fetchedSpriteX[slot] = secondaryOam[base + 3].toUnsignedInt()
        fetchedSpriteAttributes[slot] = attributes
        fetchedSpriteLow[slot] = readVram(address)
        fetchedSpriteHigh[slot] = readVram(address + 8)
    }

    private fun readStatus(): Int {
        val result = (status and 0xE0) or (openBus and 0x1F)
        if (scanline == timing.nmiScanline && cycle == 0) preventVblank = true
        status = status and STATUS_VBLANK.inv()
        writeLatch = false
        setNmiLine(false)
        openBus = result
        return result
    }

    private fun readOamData(): Int {
        val result = if (renderingEnabled() && scanline <= 239) {
            if (cycle in 257..340 || cycle == 0) {
                oamCopyBuffer = secondaryOam[secondaryOamAddress and 0x1F].toUnsignedInt()
            }
            oamCopyBuffer
        } else {
            oam[oamAddress].toUnsignedInt()
        }
        openBus = result
        return result
    }

    private fun readData(): Int {
        val address = v and 0x3FFF
        val result = if (address >= 0x3F00) {
            (ppuRead(address) and grayscaleMask()) or (openBus and 0xC0)
        } else {
            readBuffer
        }
        pendingDataReadDelay = DATA_ACCESS_DELAY
        openBus = result
        return result
    }

    private fun writeControl(data: Int) {
        ctrl = data
        t = (t and 0xF3FF) or ((data and 3) shl 10)
        val nmiEnabled = (ctrl and 0x80) != 0
        setNmiLine(nmiEnabled && (status and STATUS_VBLANK) != 0)
    }

    private fun writeOamData(data: Int) {
        if (renderingEnabled() && scanline in -1 until SCREEN_HEIGHT) {
            oamAddress = (oamAddress + 4) and 0xFC
            return
        }
        val stored = if ((oamAddress and 3) == 2) data and 0xE3 else data
        oam[oamAddress] = stored.toByte()
        oamAddress = (oamAddress + 1).low8Bits()
    }

    private fun writeScroll(data: Int) {
        if (!writeLatch) {
            fineX = data and 7
            t = (t and 0xFFE0) or (data shr 3)
        } else {
            t = (t and 0x8FFF) or ((data and 7) shl 12)
            t = (t and 0xFC1F) or ((data and 0xF8) shl 2)
        }
        writeLatch = !writeLatch
    }

    private fun writeAddress(data: Int) {
        if (!writeLatch) {
            t = (t and 0x00FF) or ((data and 0x3F) shl 8)
        } else {
            t = (t and 0x7F00) or data
            pendingVAddress = t
            pendingVAddressDelay = ADDRESS_UPDATE_DELAY
        }
        writeLatch = !writeLatch
    }

    private fun scheduleDataWrite(data: Int) {
        pendingDataWriteValue = data
        pendingDataWriteDelay = DATA_ACCESS_DELAY
    }

    private fun processDelayedRegisterOperations() {
        if (pendingVAddressDelay > 0 && --pendingVAddressDelay == 0) {
            v = pendingVAddress
            ppuBusAddress = v and 0x3FFF
        }

        if (pendingVramIncrement) {
            pendingVramIncrement = false
            incrementVramAddress()
        }
        if (pendingDataReadDelay > 0 && --pendingDataReadDelay == 0) {
            val address = ppuBusAddress and 0x3FFF
            readBuffer = readVram(if (address >= 0x3F00) address - 0x1000 else address)
            pendingVramIncrement = true
        }
        if (pendingDataWriteDelay > 0 && --pendingDataWriteDelay == 0) {
            val address = ppuBusAddress and 0x3FFF
            val value = if (address < 0x3F00 && renderingEnabled() && scanline in -1 until SCREEN_HEIGHT) {
                address.low8Bits()
            } else {
                pendingDataWriteValue
            }
            bus.write(address, value)
            if (address >= 0x3F00) paletteCacheMask = -1
            pendingVramIncrement = true
        }
        if (renderingEnabled() && (mask and (MASK_BACKGROUND or MASK_SPRITES)) == 0) {
            ppuBusAddress = v and 0x3FFF
        }
        renderingMask = mask
    }

    private fun incrementVramAddress() {
        if (renderingEnabled() && scanline in -1 until SCREEN_HEIGHT) {
            incrementCoarseX()
            incrementY()
        } else {
            v = (v + if ((ctrl and 0x04) != 0) 32 else 1) and 0x7FFF
            ppuBusAddress = v and 0x3FFF
        }
    }

    private fun incrementCoarseX() {
        v = if ((v and 0x001F) == 31) {
            (v and 0xFFE0) xor 0x0400
        } else {
            (v + 1) and 0x7FFF
        }
    }

    private fun incrementY() {
        v = if ((v and 0x7000) != 0x7000) {
            (v + 0x1000) and 0x7FFF
        } else {
            var next = v and 0x8FFF
            var y = (v and 0x03E0) shr 5
            if (y == 29) {
                y = 0
                next = next xor 0x0800
            } else if (y == 31) {
                y = 0
            } else {
                y++
            }
            (next and 0xFC1F) or (y shl 5)
        }
    }

    private fun transferHorizontalAddress() {
        v = (v and 0xFBE0) or (t and 0x041F)
    }

    private fun transferVerticalAddress() {
        v = (v and 0x841F) or (t and 0x7BE0)
    }

    private fun updateNmiLine() {
        setNmiLine((ctrl and 0x80) != 0 && (status and STATUS_VBLANK) != 0)
    }

    private fun readVram(address: Int): Int {
        ppuBusAddress = address and 0x3FFF
        return bus.read(ppuBusAddress)
    }

    private fun setNmiLine(asserted: Boolean) {
        if (!nmiLine && asserted) nmiRequested = true
        nmiLine = asserted
    }

    private fun paletteColor(index: Int): Int {
        val mask = grayscaleMask()
        if (mask != paletteCacheMask) rebuildPaletteCache(mask)
        return paletteCache[index and 0x1F]
    }

    private fun rebuildPaletteCache(mask: Int) {
        var index = 0
        while (index < paletteCache.size) {
            paletteCache[index] = Palette.COLORS[ppuRead(0x3F00 + index) and mask]
            index++
        }
        paletteCacheMask = mask
    }

    private fun grayscaleMask(): Int = if ((mask and 1) != 0) 0x30 else 0x3F

    private fun renderingEnabled(): Boolean = (renderingMask and (MASK_BACKGROUND or MASK_SPRITES)) != 0

    companion object {
        private const val SCREEN_WIDTH = 256
        private const val SCREEN_HEIGHT = 240
        private const val LAST_DOT = 340
        private const val MAPPER_SCANLINE_DOT = 260
        private const val ADDRESS_UPDATE_DELAY = 3
        private const val DATA_ACCESS_DELAY = 5
        private const val MASK_BACKGROUND_LEFT = 0x02
        private const val MASK_SPRITES_LEFT = 0x04
        private const val MASK_BACKGROUND = 0x08
        private const val MASK_SPRITES = 0x10
        private const val STATUS_SPRITE_OVERFLOW = 0x20
        private const val STATUS_SPRITE_ZERO_HIT = 0x40
        private const val STATUS_VBLANK = 0x80
    }
}
