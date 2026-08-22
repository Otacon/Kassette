import frontend.Nes2FrontendState
import frontend.SavestateCodec
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import nes.ConsoleRegion
import nes2.apu.NesApuSnapshot
import nes2.cpu.NesCpuSnapshot
import nes2.input.NesControlManagerSnapshot
import nes2.mapper.MapperSnapshot
import nes2.memory.NesMemoryManagerSnapshot
import nes2.ppu.NesPpuSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class SavestateCodecTest {
    @OptIn(ExperimentalSerializationApi::class)
    private val codec = SavestateCodec(Cbor { encodeDefaults = true; ignoreUnknownKeys = false })

    @Test
    fun `round trips nes2 savestate payload`() {
        val state = Nes2FrontendState(
            oldRegion = ConsoleRegion.NTSC,
            poweredOn = true,
            nextFrameOverclockDisabled = true,
            cpu = NesCpuSnapshot(),
            memory = NesMemoryManagerSnapshot(internalRam = byteArrayOf(1, 2, 3), openBus = 0x44),
            ppu = NesPpuSnapshot(frameCount = 12, cycle = 34),
            apu = NesApuSnapshot(CurrentCycle = 56),
            mapper = MapperSnapshot(workRam = byteArrayOf(7, 8, 9)),
            controls = NesControlManagerSnapshot(writeAddr = 0x4016, writeValue = 1, writePending = 1),
            completedFrameColorIds = byteArrayOf(10, 11, 12),
            completedFrameCount = 13,
        )

        assertEquals(state, codec.decode(codec.encode(state)))
    }
}
