/*
 * This file is part of Kassette.
 *
 * This Kotlin implementation is ported and adapted from MesenCE
 * (https://github.com/nesdev-org/MesenCE). MesenCE is licensed under
 * the GNU General Public License version 3.
 *
 * This modified Kotlin port is distributed under the GNU General Public
 * License version 3. See the repository LICENSE file for details.
 */

package nes.mapper

import nes.console.NesConsole
import nes.console.NesConsoleExpansionAudio
import nes.console.NesConsoleMapper
import nes.cpu.ConsoleRegion
import nes.cpu.MemoryOperationType
import nes.memory.MemoryOperation
import nes.memory.MemoryRanges

abstract class BaseMapper(private val romData: RomData? = null) : NesConsoleMapper {
    companion object {
        const val NametableSize = 0x400
    }

    private data class Page(var memory: ByteArray? = null, var offset: Int = 0)

    override val epsm: NesConsoleExpansionAudio? get() = epsmDevice
    private var epsmDevice: NesConsoleExpansionAudio? = null

    private var mirroringType: MirroringType = MirroringType.Horizontal
    private var nametableRam: ByteArray = ByteArray(0)
    private var nametableCount: Int = 2
    private var ntRamSize: Int = 0
    private var internalRamMask: Int = 0x7FF
    private var hasBusConflicts: Boolean = false
    private var hasDefaultWorkRam: Boolean = false
    private var hasCustomReadRam: Boolean = false
    private var hasCustomReadVram: Boolean = false
    private var hasCpuClockHookValue: Boolean = false
    private var hasVramAddressHookValue: Boolean = false
    private var allowRegisterRead: Boolean = false
    private val isReadRegisterAddr = BooleanArray(0x10000)
    private val isWriteRegisterAddr = BooleanArray(0x10000)

    private var prgRomPageSize: Int = 0
    private var saveRamPageSize: Int = 0
    private var workRamPageSize: Int = 0
    private var chrRomPageSize: Int = 0
    private var chrRamPageSize: Int = 0

    private val prgMemoryAccess = IntArray(0x100)
    private val prgPages = Array(0x100) { Page() }
    private val chrMemoryAccess = IntArray(0x40)
    private val chrPages = Array(0x40) { Page() }
    private val prgMemoryOffset = IntArray(0x100) { -1 }
    private val prgMemoryType = Array(0x100) { PrgMemoryType.PrgRom }
    private val chrMemoryOffset = IntArray(0x40) { -1 }
    private val chrMemoryType = Array(0x40) { ChrMemoryType.Default }

    private var originalPrgRom: ByteArray = ByteArray(0)
    private var originalChrRom: ByteArray = ByteArray(0)
    private var initialized = false
    private var specificMapperInitialized = false

    protected var romInfoState: NesRomInfo = NesRomInfo()
    protected var console: NesConsole? = null
    protected var prgRom: ByteArray = ByteArray(0)
    protected var chrRom: ByteArray = ByteArray(0)
    protected var chrRam: ByteArray = ByteArray(0)
    protected var prgSize: Int = 0
    protected var chrRomSize: Int = 0
    protected var chrRamSizeBytes: Int = 0
    protected var saveChrRamSize: Int = 0
    protected var saveRam: ByteArray = ByteArray(0)
    protected var saveRamSizeBytes: Int = 0
    protected var workRamSizeBytes: Int = 0
    protected var workRam: ByteArray = ByteArray(0)
    protected var hasChrBattery: Boolean = false
    protected var mapperRam: ByteArray = ByteArray(0)
    protected var mapperRamSizeBytes: Int = 0

    protected abstract fun initMapper()
    protected open fun initMapper(romData: RomData) {}
    protected abstract fun getPrgPageSize(): Int
    protected abstract fun getChrPageSize(): Int
    protected open fun getChrRamPageSize(): Int = getChrPageSize()
    protected open fun getSaveRamSize(): Int = 0x2000
    protected open fun getSaveRamPageSize(): Int = 0x2000
    protected open fun forceChrBattery(): Boolean = false
    protected open fun forceSaveRamSize(): Boolean = false
    protected open fun forceWorkRamSize(): Boolean = false
    protected open fun getChrRamSize(): Int = 0x0000
    protected open fun getWorkRamSize(): Int = 0x2000
    protected open fun getWorkRamPageSize(): Int = 0x2000
    protected open fun getMapperRamSize(): Int = 0
    protected open fun registerStartAddress(): Int = 0x8000
    protected open fun registerEndAddress(): Int = 0xFFFF
    protected open fun allowRegisterRead(): Boolean = false
    protected open fun enableCpuClockHook(): Boolean = false
    protected open fun enableCustomVramRead(): Boolean = false
    protected open fun enableVramAddressHook(): Boolean = false
    protected open fun enableCustomRamRead(): Boolean = false
    protected open fun getDipSwitchCount(): Int = 0
    protected open fun getNametableCount(): Int = 0
    protected open fun hasBusConflicts(): Boolean = false
    protected open fun writeRegister(addr: Int, value: Int) {}
    protected open fun readRegister(addr: Int): Int = 0
    protected open fun getMapperStateEntries(): List<MapperStateEntry> = emptyList()
    protected open fun captureExtraSnapshot(): MapperExtraSnapshot = MapperExtraSnapshot()
    protected open fun restoreExtraSnapshot(snapshot: MapperExtraSnapshot) {}

    override fun initConsole(console: NesConsole) {
        this.console = console
        val data = romData
        if (!initialized && data != null) {
            initialize(console, data)
            initSpecificMapper(data)
        }
    }

    fun initialize(console: NesConsole, romData: RomData) {
        this.console = console
        romInfoState = romData.info
        internalRamMask = getInternalRamSize() - 1

        saveRamSizeBytes = when {
            romData.saveRamSize == -1 -> if (hasBattery()) getSaveRamSize() else 0
            forceSaveRamSize() -> getSaveRamSize()
            else -> romData.saveRamSize
        }
        if (romData.saveRamSize == -1 && saveRamSizeBytes > 0) hasDefaultWorkRam = true

        workRamSizeBytes = when {
            romData.workRamSize == -1 -> if (hasBattery()) 0 else getWorkRamSize()
            forceWorkRamSize() -> getWorkRamSize()
            else -> romData.workRamSize
        }
        if (romData.workRamSize == -1 && workRamSizeBytes > 0) hasDefaultWorkRam = true

        allowRegisterRead = allowRegisterRead()
        hasCpuClockHookValue = enableCpuClockHook()
        hasCustomReadVram = enableCustomVramRead()
        hasVramAddressHookValue = enableVramAddressHook()
        hasCustomReadRam = enableCustomRamRead()

        isReadRegisterAddr.fill(false)
        isWriteRegisterAddr.fill(false)
        addRegisterRange(registerStartAddress(), registerEndAddress(), MemoryOperation.Any)

        prgRom = romData.prgRom.copyOf()
        chrRom = romData.chrRom.copyOf()
        prgSize = prgRom.size
        chrRomSize = chrRom.size
        originalPrgRom = romData.prgRom.copyOf()
        originalChrRom = romData.chrRom.copyOf()
        hasChrBattery = romData.saveChrRamSize > 0 || forceChrBattery()
        hasBusConflicts = when (romData.info.busConflicts) {
            BusConflictType.Default -> hasBusConflicts()
            BusConflictType.Yes -> true
            BusConflictType.No -> false
        }

        saveRam = ByteArray(saveRamSizeBytes)
        workRam = ByteArray(workRamSizeBytes)
        mapperRamSizeBytes = getMapperRamSize()
        mapperRam = ByteArray(mapperRamSizeBytes)
        console.initializeRam(saveRam)
        console.initializeRam(workRam)
        console.initializeRam(mapperRam)

        nametableCount = getNametableCount()
        if (nametableCount == 0) {
            nametableCount = if (romData.info.mirroring == MirroringType.FourScreens) 4 else 2
        }
        ntRamSize = nametableCount * NametableSize
        nametableRam = ByteArray(ntRamSize)
        console.initializeRam(nametableRam)

        for (i in 0 until 0x100) {
            prgPages[i].memory = null
            prgPages[i].offset = 0
            prgMemoryOffset[i] = -1
            prgMemoryType[i] = PrgMemoryType.PrgRom
            prgMemoryAccess[i] = MemoryAccessType.NoAccess
        }
        for (i in 0 until 0x40) {
            chrPages[i].memory = null
            chrPages[i].offset = 0
            chrMemoryOffset[i] = -1
            chrMemoryType[i] = ChrMemoryType.Default
            chrMemoryAccess[i] = MemoryAccessType.NoAccess
        }

        var totalChrRam = -1
        if (romData.chrRamSize >= 0 || romData.saveChrRamSize >= 0) {
            totalChrRam = (if (romData.chrRamSize > 0) romData.chrRamSize else 0) +
                (if (romData.saveChrRamSize > 0) romData.saveChrRamSize else 0)
        }
        if (chrRomSize == 0) {
            initializeChrRam(totalChrRam)
        } else if (totalChrRam >= 0) {
            initializeChrRam(totalChrRam)
        } else if (getChrRamSize() > 0) {
            initializeChrRam()
        }
        saveChrRamSize = if (romData.saveChrRamSize > 0) romData.saveChrRamSize else 0

        if (romData.info.hasTrainer) {
            if (workRamSizeBytes >= 0x2000 && romData.trainerData.size >= 512) {
                romData.trainerData.copyInto(workRam, destinationOffset = 0x1000, endIndex = 512)
            } else if (saveRamSizeBytes >= 0x2000 && romData.trainerData.size >= 512) {
                romData.trainerData.copyInto(saveRam, destinationOffset = 0x1000, endIndex = 512)
            }
        }

        updatePageSizes()
        if (chrRomSize == 0 && chrRamSizeBytes > 0) {
            setPpuMemoryMapping(0x0000, 0x1FFF, 0, ChrMemoryType.ChrRam)
        }
        setupDefaultWorkRam()
        setMirroringType(romData.info.mirroring)
        romInfoState = romInfoState.copy(hasChrRam = hasChrRam())
        initialized = true
    }

    fun initSpecificMapper(romData: RomData) {
        if (specificMapperInitialized) return
        initMapper()
        initMapper(romData)
        specificMapperInitialized = true
    }

    override fun reset(softReset: Boolean) {}
    override fun onAfterResetPowerOn() {}
    fun getGameSystem(): GameSystem = romInfoState.system
    fun getPpuModel(): PpuModel = romInfoState.vsPpuModel
    override fun hasCpuClockHook(): Boolean = hasCpuClockHookValue
    override fun processCpuClock() { baseProcessCpuClock() }
    fun hasVramAddressHook(): Boolean = hasVramAddressHookValue
    open fun notifyVramAddressChange(addr: Int) {}
    override fun getInternalRamSize(): Int = 0x800
    override fun endFrame() {}
    fun getRomInfo(): NesRomInfo = romInfoState.copy(busConflicts = if (hasBusConflicts) BusConflictType.Yes else BusConflictType.No)
    fun getMapperDipSwitchCount(): Int = getDipSwitchCount()
    fun hasDefaultWorkRam(): Boolean = hasDefaultWorkRam
    override fun setRegion(region: ConsoleRegion) {}
    fun saveBattery() {}

    override fun getMemoryRanges(ranges: MemoryRanges) {
        if (romInfoState.system == GameSystem.VsSystem) {
            ranges.addHandler(MemoryOperation.Read, 0x6000, 0xFFFF)
            ranges.addHandler(MemoryOperation.Write, 0x6000, 0xFFFF)
        } else {
            ranges.addHandler(MemoryOperation.Read, 0x4020, 0xFFFF)
            ranges.addHandler(MemoryOperation.Write, 0x4020, 0xFFFF)
        }
    }

    fun read(addr: Int): Int = if (hasCustomReadRam) readRam(addr) else internalRead(addr)
    fun internalRead(addr: Int): Int {
        val address = addr and 0xFFFF
        val slot = address shr 8
        return when {
            allowRegisterRead && isReadRegisterAddr[address] -> readRegister(address) and 0xFF
            (prgMemoryAccess[slot] and MemoryAccessType.Read) != 0 -> readPage(prgPages[slot], address)
            else -> console?.getOpenBus() ?: 0
        }
    }

    override fun readRam(addr: Int): Int = internalRead(addr)
    override fun peekRam(addr: Int): Int = debugReadRam(addr)
    fun debugReadRam(addr: Int): Int {
        val address = addr and 0xFFFF
        val slot = address shr 8
        return if ((prgMemoryAccess[slot] and MemoryAccessType.Read) != 0) {
            readPage(prgPages[slot], address)
        } else {
            address shr 8
        }
    }

    override fun writeRam(addr: Int, value: Int) {
        val address = addr and 0xFFFF
        var v = value and 0xFF
        if (isWriteRegisterAddr[address]) {
            if (hasBusConflicts) {
                val prgValue = readPage(prgPages[address shr 8], address)
                v = v and prgValue
            }
            writeRegister(address, v)
        } else {
            writePrgRam(address, v)
        }
    }

    override fun debugWriteRam(addr: Int, value: Int) {
        val address = addr and 0xFFFF
        val slot = address shr 8
        if (!isWriteRegisterAddr[address] && (prgMemoryAccess[slot] and MemoryAccessType.Write) != 0) {
            writePage(prgPages[slot], address, value)
        }
    }

    fun writePrgRam(addr: Int, value: Int) {
        val address = addr and 0xFFFF
        val slot = address shr 8
        if ((prgMemoryAccess[slot] and MemoryAccessType.Write) != 0) {
            writePage(prgPages[slot], address, value)
        }
    }

    open fun mapperReadVram(addr: Int, operationType: MemoryOperationType): Int = internalReadVram(addr)
    open fun mapperWriteVram(addr: Int, value: Int) { internalWriteVram(addr, value) }
    fun readVram(addr: Int, type: MemoryOperationType = MemoryOperationType.Read): Int =
        if (!hasCustomReadVram) internalReadVram(addr) else mapperReadVram(addr, type)

    fun debugReadVram(addr: Int, disableSideEffects: Boolean = true): Int {
        val address = addr and 0x3FFF
        if (!disableSideEffects) notifyVramAddressChange(address)
        return internalReadVram(address)
    }

    fun debugWriteVram(addr: Int, value: Int, disableSideEffects: Boolean = true) {
        val address = addr and 0x3FFF
        val slot = address shr 8
        if (disableSideEffects) {
            if (chrPages[slot].memory != null) writePage(chrPages[slot], address, value)
        } else {
            notifyVramAddressChange(address)
            if ((chrMemoryAccess[slot] and MemoryAccessType.Write) != 0) writePage(chrPages[slot], address, value)
        }
    }

    fun writeVram(addr: Int, value: Int) { mapperWriteVram(addr and 0x3FFF, value and 0xFF) }
    protected fun internalReadVram(addr: Int): Int {
        val address = addr and 0x3FFF
        val slot = address shr 8
        return if ((chrMemoryAccess[slot] and MemoryAccessType.Read) != 0) readPage(chrPages[slot], address) else address and 0xFF
    }

    protected fun internalWriteVram(addr: Int, value: Int) {
        val address = addr and 0x3FFF
        val slot = address shr 8
        if ((chrMemoryAccess[slot] and MemoryAccessType.Write) != 0) writePage(chrPages[slot], address, value)
    }

    protected fun internalReadRam(addr: Int): Int {
        val address = addr and 0xFFFF
        return readPage(prgPages[address shr 8], address)
    }

    protected fun selectPrgPage4x(slot: Int, page: Int, memoryType: PrgMemoryType = PrgMemoryType.PrgRom) {
        selectPrgPage2x(slot * 2, page, memoryType)
        selectPrgPage2x(slot * 2 + 1, page + 2, memoryType)
    }

    protected fun selectPrgPage2x(slot: Int, page: Int, memoryType: PrgMemoryType = PrgMemoryType.PrgRom) {
        selectPrgPage(slot * 2, page, memoryType)
        selectPrgPage(slot * 2 + 1, page + 1, memoryType)
    }

    protected open fun selectPrgPage(slotValue: Int, page: Int, memoryType: PrgMemoryType = PrgMemoryType.PrgRom) {
        var slot = slotValue
        if (prgSize < 0x8000 && getPrgPageSize() > prgSize && prgSize > 0) {
            while (slot < 0x8000 / prgSize) {
                val startAddr = 0x8000 + slot * prgSize
                val endAddr = startAddr + prgSize - 1
                setCpuMemoryMapping(startAddr, endAddr, 0, memoryType)
                slot++
            }
        } else {
            val startAddr = 0x8000 + slot * prgRomPageSize
            val endAddr = startAddr + prgRomPageSize - 1
            setCpuMemoryMapping(startAddr, endAddr, page, memoryType)
        }
    }

    protected fun setCpuMemoryMapping(startAddr: Int, endAddr: Int, pageNumber: Int, type: PrgMemoryType, accessType: Int = MemoryAccessType.Unspecified) {
        if (!validateAddressRange(startAddr, endAddr) || startAddr > 0xFF00 || endAddr <= startAddr) return
        var page = pageNumber
        val defaultAccessType = MemoryAccessType.Read or if (type == PrgMemoryType.SaveRam || type == PrgMemoryType.WorkRam) MemoryAccessType.Write else 0
        val pageSize: Int
        val pageCount: Int
        when (type) {
            PrgMemoryType.PrgRom -> { pageSize = prgRomPageSize; pageCount = getPrgPageCount() }
            PrgMemoryType.SaveRam -> { pageSize = saveRamPageSize; if (pageSize == 0) return; pageCount = saveRamSizeBytes / pageSize }
            PrgMemoryType.WorkRam -> { pageSize = workRamPageSize; if (pageSize == 0) return; pageCount = workRamSizeBytes / pageSize }
            PrgMemoryType.MapperRam -> { pageSize = prgRomPageSize; pageCount = if (pageSize == 0) 0 else mapperRamSizeBytes / pageSize }
        }
        if (pageCount == 0) return
        fun wrap(value: Int): Int = if (value < 0) pageCount + value else value % pageCount
        page = wrap(page)
        val actualAccess = if (accessType != MemoryAccessType.Unspecified) accessType else defaultAccessType
        if ((endAddr - startAddr) >= pageSize) {
            var addr = startAddr
            while (addr <= endAddr - pageSize + 1) {
                setCpuMemoryMapping(addr, addr + pageSize - 1, type, page * pageSize, actualAccess)
                addr += pageSize
                page = wrap(page + 1)
            }
        } else {
            setCpuMemoryMapping(startAddr, endAddr, type, page * pageSize, actualAccess)
        }
    }

    protected fun setCpuMemoryMapping(startAddr: Int, endAddr: Int, type: PrgMemoryType, sourceOffset: Int, accessType: Int) {
        val source = when (type) {
            PrgMemoryType.PrgRom -> prgRom
            PrgMemoryType.SaveRam -> saveRam
            PrgMemoryType.WorkRam -> workRam
            PrgMemoryType.MapperRam -> mapperRam
        }
        val sourceSize = source.size
        val firstSlot = startAddr shr 8
        val slotCount = (endAddr - startAddr + 1) shr 8
        var offset = sourceOffset
        for (i in 0 until slotCount) {
            if (sourceSize == 0 || accessType == MemoryAccessType.NoAccess) {
                prgPages[firstSlot + i].memory = null
                prgMemoryAccess[firstSlot + i] = MemoryAccessType.NoAccess
            } else {
                while (offset >= sourceSize) offset -= sourceSize
                prgPages[firstSlot + i].memory = source
                prgPages[firstSlot + i].offset = offset + i * 0x100
                prgMemoryOffset[firstSlot + i] = offset + i * 0x100
                prgMemoryType[firstSlot + i] = type
                prgMemoryAccess[firstSlot + i] = accessType
            }
        }
        setCpuMemoryMapping(startAddr, endAddr, source, sourceOffset, sourceSize, accessType)
    }

    protected fun setCpuMemoryMapping(startAddr: Int, endAddr: Int, source: ByteArray?, sourceOffset: Int, sourceSize: Int, accessType: Int = MemoryAccessType.Unspecified) {
        if (!validateAddressRange(startAddr, endAddr)) return
        var offset = sourceOffset
        var i = startAddr shr 8
        val last = endAddr shr 8
        while (i <= last) {
            if (accessType != MemoryAccessType.NoAccess && source != null && sourceSize > 0 && offset <= sourceSize - 0x100) {
                prgPages[i].memory = source
                prgPages[i].offset = offset
                prgMemoryAccess[i] = if (accessType != MemoryAccessType.Unspecified) accessType else MemoryAccessType.ReadWrite
            } else {
                prgPages[i].memory = null
                prgPages[i].offset = 0
                prgMemoryAccess[i] = MemoryAccessType.NoAccess
            }
            offset += 0x100
            i++
        }
    }

    protected fun removeCpuMemoryMapping(startAddr: Int, endAddr: Int) {
        val firstSlot = startAddr shr 8
        val slotCount = (endAddr - startAddr + 1) shr 8
        for (i in 0 until slotCount) {
            prgMemoryOffset[firstSlot + i] = -1
            prgMemoryType[firstSlot + i] = PrgMemoryType.PrgRom
            prgMemoryAccess[firstSlot + i] = MemoryAccessType.NoAccess
        }
        setCpuMemoryMapping(startAddr, endAddr, null, 0, 0, MemoryAccessType.NoAccess)
    }

    protected fun selectChrPage8x(slot: Int, page: Int, memoryType: ChrMemoryType = ChrMemoryType.Default) { selectChrPage4x(slot, page, memoryType); selectChrPage4x(slot * 2 + 1, page + 4, memoryType) }
    protected fun selectChrPage4x(slot: Int, page: Int, memoryType: ChrMemoryType = ChrMemoryType.Default) { selectChrPage2x(slot * 2, page, memoryType); selectChrPage2x(slot * 2 + 1, page + 2, memoryType) }
    protected fun selectChrPage2x(slot: Int, page: Int, memoryType: ChrMemoryType = ChrMemoryType.Default) { selectChrPage(slot * 2, page, memoryType); selectChrPage(slot * 2 + 1, page + 1, memoryType) }
    protected open fun selectChrPage(slot: Int, page: Int, memoryTypeValue: ChrMemoryType = ChrMemoryType.Default) {
        var memoryType = memoryTypeValue
        val pageSize = if (memoryType == ChrMemoryType.NametableRam) NametableSize else {
            if (memoryType == ChrMemoryType.Default) memoryType = if (chrRomSize > 0) ChrMemoryType.ChrRom else ChrMemoryType.ChrRam
            if (memoryType == ChrMemoryType.ChrRam) chrRamPageSize else chrRomPageSize
        }
        val startAddr = slot * pageSize
        val endAddr = startAddr + pageSize - 1
        setPpuMemoryMapping(startAddr, endAddr, page, memoryType)
    }

    protected fun setPpuMemoryMapping(startAddr: Int, endAddr: Int, pageNumber: Int, typeValue: ChrMemoryType = ChrMemoryType.Default, accessType: Int = MemoryAccessType.Unspecified) {
        if (!validateAddressRange(startAddr, endAddr) || startAddr > 0x3F00 || endAddr > 0x3FFF || endAddr <= startAddr) return
        var type = typeValue
        var page = pageNumber
        var defaultAccessType = MemoryAccessType.Read
        if (type == ChrMemoryType.Default) type = if (chrRomSize > 0) ChrMemoryType.ChrRom else ChrMemoryType.ChrRam
        val pageSize: Int
        val pageCount: Int
        when (type) {
            ChrMemoryType.Default, ChrMemoryType.ChrRom -> { pageSize = chrRomPageSize; if (pageSize == 0) return; pageCount = getChrRomPageCount() }
            ChrMemoryType.ChrRam -> { pageSize = chrRamPageSize; if (pageSize == 0) return; pageCount = chrRamSizeBytes / pageSize; defaultAccessType = defaultAccessType or MemoryAccessType.Write }
            ChrMemoryType.NametableRam -> { pageSize = NametableSize; pageCount = nametableCount; defaultAccessType = defaultAccessType or MemoryAccessType.Write }
            ChrMemoryType.MapperRam -> { pageSize = chrRomPageSize; if (pageSize == 0) return; pageCount = mapperRamSizeBytes / pageSize; defaultAccessType = defaultAccessType or MemoryAccessType.Write }
        }
        if (pageCount == 0) return
        page %= pageCount
        val actualAccess = if (accessType == MemoryAccessType.Unspecified) defaultAccessType else accessType
        if ((endAddr - startAddr) >= pageSize) {
            var addr = startAddr
            while (addr <= endAddr - pageSize + 1) {
                setPpuMemoryMapping(addr, addr + pageSize - 1, type, page * pageSize, actualAccess)
                addr += pageSize
                page = (page + 1) % pageCount
            }
        } else {
            setPpuMemoryMapping(startAddr, endAddr, type, page * pageSize, actualAccess)
        }
    }

    protected fun setPpuMemoryMapping(startAddr: Int, endAddr: Int, typeValue: ChrMemoryType, sourceOffset: Int, accessType: Int) {
        var type = typeValue
        if (type == ChrMemoryType.Default) type = if (chrRomSize > 0) ChrMemoryType.ChrRom else ChrMemoryType.ChrRam
        val source = when (type) {
            ChrMemoryType.Default, ChrMemoryType.ChrRom -> chrRom
            ChrMemoryType.ChrRam -> chrRam
            ChrMemoryType.NametableRam -> nametableRam
            ChrMemoryType.MapperRam -> mapperRam
        }
        val sourceSize = source.size
        val firstSlot = startAddr shr 8
        val slotCount = (endAddr - startAddr + 1) shr 8
        var offset = sourceOffset
        for (i in 0 until slotCount) {
            if (sourceSize == 0 || accessType == MemoryAccessType.NoAccess) {
                chrPages[firstSlot + i].memory = null
                chrMemoryAccess[firstSlot + i] = MemoryAccessType.NoAccess
            } else {
                while (offset >= sourceSize) offset -= sourceSize
                chrMemoryOffset[firstSlot + i] = offset + i * 0x100
                chrMemoryType[firstSlot + i] = type
                chrMemoryAccess[firstSlot + i] = accessType
            }
        }
        setPpuMemoryMapping(startAddr, endAddr, source, sourceOffset, sourceSize, accessType)
    }

    protected fun setPpuMemoryMapping(startAddr: Int, endAddr: Int, sourceMemory: ByteArray?, sourceOffset: Int, sourceSize: Int, accessType: Int = MemoryAccessType.Unspecified) {
        if (!validateAddressRange(startAddr, endAddr)) return
        var offset = sourceOffset
        var i = startAddr shr 8
        val last = endAddr shr 8
        while (i <= last) {
            if (accessType != MemoryAccessType.NoAccess && sourceMemory != null && sourceSize > 0 && offset <= sourceSize - 0x100) {
                chrPages[i].memory = sourceMemory
                chrPages[i].offset = offset
                chrMemoryAccess[i] = if (accessType != MemoryAccessType.Unspecified) accessType else MemoryAccessType.ReadWrite
            } else {
                chrPages[i].memory = null
                chrPages[i].offset = 0
                chrMemoryAccess[i] = MemoryAccessType.NoAccess
            }
            offset += 0x100
            i++
        }
    }

    protected fun removePpuMemoryMapping(startAddr: Int, endAddr: Int) {
        val firstSlot = startAddr shr 8
        val slotCount = (endAddr - startAddr + 1) shr 8
        for (i in 0 until slotCount) {
            chrMemoryOffset[firstSlot + i] = -1
            chrMemoryType[firstSlot + i] = ChrMemoryType.Default
            chrMemoryAccess[firstSlot + i] = MemoryAccessType.NoAccess
        }
        setPpuMemoryMapping(startAddr, endAddr, null, 0, 0, MemoryAccessType.NoAccess)
    }

    protected fun getPowerOnByte(defaultValue: Int = 0): Int = defaultValue and 0xFF
    protected fun getDipSwitches(): Int = 0
    protected fun hasBattery(): Boolean = romInfoState.hasBattery
    protected fun loadBattery() {}
    protected fun getPrgPageCount(): Int = if (prgRomPageSize > 0) prgSize / prgRomPageSize else 0
    protected fun getChrRomPageCount(): Int = if (chrRomPageSize > 0) chrRomSize / chrRomPageSize else 0
    protected fun initializeChrRam(chrRamSizeValue: Int = -1) {
        val defaultRamSize = if (getChrRamSize() > 0) getChrRamSize() else 0x2000
        chrRamSizeBytes = if (chrRamSizeValue >= 0) chrRamSizeValue else defaultRamSize
        if (chrRamSizeBytes > 0) {
            chrRam = ByteArray(chrRamSizeBytes)
            console?.initializeRam(chrRam)
        }
    }

    protected fun setupDefaultWorkRam() {
        if (hasBattery() && saveRamSizeBytes > 0) setCpuMemoryMapping(0x6000, 0x7FFF, 0, PrgMemoryType.SaveRam)
        else if (workRamSizeBytes > 0) setCpuMemoryMapping(0x6000, 0x7FFF, 0, PrgMemoryType.WorkRam)
    }

    fun hasChrRam(): Boolean = chrRamSizeBytes > 0
    fun hasChrRom(): Boolean = chrRomSize > 0

    protected fun addRegisterRange(startAddr: Int, endAddr: Int, operation: MemoryOperation = MemoryOperation.Any) {
        var i = startAddr
        while (i <= endAddr) {
            if (operation == MemoryOperation.Read || operation == MemoryOperation.Any) isReadRegisterAddr[i and 0xFFFF] = true
            if (operation == MemoryOperation.Write || operation == MemoryOperation.Any) isWriteRegisterAddr[i and 0xFFFF] = true
            i++
        }
    }

    protected fun removeRegisterRange(startAddr: Int, endAddr: Int, operation: MemoryOperation = MemoryOperation.Any) {
        var i = startAddr
        while (i <= endAddr) {
            if (operation == MemoryOperation.Read || operation == MemoryOperation.Any) isReadRegisterAddr[i and 0xFFFF] = false
            if (operation == MemoryOperation.Write || operation == MemoryOperation.Any) isWriteRegisterAddr[i and 0xFFFF] = false
            i++
        }
    }

    fun restorePrgChrState() {
        for (i in 0 until 0x100) {
            val startAddr = i shl 8
            if (prgMemoryAccess[i] != MemoryAccessType.NoAccess) setCpuMemoryMapping(startAddr, startAddr + 0xFF, prgMemoryType[i], prgMemoryOffset[i], prgMemoryAccess[i])
            else removeCpuMemoryMapping(startAddr, startAddr + 0xFF)
        }
        for (i in 0 until 0x40) {
            val startAddr = i shl 8
            if (chrMemoryAccess[i] != MemoryAccessType.NoAccess) setPpuMemoryMapping(startAddr, startAddr + 0xFF, chrMemoryType[i], chrMemoryOffset[i], chrMemoryAccess[i])
            else removePpuMemoryMapping(startAddr, startAddr + 0xFF)
        }
    }

    protected fun baseProcessCpuClock() {}
    protected fun getNametable(nametableIndex: Int): ByteArray = nametableRam.copyOfRange((nametableIndex.coerceAtMost(nametableCount - 1)) * NametableSize, (nametableIndex.coerceAtMost(nametableCount - 1) + 1) * NametableSize)
    protected fun setNametable(index: Int, nametableIndex: Int) {
        if (nametableIndex >= nametableCount) return
        setPpuMemoryMapping(0x2000 + index * 0x400, 0x2000 + (index + 1) * 0x400 - 1, nametableIndex, ChrMemoryType.NametableRam)
        setPpuMemoryMapping(0x3000 + index * 0x400, 0x3000 + (index + 1) * 0x400 - 1, nametableIndex, ChrMemoryType.NametableRam)
    }

    protected fun setNametables(nametable1Index: Int, nametable2Index: Int, nametable3Index: Int, nametable4Index: Int) {
        setNametable(0, nametable1Index)
        setNametable(1, nametable2Index)
        setNametable(2, nametable3Index)
        setNametable(3, nametable4Index)
    }

    protected fun setMirroringType(type: MirroringType) {
        mirroringType = type
        when (type) {
            MirroringType.Vertical -> setNametables(0, 1, 0, 1)
            MirroringType.Horizontal -> setNametables(0, 0, 1, 1)
            MirroringType.FourScreens -> setNametables(0, 1, 2, 3)
            MirroringType.ScreenAOnly -> setNametables(0, 0, 0, 0)
            MirroringType.ScreenBOnly -> setNametables(1, 1, 1, 1)
        }
    }

    fun getMirroringType(): MirroringType = mirroringType
    fun isWriteRegister(addr: Int): Boolean = isWriteRegisterAddr[addr and 0xFFFF]
    fun isReadRegister(addr: Int): Boolean = allowRegisterRead && isReadRegisterAddr[addr and 0xFFFF]
    fun getState(): CartridgeState = CartridgeState(
        prgRomSize = prgSize,
        chrRomSize = chrRomSize,
        chrRamSize = chrRamSizeBytes,
        prgPageCount = getPrgPageCount(),
        prgPageSize = prgRomPageSize,
        prgMemoryOffset = prgMemoryOffset.copyOf(),
        prgType = prgMemoryType.copyOf(),
        prgMemoryAccess = prgMemoryAccess.copyOf(),
        chrPageCount = getChrRomPageCount(),
        chrPageSize = chrRomPageSize,
        chrRamPageSize = chrRamPageSize,
        chrMemoryOffset = chrMemoryOffset.copyOf(),
        chrType = chrMemoryType.copyOf(),
        chrMemoryAccess = chrMemoryAccess.copyOf(),
        workRamPageSize = getWorkRamPageSize(),
        saveRamPageSize = getSaveRamPageSize(),
        mirroring = mirroringType,
        hasBattery = romInfoState.hasBattery,
        customEntries = getMapperStateEntries(),
    )

    fun captureSnapshot(): MapperSnapshot = MapperSnapshot(
        saveRam = saveRam.copyOf(),
        workRam = workRam.copyOf(),
        chrRam = chrRam.copyOf(),
        mapperRam = mapperRam.copyOf(),
        nametableRam = nametableRam.copyOf(),
        prgMemoryOffset = prgMemoryOffset.copyOf(),
        prgMemoryType = prgMemoryType.copyOf(),
        prgMemoryAccess = prgMemoryAccess.copyOf(),
        chrMemoryOffset = chrMemoryOffset.copyOf(),
        chrMemoryType = chrMemoryType.copyOf(),
        chrMemoryAccess = chrMemoryAccess.copyOf(),
        mirroring = mirroringType,
        extra = captureExtraSnapshot(),
    )

    fun restoreSnapshot(snapshot: MapperSnapshot) {
        snapshot.saveRam.copyInto(saveRam, endIndex = minOf(snapshot.saveRam.size, saveRam.size))
        snapshot.workRam.copyInto(workRam, endIndex = minOf(snapshot.workRam.size, workRam.size))
        snapshot.chrRam.copyInto(chrRam, endIndex = minOf(snapshot.chrRam.size, chrRam.size))
        snapshot.mapperRam.copyInto(mapperRam, endIndex = minOf(snapshot.mapperRam.size, mapperRam.size))
        snapshot.nametableRam.copyInto(nametableRam, endIndex = minOf(snapshot.nametableRam.size, nametableRam.size))
        snapshot.prgMemoryOffset.copyInto(prgMemoryOffset, endIndex = minOf(snapshot.prgMemoryOffset.size, prgMemoryOffset.size))
        snapshot.prgMemoryType.copyInto(prgMemoryType, endIndex = minOf(snapshot.prgMemoryType.size, prgMemoryType.size))
        snapshot.prgMemoryAccess.copyInto(prgMemoryAccess, endIndex = minOf(snapshot.prgMemoryAccess.size, prgMemoryAccess.size))
        snapshot.chrMemoryOffset.copyInto(chrMemoryOffset, endIndex = minOf(snapshot.chrMemoryOffset.size, chrMemoryOffset.size))
        snapshot.chrMemoryType.copyInto(chrMemoryType, endIndex = minOf(snapshot.chrMemoryType.size, chrMemoryType.size))
        snapshot.chrMemoryAccess.copyInto(chrMemoryAccess, endIndex = minOf(snapshot.chrMemoryAccess.size, chrMemoryAccess.size))
        mirroringType = snapshot.mirroring
        restorePrgChrState()
        restoreExtraSnapshot(snapshot.extra)
    }

    private fun validateAddressRange(startAddr: Int, endAddr: Int): Boolean = (startAddr and 0xFF) == 0 && (endAddr and 0xFF) == 0xFF
    private fun updatePageSizes() {
        prgRomPageSize = minOf(getPrgPageSize(), prgSize)
        saveRamPageSize = minOf(getSaveRamPageSize(), saveRamSizeBytes)
        workRamPageSize = minOf(getWorkRamPageSize(), workRamSizeBytes)
        chrRomPageSize = minOf(getChrPageSize(), chrRomSize)
        chrRamPageSize = minOf(getChrRamPageSize(), chrRamSizeBytes)
    }

    private fun readPage(page: Page, addr: Int): Int {
        val memory = page.memory ?: return 0
        val index = page.offset + (addr and 0xFF)
        return if (index >= 0 && index < memory.size) memory[index].toInt() and 0xFF else 0
    }

    private fun writePage(page: Page, addr: Int, value: Int) {
        val memory = page.memory ?: return
        val index = page.offset + (addr and 0xFF)
        if (index >= 0 && index < memory.size) memory[index] = (value and 0xFF).toByte()
    }
}
