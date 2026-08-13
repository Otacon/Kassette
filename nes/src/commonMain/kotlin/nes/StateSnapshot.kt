package nes

import nes.apu.ApuState
import nes.cartridge.Mapper0State
import nes.cartridge.MapperState
import nes.cpu.CpuBusState
import nes.cpu.CpuState
import nes.ppu.PpuBusState
import nes.ppu.PpuState

data class NesHardwareState(
    var machine: NesMachineState = NesMachineState(),
    var cpu: CpuState = CpuState(),
    var cpuBus: CpuBusState = CpuBusState(),
    var ppu: PpuState = PpuState(),
    var ppuBus: PpuBusState = PpuBusState(),
    var apu: ApuState = ApuState(),
    var mapper: MapperState = Mapper0State(),
)

data class NesMachineState(
    var ppuMasterClockRemainder: Int = 0,
    var previousNmiLine: Boolean = false,
    var cyclesUntilInputPoll: Int = 0,
)
