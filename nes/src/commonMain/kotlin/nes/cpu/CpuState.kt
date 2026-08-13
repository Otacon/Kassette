package nes.cpu

data class CpuState(
    var pc: Int = 0,
    var a: Int = 0,
    var x: Int = 0,
    var y: Int = 0,
    var sp: Int = 0xFD,
    var status: Int = Cpu6502.I or Cpu6502.U,
    var totalCycles: Long = 0,
    var nmiPending: Boolean = false,
    var irqLine: Boolean = false,
    var irqPending: Boolean = false,
    var irqSample: Boolean = false,
    var halted: Boolean = false,
)

data class CpuBusState(
    var ram: ByteArray = ByteArray(2048),
    var openBus: Int = 0,
    var oamDmaPage: Int = -1,
) {
    override fun equals(other: Any?): Boolean = other is CpuBusState &&
        ram.contentEquals(other.ram) && openBus == other.openBus && oamDmaPage == other.oamDmaPage

    override fun hashCode(): Int {
        var result = ram.contentHashCode()
        result = 31 * result + openBus
        result = 31 * result + oamDmaPage
        return result
    }
}
