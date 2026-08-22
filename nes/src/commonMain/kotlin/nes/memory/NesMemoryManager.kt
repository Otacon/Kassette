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

import kotlinx.serialization.Serializable
import nes.cpu.MemoryOperationType
import nes.cpu.NesCpuBusType
import nes.cpu.NesCpuMemoryManager

class NesMemoryManager(
    private val host: NesMemoryManagerHost,
    private val mapper: NesMemoryMapper,
) : NesCpuMemoryManager {
    companion object {
        const val CpuMemorySize = 0x10000
        const val NesInternalRamSize = 0x800
        const val FamicomBoxInternalRamSize = 0x2000
    }

    private val internalRamSize: Int = mapper.getInternalRamSize()
    private val internalRam = ByteArray(internalRamSize)
    private val openBusHandler = OpenBusHandler()
    private val internalRamHandler: InternalRamHandler
    private val ramReadHandlers: Array<INesMemoryHandler>
    private val ramWriteHandlers: Array<INesMemoryHandler>

    init {
        internalRamHandler = when (internalRamSize) {
            NesInternalRamSize -> InternalRamHandler(internalRam, 0x7FF)
            FamicomBoxInternalRamSize -> InternalRamHandler(internalRam, 0x1FFF)
            else -> error("unsupported memory size")
        }

        ramReadHandlers = Array(CpuMemorySize) { openBusHandler }
        ramWriteHandlers = Array(CpuMemorySize) { openBusHandler }

        registerIODevice(internalRamHandler)
    }

    fun reset(softReset: Boolean) {
        if (!softReset) {
            host.initializeRam(internalRam)
        }
        mapper.reset(softReset)
    }

    private fun initializeMemoryHandler(
        memoryHandlers: Array<INesMemoryHandler>,
        handler: INesMemoryHandler,
        address: Int,
        allowOverride: Boolean,
    ) {
            val addr = address and 0xFFFF
            if (!allowOverride && memoryHandlers[addr] !== openBusHandler && memoryHandlers[addr] !== handler) {
                error("Can't override existing mapping")
            }
            memoryHandlers[addr] = handler
    }

    fun registerIODevice(handler: INesMemoryHandler) {
        val ranges = MemoryRanges()
        handler.getMemoryRanges(ranges)
        ranges.forEachRAMReadAddress { initializeMemoryHandler(ramReadHandlers, handler, it, ranges.getAllowOverride()) }
        ranges.forEachRAMWriteAddress { initializeMemoryHandler(ramWriteHandlers, handler, it, ranges.getAllowOverride()) }
    }

    fun registerWriteHandler(handler: INesMemoryHandler, start: Int, end: Int) {
        var i = start
        while (i <= end) {
            ramWriteHandlers[i and 0xFFFF] = handler
            i++
        }
    }

    fun registerReadHandler(handler: INesMemoryHandler, start: Int, end: Int) {
        var i = start
        while (i <= end) {
            ramReadHandlers[i and 0xFFFF] = handler
            i++
        }
    }

    fun unregisterIODevice(handler: INesMemoryHandler) {
        val ranges = MemoryRanges()
        handler.getMemoryRanges(ranges)
        ranges.forEachRAMReadAddress { address ->
            ramReadHandlers[address and 0xFFFF] = openBusHandler
        }
        ranges.forEachRAMWriteAddress { address ->
            ramWriteHandlers[address and 0xFFFF] = openBusHandler
        }
    }

    fun getInternalRam(): ByteArray = internalRam

    fun captureSnapshot(): NesMemoryManagerSnapshot = NesMemoryManagerSnapshot(
        internalRam = internalRam.copyOf(),
        openBus = openBusHandler.getOpenBus(),
    )

    fun restoreSnapshot(snapshot: NesMemoryManagerSnapshot) {
        snapshot.internalRam.copyInto(internalRam, endIndex = minOf(snapshot.internalRam.size, internalRam.size))
        openBusHandler.setOpenBus(snapshot.openBus)
    }

    override fun debugRead(addr: Int): Int {
        val address = addr and 0xFFFF
        var value = ramReadHandlers[address].peekRam(address) and 0xFF
        if (host.hasCpuCheats()) {
            value = host.applyCpuCheat(address, value) and 0xFF
        }
        return value
    }

    fun debugReadWord(addr: Int): Int = debugRead(addr) or (debugRead(addr + 1) shl 8)

    fun debugWrite(addr: Int, value: Int, disableSideEffects: Boolean = true) {
        val address = addr and 0xFFFF
        val v = value and 0xFF
        if (address <= 0x1FFF) {
            ramWriteHandlers[address].writeRam(address, v)
        } else {
            val handler = ramReadHandlers[address]
            if (disableSideEffects) {
                if (handler === mapper) {
                    mapper.debugWriteRam(address, v)
                }
            } else {
                handler.writeRam(address, v)
            }
        }
    }

    override fun read(addr: Int, operationType: MemoryOperationType, busType: NesCpuBusType): Int {
        val address = addr and 0xFFFF
        var value = if (address >= 0x6000) {
            mapper.readRam(address)
        } else {
            ramReadHandlers[address].readRam(address)
        } and 0xFF

        if (host.hasCpuCheats()) {
            value = host.applyCpuCheat(address, value) and 0xFF
        }
        host.processMemoryRead(address, value, operationType)
        openBusHandler.setOpenBus(value, busType, forceInternal = address == 0x4015)
        return value
    }

    override fun write(addr: Int, value: Int, operationType: MemoryOperationType) {
        val address = addr and 0xFFFF
        val v = value and 0xFF
        if (host.processMemoryWrite(address, v, operationType)) {
            ramWriteHandlers[address].writeRam(address, v)
            openBusHandler.setOpenBus(v)
        }
    }

    override fun getOpenBus(): Int = getOpenBus(0xFF)
    fun getOpenBus(mask: Int): Int = openBusHandler.getOpenBus() and mask
    fun getInternalOpenBus(mask: Int = 0xFF): Int = openBusHandler.getInternalOpenBus() and mask

    override fun setOpenBus(value: Int, busType: NesCpuBusType) {
        openBusHandler.setOpenBus(value, busType)
    }
}

@Serializable
data class NesMemoryManagerSnapshot(
    val internalRam: ByteArray = ByteArray(0),
    val openBus: Int = 0,
) {
    override fun equals(other: Any?): Boolean = other is NesMemoryManagerSnapshot &&
        internalRam.contentEquals(other.internalRam) &&
        openBus == other.openBus

    override fun hashCode(): Int = 31 * internalRam.contentHashCode() + openBus
}
