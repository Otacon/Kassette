/*
 * This file is part of Kassette.
 *
 * This Kotlin implementation is ported and adapted from MesenCE
 * (https://github.com/nesdev-org/MesenCE). MesenCE is licensed under
 * the GNU General Public License version 3.
 *
 * This modified Kotlin port is distributed under the GNU General Public
 * License version 3. See the repository LICENSE file for details.
 */

package nes.console

import nes.apu.NesApu
import nes.cpu.ConsoleRegion
import nes.cpu.NesCpu
import nes.cpu.NesCpuApuBridge
import nes.cpu.NesCpuHost
import nes.input.NesControlManager
import nes.memory.INesMemoryHandler
import nes.memory.NesMemoryManager
import nes.memory.NesMemoryManagerHost
import nes.memory.NesMemoryMapper
import nes.memory.CpuCheatHandler
import nes.memory.CpuMemoryAccessHandler
import nes.input.NesConsoleType

class NesConsole(
    val mapper: NesConsoleMapper,
    val ppu: NesConsolePpu,
    private val apuDevice: NesCpuApuBridge = NesApu(),
    private val controlManager: NesConsoleControlManager = NesControlManager(),
    val options: NesConsoleOptions = NesConsoleOptions(),
) : NesCpuHost, NesMemoryManagerHost {
    override val memoryManager: NesMemoryManager = NesMemoryManager(this, mapper)
    override val region: ConsoleRegion get() = currentRegion
    override val randomizeCpuPpuAlignment: Boolean get() = options.randomizeCpuPpuAlignment
    override val apu: NesCpuApuBridge get() = apuDevice
    override val cpuCheatHandler: CpuCheatHandler? get() = options.cpuCheatHandler
    override val cpuMemoryAccessHandler: CpuMemoryAccessHandler? get() = options.cpuMemoryAccessHandler
    val cpu: NesCpu = NesCpu(this)

    private var currentRegion: ConsoleRegion = options.region
    private var nextFrameOverclockDisabled = false

    init {
        mapper.initConsole(this)
        ppu.initConsole(this)
        (apuDevice as? NesConsoleApu)?.initConsole(this)
        controlManager.initConsole(this)

        registerOptionalIODevice(mapper.epsm)
        memoryManager.registerIODevice(ppu)
        memoryManager.registerIODevice(apuMemoryHandler())
        memoryManager.registerIODevice(controlManager)
        memoryManager.registerIODevice(mapper)
        updateRegion(forceUpdate = true)
    }

    fun reset(softReset: Boolean = true) {
        memoryManager.reset(softReset)
        ppu.reset(softReset)
        apuDevice.reset(softReset)
        cpu.reset(softReset, currentRegion)
        controlManager.reset(softReset)
        mapper.onAfterResetPowerOn()
        mapper.epsm?.reset()
    }

    fun powerOn() {
        ppu.reset(false)
        apuDevice.reset(false)
        memoryManager.reset(false)
        controlManager.reset(false)
        cpu.reset(false, currentRegion)
        mapper.onAfterResetPowerOn()
    }

    fun runFrame() {
        updateRegion()
        apuDevice.beginFrame()
        val frame = ppu.frameCount
        if (nextFrameOverclockDisabled) {
            ppu.updateTimings(currentRegion, overclockEnabled = false)
            nextFrameOverclockDisabled = false
        }

        while (frame == ppu.frameCount) {
            cpu.exec()
        }

        mapper.endFrame()
        apuDevice.endFrame()
        if (!nextFrameOverclockDisabled) {
            ppu.updateTimings(currentRegion, overclockEnabled = true)
        }
    }

    fun setNextFrameOverclockStatus(disabled: Boolean) {
        nextFrameOverclockDisabled = disabled
    }

    fun getNextFrameOverclockStatus(): Boolean = nextFrameOverclockDisabled

    fun updateRegion(forceUpdate: Boolean = false) {
        val newRegion = options.regionProvider?.invoke() ?: options.region
        if (currentRegion != newRegion || forceUpdate) {
            currentRegion = newRegion
            cpu.setMasterClockDivider(currentRegion)
            mapper.setRegion(currentRegion)
            ppu.updateTimings(currentRegion)
            apuDevice.setRegion(currentRegion)
        }
    }

    override fun runPpuUntil(masterClock: Long) {
        ppu.run(masterClock)
    }

    override fun processCpuClock() {
        if (mapper.hasCpuClockHook()) {
            mapper.processCpuClock()
        }
        apuDevice.processCpuClock()
        if (controlManager.hasPendingWrites()) {
            controlManager.processWrites()
        }
    }

    override fun getOpenBusMask(port: Int): Int = controlManager.getOpenBusMask(port)
    override fun randomInt(boundExclusive: Int): Int = options.randomInt(boundExclusive)
    override fun onCpuCrash() = options.onCpuCrash()

    override fun initializeRam(ram: ByteArray) {
        options.initializeRam(ram)
    }

    fun getOpenBus(): Int = memoryManager.getOpenBus()
    fun getMasterClock(): Long = cpu.getCycleCount()
    fun getMasterClockRate(): Int = NesConstants.getClockRate(currentRegion)
    fun getFps(): Double = if (currentRegion == ConsoleRegion.Ntsc) 60.0988118623484 else 50.0069789081886
    fun notifyPpuFrame(frame: NesPpuFrame) {
        options.ppu.frameListener?.onFrame(frame)
    }

    fun notifyPpuStartFrame(frameCount: Int) {
        options.ppu.frameListener?.onStartFrame(frameCount)
    }

    private fun registerOptionalIODevice(handler: INesMemoryHandler?) {
        if (handler != null) {
            memoryManager.registerIODevice(handler)
        }
    }

    private fun apuMemoryHandler(): INesMemoryHandler = apuDevice as? INesMemoryHandler ?: EmptyMemoryHandler
}

data class NesConsoleOptions(
    val region: ConsoleRegion = ConsoleRegion.Ntsc,
    val randomizeCpuPpuAlignment: Boolean = false,
    val regionProvider: (() -> ConsoleRegion)? = null,
    val randomInt: (boundExclusive: Int) -> Int = { 0 },
    val initializeRam: (ByteArray) -> Unit = { it.fill(0) },
    val cpuCheatHandler: CpuCheatHandler? = null,
    val cpuMemoryAccessHandler: CpuMemoryAccessHandler? = null,
    val onCpuCrash: () -> Unit = {},
    val ppu: NesPpuOptions = NesPpuOptions(),
    val apu: NesApuOptions = NesApuOptions(),
    val controllerType: NesConsoleType = NesConsoleType.Nes001,
)

data class NesApuOptions(
    val enableDmcSampleDuplicationGlitch: Boolean = false,
    val reduceDmcPopping: Boolean = false,
    val reverseDpcmBitOrder: Boolean = false,
    val cpuTestMode: Boolean = false,
    val swapDutyCycles: Boolean = false,
    val silenceTriangleHighFrequency: Boolean = false,
    val disableNoiseModeFlag: Boolean = false,
)

data class NesPpuOptions(
    val backgroundEnabled: Boolean = true,
    val spritesEnabled: Boolean = true,
    val forceBackgroundFirstColumn: Boolean = false,
    val forceSpritesFirstColumn: Boolean = false,
    val removeSpriteLimit: Boolean = false,
    val adaptiveSpriteLimit: Boolean = false,
    val enableOamDecay: Boolean = false,
    val disablePpuReset: Boolean = false,
    val restrictPpuAccessOnFirstFrame: Boolean = false,
    val disablePaletteRead: Boolean = false,
    val disablePpu2004Reads: Boolean = false,
    val enablePpuOamRowCorruption: Boolean = false,
    val enablePpuSpriteEvalBug: Boolean = false,
    val disableOamAddrBug: Boolean = false,
    val enablePpu2000ScrollGlitch: Boolean = true,
    val enablePpu2006ScrollGlitch: Boolean = true,
    val randomizePowerOnState: Boolean = false,
    val extraScanlinesBeforeNmi: Int = 0,
    val extraScanlinesAfterNmi: Int = 0,
    val frameListener: NesPpuFrameListener? = null,
)

interface NesPpuFrameListener {
    fun onStartFrame(frameCount: Int) {}
    fun onFrame(frame: NesPpuFrame) {}
}

class NesPpuFrame(
    var pixels: IntArray,
    val width: Int = NesConstants.ScreenWidth,
    val height: Int = NesConstants.ScreenHeight,
    var frameCount: Int,
    var videoPhase: Int,
) {
    fun update(pixels: IntArray, frameCount: Int, videoPhase: Int): NesPpuFrame {
        this.pixels = pixels
        this.frameCount = frameCount
        this.videoPhase = videoPhase
        return this
    }

    override fun equals(other: Any?): Boolean = other is NesPpuFrame &&
        width == other.width &&
        height == other.height &&
        frameCount == other.frameCount &&
        videoPhase == other.videoPhase &&
        pixels.contentEquals(other.pixels)

    override fun hashCode(): Int {
        var result = pixels.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + frameCount
        result = 31 * result + videoPhase
        return result
    }
}

object NesConstants {
    const val ClockRateNtsc = 1_789_773
    const val ClockRatePal = 1_662_607
    const val ClockRateDendy = 1_773_448
    const val CyclesPerLine = 341
    const val ScreenWidth = 256
    const val ScreenHeight = 240
    const val ScreenPixelCount = ScreenWidth * ScreenHeight

    fun getClockRate(region: ConsoleRegion): Int = when (region) {
        ConsoleRegion.Ntsc -> ClockRateNtsc
        ConsoleRegion.Pal -> ClockRatePal
        ConsoleRegion.Dendy -> ClockRateDendy
    }
}

private object EmptyMemoryHandler : INesMemoryHandler {
    override fun getMemoryRanges(ranges: nes.memory.MemoryRanges) {}
    override fun readRam(addr: Int): Int = 0
    override fun writeRam(addr: Int, value: Int) {}
}
