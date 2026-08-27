import frontend.NesMachineState
import frontend.SavestateCodec
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import nes.ConsoleRegion
import nes.apu.NesApuSnapshot
import nes.cpu.NesCpuSnapshot
import nes.input.NesControlManagerSnapshot
import nes.mapper.MapperSnapshot
import nes.memory.NesMemoryManagerSnapshot
import nes.ppu.NesPpuSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class SavestateCodecTest {
    @OptIn(ExperimentalSerializationApi::class)
    private val codec = SavestateCodec(Cbor { encodeDefaults = true; ignoreUnknownKeys = false })

    @Test
    fun `round trips NES savestate payload`() {
        val state = NesMachineState(
            oldRegion = ConsoleRegion.NTSC,
            poweredOn = true,
            nextFrameOverclockDisabled = true,
            cpu = NesCpuSnapshot(),
            memory = NesMemoryManagerSnapshot(internalRam = byteArrayOf(1, 2, 3), openBus = 0x44),
            ppu = NesPpuSnapshot(frameCount = 12, cycle = 34),
            apu = NesApuSnapshot(CurrentCycle = 56),
            mapper = MapperSnapshot(workRam = byteArrayOf(7, 8, 9)),
            controls = NesControlManagerSnapshot(writeAddr = 0x4016, writeValue = 1, writePending = 1),
            completedFramePixels = intArrayOf(10, 11, 12),
            completedFrameCount = 13,
        )

        assertEquals(state, codec.decode(codec.encode(state)))
    }
}
