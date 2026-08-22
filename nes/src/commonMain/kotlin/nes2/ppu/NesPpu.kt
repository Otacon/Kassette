package nes2.ppu

import nes2.console.NesConsole
import nes2.console.NesConstants
import nes2.cpu.ConsoleRegion
import nes2.cpu.MemoryOperationType
import nes2.mapper.GameSystem
import nes2.mapper.PpuModel
import nes2.memory.MemoryOperation
import nes2.memory.MemoryRanges

abstract class NesPpu(console: NesConsole) : BaseNesPpu(console) {
    companion object {
        private const val OamDecayCycleCount = 4500
        private val paletteRamBootValues = intArrayOf(
            0x09, 0x01, 0x00, 0x01, 0x00, 0x02, 0x02, 0x0D,
            0x08, 0x10, 0x08, 0x24, 0x00, 0x00, 0x04, 0x2C,
            0x09, 0x01, 0x34, 0x03, 0x00, 0x04, 0x00, 0x14,
            0x08, 0x3A, 0x00, 0x02, 0x00, 0x20, 0x2C, 0x08,
        )
    }

    init {
        masterClock = 0
        masterClockDivider = 4
        currentOutputBuffer = outputBuffers[0]
        outputBuffers[0].fill(0)
        outputBuffers[1].fill(0)
        paletteRamBootValues.copyInto(paletteRam)
        videoRamAddr = 0
        console.initializeRam(intArrayAsByteArray(spriteRam))
        console.initializeRam(intArrayAsByteArray(secondarySpriteRam))
        updateTimings(ConsoleRegion.Ntsc)
        reset(false)
    }

    override fun reset(softReset: Boolean) {
        masterClock = 0
        oamDecayCycles.fill(0)
        enableOamDecay = console.options.ppu.enableOamDecay

        if (softReset && console.options.ppu.disablePpuReset) return

        preventVblFlag = false
        needStateUpdate = false
        prevRenderingEnabled = false
        renderingEnabled = false
        ignoreVramRead = 0
        openBus = 0
        openBusDecayStamp.fill(0)
        tmpVideoRamAddr = 0
        highBitShift = 0
        lowBitShift = 0
        spriteRamAddr = 0
        xScroll = 0
        writeToggle = false
        copyControl(PpuControlFlags())
        copyMask(PpuMaskFlags())
        if (!softReset) copyStatus(PPUStatusFlags())
        tile = TileInfo()
        currentTilePalette = 0
        previousTilePalette = 0
        ppuBusAddress = 0
        intensifyColorBits = 0
        paletteRamMask = 0x3F
        lastUpdatedPixel = -1
        lastSprite = null
        oamCopybuffer = 0
        spriteInRange = false
        sprite0Added = false
        oamCopyDone = false
        for (sprite in spriteTiles) {
            sprite.backgroundPriority = false
            sprite.spriteX = 0
            sprite.lowByte = 0
            sprite.highByte = 0
            sprite.paletteOffset = 0
        }
        spriteCount = 0
        secondaryOamAddr = 0
        sprite0Visible = false
        spriteIndex = 0
        scanline = -1
        cycle = 340
        frameCountValue = 1
        memoryReadBuffer = 0
        overflowBugCounter = 0
        updateVramAddrDelay = 0
        updateVramAddr = 0
        firstVisibleSpriteAddr = 0
        lastVisibleSpriteAddr = 0
        allowFullPpuAccess = false
        updateMinimumDrawCycles()
    }

    override fun updateTimings(region: ConsoleRegion, overclockEnabled: Boolean) {
        this.region = region
        when (region) {
            ConsoleRegion.Ntsc -> {
                nmiScanline = 241
                vblankEnd = 260
                standardNmiScanline = 241
                standardVblankEnd = 260
                masterClockDivider = 4
            }
            ConsoleRegion.Pal -> {
                nmiScanline = 241
                vblankEnd = 310
                standardNmiScanline = 241
                standardVblankEnd = 310
                masterClockDivider = 5
            }
            ConsoleRegion.Dendy -> {
                nmiScanline = 291
                vblankEnd = 310
                standardNmiScanline = 291
                standardVblankEnd = 310
                masterClockDivider = 5
            }
        }
        if (overclockEnabled) {
            nmiScanline += console.options.ppu.extraScanlinesBeforeNmi
            standardVblankEnd += console.options.ppu.extraScanlinesBeforeNmi
            vblankEnd += console.options.ppu.extraScanlinesAfterNmi + console.options.ppu.extraScanlinesBeforeNmi
        }
        palSpriteEvalScanline = nmiScanline + 24
    }

    override fun run(masterClock: Long) {
        do {
            exec()
            this.masterClock += masterClockDivider.toLong()
        } while (this.masterClock + masterClockDivider <= masterClock)
    }

    override fun getMemoryRanges(ranges: MemoryRanges) {
        ranges.addHandler(MemoryOperation.Read, 0x2000, 0x3FFF)
        ranges.addHandler(MemoryOperation.Write, 0x2000, 0x3FFF)
        ranges.addHandler(MemoryOperation.Write, 0x4014)
    }

    override fun getPpuModel(): PpuModel = if (mapper.getGameSystem() == GameSystem.VsSystem) mapper.getPpuModel() else PpuModel.Ppu2C02

    override fun peekRam(addr: Int): Int {
        var openBusMask = 0xFF
        var returnValue = 0
        when (getRegisterID(addr)) {
            PpuRegisters.Status -> {
                returnValue = statusByte()
                if (scanline == nmiScanline && cycle < 3) returnValue = returnValue and 0x7F
                openBusMask = 0x1F
                val processed = processStatusRegOpenBus(openBusMask, returnValue)
                openBusMask = processed.first
                returnValue = processed.second
            }
            PpuRegisters.SpriteData -> {
                if (!console.options.ppu.disablePpu2004Reads) {
                    returnValue = if (scanline <= 239 && isRenderingEnabled()) {
                        if ((cycle in 257..340) || cycle == 0) secondarySpriteRam[secondaryOamAddr and 0x1F] else oamCopybuffer
                    } else {
                        spriteRam[spriteRamAddr]
                    }
                    openBusMask = 0x00
                }
            }
            PpuRegisters.VideoMemoryData -> {
                returnValue = memoryReadBuffer
                if ((videoRamAddr and 0x3FFF) >= 0x3F00 && !console.options.ppu.disablePaletteRead) {
                    returnValue = (readPaletteRam(videoRamAddr) and paletteRamMask) or (openBus and 0xC0)
                    openBusMask = 0xC0
                } else {
                    openBusMask = 0x00
                }
            }
            else -> {}
        }
        return returnValue or (openBus and openBusMask)
    }

    override fun readRam(addr: Int): Int {
        var openBusMask = 0xFF
        var returnValue = 0
        when (getRegisterID(addr)) {
            PpuRegisters.Status -> {
                writeToggle = false
                returnValue = statusByte()
                updateStatusFlag()
                openBusMask = 0x1F
                val processed = processStatusRegOpenBus(openBusMask, returnValue)
                openBusMask = processed.first
                returnValue = processed.second
            }
            PpuRegisters.SpriteData -> {
                if (!console.options.ppu.disablePpu2004Reads) {
                    returnValue = if (scanline <= 239 && isRenderingEnabled()) {
                        if ((cycle in 257..340) || cycle == 0) oamCopybuffer = secondarySpriteRam[secondaryOamAddr and 0x1F]
                        oamCopybuffer
                    } else {
                        readSpriteRam(spriteRamAddr)
                    }
                    openBusMask = 0x00
                }
            }
            PpuRegisters.VideoMemoryData -> {
                if (!allowFullPpuAccess && console.options.ppu.restrictPpuAccessOnFirstFrame) {
                    openBusMask = 0x00
                    returnValue = 0
                } else if (ignoreVramRead != 0) {
                    openBusMask = 0xFF
                } else {
                    returnValue = memoryReadBuffer
                    ppuMemoryDataReadStateMachine = 5
                    if ((ppuBusAddress and 0x3FFF) >= 0x3F00 && !console.options.ppu.disablePaletteRead) {
                        returnValue = (readPaletteRam(ppuBusAddress) and paletteRamMask) or (openBus and 0xC0)
                        openBusMask = 0xC0
                    } else {
                        openBusMask = 0x00
                    }
                    ignoreVramRead = 6
                    needStateUpdate = true
                }
            }
            else -> {}
        }
        return applyOpenBus(openBusMask, returnValue)
    }

    override fun writeRam(addr: Int, value: Int) {
        var v = value and 0xFF
        if (addr != 0x4014) setOpenBus(0xFF, v)
        when (getRegisterID(addr)) {
            PpuRegisters.Control -> if (isPpu2C05()) setMaskRegister(v) else setControlRegister(v)
            PpuRegisters.Mask -> if (isPpu2C05()) setControlRegister(v) else setMaskRegister(v)
            PpuRegisters.Status -> {}
            PpuRegisters.SpriteAddr -> spriteRamAddr = v
            PpuRegisters.SpriteData -> {
                if (scanline >= 240 || !isRenderingEnabled()) {
                    if ((spriteRamAddr and 0x03) == 0x02) v = v and 0xE3
                    writeSpriteRam(spriteRamAddr, v)
                    if (region == ConsoleRegion.Ntsc || scanline < palSpriteEvalScanline || ((cycle and 1) != 0 && cycle != 339)) {
                        spriteRamAddr = (spriteRamAddr + 1) and 0xFF
                    }
                } else {
                    spriteRamAddr = (spriteRamAddr + 4) and 0xFC
                }
            }
            PpuRegisters.ScrollOffsets -> {
                if (!allowFullPpuAccess && console.options.ppu.restrictPpuAccessOnFirstFrame) return
                if (writeToggle) {
                    tmpVideoRamAddr = (tmpVideoRamAddr and 0x73E0.inv()) or ((v and 0xF8) shl 2) or ((v and 0x07) shl 12)
                } else {
                    xScroll = v and 0x07
                    val newAddr = (tmpVideoRamAddr and 0x001F.inv()) or (v shr 3)
                    processTmpAddrScrollGlitch(newAddr, console.memoryManager.getOpenBus() shr 3, 0x001F)
                }
                writeToggle = !writeToggle
            }
            PpuRegisters.VideoMemoryAddr -> {
                if (!allowFullPpuAccess && console.options.ppu.restrictPpuAccessOnFirstFrame) return
                if (writeToggle) {
                    tmpVideoRamAddr = (tmpVideoRamAddr and 0x00FF.inv()) or v
                    needStateUpdate = true
                    updateVramAddrDelay = 3
                    updateVramAddr = tmpVideoRamAddr
                } else {
                    val newAddr = (tmpVideoRamAddr and 0xFF00.inv()) or ((v and 0x3F) shl 8)
                    processTmpAddrScrollGlitch(newAddr, console.memoryManager.getOpenBus() shl 8, 0x0C00)
                }
                writeToggle = !writeToggle
            }
            PpuRegisters.VideoMemoryData -> {
                ppuMemoryDataWriteStateMachine = 5
                ppuMemoryDataWriteLatch = v
                needStateUpdate = true
            }
            PpuRegisters.SpriteDMA -> console.cpu.runDMATransfer(v)
        }
    }

    protected fun exec() {
        if (cycle < 340) {
            cycle++
            processScanline()
        } else {
            processScanlineFirstCycle()
        }
        processDelayedStateUpdates()
    }

    protected open fun processScanline() {}

    protected fun processScanlineFirstCycle() {
        cycle = 0
        scanline++
        if (scanline > vblankEnd) {
            scanline = -1
            frameCountValue++
            allowFullPpuAccess = true
            currentOutputBuffer = if (currentOutputBuffer === outputBuffers[0]) outputBuffers[1] else outputBuffers[0]
        }

        if (scanline == nmiScanline) beginVBlank()
        if (scanline == -1) {
            statusFlags.spriteOverflow = false
            statusFlags.sprite0Hit = false
            statusFlags.verticalBlank = false
            console.cpu.clearNmiFlag()
        }
    }

    protected fun beginVBlank() {
        if (!preventVblFlag) {
            statusFlags.verticalBlank = true
            if (control.nmiOnVerticalBlank) triggerNmi()
        }
        preventVblFlag = false
    }

    protected fun triggerNmi() {
        console.cpu.setNmiFlag()
    }

    protected fun processDelayedStateUpdates() {
        if (ignoreVramRead > 0) ignoreVramRead--
        if (updateVramAddrDelay > 0) {
            updateVramAddrDelay--
            if (updateVramAddrDelay == 0) {
                videoRamAddr = updateVramAddr and 0x7FFF
                setBusAddress(videoRamAddr and 0x3FFF)
            }
        }
        if (ppuMemoryDataReadStateMachine > 0) {
            ppuMemoryDataReadStateMachine--
            if (ppuMemoryDataReadStateMachine == 0) {
                memoryReadBuffer = readVram(ppuBusAddress and 0x3FFF)
                updateVideoRamAddr()
            }
        }
        if (ppuMemoryDataWriteStateMachine > 0) {
            ppuMemoryDataWriteStateMachine--
            if (ppuMemoryDataWriteStateMachine == 0) {
                writeVram(ppuBusAddress and 0x3FFF, ppuMemoryDataWriteLatch)
                updateVideoRamAddr()
            }
        }
        if (needStateUpdate) updateState()
    }

    protected fun updateState() {
        renderingEnabled = mask.backgroundEnabled || mask.spritesEnabled
        needStateUpdate = false
    }

    protected fun updateVideoRamAddr() {
        if (scanline >= 240 || !isRenderingEnabled()) {
            videoRamAddr = (videoRamAddr + if (control.verticalWrite) 32 else 1) and 0x7FFF
            setBusAddress(videoRamAddr and 0x3FFF)
        } else {
            incHorizontalScrolling()
            incVerticalScrolling()
        }
    }

    protected fun setOpenBus(mask: Int, value: Int) {
        var m = mask and 0xFF
        var v = value and 0xFF
        if (m == 0xFF) {
            openBus = v
            for (i in 0 until 8) openBusDecayStamp[i] = frameCountValue
        } else {
            var bus = openBus shl 8
            for (i in 0 until 8) {
                bus = bus shr 1
                if ((m and 0x01) != 0) {
                    bus = if ((v and 0x01) != 0) bus or 0x80 else bus and 0xFF7F
                    openBusDecayStamp[i] = frameCountValue
                } else if (frameCountValue - openBusDecayStamp[i] > 3) {
                    bus = bus and 0xFF7F
                }
                v = v shr 1
                m = m shr 1
            }
            openBus = bus and 0xFF
        }
    }

    protected fun applyOpenBus(mask: Int, value: Int): Int {
        setOpenBus(mask.inv(), value)
        return (value or (openBus and mask)) and 0xFF
    }

    protected fun processStatusRegOpenBus(openBusMask: Int, returnValue: Int): Pair<Int, Int> = when (getPpuModel()) {
        PpuModel.Ppu2C05A -> 0x00 to (returnValue or 0x1B)
        PpuModel.Ppu2C05B -> 0x00 to (returnValue or 0x3D)
        PpuModel.Ppu2C05C -> 0x00 to (returnValue or 0x1C)
        PpuModel.Ppu2C05D -> 0x00 to (returnValue or 0x1B)
        PpuModel.Ppu2C05E -> 0x00 to returnValue
        else -> openBusMask to returnValue
    }

    protected fun processTmpAddrScrollGlitch(normalAddr: Int, value: Int, mask: Int) {
        tmpVideoRamAddr = normalAddr and 0x7FFF
        if (cycle == 257 && console.options.ppu.enablePpu2000ScrollGlitch && scanline < 240 && isRenderingEnabled()) {
            videoRamAddr = (videoRamAddr and mask.inv()) or (value and mask)
        }
    }

    protected fun setControlRegister(value: Int) {
        if (!allowFullPpuAccess && console.options.ppu.restrictPpuAccessOnFirstFrame) return
        val nameTable = value and 0x03
        val normalAddr = (tmpVideoRamAddr and 0x0C00.inv()) or (nameTable shl 10)
        processTmpAddrScrollGlitch(normalAddr, console.memoryManager.getOpenBus() shl 10, 0x0400)
        control.verticalWrite = (value and 0x04) == 0x04
        control.spritePatternAddr = if ((value and 0x08) == 0x08) 0x1000 else 0x0000
        control.backgroundPatternAddr = if ((value and 0x10) == 0x10) 0x1000 else 0x0000
        control.largeSprites = (value and 0x20) == 0x20
        control.secondaryPpu = (value and 0x40) == 0x40
        control.nmiOnVerticalBlank = (value and 0x80) == 0x80
        if (!control.nmiOnVerticalBlank) console.cpu.clearNmiFlag()
        else if (statusFlags.verticalBlank) console.cpu.setNmiFlag()
    }

    protected fun setMaskRegister(value: Int) {
        if (!allowFullPpuAccess && console.options.ppu.restrictPpuAccessOnFirstFrame) return
        mask.grayscale = (value and 0x01) == 0x01
        mask.backgroundMask = (value and 0x02) == 0x02
        mask.spriteMask = (value and 0x04) == 0x04
        mask.backgroundEnabled = (value and 0x08) == 0x08
        mask.spritesEnabled = (value and 0x10) == 0x10
        mask.intensifyBlue = (value and 0x80) == 0x80
        if (region == ConsoleRegion.Ntsc) {
            mask.intensifyRed = (value and 0x20) == 0x20
            mask.intensifyGreen = (value and 0x40) == 0x40
        } else {
            mask.intensifyRed = (value and 0x40) == 0x40
            mask.intensifyGreen = (value and 0x20) == 0x20
        }
        if (renderingEnabled != (mask.backgroundEnabled || mask.spritesEnabled)) needStateUpdate = true
        updateMinimumDrawCycles()
        updateGrayscaleAndIntensifyBits()
    }

    protected fun updateStatusFlag() {
        statusFlags.verticalBlank = false
        console.cpu.clearNmiFlag()
        if (scanline == nmiScanline && cycle == 0) preventVblFlag = true
    }

    protected fun incVerticalScrolling() {
        var addr = videoRamAddr
        if ((addr and 0x7000) != 0x7000) {
            addr += 0x1000
        } else {
            addr = addr and 0x7000.inv()
            var y = (addr and 0x03E0) shr 5
            if (y == 29) { y = 0; addr = addr xor 0x0800 } else if (y == 31) y = 0 else y++
            addr = (addr and 0x03E0.inv()) or (y shl 5)
        }
        videoRamAddr = addr and 0x7FFF
    }

    protected fun incHorizontalScrolling() {
        videoRamAddr = if ((videoRamAddr and 0x001F) == 31) (videoRamAddr and 0x001F.inv()) xor 0x0400 else videoRamAddr + 1
    }

    protected fun getNameTableAddr(): Int = 0x2000 or (videoRamAddr and 0x0FFF)
    protected fun getAttributeAddr(): Int = 0x23C0 or (videoRamAddr and 0x0C00) or ((videoRamAddr shr 4) and 0x38) or ((videoRamAddr shr 2) and 0x07)
    protected fun setBusAddress(addr: Int) { ppuBusAddress = addr and 0x3FFF; if (mapper.hasVramAddressHook()) mapper.notifyVramAddressChange(ppuBusAddress) }
    protected fun readVram(addr: Int, type: MemoryOperationType = MemoryOperationType.Read): Int { setBusAddress(addr); return mapper.readVram(addr and 0x3FFF, type) }
    protected fun writeVram(addr: Int, value: Int) { setBusAddress(addr); mapper.writeVram(addr and 0x3FFF, value and 0xFF) }
    protected fun readSpriteRam(addr: Int): Int = spriteRam[addr and 0xFF] and 0xFF
    protected fun writeSpriteRam(addr: Int, value: Int) { spriteRam[addr and 0xFF] = value and 0xFF; oamDecayCycles[(addr and 0xFF) shr 3] = masterClock }
    protected fun corruptOamRow(sourceRow: Int, destRow: Int) { for (i in 0 until 8) spriteRam[((destRow and 0x1F) shl 3) + i] = spriteRam[((sourceRow and 0x1F) shl 3) + i] }
    protected fun getRegisterID(addr: Int): PpuRegisters = if ((addr and 0xFFFF) == 0x4014) PpuRegisters.SpriteDMA else when (addr and 0x07) {
        0 -> PpuRegisters.Control
        1 -> PpuRegisters.Mask
        2 -> PpuRegisters.Status
        3 -> PpuRegisters.SpriteAddr
        4 -> PpuRegisters.SpriteData
        5 -> PpuRegisters.ScrollOffsets
        6 -> PpuRegisters.VideoMemoryAddr
        else -> PpuRegisters.VideoMemoryData
    }

    protected fun statusByte(): Int = (if (statusFlags.spriteOverflow) 0x20 else 0) or (if (statusFlags.sprite0Hit) 0x40 else 0) or (if (statusFlags.verticalBlank) 0x80 else 0)
    private fun isPpu2C05(): Boolean = getPpuModel().ordinal in PpuModel.Ppu2C05A.ordinal..PpuModel.Ppu2C05E.ordinal
    private fun intArrayAsByteArray(values: IntArray): ByteArray = ByteArray(values.size) { values[it].toByte() }
    private fun copyControl(source: PpuControlFlags) {
        control.backgroundPatternAddr = source.backgroundPatternAddr
        control.spritePatternAddr = source.spritePatternAddr
        control.verticalWrite = source.verticalWrite
        control.largeSprites = source.largeSprites
        control.secondaryPpu = source.secondaryPpu
        control.nmiOnVerticalBlank = source.nmiOnVerticalBlank
    }
    private fun copyMask(source: PpuMaskFlags) {
        mask.grayscale = source.grayscale
        mask.backgroundMask = source.backgroundMask
        mask.spriteMask = source.spriteMask
        mask.backgroundEnabled = source.backgroundEnabled
        mask.spritesEnabled = source.spritesEnabled
        mask.intensifyRed = source.intensifyRed
        mask.intensifyGreen = source.intensifyGreen
        mask.intensifyBlue = source.intensifyBlue
    }
    private fun copyStatus(source: PPUStatusFlags) {
        statusFlags.spriteOverflow = source.spriteOverflow
        statusFlags.sprite0Hit = source.sprite0Hit
        statusFlags.verticalBlank = source.verticalBlank
    }
}
