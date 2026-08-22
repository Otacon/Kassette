package nes2.mapper

class MMC1(romData: RomData) : BaseMapper(romData) {
    private var writeBuffer = 0
    private var shiftCount = 0
    private var wramDisable = false
    private var chrMode = false
    private var prgMode = false
    private var slotSelect = false
    private var chrReg0 = 0
    private var chrReg1 = 0
    private var prgReg = 0
    private var lastWriteCycle = 0L
    private var forceWramOn = false
    private var lastChrReg = 0

    override fun getPrgPageSize(): Int = 0x4000
    override fun getChrPageSize(): Int = 0x1000

    override fun initMapper() {
        processRegisterWrite(0x8000, getPowerOnByte() or 0x0C)
        processRegisterWrite(0xA000, getPowerOnByte())
        processRegisterWrite(0xC000, getPowerOnByte())
        processRegisterWrite(0xE000, if (romInfoState.databaseInfo.board.contains("MMC1B")) 0x10 else 0x00)
        forceWramOn = romInfoState.databaseInfo.board == "MMC1A"
        lastChrReg = 0xA000
        updateState()
    }

    override fun writeRegister(addr: Int, value: Int) {
        val currentCycle = console?.getMasterClock() ?: Long.MAX_VALUE
        if ((value and 0x80) != 0 || currentCycle - lastWriteCycle >= 2) {
            processBitWrite(addr, value)
        }
        lastWriteCycle = currentCycle
    }

    private fun resetBuffer() {
        shiftCount = 0
        writeBuffer = 0
    }

    private fun processBitWrite(addr: Int, value: Int) {
        if ((value and 0x80) == 0x80) {
            resetBuffer()
            prgMode = true
            slotSelect = true
            updateState()
        } else {
            writeBuffer = (writeBuffer shr 1) or ((value shl 4) and 0x10)
            shiftCount++
            if (shiftCount == 5) {
                processRegisterWrite(addr, writeBuffer)
                updateState()
                resetBuffer()
            }
        }
    }

    private fun processRegisterWrite(addr: Int, value: Int) {
        when (addr and 0xE000) {
            0x8000 -> {
                setMirroringType(when (value and 0x03) {
                    0 -> MirroringType.ScreenAOnly
                    1 -> MirroringType.ScreenBOnly
                    2 -> MirroringType.Vertical
                    else -> MirroringType.Horizontal
                })
                slotSelect = (value and 0x04) != 0
                prgMode = (value and 0x08) != 0
                chrMode = (value and 0x10) != 0
            }
            0xA000 -> { lastChrReg = addr; chrReg0 = value and 0x1F }
            0xC000 -> { lastChrReg = addr; chrReg1 = value and 0x1F }
            0xE000 -> { prgReg = value and 0x0F; wramDisable = (value and 0x10) != 0 }
        }
    }

    private fun updateState() {
        val extraReg = if (lastChrReg == 0xC000 && chrMode) chrReg1 else chrReg0
        val prgBankSelect = if (prgSize == 0x80000) extraReg and 0x10 else 0
        val access = if (wramDisable && !forceWramOn) MemoryAccessType.NoAccess else MemoryAccessType.ReadWrite
        val memType = if (hasBattery()) PrgMemoryType.SaveRam else PrgMemoryType.WorkRam
        val totalRam = saveRamSizeBytes + workRamSizeBytes

        when {
            totalRam > 0x4000 -> setCpuMemoryMapping(0x6000, 0x7FFF, (extraReg shr 2) and 0x03, memType, access)
            totalRam > 0x2000 -> {
                if (saveRamSizeBytes == 0x2000 && workRamSizeBytes == 0x2000) {
                    setCpuMemoryMapping(0x6000, 0x7FFF, 0, if (((extraReg shr 3) and 0x01) != 0) PrgMemoryType.WorkRam else PrgMemoryType.SaveRam, access)
                } else {
                    setCpuMemoryMapping(0x6000, 0x7FFF, (extraReg shr 2) and 0x01, memType, access)
                }
            }
            totalRam == 0 -> removeCpuMemoryMapping(0x6000, 0x7FFF)
            else -> setCpuMemoryMapping(0x6000, 0x7FFF, 0, memType, access)
        }

        if (romInfoState.subMapperID == 5) {
            selectPrgPage2x(0, 0)
        } else if (prgMode) {
            if (slotSelect) {
                selectPrgPage(0, prgReg or prgBankSelect)
                selectPrgPage(1, 0x0F or prgBankSelect)
            } else {
                selectPrgPage(0, prgBankSelect)
                selectPrgPage(1, prgReg or prgBankSelect)
            }
        } else {
            selectPrgPage2x(0, (prgReg and 0xFE) or prgBankSelect)
        }

        if (chrMode) {
            selectChrPage(0, chrReg0)
            selectChrPage(1, chrReg1)
        } else {
            selectChrPage(0, chrReg0 and 0x1E)
            selectChrPage(1, (chrReg0 and 0x1E) + 1)
        }
    }

    override fun getMapperStateEntries(): List<MapperStateEntry> = listOf(
        MapperStateEntry("\$8000.2-3", "PRG Mode", rawValue = (((if (prgMode) 1 else 0) shl 1) or if (slotSelect) 1 else 0).toLong(), type = MapperStateValueType.Number8),
        MapperStateEntry("\$8000.4", "CHR Mode", rawValue = (if (chrMode) 1 else 0).toLong(), type = MapperStateValueType.Number8),
        MapperStateEntry("\$A000.0-4", "CHR Bank (\$0000)", rawValue = chrReg0.toLong(), type = MapperStateValueType.Number8),
        MapperStateEntry("\$C000.0-4", "CHR Bank (\$1000)", rawValue = chrReg1.toLong(), type = MapperStateValueType.Number8),
        MapperStateEntry("\$E000.0-3", "PRG Bank", rawValue = prgReg.toLong(), type = MapperStateValueType.Number8),
        MapperStateEntry("\$E000.4", "WRAM Disabled", rawValue = if (wramDisable) 1 else 0, type = MapperStateValueType.Bool),
    )
}
