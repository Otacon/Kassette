package nes2.apu

interface Apu {
    fun read(address: Int): Int
    fun write(address: Int, value: Int)
}

class ApuNes : Apu {
    override fun read(address: Int): Int = 0

    override fun write(address: Int, value: Int) = Unit
}
