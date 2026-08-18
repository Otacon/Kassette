package nes2.fakes

import nes2.CpuBus

class FakeBus(
    private val memory: IntArray,
) : CpuBus {
    override fun read(address: Int): Int {
        return memory[address]
    }

    override fun write(address: Int, value: Int) {
        memory[address] = value
    }
}