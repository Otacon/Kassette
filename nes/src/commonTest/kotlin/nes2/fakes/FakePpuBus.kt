package nes2.fakes

import nes.util.low8Bits
import nes2.ppuBus.PpuBus

class FakePpuBus : PpuBus {
    val memory = IntArray(0x4000)

    override fun read(address: Int): Int {
        return memory[address and 0x3FFF]
    }

    override fun write(address: Int, value: Int) {
        memory[address and 0x3FFF] = value.low8Bits()
    }
}