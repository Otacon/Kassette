package frontend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield
import nes.NesHardwareState
import nes.Timing
import nes.cartridge.Cartridge
import nes.cartridge.Mirroring
import nes.input.NesController
import nes2.apu.NesApu
import nes2.console.NesConsole
import nes2.console.NesConsoleOptions
import nes2.console.NesPpuFrame
import nes2.cpu.ConsoleRegion as ConsoleRegion2
import nes2.input.NesControlDevice
import nes2.input.NesControlManager
import nes2.mapper.BusConflictType
import nes2.mapper.GameInfo
import nes2.mapper.createMapper
import nes2.mapper.MirroringType
import nes2.mapper.NesRomInfo
import nes2.mapper.RomData as RomData2
import nes2.ppu.DefaultNesPpu

class Nes2FrontendMachine {
    val controller = NesController()
    var apu = NesApu()
        private set
    val ppu = FrameOutput()
    val timing: Timing get() = oldRegion.timing
    val isPoweredOn: StateFlow<Boolean> get() = poweredOn.asStateFlow()

    private val poweredOn = MutableStateFlow(false)
    private var oldRegion = nes.ConsoleRegion.NTSC
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
        apu = apuDevice
        ppu.setFrameBuffer(ppuDevice.frameColorIds)
        ppu.clear()
        val controlManager = NesControlManager { listOf(ControllerDevice(controller)) }
        console = NesConsole(
            mapper = mapper,
            ppu = ppuDevice,
            apuDevice = apuDevice,
            controlManager = controlManager,
            options = NesConsoleOptions(region = region, ppu = nes2.console.NesPpuOptions(onFrame = ppu::onFrame)),
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

    fun captureState(): NesHardwareState = error("nes2 savestates are not wired in the quick frontend adapter")
    fun restoreState(state: NesHardwareState) { error("nes2 savestates are not wired in the quick frontend adapter") }

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
