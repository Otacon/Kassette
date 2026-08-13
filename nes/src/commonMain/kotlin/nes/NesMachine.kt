package nes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield
import nes.apu.NesApu
import nes.cartridge.Cartridge
import nes.cartridge.CartridgeSocket
import nes.cpu.Cpu6502
import nes.cpu.CpuBus
import nes.input.NesController
import nes.ppu.Ppu

class NesMachine(
    val controller: NesController,
    val cartridgeSocket: CartridgeSocket,
    val ppu: Ppu,
    val apu: NesApu,
    val cpu: Cpu6502,
    private val cpuBus: CpuBus,
) {
    private val _isPoweredOn = MutableStateFlow(false)
    val isPoweredOn: StateFlow<Boolean> = _isPoweredOn.asStateFlow()
    val timing: Timing
        get() = cartridgeSocket.region.timing

    private var ppuMasterClockRemainder = 0
    private var previousNmiLine = false
    private var cyclesUntilInputPoll = 0
    private var inputPollCallback: (() -> Unit)? = null

    init {
        cpuBus.setCyclePhaseListener { type, beforeAccess -> clockCpuPhase(type, beforeAccess) }
    }

    fun powerOn() {
        resetComponents(softReset = false)
        _isPoweredOn.value = true
    }

    fun powerOff() {
        _isPoweredOn.value = false
    }

    fun insert(cartridge: Cartridge) {
        cartridgeSocket.insert(cartridge)
        applyCartridgeTiming()
    }

    fun reset() {
        resetComponents(softReset = true)
    }

    fun captureState(): NesHardwareState = NesHardwareState(
        machine = NesMachineState(
            ppuMasterClockRemainder = ppuMasterClockRemainder,
            previousNmiLine = previousNmiLine,
            cyclesUntilInputPoll = cyclesUntilInputPoll,
        ),
        cpu = cpu.captureState(),
        cpuBus = cpuBus.captureState(),
        ppu = ppu.captureState(),
        ppuBus = ppu.ppuBusState(),
        apu = apu.captureState(),
        mapper = cartridgeSocket.captureMapperState(),
    )

    fun restoreState(state: NesHardwareState) {
        ppuMasterClockRemainder = state.machine.ppuMasterClockRemainder
        previousNmiLine = state.machine.previousNmiLine
        cyclesUntilInputPoll = state.machine.cyclesUntilInputPoll
        cartridgeSocket.restoreMapperState(state.mapper)
        cpuBus.restoreState(state.cpuBus)
        ppu.restoreState(state.ppu)
        ppu.restorePpuBusState(state.ppuBus)
        apu.restoreState(state.apu)
        cpu.restoreState(state.cpu)
    }

    private fun resetComponents(softReset: Boolean) {
        cartridgeSocket.reset()
        applyCartridgeTiming()
        ppu.reset(softReset)
        apu.reset()
        cpu.reset(softReset)
        controller.reset()
    }

    fun runUntilFrame(onInputPoll: (() -> Unit)? = null) {
        ppu.clearFrameComplete()
        apu.beginFrame()
        inputPollCallback = onInputPoll
        cyclesUntilInputPoll = timing.cpuHz / INPUT_POLLS_PER_SECOND
        try {
            while (!ppu.frameComplete) cpu.step()
        } finally {
            inputPollCallback = null
        }
    }

    suspend fun runUntilFrameYielding(onInputPoll: (() -> Unit)? = null) {
        ppu.clearFrameComplete()
        apu.beginFrame()
        inputPollCallback = onInputPoll
        cyclesUntilInputPoll = timing.cpuHz / INPUT_POLLS_PER_SECOND
        var cyclesUntilYield = CPU_CYCLES_PER_YIELD
        try {
            while (!ppu.frameComplete) {
                cpu.step()
                cyclesUntilYield--
                if (cyclesUntilYield <= 0) {
                    yield()
                    cyclesUntilYield = CPU_CYCLES_PER_YIELD
                }
            }
        } finally {
            inputPollCallback = null
        }
    }

    private fun clockCpuPhase(type: CpuBus.CycleType, beforeAccess: Boolean) {
        val currentTiming = timing
        val preAccessClocks = when (type) {
            CpuBus.CycleType.WRITE, CpuBus.CycleType.DUMMY_WRITE, CpuBus.CycleType.DMA_WRITE ->
                currentTiming.writePreAccessClocks
            else -> currentTiming.readPreAccessClocks
        }
        val masterClocks = if (beforeAccess) {
            preAccessClocks
        } else {
            currentTiming.cpuMasterClockDivider - preAccessClocks
        }
        clockPpu(masterClocks, currentTiming.ppuMasterClockDivider)
        if (!beforeAccess) {
            sampleInterruptLines()
            return
        }

        apu.step()

        val callback = inputPollCallback ?: return
        cyclesUntilInputPoll--
        if (cyclesUntilInputPoll <= 0) {
            callback()
            cyclesUntilInputPoll += timing.cpuHz / INPUT_POLLS_PER_SECOND
        }
    }

    private fun clockPpu(masterClocks: Int, ppuMasterClockDivider: Int) {
        val totalClocks = ppuMasterClockRemainder + masterClocks
        val ppuClocks = totalClocks / ppuMasterClockDivider
        var clock = 0
        while (clock < ppuClocks) {
            ppu.step()
            clock++
        }
        ppuMasterClockRemainder = totalClocks % ppuMasterClockDivider
    }

    private fun sampleInterruptLines() {
        val nmiLine = ppu.nmiLine
        if (!previousNmiLine && nmiLine) cpu.requestNmi()
        previousNmiLine = nmiLine
        cpu.sampleIrqLine(cartridgeSocket.irqPending() || apu.irqPending())
    }

    private fun applyCartridgeTiming() {
        val cartridgeTiming = timing
        ppu.timing = cartridgeTiming
        apu.timing = cartridgeTiming
        ppuMasterClockRemainder = 0
        previousNmiLine = false
    }

    companion object {
        private const val INPUT_POLLS_PER_SECOND = 120
        private const val CPU_CYCLES_PER_YIELD = 2_000
    }
}
