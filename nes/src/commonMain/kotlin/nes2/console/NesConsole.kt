package nes2.console

import nes2.cpu.ConsoleRegion
import nes2.cpu.MemoryOperationType
import nes2.cpu.NesCpu
import nes2.cpu.NesCpuApuBridge
import nes2.cpu.NesCpuHost
import nes2.memory.INesMemoryHandler
import nes2.memory.NesMemoryManager
import nes2.memory.NesMemoryManagerHost
import nes2.memory.NesMemoryMapper

class NesConsole(
    val mapper: NesConsoleMapper,
    private val ppu: NesConsolePpu,
    private val apuDevice: NesCpuApuBridge,
    private val controlManager: NesConsoleControlManager,
    val options: NesConsoleOptions = NesConsoleOptions(),
) : NesCpuHost, NesMemoryManagerHost {
    override val memoryManager: NesMemoryManager = NesMemoryManager(this, mapper)
    override val region: ConsoleRegion get() = currentRegion
    override val randomizeCpuPpuAlignment: Boolean get() = options.randomizeCpuPpuAlignment
    override val apu: NesCpuApuBridge get() = apuDevice
    val cpu: NesCpu = NesCpu(this)

    private var currentRegion: ConsoleRegion = options.region
    private var nextFrameOverclockDisabled = false

    init {
        mapper.initConsole(this)
        ppu.initConsole(this)
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

    override fun hasCpuCheats(): Boolean = options.hasCpuCheats()
    override fun applyCpuCheat(addr: Int, value: Int): Int = options.applyCpuCheat(addr, value)
    override fun processMemoryRead(addr: Int, value: Int, operationType: MemoryOperationType) =
        options.processMemoryRead(addr, value, operationType)

    override fun processMemoryWrite(addr: Int, value: Int, operationType: MemoryOperationType): Boolean =
        options.processMemoryWrite(addr, value, operationType)

    fun getOpenBus(): Int = memoryManager.getOpenBus()
    fun getMasterClock(): Long = cpu.getCycleCount()
    fun getMasterClockRate(): Int = NesConstants.getClockRate(currentRegion)
    fun getFps(): Double = if (currentRegion == ConsoleRegion.Ntsc) 60.0988118623484 else 50.0069789081886

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
    val hasCpuCheats: () -> Boolean = { false },
    val applyCpuCheat: (addr: Int, value: Int) -> Int = { _, value -> value },
    val processMemoryRead: (addr: Int, value: Int, operationType: MemoryOperationType) -> Unit = { _, _, _ -> },
    val processMemoryWrite: (addr: Int, value: Int, operationType: MemoryOperationType) -> Boolean = { _, _, _ -> true },
    val onCpuCrash: () -> Unit = {},
    val ppu: NesPpuOptions = NesPpuOptions(),
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
    val enablePpu2000ScrollGlitch: Boolean = true,
    val extraScanlinesBeforeNmi: Int = 0,
    val extraScanlinesAfterNmi: Int = 0,
)

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
    override fun getMemoryRanges(ranges: nes2.memory.MemoryRanges) {}
    override fun readRam(addr: Int): Int = 0
    override fun writeRam(addr: Int, value: Int) {}
}
