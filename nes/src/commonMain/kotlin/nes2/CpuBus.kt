package nes2

class CpuBus(
    private val memory: IntArray,
) {

    fun read(address: Int): Int {
        return memory[address]
    }

    fun write(address: Int, value: Int) {
        memory[address] = value
    }
}