package nes.cartridge

interface Mapper {
    fun cpuRead(address: Int): Int
    fun cpuRead(address: Int, openBus: Int): Int = cpuRead(address)
    fun cpuWrite(address: Int, value: Int)
    fun ppuRead(address: Int): Int
    fun ppuWrite(address: Int, value: Int)
    fun ppuAddressChanged(address: Int, cpuCycle: Long) = Unit
    fun reset() = Unit
    fun clockScanline() = Unit
    fun irqPending(): Boolean = false
    fun mirroring(): Mirroring? = null
    fun captureState(): MapperState = error("Mapper ${this::class.simpleName} does not support savestates")
    fun restoreState(state: MapperState): Unit = error("Mapper ${this::class.simpleName} does not support savestates")
}
