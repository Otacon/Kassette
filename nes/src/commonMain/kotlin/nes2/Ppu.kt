package nes2

interface Ppu {
    fun cpuReadRegister(address: Int): Int
    fun cpuWriteRegister(address: Int, value: Int)
    fun writeOamDma(page: Int)
}