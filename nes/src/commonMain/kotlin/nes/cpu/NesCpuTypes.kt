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

package nes.cpu

import kotlinx.serialization.Serializable

@Serializable
enum class ConsoleRegion {
    Ntsc,
    Pal,
    Dendy,
}

@Serializable
enum class MemoryOperationType {
    Read,
    Write,
    ExecOpCode,
    ExecOperand,
    DummyRead,
    DummyWrite,
    DmaRead,
    DmaWrite,
}

@Serializable
enum class NesCpuBusType {
    Internal,
    External,
    Both,
}

@Serializable
enum class NesAddrMode {
    None,
    Acc,
    Imp,
    Imm,
    Rel,
    Zero,
    ZeroX,
    ZeroY,
    Abs,
    AbsX,
    AbsXW,
    AbsY,
    AbsYW,
    Ind,
    IndX,
    IndY,
    IndYW,
    Other,
}

object PSFlags {
    const val Carry = 0x01
    const val Zero = 0x02
    const val Interrupt = 0x04
    const val Decimal = 0x08
    const val Break = 0x10
    const val Reserved = 0x20
    const val Overflow = 0x40
    const val Negative = 0x80
}

enum class IRQSource(val mask: Int) {
    External(0x01),
    FrameCounter(0x02),
    Dmc(0x04),
    FdsDisk(0x08),
    Mapper(0x10),
}

@Serializable
data class NesCpuState(
    var PC: Int = 0,
    var SP: Int = 0,
    var PS: Int = 0,
    var A: Int = 0,
    var X: Int = 0,
    var Y: Int = 0,
    var CycleCount: Long = 0,
    var NmiFlag: Boolean = false,
    var IrqFlag: Int = 0,
)

@Serializable
data class NesCpuSnapshot(
    val state: NesCpuState = NesCpuState(),
    val masterClock: Long = 0,
    val ppuOffset: Int = 0,
    val startClockCount: Int = 6,
    val endClockCount: Int = 6,
    val operand: Int = 0,
    val instAddrMode: NesAddrMode = NesAddrMode.None,
    val needHalt: Boolean = false,
    val spriteDmaTransfer: Boolean = false,
    val dmcDmaRunning: Boolean = false,
    val abortDmcDma: Boolean = false,
    val needDummyRead: Boolean = false,
    val spriteDmaOffset: Int = 0,
    val cpuWrite: Boolean = false,
    val irqMask: Int = 0,
    val prevRunIrq: Boolean = false,
    val runIrq: Boolean = false,
    val prevNmiFlag: Boolean = false,
    val prevNeedNmi: Boolean = false,
    val needNmi: Boolean = false,
    val crashed: Boolean = false,
    val isDmcDmaRead: Boolean = false,
)

interface NesCpuMemoryManager {
    fun read(addr: Int, operationType: MemoryOperationType = MemoryOperationType.Read, busType: NesCpuBusType = NesCpuBusType.Both): Int
    fun write(addr: Int, value: Int, operationType: MemoryOperationType = MemoryOperationType.Write)
    fun debugRead(addr: Int): Int = read(addr, MemoryOperationType.Read)
    fun getOpenBus(): Int = 0
    fun setOpenBus(value: Int, busType: NesCpuBusType = NesCpuBusType.Both) {}
}

interface NesCpuApuBridge {
    fun getDmcReadAddress(): Int = 0
    fun setDmcReadBuffer(value: Int) {}
    fun processCpuClock() {}
    fun reset(softReset: Boolean) {}
    fun setRegion(region: ConsoleRegion) {}
    fun beginFrame() {}
    fun endFrame() {}
    fun setApuStatus(enabled: Boolean) {}
}

interface NesCpuHost {
    val memoryManager: NesCpuMemoryManager
    val apu: NesCpuApuBridge
    val region: ConsoleRegion
    val randomizeCpuPpuAlignment: Boolean get() = false

    fun runPpuUntil(masterClock: Long)
    fun processCpuClock()
    fun getOpenBusMask(port: Int): Int = 0xFF
    fun randomInt(boundExclusive: Int): Int = 0
    fun onCpuCrash() {}
}

internal fun Int.u8(): Int = this and 0xFF
internal fun Int.u16(): Int = this and 0xFFFF
internal fun Long.u64Decremented(): Long = this - 1L
