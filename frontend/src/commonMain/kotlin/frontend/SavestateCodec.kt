package frontend

import dev.zacsweers.metro.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import nes.ConsoleRegion
import nes.apu.NesApuSnapshot
import nes.cpu.NesCpuSnapshot
import nes.input.NesControlManagerSnapshot
import nes.mapper.MapperSnapshot
import nes.memory.NesMemoryManagerSnapshot
import nes.ppu.NesPpuSnapshot

@OptIn(ExperimentalSerializationApi::class)
@Inject
class SavestateCodec(
    private val cbor: Cbor,
) {
    fun encode(state: NesMachineState): ByteArray = cbor.encodeToByteArray(
        NesSavestateDto.serializer(),
        state.toDto(),
    )

    fun decode(bytes: ByteArray): NesMachineState = cbor.decodeFromByteArray(
        NesSavestateDto.serializer(),
        bytes,
    ).toState()

    private fun NesMachineState.toDto(): NesSavestateDto = NesSavestateDto(
        oldRegion = oldRegion.toDto(),
        poweredOn = poweredOn,
        nextFrameOverclockDisabled = nextFrameOverclockDisabled,
        cpu = cpu,
        memory = memory,
        ppu = ppu,
        apu = apu,
        mapper = mapper,
        controls = controls,
        completedFrameColorIds = completedFrameColorIds,
        completedFrameCount = completedFrameCount,
    )

    private fun NesSavestateDto.toState(): NesMachineState {
        require(formatVersion == CurrentFormatVersion) { "Unsupported savestate format version $formatVersion" }
        return NesMachineState(
            oldRegion = oldRegion.toConsoleRegion(),
            poweredOn = poweredOn,
            nextFrameOverclockDisabled = nextFrameOverclockDisabled,
            cpu = cpu,
            memory = memory,
            ppu = ppu,
            apu = apu,
            mapper = mapper,
            controls = controls,
            completedFrameColorIds = completedFrameColorIds,
            completedFrameCount = completedFrameCount,
        )
    }

    private fun ConsoleRegion.toDto(): Int = when (this) {
        ConsoleRegion.NTSC -> 0
        ConsoleRegion.PAL -> 1
        ConsoleRegion.MULTI_REGION -> 2
        ConsoleRegion.DENDY -> 3
    }

    private fun Int.toConsoleRegion(): ConsoleRegion = when (this) {
        0 -> ConsoleRegion.NTSC
        1 -> ConsoleRegion.PAL
        2 -> ConsoleRegion.MULTI_REGION
        3 -> ConsoleRegion.DENDY
        else -> error("Invalid savestate region $this")
    }

    private companion object {
        const val CurrentFormatVersion = 2
    }
}

@Serializable
private data class NesSavestateDto(
    val formatVersion: Int = 2,
    val oldRegion: Int,
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
    override fun equals(other: Any?): Boolean = other is NesSavestateDto &&
        formatVersion == other.formatVersion &&
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
        var result = formatVersion
        result = 31 * result + oldRegion
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
