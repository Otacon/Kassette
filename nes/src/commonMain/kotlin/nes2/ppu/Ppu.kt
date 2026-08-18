package nes2.ppu

interface Ppu {
    fun cpuReadRegister(address: Int): Int
    fun cpuWriteRegister(address: Int, value: Int)
    fun writeOamData(value: Int)
}

class PpuNes(
    private val state: PpuState = PpuState(),
) : Ppu {

    override fun cpuReadRegister(address: Int): Int {
        return when (address) {
            0x2002 -> readStatus()
            0x2004 -> state.oam[state.oamAddress]
            else -> 0
        }
    }

    override fun cpuWriteRegister(address: Int, value: Int) {
        when (address) {
            0x2000 -> state.control = value and 0xFF
            0x2003 -> state.oamAddress = value and 0xFF
            0x2004 -> writeOamData(value)
        }
    }

    override fun writeOamData(value: Int) {
        state.oam[state.oamAddress] = value and 0xFF
        state.oamAddress = (state.oamAddress + 1) and 0xFF
    }

    private fun readStatus(): Int {
        val value = state.status

        state.status = state.status and VBLANK_FLAG.inv()
        state.writeToggle = false

        return value
    }

    private companion object {
        const val VBLANK_FLAG = 0x80
    }
}