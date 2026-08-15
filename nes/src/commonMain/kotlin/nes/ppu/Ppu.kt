package nes.ppu

import nes.Timing
import nes.util.low8Bits
import nes.util.toUnsignedInt

class Ppu(
    private val bus: PpuBus,
) {
    var state = PpuState()
        private set
    private val argbFramebuffers = arrayOfNulls<IntArray>(2)

    fun framebuffer(): IntArray = argbFramebuffer(state.renderFramebufferIndex)
    var timing: Timing = Timing.DEFAULT
    internal var cpuCycle: Long = -1

    private val paletteColorIdCache = IntArray(32)
    init {
        state.counters[COUNTER_PALETTE_COLOR_ID_CACHE_MASK] = -1
        state.counters[COUNTER_FRAME_NUMBER] = 1
    }

    fun reset() = reset(softReset = false)

    fun reset(softReset: Boolean) {
        val retainedStatus = state.status
        val retainedAddress = state.v
        val retainedOam = state.oam.copyOf()
        val retainedSecondaryOam = state.secondaryOam.copyOf()
        state = PpuState()
        retainedOam.copyInto(state.oam)
        retainedSecondaryOam.copyInto(state.secondaryOam)
        state.status = if (softReset) retainedStatus else 0
        state.v = if (softReset) retainedAddress else 0
        state.counters[COUNTER_FRAME_NUMBER] = 1
        state.counters[COUNTER_PALETTE_COLOR_ID_CACHE_MASK] = -1
        state.counters[COUNTER_OAM_COPY_BUFFER] = 0
        cpuCycle = -1
        argbFramebuffers.forEach { it?.fill(0) }
    }

    fun pollNmi(): Boolean {
        val value = state.nmiRequested
        state.nmiRequested = false
        return value
    }

    fun clearFrameComplete() {
        state.frameComplete = false
    }

    fun captureState(): PpuState = state.copy(
        frameColorIds = Array(state.frameColorIds.size) { state.frameColorIds[it].copyOf() },
        oam = state.oam.copyOf(),
        secondaryOam = state.secondaryOam.copyOf(),
        activeSpriteX = state.activeSpriteX.copyOf(),
        activeSpriteAttributes = state.activeSpriteAttributes.copyOf(),
        activeSpriteLow = state.activeSpriteLow.copyOf(),
        activeSpriteHigh = state.activeSpriteHigh.copyOf(),
        fetchedSpriteX = state.fetchedSpriteX.copyOf(),
        fetchedSpriteAttributes = state.fetchedSpriteAttributes.copyOf(),
        fetchedSpriteLow = state.fetchedSpriteLow.copyOf(),
        fetchedSpriteHigh = state.fetchedSpriteHigh.copyOf(),
        openBusDecayStamps = state.openBusDecayStamps.copyOf(),
        counters = state.counters.copyOf(),
        flags = state.flags.copyOf(),
    )

    fun restoreState(state: PpuState) {
        this.state = state
        this.state.counters[COUNTER_PALETTE_COLOR_ID_CACHE_MASK] = -1
        cpuCycle = -1
        argbFramebuffers.forEach { it?.fill(0) }
    }

    fun step() {
        state.ppuCycle++
        if (state.cycle == LAST_DOT) {
            state.cycle = 0
            state.scanline++
            if (state.scanline > timing.scanlinesPerFrame - 2) state.scanline = -1
            processScanlineStart()
        } else {
            state.cycle++
            processDot()
        }
        processDelayedRegisterOperations()
    }

    fun cpuRead(register: Int): Int = when (register and 7) {
        2 -> readStatus()
        4 -> readOamData()
        7 -> readData()
        else -> applyOpenBus(0xFF, 0)
    }

    fun cpuWrite(register: Int, value: Int) {
        val data = value.low8Bits()
        setOpenBus(0xFF, data)
        when (register and 7) {
            0 -> writeControl(data)
            1 -> state.mask = data
            3 -> state.oamAddress = data
            4 -> writeOamData(data)
            5 -> writeScroll(data)
            6 -> writeAddress(data)
            7 -> scheduleDataWrite(data)
        }
    }

    fun writeOamDma(page: ByteArray) {
        var source = 0
        var destination = state.oamAddress
        while (source < page.size && source < 256) {
            var value = page[source].toUnsignedInt()
            if ((destination and 3) == 2) value = value and 0xE3
            state.oam[destination] = value.toByte()
            destination = (destination + 1).low8Bits()
            source++
        }
    }

    fun ppuRead(address: Int): Int = bus.read(address)

    fun ppuWrite(address: Int, value: Int) {
        bus.write(address, value)
        if ((address and 0x3FFF) >= 0x3F00) state.counters[COUNTER_PALETTE_COLOR_ID_CACHE_MASK] = -1
    }

    fun ppuBusState(): PpuBusState = bus.captureState()

    fun restorePpuBusState(state: PpuBusState) {
        bus.restoreState(state)
        this.state.counters[COUNTER_PALETTE_COLOR_ID_CACHE_MASK] = -1
    }

    private fun processScanlineStart() {
        val dotSkipped = state.flags[FLAG_PREVIOUS_DOT_SKIPPED]
        if (state.scanline == SCREEN_HEIGHT) {
            setBusAddress(state.v)
        } else if (state.scanline in 0 until SCREEN_HEIGHT && renderingEnabled()) {
            state.counters[COUNTER_SECONDARY_OAM_ADDRESS] = 0
            if (state.scanline > 0 || !dotSkipped) setBusAddress(backgroundPatternAddress())
        }
        state.flags[FLAG_PREVIOUS_DOT_SKIPPED] = false
        if (state.scanline in 0 until SCREEN_HEIGHT) {
            state.counters[COUNTER_ACTIVE_SPRITE_COUNT] = state.counters[COUNTER_FETCHED_SPRITE_COUNT]
            state.flags[FLAG_ACTIVE_SPRITE_ZERO] = state.flags[FLAG_FETCHED_SPRITE_ZERO]
            state.counters[COUNTER_FETCHED_SPRITE_COUNT] = 0
            state.flags[FLAG_FETCHED_SPRITE_ZERO] = false
            state.fetchedSpriteX.copyInto(state.activeSpriteX)
            state.fetchedSpriteAttributes.copyInto(state.activeSpriteAttributes)
            state.fetchedSpriteLow.copyInto(state.activeSpriteLow)
            state.fetchedSpriteHigh.copyInto(state.activeSpriteHigh)
            state.flags[FLAG_ACTIVE_DOT_SKIPPED] = dotSkipped
            if (dotSkipped) {
                var sprite = 0
                while (sprite < state.counters[COUNTER_ACTIVE_SPRITE_COUNT]) {
                    state.activeSpriteX[sprite]++
                    sprite++
                }
            }
        }

        if (state.scanline == -1) {
            if (currentRenderingEnabled() && !isPalTiming()) corruptOamRow()
            state.renderFramebufferIndex = (state.renderFramebufferIndex + 1) % state.frameColorIds.size
            state.status = state.status and STATUS_SPRITE_ZERO_HIT.inv() and STATUS_SPRITE_OVERFLOW.inv()
            state.counters[COUNTER_FETCHED_SPRITE_COUNT] = 0
        } else if (state.scanline == SCREEN_HEIGHT) {
            state.completedFramebufferIndex = state.renderFramebufferIndex
            state.frameComplete = true
            state.counters[COUNTER_FRAME_NUMBER]++
            state.counters[COUNTER_FETCHED_SPRITE_COUNT] = 0
            state.flags[FLAG_FETCHED_SPRITE_ZERO] = false
        }
    }

    private fun processDot() {
        if (state.scanline == timing.nmiScanline && state.cycle == 1) {
            if (!state.flags[FLAG_PREVENT_VBLANK]) {
                state.status = state.status or STATUS_VBLANK
                updateNmiLine()
            }
            state.flags[FLAG_PREVENT_VBLANK] = false
        }

        if (
            timing.scanlinesPerFrame == 312 && timing.nmiScanline == 241 &&
            state.scanline in 265..310 && state.cycle in 2..340 && (state.cycle and 1) == 0
        ) {
            state.oamAddress = (state.oamAddress + 1).low8Bits()
        }

        if (state.scanline !in -1 until SCREEN_HEIGHT) return

        if (state.cycle in 1..256) {
            if (state.scanline >= 0) {
                advanceSpriteCounters()
                drawPixel()
                if (renderingEnabled()) shiftActiveSprites()
            }
            if (renderingEnabled()) {
                if (state.scanline >= 0) processSpriteEvaluation()
                shiftBackground()
                fetchBackground()
                if ((state.cycle and 7) == 0) {
                    incrementCoarseX()
                    if (state.cycle == 256) incrementY()
                }
            }
        } else if (state.cycle in 257..320) {
            if (state.cycle == 257) {
                state.counters[COUNTER_FETCHED_SPRITE_COUNT] = 0
                state.flags[FLAG_FETCHED_SPRITE_ZERO] = false
                if (renderingEnabled()) transferHorizontalAddress()
            }
            if (renderingEnabled()) {
                state.oamAddress = 0
                val phase = (state.cycle - 257) and 7
                when (phase) {
                    0, 2 -> readVram(0x2000 or (state.v and 0x0FFF))
                    4 -> fetchSprite((state.cycle - 257) shr 3)
                }
                if (state.cycle == 257) {
                    state.counters[COUNTER_SECONDARY_OAM_ADDRESS] = 0
                } else if (((state.cycle - 1) and 4) == 0) {
                    state.counters[COUNTER_SECONDARY_OAM_ADDRESS]++
                }
                if (state.scanline == -1 && state.cycle in 280..304) transferVerticalAddress()
            }
        } else if (state.cycle in 321..336 && renderingEnabled()) {
            if (state.cycle == 321) state.counters[COUNTER_SECONDARY_OAM_ADDRESS]++
            shiftBackground()
            fetchBackground()
            if (state.cycle == 328 || state.cycle == 336) incrementCoarseX()
        } else if ((state.cycle == 337 || state.cycle == 339) && currentRenderingEnabled()) {
            state.counters[COUNTER_NEXT_TILE] = readVram(0x2000 or (state.v and 0x0FFF))
            if (
                state.cycle == 339 && state.scanline == -1 && timing.skipsOddFrameDot &&
                (state.counters[COUNTER_FRAME_NUMBER] and 1) != 0
            ) {
                state.flags[FLAG_PREVIOUS_DOT_SKIPPED] = true
                state.cycle = LAST_DOT
            }
        }

        if (state.scanline == -1 && state.cycle == 1) {
            state.status = state.status and STATUS_VBLANK.inv()
            setNmiLineAsserted(false)
        }
    }

    private fun drawPixel() {
        val x = state.cycle - 1
        val index = state.scanline * SCREEN_WIDTH + x
        if (!currentRenderingEnabled()) {
            val paletteIndex = if ((state.v and 0x3F00) == 0x3F00) state.v and 0x1F else 0
            writePixel(index, paletteIndex)
            return
        }
        var backgroundColor = 0
        var backgroundPalette = 0
        if ((state.mask and MASK_BACKGROUND) != 0 && (x >= 8 || (state.mask and MASK_BACKGROUND_LEFT) != 0)) {
            val bit = 0x8000 shr state.fineX
            backgroundColor =
                (if ((state.counters[COUNTER_BG_PATTERN_LOW] and bit) != 0) 1 else 0) or
                    (if ((state.counters[COUNTER_BG_PATTERN_HIGH] and bit) != 0) 2 else 0)
            backgroundPalette =
                (if ((state.counters[COUNTER_BG_ATTRIBUTE_LOW] and bit) != 0) 1 else 0) or
                    (if ((state.counters[COUNTER_BG_ATTRIBUTE_HIGH] and bit) != 0) 2 else 0)
        }

        val backgroundPaletteIndex = if (backgroundColor == 0) 0 else backgroundPalette * 4 + backgroundColor
        writePixel(index, backgroundPaletteIndex)

        if (!renderingEnabled() || (state.mask and MASK_SPRITES) == 0 ||
            (x < 8 && (state.mask and MASK_SPRITES_LEFT) == 0)) return

        var sprite = 0
        while (sprite < state.counters[COUNTER_ACTIVE_SPRITE_COUNT]) {
            if (state.flags[FLAG_ACTIVE_DOT_SKIPPED] || state.activeSpriteX[sprite] == 0) {
                val attributes = state.activeSpriteAttributes[sprite]
                val spriteColor =
                    ((state.activeSpriteLow[sprite] shr 7) and 1) or
                        (((state.activeSpriteHigh[sprite] shr 7) and 1) shl 1)
                if (spriteColor != 0) {
                    if (
                        sprite == 0 && state.flags[FLAG_ACTIVE_SPRITE_ZERO] && backgroundColor != 0 && x != 255 &&
                        (state.mask and MASK_BACKGROUND) != 0
                    ) {
                        state.status = state.status or STATUS_SPRITE_ZERO_HIT
                    }
                    if (backgroundColor == 0 || (attributes and 0x20) == 0) {
                        writePixel(index, 0x10 + (attributes and 3) * 4 + spriteColor)
                    }
                    return
                }
            }
            sprite++
        }
    }

    private fun advanceSpriteCounters() {
        var sprite = 0
        while (sprite < state.counters[COUNTER_ACTIVE_SPRITE_COUNT]) {
            if (state.activeSpriteX[sprite] > 0) state.activeSpriteX[sprite]--
            sprite++
        }
    }

    private fun shiftActiveSprites() {
        var sprite = 0
        while (sprite < state.counters[COUNTER_ACTIVE_SPRITE_COUNT]) {
            if (state.flags[FLAG_ACTIVE_DOT_SKIPPED] || state.activeSpriteX[sprite] == 0) {
                state.activeSpriteLow[sprite] = (state.activeSpriteLow[sprite] shl 1) and 0xFF
                state.activeSpriteHigh[sprite] = (state.activeSpriteHigh[sprite] shl 1) and 0xFF
            }
            sprite++
        }
        state.flags[FLAG_ACTIVE_DOT_SKIPPED] = false
    }

    private fun shiftBackground() {
        state.counters[COUNTER_BG_PATTERN_LOW] = (state.counters[COUNTER_BG_PATTERN_LOW] shl 1) and 0xFFFF
        state.counters[COUNTER_BG_PATTERN_HIGH] =
            ((state.counters[COUNTER_BG_PATTERN_HIGH] shl 1) or 1) and 0xFFFF
        state.counters[COUNTER_BG_ATTRIBUTE_LOW] = (state.counters[COUNTER_BG_ATTRIBUTE_LOW] shl 1) and 0xFFFF
        state.counters[COUNTER_BG_ATTRIBUTE_HIGH] = (state.counters[COUNTER_BG_ATTRIBUTE_HIGH] shl 1) and 0xFFFF
    }

    private fun fetchBackground() {
        when (state.cycle and 7) {
            0 -> {
                state.counters[COUNTER_BG_PATTERN_LOW] =
                    (state.counters[COUNTER_BG_PATTERN_LOW] and 0xFF00) or state.counters[COUNTER_NEXT_PATTERN_LOW]
                state.counters[COUNTER_BG_PATTERN_HIGH] =
                    (state.counters[COUNTER_BG_PATTERN_HIGH] and 0xFF00) or state.counters[COUNTER_NEXT_PATTERN_HIGH]
                state.counters[COUNTER_BG_ATTRIBUTE_LOW] =
                    (state.counters[COUNTER_BG_ATTRIBUTE_LOW] and 0xFF00) or
                    if ((state.counters[COUNTER_NEXT_ATTRIBUTE] and 1) != 0) 0xFF else 0
                state.counters[COUNTER_BG_ATTRIBUTE_HIGH] =
                    (state.counters[COUNTER_BG_ATTRIBUTE_HIGH] and 0xFF00) or
                    if ((state.counters[COUNTER_NEXT_ATTRIBUTE] and 2) != 0) 0xFF else 0
            }

            1 -> state.counters[COUNTER_NEXT_TILE] = readVram(0x2000 or (state.v and 0x0FFF))
            3 -> {
                val address = 0x23C0 or (state.v and 0x0C00) or
                    ((state.v shr 4) and 0x38) or ((state.v shr 2) and 7)
                val shift = ((state.v shr 4) and 4) or (state.v and 2)
                state.counters[COUNTER_NEXT_ATTRIBUTE] = (readVram(address) shr shift) and 3
            }

            5 -> state.counters[COUNTER_NEXT_PATTERN_LOW] = readVram(backgroundPatternAddress())
            7 -> state.counters[COUNTER_NEXT_PATTERN_HIGH] = readVram(backgroundPatternAddress() + 8)
        }
    }

    private fun backgroundPatternAddress(): Int =
        (if ((state.ctrl and 0x10) != 0) 0x1000 else 0) +
            state.counters[COUNTER_NEXT_TILE] * 16 + ((state.v shr 12) and 7)

    private fun processSpriteEvaluation() {
        if (state.cycle <= 64) {
            state.secondaryOam[state.counters[COUNTER_SECONDARY_OAM_ADDRESS] and 0x1F] = 0xFF.toByte()
            if ((state.cycle and 1) == 0) state.counters[COUNTER_SECONDARY_OAM_ADDRESS]++
            if (state.cycle == 1) state.flags[FLAG_EVALUATED_SPRITE_ZERO] = false
            state.counters[COUNTER_OAM_COPY_BUFFER] = 0xFF
            return
        }

        if (state.cycle == 65) {
            state.counters[COUNTER_EVAL_SPRITE] = state.oamAddress shr 2
            state.counters[COUNTER_EVAL_BYTE] = state.oamAddress and 3
            state.flags[FLAG_EVAL_IN_RANGE] = false
            state.flags[FLAG_EVAL_DONE] = false
            state.counters[COUNTER_OVERFLOW_BUG] = 0
            state.counters[COUNTER_SECONDARY_OAM_ADDRESS] = 0
        }

        if ((state.cycle and 1) != 0) {
            state.counters[COUNTER_OAM_COPY_BUFFER] = state.oam[
                ((state.counters[COUNTER_EVAL_SPRITE] shl 2) or state.counters[COUNTER_EVAL_BYTE]) and 0xFF
            ].toUnsignedInt()
            return
        }

        if (state.counters[COUNTER_SECONDARY_OAM_ADDRESS] < 32) {
            if (state.flags[FLAG_EVAL_DONE]) {
                state.counters[COUNTER_EVAL_SPRITE] = (state.counters[COUNTER_EVAL_SPRITE] + 1) and 0x3F
                state.counters[COUNTER_EVAL_BYTE] = 0
                state.counters[COUNTER_OAM_COPY_BUFFER] =
                    state.secondaryOam[state.counters[COUNTER_SECONDARY_OAM_ADDRESS] and 0x1F].toUnsignedInt()
            } else if (!state.flags[FLAG_EVAL_IN_RANGE]) {
                if (spriteInRange(state.counters[COUNTER_OAM_COPY_BUFFER])) {
                    state.flags[FLAG_EVAL_IN_RANGE] = true
                    if (state.cycle == 66) state.flags[FLAG_EVALUATED_SPRITE_ZERO] = true
                    state.secondaryOam[state.counters[COUNTER_SECONDARY_OAM_ADDRESS]++] =
                        state.counters[COUNTER_OAM_COPY_BUFFER].toByte()
                    advanceEvaluationByte()
                } else {
                    advanceEvaluationSprite()
                }
            } else {
                state.secondaryOam[state.counters[COUNTER_SECONDARY_OAM_ADDRESS]++] =
                    state.counters[COUNTER_OAM_COPY_BUFFER].toByte()
                advanceEvaluationByte()
                if ((state.counters[COUNTER_SECONDARY_OAM_ADDRESS] and 3) == 0) {
                    state.flags[FLAG_EVAL_IN_RANGE] = false
                    if (state.counters[COUNTER_EVAL_BYTE] != 0 &&
                        !spriteInRange(state.counters[COUNTER_OAM_COPY_BUFFER])) {
                        state.counters[COUNTER_EVAL_BYTE] = 0
                    }
                }
            }
        } else {
            val primaryInRange = state.flags[FLAG_EVAL_IN_RANGE] ||
                spriteInRange(state.counters[COUNTER_OAM_COPY_BUFFER])
            state.counters[COUNTER_OAM_COPY_BUFFER] =
                state.secondaryOam[state.counters[COUNTER_SECONDARY_OAM_ADDRESS] and 0x1F].toUnsignedInt()
            if (state.flags[FLAG_EVAL_DONE]) {
                state.counters[COUNTER_EVAL_SPRITE] = (state.counters[COUNTER_EVAL_SPRITE] + 1) and 0x3F
                state.counters[COUNTER_EVAL_BYTE] = 0
            } else if (primaryInRange) {
                state.flags[FLAG_EVAL_IN_RANGE] = true
                state.status = state.status or STATUS_SPRITE_OVERFLOW
                advanceEvaluationByte()
                if (state.counters[COUNTER_OVERFLOW_BUG] == 0) {
                    state.counters[COUNTER_OVERFLOW_BUG] = 3
                } else if (--state.counters[COUNTER_OVERFLOW_BUG] == 0) {
                    state.flags[FLAG_EVAL_DONE] = true
                    state.counters[COUNTER_EVAL_BYTE] = 0
                    state.flags[FLAG_EVAL_IN_RANGE] = false
                }
            } else {
                state.counters[COUNTER_EVAL_SPRITE] = (state.counters[COUNTER_EVAL_SPRITE] + 1) and 0x3F
                state.counters[COUNTER_EVAL_BYTE] = (state.counters[COUNTER_EVAL_BYTE] + 1) and 3
            }
            if (state.counters[COUNTER_EVAL_SPRITE] == 0) state.flags[FLAG_EVAL_DONE] = true
        }
        state.oamAddress =
            ((state.counters[COUNTER_EVAL_SPRITE] shl 2) or state.counters[COUNTER_EVAL_BYTE]) and 0xFF
    }

    private fun advanceEvaluationSprite() {
        state.counters[COUNTER_EVAL_SPRITE] = (state.counters[COUNTER_EVAL_SPRITE] + 1) and 0x3F
        state.counters[COUNTER_EVAL_BYTE] = 0
        if (state.counters[COUNTER_EVAL_SPRITE] == 0) state.flags[FLAG_EVAL_DONE] = true
    }

    private fun advanceEvaluationByte() {
        state.counters[COUNTER_EVAL_BYTE]++
        if (state.counters[COUNTER_EVAL_BYTE] == 4) {
            state.counters[COUNTER_EVAL_BYTE] = 0
            state.counters[COUNTER_EVAL_SPRITE] = (state.counters[COUNTER_EVAL_SPRITE] + 1) and 0x3F
            if (state.counters[COUNTER_EVAL_SPRITE] == 0) state.flags[FLAG_EVAL_DONE] = true
        }
    }

    private fun spriteInRange(spriteY: Int): Boolean {
        val height = if ((state.ctrl and 0x20) != 0) 16 else 8
        return state.scanline >= spriteY && state.scanline < spriteY + height
    }

    private fun fetchSprite(slot: Int) {
        val base = slot * 4
        val spriteY = state.secondaryOam[base].toUnsignedInt()
        val tile = state.secondaryOam[base + 1].toUnsignedInt()
        val attributes = state.secondaryOam[base + 2].toUnsignedInt()
        val hardwareScanline = if (state.scanline < 0) timing.scanlinesPerFrame - 1 else state.scanline
        var row = (hardwareScanline.low8Bits() - spriteY).low8Bits()
        val height = if ((state.ctrl and 0x20) != 0) 16 else 8
        if ((attributes and 0x80) != 0) row = row xor (height - 1)
        val address = if (height == 16) {
            ((tile and 1) shl 12) + ((tile and 0xFE) shl 4) + ((row and 8) shl 1) + (row and 7)
        } else {
            (if ((state.ctrl and 0x08) != 0) 0x1000 else 0) + tile * 16 + (row and 7)
        }
        var low = readVram(address)
        var high = readVram(address + 8)
        if (row !in 0 until height) return
        if ((attributes and 0x40) != 0) {
            low = reverseBits(low)
            high = reverseBits(high)
        }
        val fetched = state.counters[COUNTER_FETCHED_SPRITE_COUNT]
        if (fetched >= state.fetchedSpriteX.size) return
        state.fetchedSpriteX[fetched] = state.secondaryOam[base + 3].toUnsignedInt() + 1
        state.fetchedSpriteAttributes[fetched] = attributes
        state.fetchedSpriteLow[fetched] = low
        state.fetchedSpriteHigh[fetched] = high
        if (fetched == 0) state.flags[FLAG_FETCHED_SPRITE_ZERO] = state.flags[FLAG_EVALUATED_SPRITE_ZERO]
        state.counters[COUNTER_FETCHED_SPRITE_COUNT] = fetched + 1
    }

    private fun reverseBits(value: Int): Int {
        var source = value
        var result = 0
        var bit = 0
        while (bit < 8) {
            result = (result shl 1) or (source and 1)
            source = source ushr 1
            bit++
        }
        return result
    }

    private fun corruptOamRow() {
        val sourceRow = state.oamAddress shr 3
        val destinationRow = state.counters[COUNTER_SECONDARY_OAM_ADDRESS] and 0x1F
        if (sourceRow == destinationRow) return
        val source = sourceRow shl 3
        val destination = destinationRow shl 3
        var offset = 0
        while (offset < 8) {
            state.oam[destination + offset] = state.oam[source + offset]
            offset++
        }
        state.secondaryOam[destinationRow] = state.secondaryOam[sourceRow]
    }

    private fun isPalTiming(): Boolean = timing.scanlinesPerFrame == 312 && timing.nmiScanline == 241

    private fun readStatus(): Int {
        val result = applyOpenBus(0x1F, state.status and 0xE0)
        if (state.scanline == timing.nmiScanline && state.cycle == 0) state.flags[FLAG_PREVENT_VBLANK] = true
        state.status = state.status and STATUS_VBLANK.inv()
        state.writeLatch = false
        setNmiLineAsserted(false)
        return result
    }

    private fun readOamData(): Int {
        val result = if (currentRenderingEnabled() && state.scanline <= 239) {
            if (state.cycle in 257..340 || state.cycle == 0) {
                state.counters[COUNTER_OAM_COPY_BUFFER] =
                    state.secondaryOam[state.counters[COUNTER_SECONDARY_OAM_ADDRESS] and 0x1F].toUnsignedInt()
            }
            state.counters[COUNTER_OAM_COPY_BUFFER]
        } else {
            state.oam[state.oamAddress].toUnsignedInt()
        }
        return applyOpenBus(0, result)
    }

    private fun readData(): Int {
        if (state.counters[COUNTER_IGNORE_DATA_READ_DELAY] > 0) return applyOpenBus(0xFF, 0)
        val address = state.counters[COUNTER_PPU_BUS_ADDRESS] and 0x3FFF
        val result = if (address >= 0x3F00) {
            applyOpenBus(0xC0, ppuRead(address) and grayscaleMask())
        } else {
            applyOpenBus(0, state.counters[COUNTER_READ_BUFFER])
        }
        state.counters[COUNTER_PENDING_DATA_READ_DELAY] = DATA_ACCESS_DELAY
        state.counters[COUNTER_IGNORE_DATA_READ_DELAY] = DATA_READ_IGNORE_DELAY
        return result
    }

    private fun writeControl(data: Int) {
        state.ctrl = data
        state.t = (state.t and 0xF3FF) or ((data and 3) shl 10)
        val nmiEnabled = (state.ctrl and 0x80) != 0
        setNmiLineAsserted(nmiEnabled && (state.status and STATUS_VBLANK) != 0)
    }

    private fun writeOamData(data: Int) {
        if (currentRenderingEnabled() && state.scanline in -1 until SCREEN_HEIGHT) {
            state.oamAddress = (state.oamAddress + 4) and 0xFC
            return
        }
        val stored = if ((state.oamAddress and 3) == 2) data and 0xE3 else data
        state.oam[state.oamAddress] = stored.toByte()
        if (!isPalTiming() || state.scanline < PAL_FORCED_REFRESH_START_SCANLINE ||
            ((state.cycle and 1) != 0 && state.cycle != 339)) {
            state.oamAddress = (state.oamAddress + 1).low8Bits()
        }
    }

    private fun writeScroll(data: Int) {
        if (!state.writeLatch) {
            state.fineX = data and 7
            state.t = (state.t and 0xFFE0) or (data shr 3)
        } else {
            state.t = (state.t and 0x8FFF) or ((data and 7) shl 12)
            state.t = (state.t and 0xFC1F) or ((data and 0xF8) shl 2)
        }
        state.writeLatch = !state.writeLatch
    }

    private fun writeAddress(data: Int) {
        if (!state.writeLatch) {
            state.t = (state.t and 0x00FF) or ((data and 0x3F) shl 8)
        } else {
            state.t = (state.t and 0x7F00) or data
            state.counters[COUNTER_PENDING_V_ADDRESS] = state.t
            state.counters[COUNTER_PENDING_V_ADDRESS_DELAY] = ADDRESS_UPDATE_DELAY
        }
        state.writeLatch = !state.writeLatch
    }

    private fun scheduleDataWrite(data: Int) {
        state.counters[COUNTER_PENDING_DATA_WRITE_VALUE] = data
        state.counters[COUNTER_PENDING_DATA_WRITE_DELAY] = DATA_ACCESS_DELAY
    }

    private fun processDelayedRegisterOperations() {
        // Rendering enable has two delayed stages and advances before register state machines.
        if (renderingEnabled() != currentRenderingEnabled()) {
            state.counters[COUNTER_RENDERING_MASK] = state.counters[COUNTER_CURRENT_RENDERING_MASK]
            if (!renderingEnabled() && state.scanline in -1 until SCREEN_HEIGHT) {
                setBusAddress(state.v)
                if (state.cycle in 65..256) state.oamAddress = (state.oamAddress + 1).low8Bits()
            }
        }
        state.counters[COUNTER_CURRENT_RENDERING_MASK] = state.mask

        if (
            state.counters[COUNTER_PENDING_V_ADDRESS_DELAY] > 0 &&
            --state.counters[COUNTER_PENDING_V_ADDRESS_DELAY] == 0
        ) {
            state.v = state.counters[COUNTER_PENDING_V_ADDRESS]
            state.t = state.v
            if (!currentRenderingEnabled() || state.scanline !in -1 until SCREEN_HEIGHT) setBusAddress(state.v)
        }

        if (state.counters[COUNTER_IGNORE_DATA_READ_DELAY] > 0) state.counters[COUNTER_IGNORE_DATA_READ_DELAY]--
        if (state.flags[FLAG_PENDING_VRAM_INCREMENT]) {
            state.flags[FLAG_PENDING_VRAM_INCREMENT] = false
            incrementVramAddress()
        }
        if (
            state.counters[COUNTER_PENDING_DATA_READ_DELAY] > 0 &&
            --state.counters[COUNTER_PENDING_DATA_READ_DELAY] == 0
        ) {
            val address = state.counters[COUNTER_PPU_BUS_ADDRESS] and 0x3FFF
            setBusAddress(address)
            state.counters[COUNTER_READ_BUFFER] = bus.read(if (address >= 0x3F00) address - 0x1000 else address)
            state.flags[FLAG_PENDING_VRAM_INCREMENT] = true
        }
        if (
            state.counters[COUNTER_PENDING_DATA_WRITE_DELAY] > 0 &&
            --state.counters[COUNTER_PENDING_DATA_WRITE_DELAY] == 0
        ) {
            val address = state.counters[COUNTER_PPU_BUS_ADDRESS] and 0x3FFF
            val value = if (
                address < 0x3F00 && currentRenderingEnabled() && state.scanline in -1 until SCREEN_HEIGHT
            ) {
                address.low8Bits()
            } else {
                state.counters[COUNTER_PENDING_DATA_WRITE_VALUE]
            }
            bus.write(address, value)
            if (address >= 0x3F00) state.counters[COUNTER_PALETTE_COLOR_ID_CACHE_MASK] = -1
            state.flags[FLAG_PENDING_VRAM_INCREMENT] = true
        }
    }

    private fun incrementVramAddress() {
        if (currentRenderingEnabled() && state.scanline in -1 until SCREEN_HEIGHT) {
            incrementCoarseX()
            incrementY()
        } else {
            state.v = (state.v + if ((state.ctrl and 0x04) != 0) 32 else 1) and 0x7FFF
            setBusAddress(state.v)
        }
    }

    private fun incrementCoarseX() {
        state.v = if ((state.v and 0x001F) == 31) {
            (state.v and 0xFFE0) xor 0x0400
        } else {
            (state.v + 1) and 0x7FFF
        }
    }

    private fun incrementY() {
        state.v = if ((state.v and 0x7000) != 0x7000) {
            (state.v + 0x1000) and 0x7FFF
        } else {
            var next = state.v and 0x8FFF
            var y = (state.v and 0x03E0) shr 5
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
        state.v = (state.v and 0xFBE0) or (state.t and 0x041F)
    }

    private fun transferVerticalAddress() {
        state.v = (state.v and 0x841F) or (state.t and 0x7BE0)
    }

    private fun updateNmiLine() {
        setNmiLineAsserted((state.ctrl and 0x80) != 0 && (state.status and STATUS_VBLANK) != 0)
    }

    private fun readVram(address: Int): Int {
        setBusAddress(address)
        return bus.read(state.counters[COUNTER_PPU_BUS_ADDRESS])
    }

    private fun setBusAddress(address: Int) {
        state.counters[COUNTER_PPU_BUS_ADDRESS] = address and 0x3FFF
        val mapperCycle = if (cpuCycle >= 0) {
            cpuCycle
        } else {
            state.ppuCycle * timing.ppuMasterClockDivider / timing.cpuMasterClockDivider
        }
        bus.addressChanged(state.counters[COUNTER_PPU_BUS_ADDRESS], mapperCycle)
    }

    private fun setNmiLineAsserted(asserted: Boolean) {
        if (!state.nmiLine && asserted) state.nmiRequested = true
        if (!asserted) state.nmiRequested = false
        state.nmiLine = asserted
    }

    private fun setOpenBus(mask: Int, value: Int) {
        var bit = 0
        var openBus = state.counters[COUNTER_OPEN_BUS]
        val frame = state.counters[COUNTER_FRAME_NUMBER]
        while (bit < 8) {
            val bitMask = 1 shl bit
            if ((mask and bitMask) != 0) {
                openBus = if ((value and bitMask) != 0) openBus or bitMask else openBus and bitMask.inv()
                state.openBusDecayStamps[bit] = frame
            } else if (frame - state.openBusDecayStamps[bit] > OPEN_BUS_DECAY_FRAMES) {
                openBus = openBus and bitMask.inv()
            }
            bit++
        }
        state.counters[COUNTER_OPEN_BUS] = openBus.low8Bits()
    }

    private fun applyOpenBus(preservedMask: Int, value: Int): Int {
        setOpenBus(preservedMask.inv() and 0xFF, value)
        return value or (state.counters[COUNTER_OPEN_BUS] and preservedMask)
    }

    private fun writePixel(index: Int, paletteIndex: Int) {
        val colorId = paletteColorId(paletteIndex) or emphasisBits()
        val offset = index shl 2
        val framebuffer = state.frameColorIds[state.renderFramebufferIndex]
        framebuffer[offset] = colorId.toByte()
        framebuffer[offset + 1] = (colorId shr 8).toByte()
        framebuffer[offset + 2] = 0
        framebuffer[offset + 3] = 0xFF.toByte()
    }

    private fun argbFramebuffer(framebufferIndex: Int): IntArray {
        val argbFramebuffer = argbFramebuffers[framebufferIndex]
            ?: IntArray(SCREEN_WIDTH * SCREEN_HEIGHT).also { argbFramebuffers[framebufferIndex] = it }

        val colorIds = state.frameColorIds[framebufferIndex]
        var index = 0
        while (index < argbFramebuffer.size) {
            val offset = index shl 2
            val colorId = colorIds[offset].toUnsignedInt() or (colorIds[offset + 1].toUnsignedInt() shl 8)
            argbFramebuffer[index] = Palette.color(colorId)
            index++
        }

        return argbFramebuffer
    }

    private fun paletteColorId(index: Int): Int {
        val mask = grayscaleMask()
        if (mask != state.counters[COUNTER_PALETTE_COLOR_ID_CACHE_MASK]) rebuildPaletteColorIdCache(mask)
        return paletteColorIdCache[index and 0x1F]
    }

    private fun rebuildPaletteColorIdCache(mask: Int) {
        var index = 0
        while (index < paletteColorIdCache.size) {
            paletteColorIdCache[index] = ppuRead(0x3F00 + index) and mask
            index++
        }
        state.counters[COUNTER_PALETTE_COLOR_ID_CACHE_MASK] = mask
    }

    private fun grayscaleMask(): Int = if ((state.mask and 1) != 0) 0x30 else 0x3F

    private fun emphasisBits(): Int = if (isPalTiming()) {
        ((state.mask and 0x40) shl 0) or ((state.mask and 0x20) shl 2) or ((state.mask and 0x80) shl 1)
    } else {
        (state.mask and 0xE0) shl 1
    }

    private fun renderingEnabled(): Boolean =
        (state.counters[COUNTER_RENDERING_MASK] and (MASK_BACKGROUND or MASK_SPRITES)) != 0

    private fun currentRenderingEnabled(): Boolean =
        (state.counters[COUNTER_CURRENT_RENDERING_MASK] and (MASK_BACKGROUND or MASK_SPRITES)) != 0

    companion object {
        private const val SCREEN_WIDTH = 256
        private const val SCREEN_HEIGHT = 240
        private const val LAST_DOT = 340
        private const val ADDRESS_UPDATE_DELAY = 3
        private const val DATA_ACCESS_DELAY = 5
        private const val DATA_READ_IGNORE_DELAY = 6
        private const val OPEN_BUS_DECAY_FRAMES = 3
        private const val PAL_FORCED_REFRESH_START_SCANLINE = 265
        private const val COUNTER_ACTIVE_SPRITE_COUNT = 0
        private const val COUNTER_FETCHED_SPRITE_COUNT = 1
        private const val COUNTER_SECONDARY_OAM_ADDRESS = 2
        private const val COUNTER_OAM_COPY_BUFFER = 3
        private const val COUNTER_EVAL_SPRITE = 4
        private const val COUNTER_EVAL_BYTE = 5
        private const val COUNTER_BG_PATTERN_LOW = 6
        private const val COUNTER_BG_PATTERN_HIGH = 7
        private const val COUNTER_BG_ATTRIBUTE_LOW = 8
        private const val COUNTER_BG_ATTRIBUTE_HIGH = 9
        private const val COUNTER_NEXT_TILE = 10
        private const val COUNTER_NEXT_ATTRIBUTE = 11
        private const val COUNTER_NEXT_PATTERN_LOW = 12
        private const val COUNTER_NEXT_PATTERN_HIGH = 13
        private const val COUNTER_OPEN_BUS = 14
        private const val COUNTER_PPU_BUS_ADDRESS = 15
        private const val COUNTER_RENDERING_MASK = 16
        private const val COUNTER_READ_BUFFER = 17
        private const val COUNTER_FRAME_NUMBER = 18
        private const val COUNTER_PENDING_V_ADDRESS = 19
        private const val COUNTER_PENDING_V_ADDRESS_DELAY = 20
        private const val COUNTER_PENDING_DATA_READ_DELAY = 21
        private const val COUNTER_PENDING_DATA_WRITE_VALUE = 22
        private const val COUNTER_PENDING_DATA_WRITE_DELAY = 23
        private const val COUNTER_PALETTE_COLOR_ID_CACHE_MASK = 24
        private const val COUNTER_IGNORE_DATA_READ_DELAY = 25
        private const val COUNTER_CURRENT_RENDERING_MASK = 26
        private const val COUNTER_OVERFLOW_BUG = 27
        private const val FLAG_ACTIVE_SPRITE_ZERO = 0
        private const val FLAG_FETCHED_SPRITE_ZERO = 1
        private const val FLAG_EVALUATED_SPRITE_ZERO = 2
        private const val FLAG_EVAL_IN_RANGE = 3
        private const val FLAG_EVAL_DONE = 4
        private const val FLAG_PREVENT_VBLANK = 5
        private const val FLAG_PENDING_VRAM_INCREMENT = 6
        private const val FLAG_PREVIOUS_DOT_SKIPPED = 7
        private const val FLAG_ACTIVE_DOT_SKIPPED = 8
        private const val MASK_BACKGROUND_LEFT = 0x02
        private const val MASK_SPRITES_LEFT = 0x04
        private const val MASK_BACKGROUND = 0x08
        private const val MASK_SPRITES = 0x10
        private const val STATUS_SPRITE_OVERFLOW = 0x20
        private const val STATUS_SPRITE_ZERO_HIT = 0x40
        private const val STATUS_VBLANK = 0x80
    }
}
