package nes.cpu

import nes.apu.NesApu
import nes.ConsoleRegion
import nes.cartridge.CartridgeSocket
import nes.input.NesController
import nes.ppu.Ppu
import nes.util.low16Bits
import nes.util.low8Bits
import nes.util.toUnsignedInt

class CpuBus(
    private val cartridgeSocket: CartridgeSocket,
    private val ppu: Ppu,
    private val controller: NesController,
    private val apu: NesApu,
    private val cpuStall: CpuStall,
) {
    enum class CycleType {
        READ,
        WRITE,
        DUMMY_READ,
        DUMMY_WRITE,
        DMA_READ,
        DMA_WRITE,
        IDLE,
        RESET,
        STALL,
    }

    data class Cycle(
        val type: CycleType,
        val address: Int? = null,
        val value: Int? = null,
    )

    fun interface CycleListener {
        fun onCycle(cycle: Cycle)
    }

    fun interface CyclePhaseListener {
        fun onPhase(type: CycleType, beforeAccess: Boolean)
    }

    private var state = CpuBusState()
    val ram: ByteArray get() = state.ram
    private val cycleListeners = mutableListOf<CycleListener>()
    private var cyclePhaseListener: CyclePhaseListener? = null
    private var openBus: Int get() = state.openBus; set(value) { state.openBus = value }
    private var oamDmaPage: Int get() = state.oamDmaPage; set(value) { state.oamDmaPage = value }

    /** Adds a listener invoked exactly once for every CPU-owned cycle. */
    fun setCycleListener(listener: CycleListener) {
        cycleListeners += listener
    }

    fun setCyclePhaseListener(listener: CyclePhaseListener) {
        cyclePhaseListener = listener
    }

    /** Direct, unclocked bus access for tests, debuggers, and setup code. */
    fun read(address: Int): Int = readMapped(address)

    /** Direct, unclocked bus access for tests, debuggers, and setup code. */
    fun write(address: Int, value: Int) {
        writeMapped(address, value)
    }

    internal fun cpuRead(
        address: Int,
        cycle: Long,
        dummy: Boolean = false,
        opcodeFetch: Boolean = false,
    ): Int {
        val dmaCycles = if (cartridgeSocket.region != ConsoleRegion.PAL || opcodeFetch) {
            runPendingOamDma(address.low16Bits(), cycle)
        } else {
            0
        }
        val type = if (dummy) CycleType.DUMMY_READ else CycleType.READ
        val value = clockedRead(address, type)
        return value or ((dmaCycles + 1) shl READ_CYCLES_SHIFT)
    }

    internal fun cpuWrite(address: Int, value: Int, dummy: Boolean = false) {
        clockedWrite(address, value, if (dummy) CycleType.DUMMY_WRITE else CycleType.WRITE)
    }

    internal fun idle(type: CycleType = CycleType.IDLE) {
        notifyPhase(type, beforeAccess = true)
        notifyCycle(type)
        notifyPhase(type, beforeAccess = false)
    }

    fun consumeDmaCycles(): Int = cpuStall.drain()

    fun captureState(): CpuBusState = state.copy(ram = ram.copyOf())

    fun restoreState(state: CpuBusState) {
        this.state = state
    }

    fun reset() {
        cpuStall.reset()
        openBus = 0
        oamDmaPage = NO_DMA_PAGE
    }

    private fun runPendingOamDma(readAddress: Int, startCycle: Long): Int {
        val page = oamDmaPage
        if (page == NO_DMA_PAGE) return 0
        oamDmaPage = NO_DMA_PAGE

        var cycles = 0
        var value = clockedRead(readAddress, CycleType.DMA_READ)
        cycles++

        // The halt increments the CPU cycle first; an even completed cycle needs alignment.
        if ((startCycle and 1L) == 0L) {
            value = clockedRead(readAddress, CycleType.DMA_READ)
            cycles++
        }

        val base = page shl 8
        var offset = 0
        while (offset < 256) {
            value = dmaRead(base + offset)
            clockedWrite(0x2004, value, CycleType.DMA_WRITE)
            cycles += 2
            offset++
        }
        return cycles
    }

    private fun readMapped(address: Int): Int {
        val value = when (val a = address.low16Bits()) {
            in 0x0000..0x1FFF -> ram[a and 0x07FF].toUnsignedInt()
            in 0x2000..0x3FFF -> ppu.cpuRead(0x2000 + (a and 7))
            in 0x4000..0x4013 -> apu.cpuRead(a)
            0x4014 -> openBus
            0x4015 -> apu.cpuRead(a)
            0x4016 -> controller.read()
            0x4017 -> openBus
            in 0x4020..0xFFFF -> cartridgeSocket.cpuRead(a, openBus)
            else -> openBus
        }.low8Bits()
        openBus = value
        return value
    }

    private fun writeMapped(address: Int, value: Int) {
        val a = address.low16Bits()
        val v = value.low8Bits()
        openBus = v
        when (a) {
            in 0x0000..0x1FFF -> ram[a and 0x07FF] = v.toByte()
            in 0x2000..0x3FFF -> ppu.cpuWrite(0x2000 + (a and 7), v)
            in 0x4000..0x4013 -> apu.cpuWrite(a, v)
            0x4014 -> oamDmaPage = v
            0x4015 -> apu.cpuWrite(a, v)
            0x4016 -> controller.write(v)
            0x4017 -> apu.cpuWrite(a, v)
            in 0x4020..0xFFFF -> cartridgeSocket.cpuWrite(a, v)
        }
    }

    private fun clockedRead(address: Int, type: CycleType): Int {
        notifyPhase(type, beforeAccess = true)
        val value = readMapped(address)
        notifyCycle(type, address, value)
        notifyPhase(type, beforeAccess = false)
        return value
    }

    private fun clockedWrite(address: Int, value: Int, type: CycleType) {
        notifyPhase(type, beforeAccess = true)
        writeMapped(address, value)
        notifyCycle(type, address, value)
        notifyPhase(type, beforeAccess = false)
    }

    private fun dmaRead(address: Int): Int {
        val a = address.low16Bits()
        if (a in 0x4015..0x401A) {
            notifyPhase(CycleType.DMA_READ, beforeAccess = true)
            val value = openBus
            notifyCycle(CycleType.DMA_READ, a, value)
            notifyPhase(CycleType.DMA_READ, beforeAccess = false)
            return value
        }
        return clockedRead(a, CycleType.DMA_READ)
    }

    private fun notifyCycle(type: CycleType, address: Int = NO_ADDRESS, value: Int = 0) {
        if (cycleListeners.isEmpty()) return
        val cycle = Cycle(
            type,
            if (address == NO_ADDRESS) null else address.low16Bits(),
            if (address == NO_ADDRESS) null else value.low8Bits(),
        )
        var index = 0
        while (index < cycleListeners.size) cycleListeners[index++].onCycle(cycle)
    }

    private fun notifyPhase(type: CycleType, beforeAccess: Boolean) {
        cyclePhaseListener?.onPhase(type, beforeAccess)
    }

    private companion object {
        const val READ_CYCLES_SHIFT = 8
        const val NO_DMA_PAGE = -1
        const val NO_ADDRESS = -1
    }
}
