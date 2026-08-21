package nes2.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Scope
import nes2.*
import nes2.apu.Apu
import nes2.apu.ApuNes
import nes2.cartridge.CartridgePort
import nes2.cartridge.CartridgePortNes
import nes2.controller.ControllerPort
import nes2.controller.ControllerPortNes
import nes2.cpu.Cpu6502
import nes2.ppu.FramebufferNes
import nes2.ppu.Ppu
import nes2.ppu.PpuNes
import nes2.ppu.PpuState
import nes2.ppuBus.PpuBus
import nes2.ppuBus.PpuBusNes
import nes2.ppuBus.PpuBusState

@Nes2Scope
@DependencyGraph
@Suppress("unused")
interface NesComponent {

    val nesMachine: NesMachine

    @Nes2Scope
    @Provides
    fun cartridgePort(): CartridgePort = CartridgePortNes()

    @Nes2Scope
    @Provides
    fun ppuBus(cartridgeSocket: CartridgePort): PpuBus = PpuBusNes(
        state = PpuBusState(),
        cartridge = cartridgeSocket,
    )

    @Nes2Scope
    @Provides
    fun ppu(ppuBus: PpuBus, cartridgeSocket: CartridgePort): Ppu = PpuNes(
        state = PpuState(),
        ppuBus = ppuBus,
        onMapperScanline = { cartridgeSocket.clockScanline() },
        frameBuffer = FramebufferNes()
    )

    @Nes2Scope
    @Provides
    fun controllerPort(): ControllerPort = ControllerPortNes()

    @Nes2Scope
    @Provides
    fun oamDma(ppu: Ppu): OamDma = OamDmaNes(ppu = ppu)

    @Nes2Scope
    @Provides
    fun apu(): Apu = ApuNes()

    @Nes2Scope
    @Provides
    fun cpuBus(
        cartridgeSocket: CartridgePort,
        ppu: Ppu,
        controllerPort: ControllerPort,
        dma: OamDma,
        apu: Apu
    ): CpuBus =
        CpuBusNes(
            ram = IntArray(2048),
            cartridge = cartridgeSocket,
            ppu = ppu,
            dma = dma,
            apu = apu,
            controller1 = controllerPort,
            controller2 = controllerPort,
        )

    @Nes2Scope
    @Provides
    fun cpu6502(cpuBus: CpuBus): Cpu6502 = Cpu6502(cpuBus)

    @Nes2Scope
    @Provides
    fun nesMachine(
        cpu6502: Cpu6502,
        cpuBus: CpuBus,
        ppu: Ppu,
        oamDma: OamDma,
        cartridgeSocket: CartridgePort,
    ): NesMachine {
        ppu.onNmi = { cpu6502.requestNmi() }
        oamDma.cpuBusRead = { cpuBus.read(it) }
        return NesMachineImpl(cpu6502, ppu, oamDma, cartridgeSocket)
    }

}

@Scope
annotation class Nes2Scope
