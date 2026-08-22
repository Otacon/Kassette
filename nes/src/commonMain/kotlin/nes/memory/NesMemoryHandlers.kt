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

package nes.memory

import nes.cpu.NesCpuBusType

enum class MemoryOperation {
    Read,
    Write,
    Any,
}

class MemoryRanges {
    private val ramReadAddresses = IntArray(0x10000)
    private val ramWriteAddresses = IntArray(0x10000)
    private var ramReadAddressCount = 0
    private var ramWriteAddressCount = 0
    private var allowOverride = false

    fun getAllowOverride(): Boolean = allowOverride

    fun forEachRAMReadAddress(action: (Int) -> Unit) {
        var i = 0
        while (i < ramReadAddressCount) action(ramReadAddresses[i++])
    }

    fun forEachRAMWriteAddress(action: (Int) -> Unit) {
        var i = 0
        while (i < ramWriteAddressCount) action(ramWriteAddresses[i++])
    }

    fun setAllowOverride() {
        allowOverride = true
    }

    fun addHandler(operation: MemoryOperation, start: Int, end: Int = 0) {
        val last = if (end == 0) start else end
        if (operation == MemoryOperation.Read || operation == MemoryOperation.Any) {
            var i = start
            while (i <= last) {
                ramReadAddresses[ramReadAddressCount++] = i and 0xFFFF
                i++
            }
        }

        if (operation == MemoryOperation.Write || operation == MemoryOperation.Any) {
            var i = start
            while (i <= last) {
                ramWriteAddresses[ramWriteAddressCount++] = i and 0xFFFF
                i++
            }
        }
    }
}

interface INesMemoryHandler {
    fun getMemoryRanges(ranges: MemoryRanges)
    fun readRam(addr: Int): Int
    fun writeRam(addr: Int, value: Int)
    fun peekRam(addr: Int): Int = 0
}

class OpenBusHandler : INesMemoryHandler {
    private var externalOpenBus = 0
    private var internalOpenBus = 0

    override fun readRam(addr: Int): Int = externalOpenBus
    override fun peekRam(addr: Int): Int = addr shr 8

    fun getOpenBus(): Int = externalOpenBus
    fun getInternalOpenBus(): Int = internalOpenBus

    fun setOpenBus(value: Int, busType: NesCpuBusType = NesCpuBusType.Both, forceInternal: Boolean = false) {
        val v = value and 0xFF
        if (forceInternal) {
            internalOpenBus = v
            return
        }

        when (busType) {
            NesCpuBusType.Internal -> internalOpenBus = v
            NesCpuBusType.External -> externalOpenBus = v
            NesCpuBusType.Both -> {
                internalOpenBus = v
                externalOpenBus = v
            }
        }
    }

    override fun getMemoryRanges(ranges: MemoryRanges) {}
    override fun writeRam(addr: Int, value: Int) {}
}

class InternalRamHandler(private val internalRam: ByteArray, private val mask: Int) : INesMemoryHandler {
    override fun getMemoryRanges(ranges: MemoryRanges) {
        ranges.setAllowOverride()
        ranges.addHandler(MemoryOperation.Any, 0, 0x1FFF)
    }

    override fun readRam(addr: Int): Int = internalRam[addr and mask].toInt() and 0xFF
    override fun peekRam(addr: Int): Int = readRam(addr)
    override fun writeRam(addr: Int, value: Int) {
        internalRam[addr and mask] = (value and 0xFF).toByte()
    }
}

interface NesMemoryMapper : INesMemoryHandler {
    fun getInternalRamSize(): Int
    fun reset(softReset: Boolean)
    fun debugWriteRam(addr: Int, value: Int) {
        writeRam(addr, value)
    }
}

interface NesMemoryManagerHost {
    fun initializeRam(ram: ByteArray) {
        ram.fill(0)
    }

    fun hasCpuCheats(): Boolean = false
    fun applyCpuCheat(addr: Int, value: Int): Int = value
    fun processMemoryRead(addr: Int, value: Int, operationType: nes.cpu.MemoryOperationType) {}
    fun processMemoryWrite(addr: Int, value: Int, operationType: nes.cpu.MemoryOperationType): Boolean = true
}
