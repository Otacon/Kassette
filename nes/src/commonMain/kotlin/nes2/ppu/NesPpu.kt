package nes2.ppu

import nes2.console.NesConsole
import nes2.console.NesConstants
import nes2.console.NesPpuFrame
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

    override fun getScreenBuffer(previousBuffer: Boolean, processGrayscaleEmphasisBits: Boolean): IntArray {
        if (!previousBuffer && processGrayscaleEmphasisBits) updateGrayscaleAndIntensifyBits()
        return if (previousBuffer) outputBuffers[if (currentOutputBuffer === outputBuffers[0]) 1 else 0] else currentOutputBuffer
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
            if (scanline < 240) {
                processScanline()
            } else if (cycle == 1 && scanline == nmiScanline) {
                if (!preventVblFlag) {
                    statusFlags.verticalBlank = true
                    beginVBlank()
                }
                preventVblFlag = false
            } else if (region == ConsoleRegion.Pal && scanline >= palSpriteEvalScanline && (cycle and 1) == 0) {
                spriteRamAddr = (spriteRamAddr + 1) and 0xFF
                if (enableOamDecay) oamDecayCycles[spriteRamAddr shr 3] = console.cpu.getCycleCount()
            }
        } else {
            processScanlineFirstCycle()
        }
        processDelayedStateUpdates()
    }

    protected open fun processScanline() {
        processScanlineImpl()
    }

    protected fun processScanlineImpl() {
        if (cycle <= 256) {
            if (prevRenderingEnabled) {
                if (scanline >= 0) {
                    processSpriteShifters()
                    drawPixel()
                    processSpriteEvaluation()
                    shiftTileRegisters()
                } else if (cycle == 1) {
                    statusFlags.verticalBlank = false
                    console.cpu.clearNmiFlag()
                }
                if ((cycle and 0x07) == 0) {
                    incHorizontalScrolling()
                    if (cycle == 256) incVerticalScrolling()
                }
                loadTileInfo()
            } else {
                processRenderingDisabledPixel()
            }
        } else if (cycle in 257..320) {
            if (prevRenderingEnabled) {
                sprite0Visible = sprite0Added
                spriteRamAddr = 0
                when ((cycle - 257) % 8) {
                    0 -> readVram(getNameTableAddr())
                    2 -> readVram(getNameTableAddr())
                    4 -> loadSpriteTileInfo()
                }
                if (scanline == -1 && cycle in 280..304) videoRamAddr = (videoRamAddr and 0x7BE0.inv()) or (tmpVideoRamAddr and 0x7BE0)
                if (cycle == 320) loadExtraSprites()
                if (((cycle - 1) and 4) == 0) secondaryOamAddr = (secondaryOamAddr + 1) and 0xFF
            }
            if (cycle == 257) {
                spriteIndex = 0
                spriteCount = 0
                if (prevRenderingEnabled) {
                    videoRamAddr = (videoRamAddr and 0x041F.inv()) or (tmpVideoRamAddr and 0x041F)
                    secondaryOamAddr = 0
                }
            }
        } else if (cycle in 321..336) {
            if (prevRenderingEnabled) {
                if (cycle == 321) secondaryOamAddr = (secondaryOamAddr + 1) and 0xFF
                else if (cycle == 328 || cycle == 336) incHorizontalScrolling()
                shiftTileRegisters()
                loadTileInfo()
            }
        } else if (cycle == 337) {
            if (isRenderingEnabled()) tile.tileAddr = readVram(getNameTableAddr())
        } else if (cycle == 339) {
            activeSpriteShifters = 0
            if (isRenderingEnabled()) {
                tile.tileAddr = readVram(getNameTableAddr())
                for (i in 0 until 8) {
                    val bit = 1 shl (spriteShifterList[i] and 0x07)
                    if (spriteShifterList[i] != SpriteShifterDone && (expiredSpriteShifters and bit) == 0) countingSpriteShifters = countingSpriteShifters or bit
                }
                if (scanline == -1 && (frameCountValue and 0x01) != 0 && region == ConsoleRegion.Ntsc && getPpuModel() == PpuModel.Ppu2C02) {
                    cycle = 340
                    dotSkipped = 3
                    needStateUpdate = true
                    for (i in 0 until 8) spriteShifterList[i] += 1 shl 4
                    nextSpriteShifterCycle++
                }
            }
            for (i in 0 until 8) {
                if (spriteShifterList[i] != SpriteShifterDone) {
                    val bit = 1 shl (spriteShifterList[i] and 0x07)
                    if ((countingSpriteShifters and bit) == 0) activeSpriteShifters = activeSpriteShifters or bit
                }
            }
            updateProcessSpritesFlag()
        }
    }

    protected fun processScanlineFirstCycle() {
        cycle = 0
        scanline++
        if (scanline > vblankEnd) {
            lastUpdatedPixel = -1
            scanline = -1
            spriteCount = 0
            console.notifyPpuStartFrame(frameCountValue)
            updateMinimumDrawCycles()
        }

        updateApuStatus()

        if (scanline < 240) {
            spriteShifterList.sort(0, 8)
            nextSpriteShifter = 0
            nextSpriteShifterCycle = spriteShifterList[0] shr 4
            if (scanline == -1) {
                statusFlags.spriteOverflow = false
                statusFlags.sprite0Hit = false
                statusFlags.verticalBlank = false
                console.cpu.clearNmiFlag()
                allowFullPpuAccess = true
                currentOutputBuffer = if (currentOutputBuffer === outputBuffers[0]) outputBuffers[1] else outputBuffers[0]
            } else if (prevRenderingEnabled) {
                secondaryOamAddr = 0
                if (scanline > 0 || ((frameCountValue and 0x01) == 0 || region != ConsoleRegion.Ntsc || getPpuModel() != PpuModel.Ppu2C02)) {
                    setBusAddress((tile.tileAddr shl 4) or (videoRamAddr shr 12) or control.backgroundPatternAddr)
                }
            }
        } else if (scanline == 240) {
            if (prevRenderingEnabled) secondaryOamAddr = 0
            setBusAddress(videoRamAddr and 0x3FFF)
            sendFrame()
            frameCountValue++
        }

        if (enableOamDecay && (scanline >= 240 || !isRenderingEnabled())) oamDecayCycles[spriteRamAddr shr 3] = console.cpu.getCycleCount()
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
        if (ignoreVramRead > 0) {
            ignoreVramRead--
            if (ignoreVramRead > 0) needStateUpdate = true
        }
        if (updateVramAddrDelay > 0) {
            updateVramAddrDelay--
            if (updateVramAddrDelay == 0) {
                videoRamAddr = updateVramAddr and 0x7FFF
                tmpVideoRamAddr = videoRamAddr
                setBusAddress(videoRamAddr and 0x3FFF)
            } else {
                needStateUpdate = true
            }
        }
        if (needVideoRamIncrement) {
            needVideoRamIncrement = false
            updateVideoRamAddr()
        }
        if (ppuMemoryDataReadStateMachine > 0) {
            ppuMemoryDataReadStateMachine--
            if (ppuMemoryDataReadStateMachine == 0) {
                memoryReadBuffer = readVram(ppuBusAddress and 0x3FFF, MemoryOperationType.Read)
                needVideoRamIncrement = true
            }
            needStateUpdate = true
        }
        if (ppuMemoryDataWriteStateMachine > 0) {
            ppuMemoryDataWriteStateMachine--
            if (ppuMemoryDataWriteStateMachine == 0) {
                if ((ppuBusAddress and 0x3FFF) >= 0x3F00) writePaletteRam(ppuBusAddress, ppuMemoryDataWriteLatch)
                else if (scanline >= 240 || !isRenderingEnabled()) mapper.writeVram(ppuBusAddress and 0x3FFF, ppuMemoryDataWriteLatch)
                else mapper.writeVram(ppuBusAddress and 0x3FFF, ppuBusAddress and 0xFF)
                needVideoRamIncrement = true
            }
            needStateUpdate = true
        }
        if (dotSkipped != 0) {
            dotSkipped--
            needStateUpdate = needStateUpdate || dotSkipped != 0
            updateProcessSpritesFlag()
        }
        if (needStateUpdate) updateState()
    }

    protected fun updateState() {
        needStateUpdate = false
        val newRenderingEnabled = mask.backgroundEnabled || mask.spritesEnabled
        if (renderingEnabled != newRenderingEnabled) {
            renderingEnabled = newRenderingEnabled
            needStateUpdate = true
        }
        if (prevRenderingEnabled != renderingEnabled) {
            prevRenderingEnabled = renderingEnabled
            if (scanline < 240 && console.options.ppu.enablePpuOamRowCorruption && (cycle >= 257 || (cycle and 1) == 0)) {
                if (renderingEnabled) corruptOamRow(spriteRamAddr shr 3, secondaryOamAddr and 0x1F)
                else corruptOamRow(secondaryOamAddr and 0x1F, spriteRamAddr shr 3)
            }
        }
    }

    protected fun loadTileInfo() {
        when (cycle and 0x07) {
            0 -> {
                highBitShift = (highBitShift and 0xFF00) or tile.highByte
                lowBitShift = (lowBitShift and 0xFF00) or tile.lowByte
                previousTilePalette = currentTilePalette
                currentTilePalette = tile.paletteOffset
                pushTileInformation()
            }
            1 -> {
                val tileIndex = readVram(getNameTableAddr())
                tile.tileAddr = (tileIndex shl 4) or (videoRamAddr shr 12) or control.backgroundPatternAddr
                storeTileInformation()
            }
            3 -> {
                val shift = ((videoRamAddr shr 4) and 0x04) or (videoRamAddr and 0x02)
                tile.paletteOffset = ((readVram(getAttributeAddr()) shr shift) and 0x03) shl 2
            }
            5 -> tile.lowByte = readVram(tile.tileAddr)
            7 -> tile.highByte = readVram(tile.tileAddr + 8)
        }
    }

    protected open fun storeTileInformation() {}
    protected open fun pushTileInformation() {}

    protected fun loadSprite(spriteYValue: Int, tileIndex: Int, attributes: Int, spriteX: Int, extraSprite: Boolean) {
        val spriteY = spriteYValue and 0xFF
        val backgroundPriority = (attributes and 0x20) == 0x20
        val horizontalMirror = (attributes and 0x40) == 0x40
        val verticalMirror = (attributes and 0x80) == 0x80
        val spriteSizeMask = if (control.largeSprites) 15 else 7
        val scanline8Bit = (if (scanline >= 0) scanline else if (region == ConsoleRegion.Ntsc) 261 else 311) and 0xFF
        var rangeResult = (scanline8Bit - spriteY) and 0xFFFF
        if (verticalMirror) rangeResult = rangeResult xor spriteSizeMask
        val tileAddr = if (control.largeSprites) {
            (((tileIndex and 0x01) shl 12) or ((tileIndex and 0x01.inv()) shl 4)) + ((rangeResult and 0x08) shl 1) + (rangeResult and 0x07)
        } else {
            (control.spritePatternAddr or (tileIndex shl 4)) + (rangeResult and 0x07)
        }

        val info = spriteTiles[spriteIndex]
        info.backgroundPriority = backgroundPriority
        info.paletteOffset = ((attributes and 0x03) shl 2) or 0x10
        if (extraSprite) {
            info.lowByte = mapper.debugReadVram(tileAddr)
            info.highByte = mapper.debugReadVram(tileAddr + 8)
        } else {
            info.lowByte = readVram(tileAddr)
            info.highByte = readVram(tileAddr + 8)
        }
        info.spriteX = spriteX and 0xFF

        if (rangeResult <= spriteSizeMask) {
            if (horizontalMirror) {
                info.lowByte = reverseByte(info.lowByte)
                info.highByte = reverseByte(info.highByte)
            }
            storeSpriteInformation(horizontalMirror, verticalMirror, tileAddr, rangeResult, info)
            if (!extraSprite) {
                spriteShifterList[spriteIndex] = (((spriteX and 0xFF) + 1) shl 4) or spriteIndex
                expiredSpriteShifters = expiredSpriteShifters and (1 shl spriteIndex).inv()
            }
            spriteCount++
            processSprites = true
        } else if (!extraSprite) {
            info.lowByte = 0
            info.highByte = 0
            spriteShifterList[spriteIndex] = SpriteShifterDone
        }
        spriteIndex++
    }

    protected open fun storeSpriteInformation(horizontalMirror: Boolean, verticalMirror: Boolean, tileAddr: Int, lineOffset: Int, sprite: NesSpriteInfo) {}

    protected fun loadExtraSprites() {
        if (spriteCount == 8 && removeSpriteLimit() && scanline >= 0) {
            var loadExtraSprites = true
            if (useAdaptiveSpriteLimit()) {
                var lastPosition = 0xFFFF
                var identicalSpriteCount = 0
                var maxIdenticalSpriteCount = 0
                for (i in 0 until 64) {
                    val y = spriteRam[i shl 2]
                    if (scanline >= y && scanline < y + if (control.largeSprites) 16 else 8) {
                        val x = spriteRam[(i shl 2) + 3]
                        val position = (y shl 8) or x
                        if (lastPosition != position) {
                            if (identicalSpriteCount > maxIdenticalSpriteCount) maxIdenticalSpriteCount = identicalSpriteCount
                            lastPosition = position
                            identicalSpriteCount = 1
                        } else identicalSpriteCount++
                    }
                }
                loadExtraSprites = identicalSpriteCount < 8 && maxIdenticalSpriteCount < 8
            }
            if (loadExtraSprites) {
                var i = (lastVisibleSpriteAddr + 4) and 0xFC
                while (i != (firstVisibleSpriteAddr and 0xFC)) {
                    val spriteY = spriteRam[i]
                    if (scanline >= spriteY && scanline < spriteY + if (control.largeSprites) 16 else 8) {
                        loadSprite(spriteY, spriteRam[i + 1], spriteRam[i + 2], spriteRam[i + 3], true)
                    }
                    i = (i + 4) and 0xFC
                }
            }
        }
    }

    protected open fun removeSpriteLimit(): Boolean = false
    protected open fun useAdaptiveSpriteLimit(): Boolean = false

    protected fun loadSpriteTileInfo() {
        spriteIndex = (cycle - 257) shr 3
        val addr = spriteIndex * 4
        loadSprite(secondarySpriteRam[addr], secondarySpriteRam[addr + 1], secondarySpriteRam[addr + 2], secondarySpriteRam[addr + 3], false)
    }

    protected fun shiftTileRegisters() {
        lowBitShift = (lowBitShift shl 1) and 0xFFFF
        highBitShift = ((highBitShift shl 1) or 1) and 0xFFFF
    }

    protected fun processRenderingDisabledPixel() {
        if (scanline >= 0) {
            processSpriteShifters()
            drawPixel()
        } else if (cycle == 1) {
            statusFlags.verticalBlank = false
            console.cpu.clearNmiFlag()
        }
    }

    protected open fun drawPixel() {}

    protected fun updateProcessSpritesFlag() {
        processSprites = spriteCount != 0 || activeSpriteShifters != 0 || dotSkipped != 0
    }

    protected fun processSpriteEvaluationStart() {
        sprite0Added = false
        spriteInRange = false
        secondaryOamAddr = 0
        overflowBugCounter = 0
        oamCopyDone = false
        firstVisibleSpriteAddr = spriteRamAddr and 0xFC
        lastVisibleSpriteAddr = firstVisibleSpriteAddr
    }

    protected fun processSpriteShifters() {
        if (nextSpriteShifterCycle == cycle) {
            while (nextSpriteShifter < spriteShifterList.size && (spriteShifterList[nextSpriteShifter] shr 4) == cycle) {
                val bit = 1 shl (spriteShifterList[nextSpriteShifter] and 7)
                if ((countingSpriteShifters and bit) != 0) {
                    activeSpriteShifters = activeSpriteShifters or bit
                    expiredSpriteShifters = expiredSpriteShifters or bit
                    countingSpriteShifters = countingSpriteShifters and bit.inv()
                }
                nextSpriteShifter++
            }
            nextSpriteShifterCycle = if (nextSpriteShifter < spriteShifterList.size) spriteShifterList[nextSpriteShifter] shr 4 else SpriteShifterDone shr 4
        }
    }

    protected fun processSpriteEvaluation() {
        if (cycle < 65) {
            oamCopybuffer = 0xFF
            secondarySpriteRam[secondaryOamAddr and 0x1F] = 0xFF
            if ((cycle and 1) == 0) secondaryOamAddr = (secondaryOamAddr + 1) and 0xFF
            return
        }
        if ((cycle and 0x01) != 0) {
            if (cycle == 65) processSpriteEvaluationStart()
            oamCopybuffer = readSpriteRam(spriteRamAddr)
            return
        }
        var spriteAddrH = spriteRamAddr shr 2
        var spriteAddrL = spriteRamAddr and 3
        if (oamCopyDone && !console.options.ppu.enablePpuOamRowCorruption) {
            spriteAddrH = (spriteAddrH + 1) and 0x3F
            oamCopybuffer = secondarySpriteRam[secondaryOamAddr and 0x1F]
        } else {
            if (!spriteInRange && scanline >= oamCopybuffer && scanline < oamCopybuffer + if (control.largeSprites) 16 else 8) spriteInRange = !oamCopyDone
            if (secondaryOamAddr < 0x20) {
                secondarySpriteRam[secondaryOamAddr] = oamCopybuffer
                if (spriteInRange) {
                    if (cycle == 66) sprite0Added = true
                    spriteAddrL++
                    secondaryOamAddr++
                    if (spriteAddrL >= 4) {
                        spriteAddrH = (spriteAddrH + 1) and 0x3F
                        spriteAddrL = 0
                        if (spriteAddrH == 0) oamCopyDone = true
                    }
                    if ((secondaryOamAddr and 0x03) == 0) {
                        spriteInRange = false
                        lastVisibleSpriteAddr = ((spriteAddrH - 1) and 0x3F) * 4
                        if (spriteAddrL != 0) {
                            val inRange = scanline >= oamCopybuffer && scanline < oamCopybuffer + if (control.largeSprites) 16 else 8
                            if (!inRange) spriteAddrL = 0
                        }
                    }
                } else {
                    spriteAddrH = (spriteAddrH + 1) and 0x3F
                    spriteAddrL = 0
                    if (spriteAddrH == 0) oamCopyDone = true
                }
            } else {
                oamCopybuffer = secondarySpriteRam[secondaryOamAddr and 0x1F]
                if (oamCopyDone) {
                    spriteAddrH = (spriteAddrH + 1) and 0x3F
                    spriteAddrL = 0
                } else if (spriteInRange) {
                    statusFlags.spriteOverflow = true
                    spriteAddrL++
                    if (spriteAddrL == 4) {
                        spriteAddrH = (spriteAddrH + 1) and 0x3F
                        spriteAddrL = 0
                    }
                    if (overflowBugCounter == 0) overflowBugCounter = 3
                    else if (overflowBugCounter > 0) {
                        overflowBugCounter--
                        if (overflowBugCounter == 0) {
                            oamCopyDone = true
                            spriteAddrL = 0
                        }
                    }
                } else {
                    spriteAddrH = (spriteAddrH + 1) and 0x3F
                    spriteAddrL = (spriteAddrL + 1) and 0x03
                    if (spriteAddrH == 0) oamCopyDone = true
                }
            }
        }
        spriteRamAddr = (spriteAddrL and 0x03) or (spriteAddrH shl 2)
    }

    protected fun getPixelColor(): Int {
        val offset = xScroll
        var backgroundColor = 0
        var spriteBgColor = 0
        if (cycle > minimumDrawBgCycle) {
            spriteBgColor = (((lowBitShift shl offset) and 0x8000) shr 15) or (((highBitShift shl offset) and 0x8000) shr 14)
            if (emulatorBgEnabled) backgroundColor = spriteBgColor
        }
        var spriteIndexValue = -1
        var spriteColor = 0
        if (processSprites && prevRenderingEnabled) {
            var remainingShifters = if (dotSkipped != 0) 0xFF else activeSpriteShifters
            if (activeSpriteShifters != 0) lastSprite = spriteTiles[highestBitIndex(activeSpriteShifters)]
            while (remainingShifters != 0) {
                val i = highestBitIndex(remainingShifters)
                remainingShifters = remainingShifters and (1 shl i).inv()
                val sprite = spriteTiles[i]
                val currColor = ((sprite.highByte shr 6) and 0x2) or (sprite.lowByte shr 7)
                if (currColor != 0) {
                    spriteIndexValue = i
                    spriteColor = currColor
                    lastSprite = sprite
                }
                sprite.highByte = (sprite.highByte shl 1) and 0xFF
                sprite.lowByte = (sprite.lowByte shl 1) and 0xFF
                if ((sprite.highByte or sprite.lowByte) == 0) {
                    activeSpriteShifters = activeSpriteShifters and (1 shl i).inv()
                    updateProcessSpritesFlag()
                }
            }
            if (cycle > minimumDrawSpriteCycle && mask.spritesEnabled) {
                if (spriteCount > 8 && spriteColor == 0) {
                    spriteIndexValue = 8
                    while (spriteIndexValue < spriteCount) {
                        val sprite = spriteTiles[spriteIndexValue]
                        val shift = cycle - sprite.spriteX - 1
                        if (shift in 0..7) {
                            lastSprite = sprite
                            spriteColor = (((sprite.lowByte shl shift) and 0x80) shr 7) or (((sprite.highByte shl shift) and 0x80) shr 6)
                            if (spriteColor != 0) break
                        }
                        spriteIndexValue++
                    }
                }
                if (spriteColor != 0 && spriteIndexValue >= 0) {
                    if (sprite0Visible && spriteIndexValue == 0 && spriteBgColor != 0 && cycle != 256 && mask.backgroundEnabled && !statusFlags.sprite0Hit && cycle > minimumDrawSpriteStandardCycle) statusFlags.sprite0Hit = true
                    if (emulatorSpritesEnabled && (backgroundColor == 0 || !spriteTiles[spriteIndexValue].backgroundPriority)) return spriteTiles[spriteIndexValue].paletteOffset + spriteColor
                }
            }
        }
        return (if (offset + ((cycle - 1) and 0x07) < 8) previousTilePalette else currentTilePalette) + backgroundColor
    }

    protected fun sendFrame() {
        updateGrayscaleAndIntensifyBits()
        val ppuCycles = if (masterClockDivider == 0) 0L else masterClock / masterClockDivider
        var videoPhase = ((ppuCycles - 82181L) % 3L).toInt()
        if (videoPhase < 0) videoPhase += 3
        if (region != ConsoleRegion.Ntsc || console.options.ppu.extraScanlinesAfterNmi != 0 || console.options.ppu.extraScanlinesBeforeNmi != 0) {
            videoPhase = frameCountValue and 0x01
        }
        console.notifyPpuFrame(
            NesPpuFrame(
                pixels = currentOutputBuffer.copyOf(),
                frameCount = frameCountValue,
                videoPhase = videoPhase,
            )
        )
        enableOamDecay = console.options.ppu.enableOamDecay
    }

    fun captureSnapshot(): NesPpuSnapshot = NesPpuSnapshot(
        paletteRam = paletteRam.copyOf(),
        spriteRam = spriteRam.copyOf(),
        secondarySpriteRam = secondarySpriteRam.copyOf(),
        openBusDecayStamp = openBusDecayStamp.copyOf(),
        spriteRamAddr = spriteRamAddr,
        videoRamAddr = videoRamAddr,
        xScroll = xScroll,
        tmpVideoRamAddr = tmpVideoRamAddr,
        writeToggle = writeToggle,
        highBitShift = highBitShift,
        lowBitShift = lowBitShift,
        control = control.copy(),
        mask = mask.copy(),
        paletteRamMask = paletteRamMask,
        intensifyColorBits = intensifyColorBits,
        statusFlags = statusFlags.copy(),
        scanline = scanline,
        cycle = cycle,
        frameCount = frameCountValue,
        memoryReadBuffer = memoryReadBuffer,
        region = region,
        ppuBusAddress = ppuBusAddress,
        masterClock = masterClock,
        currentTilePalette = currentTilePalette,
        tile = tile.copy(),
        previousTilePalette = previousTilePalette,
        spriteIndex = spriteIndex,
        spriteCount = spriteCount,
        sprite0Added = sprite0Added,
        sprite0Visible = sprite0Visible,
        oamCopybuffer = oamCopybuffer,
        secondaryOamAddr = secondaryOamAddr,
        spriteInRange = spriteInRange,
        prevRenderingEnabled = prevRenderingEnabled,
        renderingEnabled = renderingEnabled,
        openBus = openBus,
        ignoreVramRead = ignoreVramRead,
        spriteShifterList = spriteShifterList.copyOf(),
        nextSpriteShifter = nextSpriteShifter,
        nextSpriteShifterCycle = nextSpriteShifterCycle,
        activeSpriteShifters = activeSpriteShifters,
        countingSpriteShifters = countingSpriteShifters,
        expiredSpriteShifters = expiredSpriteShifters,
        dotSkipped = dotSkipped,
        processSprites = processSprites,
        oamCopyDone = oamCopyDone,
        needStateUpdate = needStateUpdate,
        preventVblFlag = preventVblFlag,
        needVideoRamIncrement = needVideoRamIncrement,
        overflowBugCounter = overflowBugCounter,
        updateVramAddr = updateVramAddr,
        updateVramAddrDelay = updateVramAddrDelay,
        allowFullPpuAccess = allowFullPpuAccess,
        ppuMemoryDataReadStateMachine = ppuMemoryDataReadStateMachine,
        ppuMemoryDataWriteStateMachine = ppuMemoryDataWriteStateMachine,
        ppuMemoryDataWriteLatch = ppuMemoryDataWriteLatch,
        spriteTiles = Array(spriteTiles.size) { spriteTiles[it].copy() },
        firstVisibleSpriteAddr = firstVisibleSpriteAddr,
        lastVisibleSpriteAddr = lastVisibleSpriteAddr,
        oamDecayCycles = oamDecayCycles.copyOf(),
        currentOutputBufferIndex = if (currentOutputBuffer === outputBuffers[0]) 0 else 1,
        outputBuffers = Array(outputBuffers.size) { outputBuffers[it].copyOf() },
    )

    fun restoreSnapshot(snapshot: NesPpuSnapshot) {
        snapshot.paletteRam.copyInto(paletteRam, endIndex = minOf(snapshot.paletteRam.size, paletteRam.size))
        snapshot.spriteRam.copyInto(spriteRam, endIndex = minOf(snapshot.spriteRam.size, spriteRam.size))
        snapshot.secondarySpriteRam.copyInto(secondarySpriteRam, endIndex = minOf(snapshot.secondarySpriteRam.size, secondarySpriteRam.size))
        snapshot.openBusDecayStamp.copyInto(openBusDecayStamp, endIndex = minOf(snapshot.openBusDecayStamp.size, openBusDecayStamp.size))
        spriteRamAddr = snapshot.spriteRamAddr and 0xFF
        videoRamAddr = snapshot.videoRamAddr and 0x7FFF
        xScroll = snapshot.xScroll and 0xFF
        tmpVideoRamAddr = snapshot.tmpVideoRamAddr and 0x7FFF
        writeToggle = snapshot.writeToggle
        highBitShift = snapshot.highBitShift and 0xFFFF
        lowBitShift = snapshot.lowBitShift and 0xFFFF
        copyControlFrom(snapshot.control)
        copyMaskFrom(snapshot.mask)
        paletteRamMask = snapshot.paletteRamMask
        intensifyColorBits = snapshot.intensifyColorBits
        copyStatusFrom(snapshot.statusFlags)
        scanline = snapshot.scanline
        cycle = snapshot.cycle
        frameCountValue = snapshot.frameCount
        memoryReadBuffer = snapshot.memoryReadBuffer and 0xFF
        region = snapshot.region
        ppuBusAddress = snapshot.ppuBusAddress and 0x3FFF
        masterClock = snapshot.masterClock
        currentTilePalette = snapshot.currentTilePalette and 0xFF
        tile = snapshot.tile.copy()
        previousTilePalette = snapshot.previousTilePalette and 0xFF
        spriteIndex = snapshot.spriteIndex
        spriteCount = snapshot.spriteCount
        sprite0Added = snapshot.sprite0Added
        sprite0Visible = snapshot.sprite0Visible
        oamCopybuffer = snapshot.oamCopybuffer and 0xFF
        secondaryOamAddr = snapshot.secondaryOamAddr and 0xFF
        spriteInRange = snapshot.spriteInRange
        prevRenderingEnabled = snapshot.prevRenderingEnabled
        renderingEnabled = snapshot.renderingEnabled
        openBus = snapshot.openBus and 0xFF
        ignoreVramRead = snapshot.ignoreVramRead
        snapshot.spriteShifterList.copyInto(spriteShifterList, endIndex = minOf(snapshot.spriteShifterList.size, spriteShifterList.size))
        spriteShifterList[spriteShifterList.lastIndex] = SpriteShifterDone
        nextSpriteShifter = snapshot.nextSpriteShifter.coerceIn(0, spriteShifterList.lastIndex)
        nextSpriteShifterCycle = snapshot.nextSpriteShifterCycle
        activeSpriteShifters = snapshot.activeSpriteShifters
        countingSpriteShifters = snapshot.countingSpriteShifters
        expiredSpriteShifters = snapshot.expiredSpriteShifters
        dotSkipped = snapshot.dotSkipped
        processSprites = snapshot.processSprites
        oamCopyDone = snapshot.oamCopyDone
        needStateUpdate = snapshot.needStateUpdate
        preventVblFlag = snapshot.preventVblFlag
        needVideoRamIncrement = snapshot.needVideoRamIncrement
        overflowBugCounter = snapshot.overflowBugCounter
        updateVramAddr = snapshot.updateVramAddr and 0x7FFF
        updateVramAddrDelay = snapshot.updateVramAddrDelay
        allowFullPpuAccess = snapshot.allowFullPpuAccess
        ppuMemoryDataReadStateMachine = snapshot.ppuMemoryDataReadStateMachine
        ppuMemoryDataWriteStateMachine = snapshot.ppuMemoryDataWriteStateMachine
        ppuMemoryDataWriteLatch = snapshot.ppuMemoryDataWriteLatch and 0xFF
        for (i in spriteTiles.indices) {
            val source = snapshot.spriteTiles.getOrNull(i) ?: NesSpriteInfo()
            spriteTiles[i].backgroundPriority = source.backgroundPriority
            spriteTiles[i].spriteX = source.spriteX
            spriteTiles[i].lowByte = source.lowByte
            spriteTiles[i].highByte = source.highByte
            spriteTiles[i].paletteOffset = source.paletteOffset
        }
        firstVisibleSpriteAddr = snapshot.firstVisibleSpriteAddr and 0xFF
        lastVisibleSpriteAddr = snapshot.lastVisibleSpriteAddr and 0xFF
        snapshot.oamDecayCycles.copyInto(oamDecayCycles, endIndex = minOf(snapshot.oamDecayCycles.size, oamDecayCycles.size))
        for (i in outputBuffers.indices) {
            snapshot.outputBuffers.getOrNull(i)?.copyInto(outputBuffers[i], endIndex = minOf(snapshot.outputBuffers[i].size, outputBuffers[i].size))
        }
        currentOutputBuffer = outputBuffers[snapshot.currentOutputBufferIndex.coerceIn(0, outputBuffers.lastIndex)]

        updateTimings(region)
        updateMinimumDrawCycles()
        updateGrayscaleAndIntensifyBits()
        for (i in oamDecayCycles.indices) oamDecayCycles[i] = console.cpu.getCycleCount()
        lastUpdatedPixel = -1
        updateApuStatus()
    }

    private fun copyControlFrom(source: PpuControlFlags) {
        control.backgroundPatternAddr = source.backgroundPatternAddr
        control.spritePatternAddr = source.spritePatternAddr
        control.verticalWrite = source.verticalWrite
        control.largeSprites = source.largeSprites
        control.secondaryPpu = source.secondaryPpu
        control.nmiOnVerticalBlank = source.nmiOnVerticalBlank
    }

    private fun copyMaskFrom(source: PpuMaskFlags) {
        mask.grayscale = source.grayscale
        mask.backgroundMask = source.backgroundMask
        mask.spriteMask = source.spriteMask
        mask.backgroundEnabled = source.backgroundEnabled
        mask.spritesEnabled = source.spritesEnabled
        mask.intensifyRed = source.intensifyRed
        mask.intensifyGreen = source.intensifyGreen
        mask.intensifyBlue = source.intensifyBlue
    }

    private fun copyStatusFrom(source: PPUStatusFlags) {
        statusFlags.spriteOverflow = source.spriteOverflow
        statusFlags.sprite0Hit = source.sprite0Hit
        statusFlags.verticalBlank = source.verticalBlank
    }

    protected fun updateApuStatus() {
        var enabled = true
        if (scanline > 240) {
            if (scanline > standardVblankEnd) {
                enabled = false
            } else if (scanline >= standardNmiScanline && scanline < nmiScanline) {
                enabled = false
            }
        }
        console.apu.setApuStatus(enabled)
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
    private fun reverseByte(value: Int): Int {
        var v = value and 0xFF
        var r = 0
        repeat(8) {
            r = (r shl 1) or (v and 1)
            v = v shr 1
        }
        return r and 0xFF
    }

    private fun highestBitIndex(value: Int): Int {
        var i = 31
        while (i > 0 && (value and (1 shl i)) == 0) i--
        return i
    }

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
