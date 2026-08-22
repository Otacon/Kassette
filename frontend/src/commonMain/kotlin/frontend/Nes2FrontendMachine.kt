package frontend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield
import nes.Timing
import nes.cartridge.Cartridge
import nes.cartridge.Mirroring
import nes.input.NesController
import nes.apu.NesApuSnapshot
import nes.apu.NesApu
import nes.console.NesConsole
import nes.console.NesConsoleOptions
import nes.console.NesPpuFrame
import nes.cpu.NesCpuSnapshot
import nes.cpu.ConsoleRegion as ConsoleRegion2
import nes.input.NesControlDevice
import nes.input.NesControlManager
import nes.input.NesControlManagerSnapshot
import nes.mapper.BaseMapper
import nes.mapper.BusConflictType
import nes.mapper.GameInfo
import nes.mapper.MapperSnapshot
import nes.mapper.createMapper
import nes.mapper.MirroringType
import nes.mapper.NesRomInfo
import nes.mapper.RomData as RomData2
import nes.memory.NesMemoryManagerSnapshot
import nes.ppu.DefaultNesPpu
import nes.ppu.NesPpuSnapshot

class Nes2FrontendMachine {
    val controller = NesController()
    var apu = NesApu()
        private set
    val ppu = FrameOutput()
    val timing: Timing get() = oldRegion.timing
    val isPoweredOn: StateFlow<Boolean> get() = poweredOn.asStateFlow()

    private val poweredOn = MutableStateFlow(false)
    private var oldRegion = nes.ConsoleRegion.NTSC
    private var ppuDevice: DefaultNesPpu? = null
    private var controlManager: NesControlManager? = null
    private var console: NesConsole? = null

    fun powerOff() {
        poweredOn.value = false
    }

    fun powerOn() {
        console?.powerOn()
        poweredOn.value = console != null
    }

    fun insert(cartridge: Cartridge) {
        oldRegion = cartridge.region.takeUnless { it == nes.ConsoleRegion.MULTI_REGION } ?: nes.ConsoleRegion.NTSC
        val region = when (oldRegion) {
            nes.ConsoleRegion.PAL -> ConsoleRegion2.Pal
            nes.ConsoleRegion.DENDY -> ConsoleRegion2.Dendy
            else -> ConsoleRegion2.Ntsc
        }
        val chrRom = if (cartridge.isChrRam) ByteArray(0) else cartridge.chr
        val rom = RomData2(
            info = NesRomInfo(
                mapperID = cartridge.mapperId,
                subMapperID = cartridge.submapperId,
                hasTrainer = cartridge.trainerPresent,
                busConflicts = if (cartridge.submapperId == 2) BusConflictType.Yes else BusConflictType.Default,
                databaseInfo = GameInfo(
                    mapperID = cartridge.mapperId,
                    hasBattery = false,
                ),
                mirroring = when (cartridge.mirroring) {
                    Mirroring.VERTICAL -> MirroringType.Vertical
                    Mirroring.SINGLE_SCREEN_LOWER -> MirroringType.ScreenAOnly
                    Mirroring.SINGLE_SCREEN_UPPER -> MirroringType.ScreenBOnly
                    else -> MirroringType.Horizontal
                },
            ),
            chrRamSize = if (cartridge.isChrRam) cartridge.chr.size else -1,
            workRamSize = cartridge.prgRamSize,
            prgRom = cartridge.prgRom,
            chrRom = chrRom,
        )

        val mapper = createMapper(rom)
        val ppuDevice = DefaultNesPpu()
        val apuDevice = NesApu()
        val controlManager = NesControlManager { listOf(ControllerDevice(controller)) }
        this.ppuDevice = ppuDevice
        this.controlManager = controlManager
        apu = apuDevice
        ppu.setFrameBuffer(ppuDevice.frameColorIds)
        ppu.clear()
        console = NesConsole(
            mapper = mapper,
            ppu = ppuDevice,
            apuDevice = apuDevice,
            controlManager = controlManager,
            options = NesConsoleOptions(region = region, ppu = nes.console.NesPpuOptions(onFrame = ppu::onFrame)),
        )
    }

    fun reset() {
        console?.reset()
    }

    suspend fun runUntilFrameYielding(onInputPoll: (() -> Unit)? = null) {
        val c = console ?: return
        val frame = c.ppu.frameCount
        var cycles = 0
        apu.beginFrame()
        while (frame == c.ppu.frameCount) {
            c.cpu.exec()
            cycles++
            if (cycles % INPUT_POLL_INTERVAL == 0) onInputPoll?.invoke()
            if (cycles % CPU_CYCLES_PER_YIELD == 0) yield()
        }
        c.mapper.endFrame()
        c.apu.endFrame()
    }

    fun captureState(): Nes2FrontendState {
        val c = console ?: error("Cannot capture state without a loaded console")
        val p = ppuDevice ?: error("Cannot capture state without a PPU")
        val controls = controlManager ?: error("Cannot capture state without controls")
        val mapper = c.mapper as? BaseMapper ?: error("Unsupported mapper state type")
        return Nes2FrontendState(
            oldRegion = oldRegion,
            poweredOn = poweredOn.value,
            nextFrameOverclockDisabled = c.getNextFrameOverclockStatus(),
            cpu = c.cpu.captureSnapshot(),
            memory = c.memoryManager.captureSnapshot(),
            ppu = p.captureSnapshot(),
            apu = apu.captureInternalSnapshot(),
            mapper = mapper.captureSnapshot(),
            controls = controls.captureSnapshot(),
            completedFrameColorIds = ppu.completedFrameColorIds.copyOf(),
            completedFrameCount = ppu.frameCount,
        )
    }

    fun restoreState(state: Nes2FrontendState) {
        val c = console ?: error("Cannot restore state without a loaded console")
        val p = ppuDevice ?: error("Cannot restore state without a PPU")
        val controls = controlManager ?: error("Cannot restore state without controls")
        val mapper = c.mapper as? BaseMapper ?: error("Unsupported mapper state type")
        oldRegion = state.oldRegion
        c.setNextFrameOverclockStatus(state.nextFrameOverclockDisabled)
        mapper.restoreSnapshot(state.mapper)
        c.memoryManager.restoreSnapshot(state.memory)
        p.restoreSnapshot(state.ppu)
        apu.restoreSnapshot(state.apu)
        controls.restoreSnapshot(state.controls)
        c.cpu.restoreSnapshot(state.cpu)
        ppu.restoreSnapshot(state.completedFrameColorIds, state.completedFrameCount)
        poweredOn.value = state.poweredOn
    }

    class FrameOutput {
        var completedFrameColorIds = ByteArray(256 * 240)
            private set
        var frameCount: Int = 0
            private set

        fun setFrameBuffer(frameBuffer: ByteArray) {
            completedFrameColorIds = frameBuffer
        }

        fun clear() {
            completedFrameColorIds.fill(0)
            frameCount = 0
        }

        fun onFrame(frame: NesPpuFrame) {
            frameCount = frame.frameCount
        }

        fun restoreSnapshot(colorIds: ByteArray, frameCount: Int) {
            colorIds.copyInto(completedFrameColorIds, endIndex = minOf(colorIds.size, completedFrameColorIds.size))
            this.frameCount = frameCount
        }
    }

    private class ControllerDevice(private val controller: NesController) : NesControlDevice {
        override var previousReadCycle: Long = 0
        override var previousReadValue: Int = 0

        override fun readRam(addr: Int): Int = if ((addr and 1) == 0) controller.read() else 0
        override fun writeRam(addr: Int, value: Int) = controller.write(value)
        override fun reset(softReset: Boolean) = controller.reset()
    }

    private companion object {
        const val INPUT_POLL_INTERVAL = 15_000
        const val CPU_CYCLES_PER_YIELD = 2_000
    }
}

data class Nes2FrontendState(
    val oldRegion: nes.ConsoleRegion,
    val poweredOn: Boolean,
    val nextFrameOverclockDisabled: Boolean,
    val cpu: NesCpuSnapshot,
    val memory: NesMemoryManagerSnapshot,
    val ppu: NesPpuSnapshot,
    val apu: NesApuSnapshot,
    val mapper: MapperSnapshot,
    val controls: NesControlManagerSnapshot,
    val completedFrameColorIds: ByteArray,
    val completedFrameCount: Int,
) {
    override fun equals(other: Any?): Boolean = other is Nes2FrontendState &&
        oldRegion == other.oldRegion &&
        poweredOn == other.poweredOn &&
        nextFrameOverclockDisabled == other.nextFrameOverclockDisabled &&
        cpu == other.cpu &&
        memory == other.memory &&
        ppu == other.ppu &&
        apu == other.apu &&
        mapper == other.mapper &&
        controls == other.controls &&
        completedFrameColorIds.contentEquals(other.completedFrameColorIds) &&
        completedFrameCount == other.completedFrameCount

    override fun hashCode(): Int {
        var result = oldRegion.hashCode()
        result = 31 * result + poweredOn.hashCode()
        result = 31 * result + nextFrameOverclockDisabled.hashCode()
        result = 31 * result + cpu.hashCode()
        result = 31 * result + memory.hashCode()
        result = 31 * result + ppu.hashCode()
        result = 31 * result + apu.hashCode()
        result = 31 * result + mapper.hashCode()
        result = 31 * result + controls.hashCode()
        result = 31 * result + completedFrameColorIds.contentHashCode()
        result = 31 * result + completedFrameCount
        return result
    }
}
