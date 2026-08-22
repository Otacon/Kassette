package nes2.apu

import nes2.cpu.ConsoleRegion
import nes2.cpu.IRQSource

internal class DeltaModulationChannel(private val apu: NesApu) {
    private val ntsc = intArrayOf(428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54)
    private val pal = intArrayOf(398, 354, 316, 298, 276, 236, 210, 198, 176, 148, 132, 118, 98, 78, 66, 50)
    private val timer = ApuTimer(ApuAudioChannel.Dmc, apu)
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
    private var lastValue4011 = 0
    val output: Int get() = timer.lastOutput

    fun setRegion(region: ConsoleRegion) {
        periods = if (region == ConsoleRegion.Pal) pal else ntsc
        timer.period = periods[0] - 1
    }

    fun reset(softReset: Boolean) {
        timer.reset()
        if (!softReset) {
            sampleAddr = 0xC000
            sampleLength = 1
        }

        outputLevel = 0
        irqEnabled = false
        loopFlag = false
        currentAddr = 0
        bytesRemaining = 0
        readBuffer = 0
        bufferEmpty = true
        shiftRegister = 0
        bitsRemaining = 8
        silenceFlag = true
        needToRun = false
        transferStartDelay = 0
        disableDelay = 0
        lastValue4011 = 0
        timer.period = periods[0] - 1
        timer.timer = timer.period
    }

    fun writeRam(addr: Int, value: Int) {
        apu.run()
        when (addr and 3) {
            0 -> {
                irqEnabled = (value and 0x80) != 0
                loopFlag = (value and 0x40) != 0
                timer.period = periods[value and 0x0F] - 1
                if (!irqEnabled) apu.clearIrq(IRQSource.Dmc)
            }
            1 -> writeOutputLevel(value)
            2 -> {
                sampleAddr = 0xC000 or (value shl 6)
                if (value > 0) apu.setNextFrameOverclockStatus(false)
            }
            3 -> {
                sampleLength = (value shl 4) or 1
                if (value > 0) apu.setNextFrameOverclockStatus(false)
            }
        }
    }

    fun run(targetCycle: Int) {
        while (timer.run(targetCycle)) {
            if (!silenceFlag) {
                val bit = if (apu.reverseDpcmBitOrder()) {
                    val result = shiftRegister and 0x80
                    shiftRegister = (shiftRegister shl 1) and 0xFF
                    result
                } else {
                    val result = shiftRegister and 0x01
                    shiftRegister = shiftRegister shr 1
                    result
                }

                if (bit != 0) {
                    if (outputLevel <= 125) outputLevel += 2
                } else if (outputLevel >= 2) {
                    outputLevel -= 2
                }
            }

            bitsRemaining--
            if (bitsRemaining == 0) {
                bitsRemaining = 8
                if (bufferEmpty) {
                    silenceFlag = true
                } else {
                    silenceFlag = false
                    shiftRegister = readBuffer
                    bufferEmpty = true
                    needToRun = true
                    if (transferStartDelay == 0) startTransfer()
                }
            }

            timer.addOutput(outputLevel)
        }
    }
    fun irqPending(cyclesToRun: Int): Boolean = irqEnabled && bytesRemaining > 0 && cyclesToRun >= (bitsRemaining + (bytesRemaining - 1) * 8) * timer.period
    fun getStatus(): Boolean = bytesRemaining > 0
    fun endFrame() = timer.endFrame()
    fun setEnabled(enabled: Boolean) { if (!enabled) { if (disableDelay == 0) disableDelay = if ((apu.cpuCycleCount() and 1L) == 0L) 2 else 3; needToRun = true } else if (bytesRemaining == 0) { initSample(); transferStartDelay = if ((apu.cpuCycleCount() and 1L) == 0L) 2 else 3; needToRun = true } }
    fun processClock() { if (disableDelay != 0 && --disableDelay == 0) { bytesRemaining = 0; apu.stopDmcTransfer() }; if (transferStartDelay != 0 && --transferStartDelay == 0) startTransfer(); needToRun = disableDelay != 0 || transferStartDelay != 0 || bytesRemaining != 0 }
    fun needToRun(): Boolean { if (needToRun) processClock(); return needToRun }
    fun getDmcReadAddress(): Int = currentAddr
    fun setDmcReadBuffer(value: Int) {
        if (bytesRemaining > 0) {
            readBuffer = value and 0xFF
            bufferEmpty = false
            currentAddr = (currentAddr + 1) and 0xFFFF
            if (currentAddr == 0) currentAddr = 0x8000
            bytesRemaining--
            if (bytesRemaining == 0) {
                if (loopFlag) initSample() else if (irqEnabled) apu.setIrq(IRQSource.Dmc)
            }
        }

        if (bitsRemaining == 8 && timer.timer == timer.period && apu.enableDmcSampleDuplicationGlitch()) {
            shiftRegister = readBuffer
            silenceFlag = false
            bufferEmpty = true
            if (sampleLength == 1) initSample()
            startTransfer()
        } else if (sampleLength == 1 && !loopFlag && bitsRemaining == 1 && timer.timer < 2) {
            shiftRegister = readBuffer
            bufferEmpty = false
            initSample()
            disableDelay = 3
        }
    }
    fun getState(): ApuDmcState = ApuDmcState(bytesRemaining, irqEnabled, loopFlag, output, timer.period, timer.timer, apu.clockRate().toDouble() / (timer.period + 1), sampleAddr, currentAddr, sampleLength)
    fun captureSnapshot(): DeltaModulationChannelSnapshot = DeltaModulationChannelSnapshot(timer.captureSnapshot(), sampleAddr, sampleLength, outputLevel, irqEnabled, loopFlag, currentAddr, bytesRemaining, readBuffer, bufferEmpty, shiftRegister, bitsRemaining, silenceFlag, needToRun, disableDelay, transferStartDelay, lastValue4011)

    fun restoreSnapshot(snapshot: DeltaModulationChannelSnapshot) {
        timer.restoreSnapshot(snapshot.Timer)
        sampleAddr = snapshot.SampleAddr and 0xFFFF
        sampleLength = snapshot.SampleLength and 0xFFFF
        outputLevel = snapshot.OutputLevel and 0x7F
        irqEnabled = snapshot.IrqEnabled
        loopFlag = snapshot.LoopFlag
        currentAddr = snapshot.CurrentAddr and 0xFFFF
        bytesRemaining = snapshot.BytesRemaining and 0xFFFF
        readBuffer = snapshot.ReadBuffer and 0xFF
        bufferEmpty = snapshot.BufferEmpty
        shiftRegister = snapshot.ShiftRegister and 0xFF
        bitsRemaining = snapshot.BitsRemaining and 0xFF
        silenceFlag = snapshot.SilenceFlag
        needToRun = snapshot.NeedToRun
        disableDelay = snapshot.DisableDelay and 0xFF
        transferStartDelay = snapshot.TransferStartDelay and 0xFF
        lastValue4011 = snapshot.LastValue4011 and 0xFF
    }

    private fun initSample() { currentAddr = sampleAddr; bytesRemaining = sampleLength; needToRun = bytesRemaining > 0 }
    private fun startTransfer() { if (bufferEmpty && bytesRemaining > 0) apu.startDmcTransfer() }

    private fun writeOutputLevel(value: Int) {
        val previousLevel = outputLevel
        var newLevel = value and 0x7F
        if (apu.reduceDmcPopping() && kotlin.math.abs(newLevel - previousLevel) > 50) {
            newLevel -= (newLevel - previousLevel) / 2
        }

        outputLevel = newLevel and 0x7F
        timer.addOutput(outputLevel)
        if (lastValue4011 != (value and 0x7F) && outputLevel > 0) {
            apu.setNextFrameOverclockStatus(true)
        }
        lastValue4011 = value and 0x7F
    }
}
