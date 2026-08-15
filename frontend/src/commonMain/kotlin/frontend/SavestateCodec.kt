package frontend

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import dev.zacsweers.metro.Inject
import nes.NesHardwareState
import nes.NesMachineState
import nes.ConsoleRegion
import nes.apu.*
import nes.cartridge.*
import nes.cpu.CpuBusState
import nes.cpu.CpuState
import nes.ppu.PpuBusState
import nes.ppu.PpuState

@OptIn(ExperimentalSerializationApi::class)
@Inject
class SavestateCodec(
    private val cbor: Cbor,
    private val mapper: SavestateStateMapper,
) {
    fun encode(state: NesHardwareState): ByteArray = cbor.encodeToByteArray(SavestateDto.serializer(), mapper.toDto(state))

    fun decode(bytes: ByteArray): NesHardwareState {
        val dto = cbor.decodeFromByteArray(SavestateDto.serializer(), bytes)
        require(dto.formatVersion in 1..CURRENT_SAVESTATE_FORMAT) {
            "Unsupported savestate format ${dto.formatVersion}"
        }
        return mapper.fromDto(dto)
    }

    private companion object {
        const val CURRENT_SAVESTATE_FORMAT = 3
    }
}

@Inject
class SavestateStateMapper {
    fun toDto(state: NesHardwareState): SavestateDto = SavestateDto(
        machine = state.machine.toDto(),
        cpu = state.cpu.toDto(),
        cpuBus = state.cpuBus.toDto(),
        ppu = state.ppu.toDto(),
        ppuBus = state.ppuBus.toDto(),
        apu = state.apu.toDto(),
        mapper = state.mapper.toDto(),
    )

    fun fromDto(dto: SavestateDto): NesHardwareState = NesHardwareState(
        machine = dto.machine.toState(),
        cpu = dto.cpu.toState(),
        cpuBus = dto.cpuBus.toState(),
        ppu = dto.ppu.toState(),
        ppuBus = dto.ppuBus.toState(),
        apu = dto.apu.toState(),
        mapper = dto.mapper.toState(),
    )

    private fun NesMachineState.toDto() = NesMachineStateDto(ppuMasterClockRemainder, previousNmiLine, cyclesUntilInputPoll, region)
    private fun NesMachineStateDto.toState() = NesMachineState(ppuMasterClockRemainder, previousNmiLine, cyclesUntilInputPoll, region)

    private fun CpuState.toDto() = CpuStateDto(
        pc, a, x, y, sp, status, totalCycles, nmiPending, irqLine, irqPending, irqSample, halted,
        nmiSample, nmiDetected,
    )
    private fun CpuStateDto.toState() = CpuState(
        pc = pc, a = a, x = x, y = y, sp = sp, status = status, totalCycles = totalCycles,
        nmiPending = nmiPending, nmiSample = nmiSample, nmiDetected = nmiDetected,
        irqLine = irqLine, irqPending = irqPending, irqSample = irqSample, halted = halted,
    )

    private fun CpuBusState.toDto() = CpuBusStateDto(ram, openBus, oamDmaPage)
    private fun CpuBusStateDto.toState() = CpuBusState(ram, openBus, oamDmaPage)

    private fun PpuBusState.toDto() = PpuBusStateDto(nametables, paletteRam)
    private fun PpuBusStateDto.toState() = PpuBusState(nametables, paletteRam)

    private fun PpuState.toDto() = PpuStateDto(
        frameColorIds = frameColorIds.toList(),
        renderFramebufferIndex = renderFramebufferIndex,
        completedFramebufferIndex = completedFramebufferIndex,
        oam = oam,
        ctrl = ctrl,
        mask = mask,
        status = status,
        oamAddress = oamAddress,
        v = v,
        t = t,
        fineX = fineX,
        writeLatch = writeLatch,
        scanline = scanline,
        cycle = cycle,
        frameComplete = frameComplete,
        nmiRequested = nmiRequested,
        nmiLine = nmiLine,
        ppuCycle = ppuCycle,
        openBusDecayStamps = openBusDecayStamps,
        secondaryOam = secondaryOam,
        activeSpriteX = activeSpriteX,
        activeSpriteAttributes = activeSpriteAttributes,
        activeSpriteLow = activeSpriteLow,
        activeSpriteHigh = activeSpriteHigh,
        fetchedSpriteX = fetchedSpriteX,
        fetchedSpriteAttributes = fetchedSpriteAttributes,
        fetchedSpriteLow = fetchedSpriteLow,
        fetchedSpriteHigh = fetchedSpriteHigh,
        counters = counters,
        flags = flags.copyOf(maxOf(flags.size, 9)),
    )

    private fun PpuStateDto.toState(): PpuState {
        val restoredCounters = counters.copyOf(maxOf(counters.size, 28))
        if (counters.size <= 26) restoredCounters[26] = restoredCounters[16]
        return PpuState(
        frameColorIds = Array(frameColorIds.size) { restorePpuFramebuffer(frameColorIds[it]) },
        renderFramebufferIndex = renderFramebufferIndex,
        completedFramebufferIndex = completedFramebufferIndex,
        oam = oam,
        ctrl = ctrl,
        mask = mask,
        status = status,
        oamAddress = oamAddress,
        v = v,
        t = t,
        fineX = fineX,
        writeLatch = writeLatch,
        scanline = scanline,
        cycle = cycle,
        frameComplete = frameComplete,
        nmiRequested = nmiRequested,
        nmiLine = nmiLine,
        ppuCycle = ppuCycle,
        openBusDecayStamps = openBusDecayStamps.copyOf(maxOf(openBusDecayStamps.size, 8)),
        secondaryOam = secondaryOam,
        activeSpriteX = activeSpriteX,
        activeSpriteAttributes = activeSpriteAttributes,
        activeSpriteLow = activeSpriteLow,
        activeSpriteHigh = activeSpriteHigh,
        fetchedSpriteX = fetchedSpriteX,
        fetchedSpriteAttributes = fetchedSpriteAttributes,
        fetchedSpriteLow = fetchedSpriteLow,
        fetchedSpriteHigh = fetchedSpriteHigh,
        counters = restoredCounters,
        flags = flags.copyOf(maxOf(flags.size, 9)),
        )
    }


    private fun restorePpuFramebuffer(source: ByteArray): ByteArray {
        if (source.size != 256 * 240) return source
        val restored = ByteArray(256 * 240 * 4)
        var pixel = 0
        while (pixel < source.size) {
            val offset = pixel shl 2
            restored[offset] = source[pixel]
            restored[offset + 3] = 0xFF.toByte()
            pixel++
        }
        return restored
    }

    private fun ApuState.toDto() = ApuStateDto(
        pulse1.toDto(), pulse2.toDto(), triangle.toDto(), noise.toDto(), dmc.toDto(),
        frameCycle, frameEventIndex, frameMode, frameIrqInhibit, frameIrqPending, apuCycle, samplePhase, filters,
    )

    private fun ApuStateDto.toState() = ApuState(
        pulse1.toState(), pulse2.toState(), triangle.toState(), noise.toState(), dmc.toState(),
        frameCycle, frameEventIndex, frameMode, frameIrqInhibit, frameIrqPending, apuCycle, samplePhase, filters,
    )

    private fun PulseState.toDto() = PulseStateDto(enabled, lengthCounter, values, flags)
    private fun PulseStateDto.toState() = PulseState(enabled, lengthCounter, values, flags)
    private fun TriangleState.toDto() = TriangleStateDto(enabled, lengthCounter, values, flags)
    private fun TriangleStateDto.toState() = TriangleState(enabled, lengthCounter, values, flags)
    private fun NoiseState.toDto() = NoiseStateDto(enabled, lengthCounter, values, flags)
    private fun NoiseStateDto.toState() = NoiseState(enabled, lengthCounter, values, flags)
    private fun DmcState.toDto() = DmcStateDto(values, flags)
    private fun DmcStateDto.toState() = DmcState(values, flags.copyOf(maxOf(flags.size, 7)))

    private fun MapperState.toDto(): MapperStateDto = when (this) {
        is Mapper0State -> MapperStateDto.Mapper0(chr)
        is Mapper1State -> MapperStateDto.Mapper1(chr, prgRam, registers)
        is Mapper2State -> MapperStateDto.Mapper2(chrRam, selectedBankBase)
        is Mapper3State -> MapperStateDto.Mapper3(selectedChrBankBase)
        is Mapper4State -> MapperStateDto.Mapper4(chr, prgRam, registers, selectedRegister, prgMode, chrMode, irqLatch, irqCounter, irqReload, irqEnabled, irqRequested, mirroring, prgRamEnabled, prgRamWriteProtected, a12LowCycle)
        is Mapper7State -> MapperStateDto.Mapper7(chrRam, selectedBankBase, mirroring)
        is Mapper11State -> MapperStateDto.Mapper11(selectedPrgBankBase, selectedChrBankBase)
        is Mapper34State -> MapperStateDto.Mapper34(chr, prgRam, selectedPrgBankBase, chrBank0Base, chrBank1Base)
        is Mapper66State -> MapperStateDto.Mapper66(selectedPrgBankBase, selectedChrBankBase)
        is Mapper71State -> MapperStateDto.Mapper71(chrRam, selectedPrgBankBase, firehawkMode, mirroring)
        is Mapper79State -> MapperStateDto.Mapper79(selectedPrgBankBase, selectedChrBankBase, mirroring)
        is Mapper87State -> MapperStateDto.Mapper87(selectedChrBankBase)
    }

    private fun MapperStateDto.toState(): MapperState = when (this) {
        is MapperStateDto.Mapper0 -> Mapper0State(chr)
        is MapperStateDto.Mapper1 -> Mapper1State(chr, prgRam, registers)
        is MapperStateDto.Mapper2 -> Mapper2State(chrRam, selectedBankBase)
        is MapperStateDto.Mapper3 -> Mapper3State(selectedChrBankBase)
        is MapperStateDto.Mapper4 -> Mapper4State(chr, prgRam, registers, selectedRegister, prgMode, chrMode, irqLatch, irqCounter, irqReload, irqEnabled, irqRequested, mirroring, prgRamEnabled, prgRamWriteProtected, a12LowCycle)
        is MapperStateDto.Mapper7 -> Mapper7State(chrRam, selectedBankBase, mirroring)
        is MapperStateDto.Mapper11 -> Mapper11State(selectedPrgBankBase, selectedChrBankBase)
        is MapperStateDto.Mapper34 -> Mapper34State(chr, prgRam, selectedPrgBankBase, chrBank0Base, chrBank1Base)
        is MapperStateDto.Mapper66 -> Mapper66State(selectedPrgBankBase, selectedChrBankBase)
        is MapperStateDto.Mapper71 -> Mapper71State(chrRam, selectedPrgBankBase, firehawkMode, mirroring)
        is MapperStateDto.Mapper79 -> Mapper79State(selectedPrgBankBase, selectedChrBankBase, mirroring)
        is MapperStateDto.Mapper87 -> Mapper87State(selectedChrBankBase)
    }
}

@Serializable
data class SavestateDto(
    val formatVersion: Int = 3,
    val machine: NesMachineStateDto,
    val cpu: CpuStateDto,
    val cpuBus: CpuBusStateDto,
    val ppu: PpuStateDto,
    val ppuBus: PpuBusStateDto,
    val apu: ApuStateDto,
    val mapper: MapperStateDto,
)

@Serializable data class NesMachineStateDto(val ppuMasterClockRemainder: Int, val previousNmiLine: Boolean, val cyclesUntilInputPoll: Int, val region: ConsoleRegion? = null)
@Serializable data class CpuStateDto(val pc: Int, val a: Int, val x: Int, val y: Int, val sp: Int, val status: Int, val totalCycles: Long, val nmiPending: Boolean, val irqLine: Boolean, val irqPending: Boolean, val irqSample: Boolean, val halted: Boolean, val nmiSample: Boolean = false, val nmiDetected: Boolean = false)
@Serializable data class CpuBusStateDto(val ram: ByteArray, val openBus: Int, val oamDmaPage: Int)
@Serializable data class PpuBusStateDto(val nametables: ByteArray, val paletteRam: ByteArray)
@Serializable data class PpuStateDto(
    val frameColorIds: List<ByteArray>, val renderFramebufferIndex: Int, val completedFramebufferIndex: Int, val oam: ByteArray,
    val ctrl: Int, val mask: Int, val status: Int, val oamAddress: Int, val v: Int, val t: Int, val fineX: Int,
    val writeLatch: Boolean, val scanline: Int, val cycle: Int, val frameComplete: Boolean, val nmiRequested: Boolean,
    val nmiLine: Boolean, val ppuCycle: Long = 0, val openBusDecayStamps: IntArray = IntArray(8), val secondaryOam: ByteArray, val activeSpriteX: IntArray, val activeSpriteAttributes: IntArray,
    val activeSpriteLow: IntArray, val activeSpriteHigh: IntArray, val fetchedSpriteX: IntArray, val fetchedSpriteAttributes: IntArray,
    val fetchedSpriteLow: IntArray, val fetchedSpriteHigh: IntArray, val counters: IntArray, val flags: BooleanArray,
)
@Serializable data class ApuStateDto(val pulse1: PulseStateDto, val pulse2: PulseStateDto, val triangle: TriangleStateDto, val noise: NoiseStateDto, val dmc: DmcStateDto, val frameCycle: Int, val frameEventIndex: Int, val frameMode: Int, val frameIrqInhibit: Boolean, val frameIrqPending: Boolean, val apuCycle: Boolean, val samplePhase: Int, val filters: DoubleArray)
@Serializable data class PulseStateDto(val enabled: Boolean, val lengthCounter: Int, val values: IntArray, val flags: BooleanArray)
@Serializable data class TriangleStateDto(val enabled: Boolean, val lengthCounter: Int, val values: IntArray, val flags: BooleanArray)
@Serializable data class NoiseStateDto(val enabled: Boolean, val lengthCounter: Int, val values: IntArray, val flags: BooleanArray)
@Serializable data class DmcStateDto(val values: IntArray, val flags: BooleanArray)

@Serializable
sealed interface MapperStateDto {
    @Serializable data class Mapper0(val chr: ByteArray) : MapperStateDto
    @Serializable data class Mapper1(val chr: ByteArray, val prgRam: ByteArray, val registers: IntArray) : MapperStateDto
    @Serializable data class Mapper2(val chrRam: ByteArray, val selectedBankBase: Int) : MapperStateDto
    @Serializable data class Mapper3(val selectedChrBankBase: Int) : MapperStateDto
    @Serializable data class Mapper4(val chr: ByteArray, val prgRam: ByteArray, val registers: IntArray, val selectedRegister: Int, val prgMode: Boolean, val chrMode: Boolean, val irqLatch: Int, val irqCounter: Int, val irqReload: Boolean, val irqEnabled: Boolean, val irqRequested: Boolean, val mirroring: Mirroring?, val prgRamEnabled: Boolean, val prgRamWriteProtected: Boolean, val a12LowCycle: Long = -1) : MapperStateDto
    @Serializable data class Mapper7(val chrRam: ByteArray, val selectedBankBase: Int, val mirroring: Mirroring) : MapperStateDto
    @Serializable data class Mapper11(val selectedPrgBankBase: Int, val selectedChrBankBase: Int) : MapperStateDto
    @Serializable data class Mapper34(val chr: ByteArray, val prgRam: ByteArray, val selectedPrgBankBase: Int, val chrBank0Base: Int, val chrBank1Base: Int) : MapperStateDto
    @Serializable data class Mapper66(val selectedPrgBankBase: Int, val selectedChrBankBase: Int) : MapperStateDto
    @Serializable data class Mapper71(val chrRam: ByteArray, val selectedPrgBankBase: Int, val firehawkMode: Boolean, val mirroring: Mirroring?) : MapperStateDto
    @Serializable data class Mapper79(val selectedPrgBankBase: Int, val selectedChrBankBase: Int, val mirroring: Mirroring?) : MapperStateDto
    @Serializable data class Mapper87(val selectedChrBankBase: Int) : MapperStateDto
}
