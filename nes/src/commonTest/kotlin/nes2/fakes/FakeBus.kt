package nes2.fakes

import nes2.CpuBus

class FakeBus(
    private val memory: IntArray,
) : CpuBus {
    data class Read(val address: Int)
    data class Write(val address: Int, val value: Int)

    val reads = mutableListOf<Read>()
    val writes = mutableListOf<Write>()

    override fun read(address: Int): Int {
        reads += Read(address)
        return memory[address]
    }

    override fun write(address: Int, value: Int) {
        writes += Write(address, value)
        memory[address] = value
    }
}
