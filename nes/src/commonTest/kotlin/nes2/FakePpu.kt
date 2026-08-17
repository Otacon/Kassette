package nes2

class FakePpu(
    private val registers: IntArray = IntArray(8),
) : Ppu {

    var oamDmaPage: Int = 0
        private set

    override fun cpuReadRegister(address: Int): Int {
        return registers[address - 0x2000]
    }

    override fun cpuWriteRegister(address: Int, value: Int) {
        registers[address - 0x2000] = value and 0xFF
    }

    override fun writeOamData(value: Int) {
        TODO("Not yet implemented")
    }

}