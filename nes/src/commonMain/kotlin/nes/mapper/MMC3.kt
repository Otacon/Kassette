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

import nes.cpu.IRQSource

class MMC3(romData: RomData) : BaseMapper(romData) {
    private var currentRegister = 0
    private var wramEnabled = false
    private var wramWriteProtected = false
    private var a12LowClock = 0L
    private var forceMmc3RevAIrqs = false
    private var reg8000 = 0
    private var regA000 = 0
    private var regA001 = 0
    private var irqReloadValue = 0
    private var irqCounter = 0
    private var irqReload = false
    private var irqEnabled = false
    private var prgMode = 0
    private var chrMode = 0
    private val registers = IntArray(8)

    override fun getPrgPageSize(): Int = 0x2000
    override fun getChrPageSize(): Int = 0x0400
    override fun getSaveRamPageSize(): Int = if (romInfoState.subMapperID == 1) 0x200 else 0x2000
    override fun getSaveRamSize(): Int = if (romInfoState.subMapperID == 1) 0x400 else 0x2000
    override fun enableVramAddressHook(): Boolean = true

    override fun initMapper() {
        forceMmc3RevAIrqs = romInfoState.databaseInfo.chip.startsWith("MMC3A")
        resetMmc3()
        setCpuMemoryMapping(0x6000, 0x7FFF, 0, if (hasBattery()) PrgMemoryType.SaveRam else PrgMemoryType.WorkRam)
        updateState()
        updateMirroring()
    }

    override fun writeRegister(addr: Int, value: Int) {
        when (addr and 0xE001) {
            0x8000 -> { reg8000 = value; updateState() }
            0x8001 -> {
                registers[currentRegister] = if (currentRegister <= 1) value and 0xFE else value
                updateState()
            }
            0xA000 -> { regA000 = value; updateMirroring() }
            0xA001 -> { regA001 = value; updateState() }
            0xC000 -> irqReloadValue = value
            0xC001 -> { irqCounter = 0; irqReload = true }
            0xE000 -> { irqEnabled = false; console?.cpu?.clearIrqSource(IRQSource.External) }
            0xE001 -> irqEnabled = true
        }
    }

    override fun notifyVramAddressChange(addr: Int) {
        if (isA12RisingEdge(addr)) {
            val count = irqCounter
            if (irqCounter == 0 || irqReload) {
                irqCounter = irqReloadValue
            } else {
                irqCounter--
            }

            if (forceMmc3RevAIrqs) {
                if ((count > 0 || irqReload) && irqCounter == 0 && irqEnabled) triggerIrq()
            } else if (irqCounter == 0 && irqEnabled) {
                triggerIrq()
            }
            irqReload = false
        }
    }

    override fun captureExtraSnapshot(): MapperExtraSnapshot = MapperExtraSnapshot(
        ints = intArrayOf(
            currentRegister,
            reg8000,
            regA000,
            regA001,
            irqReloadValue,
            irqCounter,
            prgMode,
            chrMode,
            registers[0],
            registers[1],
            registers[2],
            registers[3],
            registers[4],
            registers[5],
            registers[6],
            registers[7],
        ),
        longs = longArrayOf(a12LowClock),
        booleans = booleanArrayOf(wramEnabled, wramWriteProtected, forceMmc3RevAIrqs, irqReload, irqEnabled),
    )

    override fun restoreExtraSnapshot(snapshot: MapperExtraSnapshot) {
        val ints = snapshot.ints
        val longs = snapshot.longs
        val booleans = snapshot.booleans
        if (ints.size >= 16) {
            currentRegister = ints[0]
            reg8000 = ints[1]
            regA000 = ints[2]
            regA001 = ints[3]
            irqReloadValue = ints[4]
            irqCounter = ints[5]
            prgMode = ints[6]
            chrMode = ints[7]
            var i = 0
            while (i < registers.size) {
                registers[i] = ints[8 + i]
                i++
            }
        }
        if (longs.isNotEmpty()) a12LowClock = longs[0]
        if (booleans.size >= 5) {
            wramEnabled = booleans[0]
            wramWriteProtected = booleans[1]
            forceMmc3RevAIrqs = booleans[2]
            irqReload = booleans[3]
            irqEnabled = booleans[4]
        }
    }

    private fun resetMmc3() {
        reg8000 = getPowerOnByte()
        regA000 = getPowerOnByte()
        regA001 = getPowerOnByte()
        chrMode = getPowerOnByte() and 0x01
        prgMode = getPowerOnByte() and 0x01
        currentRegister = getPowerOnByte()
        registers[0] = getPowerOnByte(0)
        registers[1] = getPowerOnByte(2)
        registers[2] = getPowerOnByte(4)
        registers[3] = getPowerOnByte(5)
        registers[4] = getPowerOnByte(6)
        registers[5] = getPowerOnByte(7)
        registers[6] = getPowerOnByte(0)
        registers[7] = getPowerOnByte(1)
        irqCounter = getPowerOnByte()
        irqReloadValue = getPowerOnByte()
        irqReload = (getPowerOnByte() and 0x01) != 0
        irqEnabled = (getPowerOnByte() and 0x01) != 0
        wramEnabled = (getPowerOnByte() and 0x01) != 0
        wramWriteProtected = (getPowerOnByte() and 0x01) != 0
    }

    private fun updateMirroring() {
        if (getMirroringType() != MirroringType.FourScreens) {
            setMirroringType(if ((regA000 and 0x01) == 0x01) MirroringType.Horizontal else MirroringType.Vertical)
        }
    }

    private fun updateChrMapping() {
        if (chrMode == 0) {
            selectChrPage(0, registers[0] and 0xFE)
            selectChrPage(1, registers[0] or 0x01)
            selectChrPage(2, registers[1] and 0xFE)
            selectChrPage(3, registers[1] or 0x01)
            selectChrPage(4, registers[2])
            selectChrPage(5, registers[3])
            selectChrPage(6, registers[4])
            selectChrPage(7, registers[5])
        } else {
            selectChrPage(0, registers[2])
            selectChrPage(1, registers[3])
            selectChrPage(2, registers[4])
            selectChrPage(3, registers[5])
            selectChrPage(4, registers[0] and 0xFE)
            selectChrPage(5, registers[0] or 0x01)
            selectChrPage(6, registers[1] and 0xFE)
            selectChrPage(7, registers[1] or 0x01)
        }
    }

    private fun updatePrgMapping() {
        if (prgMode == 0) {
            selectPrgPage(0, registers[6])
            selectPrgPage(1, registers[7])
            selectPrgPage(2, -2)
            selectPrgPage(3, -1)
        } else {
            selectPrgPage(0, -2)
            selectPrgPage(1, registers[7])
            selectPrgPage(2, registers[6])
            selectPrgPage(3, -1)
        }
    }

    private fun updateState() {
        currentRegister = reg8000 and 0x07
        chrMode = (reg8000 and 0x80) shr 7
        prgMode = (reg8000 and 0x40) shr 6

        if (romInfoState.mapperID == 4 && romInfoState.subMapperID == 1) {
            val enabled = (reg8000 and 0x20) == 0x20
            var firstAccess = (if ((regA001 and 0x10) != 0) MemoryAccessType.Write else 0) or (if ((regA001 and 0x20) != 0) MemoryAccessType.Read else 0)
            var lastAccess = (if ((regA001 and 0x40) != 0) MemoryAccessType.Write else 0) or (if ((regA001 and 0x80) != 0) MemoryAccessType.Read else 0)
            if (!enabled) {
                firstAccess = MemoryAccessType.NoAccess
                lastAccess = MemoryAccessType.NoAccess
            }
            var i = 0
            while (i < 4) {
                setCpuMemoryMapping(0x7000 + i * 0x400, 0x71FF + i * 0x400, 0, PrgMemoryType.SaveRam, firstAccess)
                setCpuMemoryMapping(0x7200 + i * 0x400, 0x73FF + i * 0x400, 1, PrgMemoryType.SaveRam, lastAccess)
                i++
            }
        } else {
            wramEnabled = (regA001 and 0x80) == 0x80
            wramWriteProtected = (regA001 and 0x40) == 0x40
            if (romInfoState.subMapperID == 0) {
                val access = if (wramEnabled) {
                    if (wramEnabled && !wramWriteProtected) MemoryAccessType.ReadWrite else MemoryAccessType.Read
                } else {
                    MemoryAccessType.NoAccess
                }
                if ((hasBattery() && saveRamSizeBytes > 0) || (!hasBattery() && workRamSizeBytes > 0)) {
                    setCpuMemoryMapping(0x6000, 0x7FFF, 0, if (hasBattery()) PrgMemoryType.SaveRam else PrgMemoryType.WorkRam, access)
                } else {
                    removeCpuMemoryMapping(0x6000, 0x7FFF)
                }
            }
        }
        updatePrgMapping()
        updateChrMapping()
    }

    private fun isA12RisingEdge(addr: Int): Boolean {
        return if ((addr and 0x1000) != 0) {
            val clock = console?.getMasterClock() ?: 0L
            val risingEdge = a12LowClock > 0 && clock - a12LowClock >= 3
            a12LowClock = 0
            risingEdge
        } else {
            if (a12LowClock == 0L) a12LowClock = console?.getMasterClock() ?: 0L
            false
        }
    }

    private fun triggerIrq() {
        console?.cpu?.setIrqSource(IRQSource.External)
    }
}
