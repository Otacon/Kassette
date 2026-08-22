package nes2.cpu

enum class ConsoleRegion {
    Ntsc,
    Pal,
    Dendy,
}

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

enum class NesCpuBusType {
    Default,
    Internal,
    External,
}

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

interface NesCpuMemoryManager {
    fun read(addr: Int, operationType: MemoryOperationType = MemoryOperationType.Read, busType: NesCpuBusType = NesCpuBusType.Default): Int
    fun write(addr: Int, value: Int, operationType: MemoryOperationType = MemoryOperationType.Write)
    fun debugRead(addr: Int): Int = read(addr, MemoryOperationType.Read)
    fun getOpenBus(): Int = 0
    fun setOpenBus(value: Int, busType: NesCpuBusType = NesCpuBusType.Default) {}
}

interface NesCpuApuBridge {
    fun getDmcReadAddress(): Int
    fun setDmcReadBuffer(value: Int)
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
