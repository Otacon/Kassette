package nes.cpu

import nes.apu.NesApu
import nes.apu.DmcDma
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
    private val dmcDma: DmcDma,
) {
    private val controller2 = NesController()
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
    private var externalOpenBus: Int get() = state.externalOpenBus; set(value) { state.externalOpenBus = value }

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
        writeMapped(address, value, -1)
    }

    internal fun cpuRead(
        address: Int,
        cycle: Long,
        dummy: Boolean = false,
        opcodeFetch: Boolean = false,
    ): Int {
        val dmaCycles = if (cartridgeSocket.region != ConsoleRegion.PAL || opcodeFetch) {
            runPendingDma(address.low16Bits(), cycle)
        } else {
            0
        }
        val type = if (dummy) CycleType.DUMMY_READ else CycleType.READ
        val value = clockedRead(address, type)
        return value or ((dmaCycles + 1) shl READ_CYCLES_SHIFT)
    }

    internal fun cpuWrite(address: Int, value: Int, cycle: Long, dummy: Boolean = false) {
        clockedWrite(address, value, cycle, if (dummy) CycleType.DUMMY_WRITE else CycleType.WRITE)
    }

    internal fun idle(type: CycleType = CycleType.IDLE) {
        notifyPhase(type, beforeAccess = true)
        notifyCycle(type)
        notifyPhase(type, beforeAccess = false)
    }

    fun captureState(): CpuBusState = state.copy(
        ram = ram.copyOf(),
        controller1 = controller.captureState(),
        controller2 = controller2.captureState(),
    )

    fun restoreState(state: CpuBusState) {
        this.state = state.copy(
            ram = state.ram.copyOf(),
            controller1 = state.controller1.copy(),
            controller2 = state.controller2.copy(),
        )
        controller.restoreState(this.state.controller1)
        controller2.restoreState(this.state.controller2)
    }

    fun reset(softReset: Boolean) {
        dmcDma.reset()
        openBus = 0
        externalOpenBus = 0
        oamDmaPage = NO_DMA_PAGE
        if (!softReset) {
            controller.reset()
            controller2.reset()
        }
    }

    fun stepControllers() {
        controller.step()
        controller2.step()
    }

    private fun runPendingDma(readAddress: Int, startCycle: Long): Int {
        val page = oamDmaPage
        if (page == NO_DMA_PAGE && !dmcDma.pending()) return 0
        oamDmaPage = NO_DMA_PAGE

        // DMA starts by halting and repeating the CPU read that was about to occur.
        var cycles = 0
        val internalRegisterOverlap = readAddress in 0x4000..0x401F
        dmcDma.beginCycle()
        clockedRead(readAddress, CycleType.DMA_READ)
        cycles++

        val base = page shl 8
        var offset = 0
        var oamValue = 0
        var oamValueReady = false
        while (dmcDma.pending() || (page != NO_DMA_PAGE && offset < 256) || oamValueReady) {
            // The parity after the previous DMA cycle determines whether this is a get or put slot.
            val getCycle = ((startCycle + cycles) and 1L) == 0L
            val dmcReady = dmcDma.readyToRead()
            dmcDma.beginCycle()

            when {
                getCycle && dmcReady -> {
                    dmcDma.complete(dmaRead(dmcDma.requestedAddress(), internalRegisterOverlap))
                }

                getCycle && page != NO_DMA_PAGE && offset < 256 -> {
                    oamValue = dmaRead(base + offset, internalRegisterOverlap)
                    oamValueReady = true
                }

                !getCycle && oamValueReady -> {
                    clockedWrite(0x2004, oamValue, startCycle + cycles, CycleType.DMA_WRITE)
                    oamValueReady = false
                    offset++
                }

                else -> clockedRead(readAddress, CycleType.DMA_READ)
            }
            cycles++
        }
        return cycles
    }

    private fun readMapped(address: Int): Int {
        val value = when (val a = address.low16Bits()) {
            in 0x0000..0x1FFF -> ram[a and 0x07FF].toUnsignedInt()
            in 0x2000..0x3FFF -> ppu.cpuRead(0x2000 + (a and 7))
            in 0x4000..0x4013 -> openBus
            0x4014 -> openBus
            0x4015 -> apu.cpuRead(a, openBus)
            0x4016 -> controllerValue(controller)
            0x4017 -> controllerValue(controller2)
            in 0x4020..0xFFFF -> cartridgeSocket.cpuRead(a, externalOpenBus).also { externalOpenBus = it }
            else -> openBus
        }.low8Bits()
        openBus = value
        return value
    }

    private fun writeMapped(address: Int, value: Int, cycle: Long) {
        val a = address.low16Bits()
        val v = value.low8Bits()
        openBus = v
        when (a) {
            in 0x0000..0x1FFF -> ram[a and 0x07FF] = v.toByte()
            in 0x2000..0x3FFF -> ppu.cpuWrite(0x2000 + (a and 7), v)
            in 0x4000..0x4013 -> apu.cpuWrite(a, v)
            0x4014 -> oamDmaPage = v
            0x4015 -> apu.cpuWrite(a, v)
            0x4016 -> {
                if (cycle < 0) {
                    controller.write(v)
                    controller2.write(v)
                } else {
                    val delay = if ((cycle and 1L) != 0L) 1 else 2
                    controller.scheduleWrite(v, delay)
                    controller2.scheduleWrite(v, delay)
                }
            }
            0x4017 -> apu.cpuWrite(a, v)
            in 0x4020..0xFFFF -> {
                externalOpenBus = v
                cartridgeSocket.cpuWrite(a, v)
            }
        }
    }

    private fun clockedRead(address: Int, type: CycleType): Int {
        notifyPhase(type, beforeAccess = true)
        val value = readMapped(address)
        notifyCycle(type, address, value)
        notifyPhase(type, beforeAccess = false)
        return value
    }

    private fun clockedWrite(address: Int, value: Int, cycle: Long, type: CycleType) {
        notifyPhase(type, beforeAccess = true)
        writeMapped(address, value, cycle)
        notifyCycle(type, address, value)
        notifyPhase(type, beforeAccess = false)
    }

    private fun dmaRead(address: Int, internalRegisterOverlap: Boolean): Int {
        val a = address.low16Bits()
        if (internalRegisterOverlap) {
            notifyPhase(CycleType.DMA_READ, beforeAccess = true)
            val internalAddress = 0x4000 or (a and 0x1F)
            val value = when (internalAddress) {
                0x4015 -> {
                    val internalValue = apu.cpuRead(internalAddress, openBus)
                    openBus = internalValue
                    if (a != internalAddress) readExternalDma(a)
                    internalValue
                }

                0x4016, 0x4017 -> {
                    val internalValue = controllerValue(if (internalAddress == 0x4016) controller else controller2)
                    openBus = internalValue
                    if (a == internalAddress) {
                        internalValue
                    } else {
                        val externalValue = readExternalDma(a)
                        externalOpenBus = (externalValue and CONTROLLER_OPEN_BUS_MASK) or
                            (internalValue and CONTROLLER_DRIVEN_MASK)
                        (externalValue and CONTROLLER_OPEN_BUS_MASK) or
                            ((internalValue and CONTROLLER_DRIVEN_MASK) and
                                (externalValue and CONTROLLER_DRIVEN_MASK))
                    }
                }

                else -> readExternalDma(a)
            }.low8Bits()
            notifyCycle(CycleType.DMA_READ, a, value)
            notifyPhase(CycleType.DMA_READ, beforeAccess = false)
            return value
        }
        if (a in 0x4015..0x401A) {
            notifyPhase(CycleType.DMA_READ, beforeAccess = true)
            val value = openBus
            notifyCycle(CycleType.DMA_READ, a, value)
            notifyPhase(CycleType.DMA_READ, beforeAccess = false)
            return value
        }
        return clockedRead(a, CycleType.DMA_READ)
    }

    private fun readDmaMapped(address: Int): Int = if (address in 0x4015..0x401A) openBus else readMapped(address)

    private fun readExternalDma(address: Int): Int {
        val internalOpenBus = openBus
        val value = readDmaMapped(address)
        openBus = internalOpenBus
        externalOpenBus = value
        return value
    }

    private fun controllerValue(port: NesController): Int =
        (openBus and CONTROLLER_OPEN_BUS_MASK) or port.read()

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
        const val CONTROLLER_OPEN_BUS_MASK = 0xE0
        const val CONTROLLER_DRIVEN_MASK = 0x1F
    }
}
