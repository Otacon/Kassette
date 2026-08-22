package nes2.apu

import nes2.console.NesConsole
import nes2.console.NesConsoleApu
import nes2.console.NesConstants
import nes2.cpu.ConsoleRegion
import nes2.cpu.IRQSource
import nes2.memory.INesMemoryHandler
import nes2.memory.MemoryOperation
import nes2.memory.MemoryRanges

class NesApu : NesConsoleApu, INesMemoryHandler {
    private lateinit var console: NesConsole
    private var region: ConsoleRegion = ConsoleRegion.Ntsc
    private var apuEnabled = true
    private var needToRun = false
    private var previousCycle = 0
    private var currentCycle = 0
    private var apuDisabledStamp = 0L

    private val square1 = SquareChannel(isChannel1 = true, apu = this)
    private val square2 = SquareChannel(isChannel1 = false, apu = this)
    private val triangle = TriangleChannel(apu = this)
    private val noise = NoiseChannel(apu = this)
    private val dmc = DeltaModulationChannel(apu = this)
    private val frameCounter = ApuFrameCounter(apu = this)

    override fun initConsole(console: NesConsole) {
        this.console = console
        setRegion(console.region)
        reset(false)
    }

    override fun getMemoryRanges(ranges: MemoryRanges) {
        ranges.addHandler(MemoryOperation.Write, 0x4000, 0x4015)
        ranges.addHandler(MemoryOperation.Write, 0x4017)
        ranges.addHandler(MemoryOperation.Read, 0x4015)
        ranges.addHandler(MemoryOperation.Read, 0x4018, 0x401A)
    }

    override fun readRam(addr: Int): Int {
        run()
        return when (addr and 0xFFFF) {
            0x4015 -> {
                val status = status(peek = false) or (console.memoryManager.getInternalOpenBus() and 0x20)
                console.cpu.clearIrqSource(IRQSource.FrameCounter)
                status and 0xFF
            }
            0x4018 -> square1.output or (square2.output shl 4)
            0x4019 -> triangle.output or (noise.output shl 4)
            0x401A -> dmc.output
            else -> console.getOpenBus()
        }
    }

    override fun peekRam(addr: Int): Int = status(peek = true)

    override fun writeRam(addr: Int, value: Int) {
        val address = addr and 0xFFFF
        val v = value and 0xFF
        when (address) {
            in 0x4000..0x4003 -> square1.writeRam(address, v)
            in 0x4004..0x4007 -> square2.writeRam(address, v)
            in 0x4008..0x400B -> triangle.writeRam(address, v)
            in 0x400C..0x400F -> noise.writeRam(address, v)
            in 0x4010..0x4013 -> dmc.writeRam(address, v)
            0x4015 -> {
                run()
                console.cpu.clearIrqSource(IRQSource.Dmc)
                square1.setEnabled((v and 0x01) != 0)
                square2.setEnabled((v and 0x02) != 0)
                triangle.setEnabled((v and 0x04) != 0)
                noise.setEnabled((v and 0x08) != 0)
                dmc.setEnabled((v and 0x10) != 0)
            }
            0x4017 -> frameCounter.writeRam(v)
        }
    }

    override fun reset(softReset: Boolean) {
        apuEnabled = true
        currentCycle = 0
        previousCycle = 0
        needToRun = false
        square1.reset(softReset)
        square2.reset(softReset)
        triangle.reset(softReset)
        noise.reset(softReset)
        dmc.reset(softReset)
        frameCounter.reset(softReset)
    }

    override fun setRegion(region: ConsoleRegion) {
        this.region = if (region == ConsoleRegion.Dendy) ConsoleRegion.Ntsc else region
        frameCounter.setRegion(this.region)
        noise.setRegion(this.region)
        dmc.setRegion(this.region)
    }

    override fun processCpuClock() {
        if (apuEnabled) exec()
    }

    override fun endFrame() {
        run()
        square1.endFrame()
        square2.endFrame()
        triangle.endFrame()
        noise.endFrame()
        dmc.endFrame()
        currentCycle = 0
        previousCycle = 0
    }

    override fun setApuStatus(enabled: Boolean) {
        if (apuEnabled == enabled) return
        if (!enabled) {
            apuDisabledStamp = console.cpu.getCycleCount()
            apuEnabled = false
        } else {
            val gap = console.cpu.getCycleCount() - apuDisabledStamp
            if ((gap and 1L) != 0L) exec()
            apuEnabled = true
        }
    }

    override fun getDmcReadAddress(): Int = dmc.getDmcReadAddress()

    override fun setDmcReadBuffer(value: Int) = dmc.setDmcReadBuffer(value)

    fun captureSnapshot(): ApuState = ApuState(
        Square1 = square1.getState(),
        Square2 = square2.getState(),
        Triangle = triangle.getState(),
        Noise = noise.getState(),
        Dmc = dmc.getState(),
        FrameCounter = frameCounter.getState(),
    )

    internal fun setNeedToRun() {
        needToRun = true
    }

    internal fun run() {
        var cyclesToRun = currentCycle - previousCycle
        while (cyclesToRun > 0) {
            previousCycle += frameCounter.run(cyclesToRun).also { cyclesToRun -= it }
            square1.reloadLengthCounter()
            square2.reloadLengthCounter()
            noise.reloadLengthCounter()
            triangle.reloadLengthCounter()
            square1.run(previousCycle)
            square2.run(previousCycle)
            noise.run(previousCycle)
            triangle.run(previousCycle)
            dmc.run(previousCycle)
        }
    }

    internal fun frameCounterTick(type: FrameType) {
        square1.tickEnvelope()
        square2.tickEnvelope()
        triangle.tickLinearCounter()
        noise.tickEnvelope()
        if (type == FrameType.HalfFrame) {
            square1.tickLengthCounter()
            square2.tickLengthCounter()
            triangle.tickLengthCounter()
            noise.tickLengthCounter()
            square1.tickSweep()
            square2.tickSweep()
        }
    }

    internal fun cpuCycleCount(): Long = console.cpu.getCycleCount()
    internal fun masterClock(): Long = console.getMasterClock()
    internal fun clockRate(): Int = NesConstants.getClockRate(region)
    internal fun startDmcTransfer() = console.cpu.startDmcTransfer()
    internal fun stopDmcTransfer() = console.cpu.stopDmcTransfer()
    internal fun setIrq(source: IRQSource) = console.cpu.setIrqSource(source)
    internal fun clearIrq(source: IRQSource) = console.cpu.clearIrqSource(source)
    internal fun hasIrq(source: IRQSource): Boolean = console.cpu.hasIrqSource(source)
    internal fun setNextFrameOverclockStatus(disabled: Boolean) = console.setNextFrameOverclockStatus(disabled)

    private fun exec() {
        currentCycle++
        if (needToRun(currentCycle)) run()
    }

    private fun needToRun(cycle: Int): Boolean {
        if (dmc.needToRun() || needToRun) {
            needToRun = false
            return true
        }
        val cyclesToRun = cycle - previousCycle
        return frameCounter.needToRun(cyclesToRun) || dmc.irqPending(cyclesToRun)
    }

    private fun status(peek: Boolean): Int {
        var status = 0
        if (square1.getStatus()) status = status or 0x01
        if (square2.getStatus()) status = status or 0x02
        if (triangle.getStatus()) status = status or 0x04
        if (noise.getStatus()) status = status or 0x08
        if (dmc.getStatus()) status = status or 0x10
        if (if (peek) frameCounter.peekIrqFlag() else frameCounter.getIrqFlag()) status = status or 0x40
        if (hasIrq(IRQSource.Dmc)) status = status or 0x80
        return status and 0xFF
    }
}

private val LENGTH_LOOKUP = intArrayOf(
    10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
    12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30,
)

private class ApuTimer {
    var previousCycle = 0
    var timer = 0
    var period = 0
    var lastOutput = 0

    fun reset() {
        previousCycle = 0
        timer = 0
        period = 0
        lastOutput = 0
    }

    fun addOutput(output: Int) {
        lastOutput = output and 0xFF
    }

    fun run(targetCycle: Int): Boolean {
        val cyclesToRun = targetCycle - previousCycle
        return if (cyclesToRun > timer) {
            previousCycle += timer + 1
            timer = period
            true
        } else {
            timer -= cyclesToRun
            previousCycle = targetCycle
            false
        }
    }

    fun endFrame() {
        previousCycle = 0
    }
}

private class ApuLengthCounter(private val apu: NesApu, private val triangle: Boolean = false) {
    private var enabled = false
    private var halt = false
    private var counter = 0
    private var reloadValue = 0
    private var previousValue = 0
    private var newHaltValue = false

    fun initialize(haltFlag: Boolean) {
        apu.setNeedToRun()
        newHaltValue = haltFlag
    }

    fun load(value: Int) {
        if (enabled) {
            reloadValue = LENGTH_LOOKUP[value and 0x1F]
            previousValue = counter
            apu.setNeedToRun()
        }
    }

    fun reset(softReset: Boolean) {
        enabled = false
        if (!softReset || !triangle) {
            halt = false
            counter = 0
            reloadValue = 0
            previousValue = 0
            newHaltValue = false
        }
    }

    fun reload() {
        if (reloadValue != 0) {
            if (counter == previousValue) counter = reloadValue
            reloadValue = 0
        }
        halt = newHaltValue
    }

    fun tick() {
        if (counter > 0 && !halt) counter--
    }

    fun setEnabled(value: Boolean) {
        if (!value) counter = 0
        enabled = value
    }

    fun status(): Boolean = counter > 0
    fun halted(): Boolean = halt
    fun isEnabled(): Boolean = enabled
    fun state(): ApuLengthCounterState = ApuLengthCounterState(counter, halt, reloadValue)
}

private class ApuEnvelope(private val apu: NesApu, triangle: Boolean = false) {
    val lengthCounter = ApuLengthCounter(apu, triangle)
    private var constantVolume = false
    private var volume = 0
    private var start = false
    private var divider = 0
    private var counter = 0

    fun initialize(value: Int) {
        lengthCounter.initialize((value and 0x20) != 0)
        constantVolume = (value and 0x10) != 0
        volume = value and 0x0F
    }

    fun resetEnvelope() {
        start = true
    }

    fun getVolume(): Int = if (lengthCounter.status()) if (constantVolume) volume else counter else 0

    fun reset(softReset: Boolean) {
        lengthCounter.reset(softReset)
        constantVolume = false
        volume = 0
        start = false
        divider = 0
        counter = 0
    }

    fun tick() {
        if (!start) {
            divider--
            if (divider < 0) {
                divider = volume
                if (counter > 0) counter-- else if (lengthCounter.halted()) counter = 15
            }
        } else {
            start = false
            counter = 15
            divider = volume
        }
    }

    fun state(): ApuEnvelopeState = ApuEnvelopeState(constantVolume, counter, divider, lengthCounter.halted(), start, volume)
}

private class SquareChannel(private val isChannel1: Boolean, private val apu: NesApu) {
    private val dutySequences = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(0, 0, 0, 0, 0, 0, 1, 1),
        intArrayOf(0, 0, 0, 0, 1, 1, 1, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 0, 0),
    )
    private val envelope = ApuEnvelope(apu)
    private val timer = ApuTimer()
    private var duty = 0
    private var dutyPos = 0
    private var sweepEnabled = false
    private var sweepPeriod = 0
    private var sweepNegate = false
    private var sweepShift = 0
    private var reloadSweep = false
    private var sweepDivider = 0
    private var sweepTargetPeriod = 0
    private var realPeriod = 0
    val output: Int get() = timer.lastOutput

    fun writeRam(addr: Int, value: Int) {
        apu.run()
        when (addr and 3) {
            0 -> { envelope.initialize(value); duty = (value and 0xC0) shr 6 }
            1 -> initializeSweep(value)
            2 -> setPeriod((realPeriod and 0x700) or value)
            3 -> { envelope.lengthCounter.load(value shr 3); setPeriod((realPeriod and 0xFF) or ((value and 7) shl 8)); dutyPos = 0; envelope.resetEnvelope() }
        }
        updateOutput()
    }

    fun run(targetCycle: Int) {
        while (timer.run(targetCycle)) {
            dutyPos = (dutyPos - 1) and 7
            updateOutput()
        }
    }

    fun reset(softReset: Boolean) {
        envelope.reset(softReset)
        timer.reset()
        duty = 0; dutyPos = 0; realPeriod = 0; sweepEnabled = false; sweepPeriod = 0
        sweepNegate = false; sweepShift = 0; reloadSweep = false; sweepDivider = 0; sweepTargetPeriod = 0
    }

    fun tickSweep() {
        sweepDivider--
        if (sweepDivider == 0) {
            if (sweepShift > 0 && sweepEnabled && realPeriod >= 8 && sweepTargetPeriod <= 0x7FF) setPeriod(sweepTargetPeriod)
            sweepDivider = sweepPeriod
        }
        if (reloadSweep) { sweepDivider = sweepPeriod; reloadSweep = false }
    }

    fun tickEnvelope() = envelope.tick()
    fun tickLengthCounter() = envelope.lengthCounter.tick()
    fun reloadLengthCounter() = envelope.lengthCounter.reload()
    fun endFrame() = timer.endFrame()
    fun setEnabled(enabled: Boolean) = envelope.lengthCounter.setEnabled(enabled)
    fun getStatus(): Boolean = envelope.lengthCounter.status()

    fun getState(): ApuSquareState = ApuSquareState(
        Duty = duty,
        DutyPosition = dutyPos,
        Enabled = envelope.lengthCounter.isEnabled(),
        Envelope = envelope.state(),
        Frequency = apu.clockRate() / 16.0 / (realPeriod + 1),
        LengthCounter = envelope.lengthCounter.state(),
        OutputVolume = output,
        Period = realPeriod,
        Timer = timer.timer / 2,
        SweepEnabled = sweepEnabled,
        SweepNegate = sweepNegate,
        SweepPeriod = sweepPeriod,
        SweepShift = sweepShift,
    )

    private fun initializeSweep(value: Int) {
        sweepEnabled = (value and 0x80) != 0
        sweepNegate = (value and 0x08) != 0
        sweepPeriod = ((value and 0x70) shr 4) + 1
        sweepShift = value and 7
        updateTargetPeriod()
        reloadSweep = true
    }

    private fun setPeriod(value: Int) {
        realPeriod = value and 0x7FF
        timer.period = realPeriod * 2 + 1
        updateTargetPeriod()
    }

    private fun updateTargetPeriod() {
        val shiftResult = realPeriod shr sweepShift
        sweepTargetPeriod = if (sweepNegate) {
            realPeriod - shiftResult - if (isChannel1) 1 else 0
        } else {
            realPeriod + shiftResult
        }
    }

    private fun updateOutput() {
        timer.addOutput(if (realPeriod < 8 || (!sweepNegate && sweepTargetPeriod > 0x7FF)) 0 else dutySequences[duty][dutyPos] * envelope.getVolume())
    }
}

private class TriangleChannel(private val apu: NesApu) {
    private val sequence = intArrayOf(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
    private val lengthCounter = ApuLengthCounter(apu, triangle = true)
    private val timer = ApuTimer()
    private var linearCounter = 0
    private var linearCounterReload = 0
    private var linearReloadFlag = false
    private var linearControlFlag = false
    private var sequencePosition = 0
    val output: Int get() = timer.lastOutput

    fun writeRam(addr: Int, value: Int) {
        apu.run()
        when (addr and 3) {
            0 -> { linearControlFlag = (value and 0x80) != 0; linearCounterReload = value and 0x7F; lengthCounter.initialize(linearControlFlag) }
            2 -> timer.period = (timer.period and 0x700) or value
            3 -> { lengthCounter.load(value shr 3); timer.period = (timer.period and 0xFF) or ((value and 7) shl 8); linearReloadFlag = true }
        }
    }

    fun run(targetCycle: Int) {
        while (timer.run(targetCycle)) {
            if (lengthCounter.status() && linearCounter > 0) {
                sequencePosition = (sequencePosition + 1) and 0x1F
                timer.addOutput(sequence[sequencePosition])
            }
        }
    }

    fun reset(softReset: Boolean) { timer.reset(); lengthCounter.reset(softReset); linearCounter = 0; linearCounterReload = 0; linearReloadFlag = false; linearControlFlag = false; sequencePosition = 0 }
    fun tickLinearCounter() { if (linearReloadFlag) linearCounter = linearCounterReload else if (linearCounter > 0) linearCounter--; if (!linearControlFlag) linearReloadFlag = false }
    fun tickLengthCounter() = lengthCounter.tick()
    fun reloadLengthCounter() = lengthCounter.reload()
    fun endFrame() = timer.endFrame()
    fun setEnabled(enabled: Boolean) = lengthCounter.setEnabled(enabled)
    fun getStatus(): Boolean = lengthCounter.status()
    fun getState(): ApuTriangleState = ApuTriangleState(lengthCounter.isEnabled(), apu.clockRate() / 32.0 / (timer.period + 1), lengthCounter.state(), output, timer.period, timer.timer, sequencePosition, linearCounterReload, linearCounter, linearReloadFlag)
}

private class NoiseChannel(private val apu: NesApu) {
    private val ntsc = intArrayOf(4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068)
    private val pal = intArrayOf(4, 8, 14, 30, 60, 88, 118, 148, 188, 236, 354, 472, 708, 944, 1890, 3778)
    private val envelope = ApuEnvelope(apu)
    private val timer = ApuTimer()
    private var periods = ntsc
    private var shiftRegister = 1
    private var modeFlag = false
    val output: Int get() = timer.lastOutput

    fun setRegion(region: ConsoleRegion) { periods = if (region == ConsoleRegion.Pal) pal else ntsc; timer.period = periods[0] - 1 }
    fun writeRam(addr: Int, value: Int) { apu.run(); when (addr and 3) { 0 -> envelope.initialize(value); 2 -> { timer.period = periods[value and 0x0F] - 1; modeFlag = (value and 0x80) != 0 }; 3 -> { envelope.lengthCounter.load(value shr 3); envelope.resetEnvelope() } } }
    fun run(targetCycle: Int) { while (timer.run(targetCycle)) { val feedback = (shiftRegister and 1) xor ((shiftRegister shr if (modeFlag) 6 else 1) and 1); shiftRegister = (shiftRegister shr 1) or (feedback shl 14); timer.addOutput(if ((shiftRegister and 1) != 0) 0 else envelope.getVolume()) } }
    fun reset(softReset: Boolean) { envelope.reset(softReset); timer.reset(); timer.period = periods[0] - 1; shiftRegister = 1; modeFlag = false }
    fun tickEnvelope() = envelope.tick()
    fun tickLengthCounter() = envelope.lengthCounter.tick()
    fun reloadLengthCounter() = envelope.lengthCounter.reload()
    fun endFrame() = timer.endFrame()
    fun setEnabled(enabled: Boolean) = envelope.lengthCounter.setEnabled(enabled)
    fun getStatus(): Boolean = envelope.lengthCounter.status()
    fun getState(): ApuNoiseState = ApuNoiseState(envelope.lengthCounter.isEnabled(), envelope.state(), apu.clockRate().toDouble() / (timer.period + 1) / if (modeFlag) 93.0 else 1.0, envelope.lengthCounter.state(), modeFlag, output, timer.period, timer.timer, shiftRegister)
}

private class DeltaModulationChannel(private val apu: NesApu) {
    private val ntsc = intArrayOf(428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54)
    private val pal = intArrayOf(398, 354, 316, 298, 276, 236, 210, 198, 176, 148, 132, 118, 98, 78, 66, 50)
    private val timer = ApuTimer()
    private var periods = ntsc
    private var sampleAddr = 0xC000
    private var sampleLength = 1
    private var outputLevel = 0
    private var irqEnabled = false
    private var loopFlag = false
    private var currentAddr = 0
    private var bytesRemaining = 0
    private var readBuffer = 0
    private var bufferEmpty = true
    private var shiftRegister = 0
    private var bitsRemaining = 8
    private var silenceFlag = true
    private var needToRun = false
    private var disableDelay = 0
    private var transferStartDelay = 0
    val output: Int get() = timer.lastOutput

    fun setRegion(region: ConsoleRegion) { periods = if (region == ConsoleRegion.Pal) pal else ntsc; timer.period = periods[0] - 1 }
    fun reset(softReset: Boolean) { timer.reset(); if (!softReset) { sampleAddr = 0xC000; sampleLength = 1 }; outputLevel = 0; irqEnabled = false; loopFlag = false; currentAddr = 0; bytesRemaining = 0; readBuffer = 0; bufferEmpty = true; shiftRegister = 0; bitsRemaining = 8; silenceFlag = true; needToRun = false; transferStartDelay = 0; disableDelay = 0; timer.period = periods[0] - 1; timer.timer = timer.period }
    fun writeRam(addr: Int, value: Int) { apu.run(); when (addr and 3) { 0 -> { irqEnabled = (value and 0x80) != 0; loopFlag = (value and 0x40) != 0; timer.period = periods[value and 0x0F] - 1; if (!irqEnabled) apu.clearIrq(IRQSource.Dmc) }; 1 -> { outputLevel = value and 0x7F; timer.addOutput(outputLevel); if (outputLevel > 0) apu.setNextFrameOverclockStatus(true) }; 2 -> { sampleAddr = 0xC000 or (value shl 6); if (value > 0) apu.setNextFrameOverclockStatus(false) }; 3 -> { sampleLength = (value shl 4) or 1; if (value > 0) apu.setNextFrameOverclockStatus(false) } } }
    fun run(targetCycle: Int) { while (timer.run(targetCycle)) { if (!silenceFlag) { if ((shiftRegister and 1) != 0) { if (outputLevel <= 125) outputLevel += 2 } else if (outputLevel >= 2) outputLevel -= 2; shiftRegister = shiftRegister shr 1 }; bitsRemaining--; if (bitsRemaining == 0) { bitsRemaining = 8; if (bufferEmpty) silenceFlag = true else { silenceFlag = false; shiftRegister = readBuffer; bufferEmpty = true; needToRun = true; if (transferStartDelay == 0) startTransfer() } }; timer.addOutput(outputLevel) } }
    fun irqPending(cyclesToRun: Int): Boolean = irqEnabled && bytesRemaining > 0 && cyclesToRun >= (bitsRemaining + (bytesRemaining - 1) * 8) * timer.period
    fun getStatus(): Boolean = bytesRemaining > 0
    fun endFrame() = timer.endFrame()
    fun setEnabled(enabled: Boolean) { if (!enabled) { if (disableDelay == 0) disableDelay = if ((apu.cpuCycleCount() and 1L) == 0L) 2 else 3; needToRun = true } else if (bytesRemaining == 0) { initSample(); transferStartDelay = if ((apu.cpuCycleCount() and 1L) == 0L) 2 else 3; needToRun = true } }
    fun processClock() { if (disableDelay != 0 && --disableDelay == 0) { bytesRemaining = 0; apu.stopDmcTransfer() }; if (transferStartDelay != 0 && --transferStartDelay == 0) startTransfer(); needToRun = disableDelay != 0 || transferStartDelay != 0 || bytesRemaining != 0 }
    fun needToRun(): Boolean { if (needToRun) processClock(); return needToRun }
    fun getDmcReadAddress(): Int = currentAddr
    fun setDmcReadBuffer(value: Int) { if (bytesRemaining > 0) { readBuffer = value and 0xFF; bufferEmpty = false; currentAddr = (currentAddr + 1) and 0xFFFF; if (currentAddr == 0) currentAddr = 0x8000; bytesRemaining--; if (bytesRemaining == 0) { if (loopFlag) initSample() else if (irqEnabled) apu.setIrq(IRQSource.Dmc) } } }
    fun getState(): ApuDmcState = ApuDmcState(bytesRemaining, irqEnabled, loopFlag, output, timer.period, timer.timer, apu.clockRate().toDouble() / (timer.period + 1), sampleAddr, currentAddr, sampleLength)
    private fun initSample() { currentAddr = sampleAddr; bytesRemaining = sampleLength; needToRun = bytesRemaining > 0 }
    private fun startTransfer() { if (bufferEmpty && bytesRemaining > 0) apu.startDmcTransfer() }
}

private class ApuFrameCounter(private val apu: NesApu) {
    private val ntsc = arrayOf(intArrayOf(7457, 14913, 22371, 29828, 29829, 29830), intArrayOf(7457, 14913, 22371, 29829, 37281, 37282))
    private val pal = arrayOf(intArrayOf(8313, 16627, 24939, 33252, 33253, 33254), intArrayOf(8313, 16627, 24939, 33253, 41565, 41566))
    private val frameTypes = arrayOf(arrayOf(FrameType.QuarterFrame, FrameType.HalfFrame, FrameType.QuarterFrame, FrameType.None, FrameType.HalfFrame, FrameType.None), arrayOf(FrameType.QuarterFrame, FrameType.HalfFrame, FrameType.QuarterFrame, FrameType.None, FrameType.HalfFrame, FrameType.None))
    private var steps = ntsc
    private var previousCycle = 0
    private var currentStep = 0
    private var stepMode = 0
    private var inhibitIrq = false
    private var blockFrameCounterTick = 0
    private var newValue = 0
    private var writeDelayCounter = 3
    private var irqFlag = false
    private var irqFlagClearClock = 0L

    fun reset(softReset: Boolean) { previousCycle = 0; irqFlag = false; irqFlagClearClock = 0; if (!softReset) stepMode = 0; currentStep = 0; newValue = if (stepMode != 0) 0x80 else 0; writeDelayCounter = 3; inhibitIrq = false; blockFrameCounterTick = 0 }
    fun setRegion(region: ConsoleRegion) { steps = if (region == ConsoleRegion.Pal) pal else ntsc }
    fun run(cyclesToRunInput: Int): Int { var cyclesToRun = cyclesToRunInput; val cyclesRan: Int; if (previousCycle + cyclesToRun >= steps[stepMode][currentStep]) { if (stepMode == 0 && currentStep >= 3) { irqFlag = true; irqFlagClearClock = 0; if (!inhibitIrq) apu.setIrq(IRQSource.FrameCounter) else if (currentStep == 5) irqFlag = false }; val type = frameTypes[stepMode][currentStep]; if (type != FrameType.None && blockFrameCounterTick == 0) { apu.frameCounterTick(type); blockFrameCounterTick = 2 }; cyclesRan = if (steps[stepMode][currentStep] < previousCycle) 0 else steps[stepMode][currentStep] - previousCycle; cyclesToRun -= cyclesRan; currentStep++; if (currentStep == 6) { currentStep = 0; previousCycle = 0 } else previousCycle += cyclesRan } else { cyclesRan = cyclesToRun; cyclesToRun = 0; previousCycle += cyclesRan }; if (newValue >= 0) { writeDelayCounter--; if (writeDelayCounter == 0) { stepMode = if ((newValue and 0x80) != 0) 1 else 0; writeDelayCounter = -1; currentStep = 0; previousCycle = 0; newValue = -1; if (stepMode != 0 && blockFrameCounterTick == 0) { apu.frameCounterTick(FrameType.HalfFrame); blockFrameCounterTick = 2 } } }; if (blockFrameCounterTick > 0) blockFrameCounterTick--; return cyclesRan }
    fun needToRun(cyclesToRun: Int): Boolean = newValue >= 0 || blockFrameCounterTick > 0 || previousCycle + cyclesToRun >= steps[stepMode][currentStep] - 1
    fun writeRam(value: Int) { apu.run(); newValue = value; writeDelayCounter = if ((apu.cpuCycleCount() and 1L) != 0L) 4 else 3; inhibitIrq = (value and 0x40) != 0; if (inhibitIrq) { apu.clearIrq(IRQSource.FrameCounter); irqFlag = false; irqFlagClearClock = 0 } }
    fun getIrqFlag(): Boolean { if (irqFlag) { val clock = apu.masterClock(); if (irqFlagClearClock == 0L) irqFlagClearClock = clock + if ((clock and 1L) != 0L) 2 else 1 else if (clock >= irqFlagClearClock) { irqFlagClearClock = 0; irqFlag = false } }; return irqFlag }
    fun peekIrqFlag(): Boolean = irqFlag && (irqFlagClearClock == 0L || apu.masterClock() < irqFlagClearClock)
    fun getState(): ApuFrameCounterState = ApuFrameCounterState(!inhibitIrq, minOf(currentStep, if (stepMode != 0) 5 else 4), stepMode == 1)
}
