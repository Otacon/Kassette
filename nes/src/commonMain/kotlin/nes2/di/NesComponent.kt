package nes2.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Scope
import nes.cartridge.CartridgeSocket
import nes2.ControllerPort
import nes2.CpuBus
import nes2.CpuBusNes
import nes2.OamDmaNes
import nes2.cpu.Cpu6502
import nes2.ppu.FramebufferNes
import nes2.ppu.Ppu
import nes2.ppu.PpuNes
import nes2.ppu.PpuState
import nes2.ppuBus.PpuBus
import nes2.ppuBus.PpuBusNes
import nes2.ppuBus.PpuBusState

@NesScope
@DependencyGraph
@Suppress("unused")
interface NesComponent {


    @NesScope
    @Provides
    fun cartridgeSocket(): CartridgeSocket = CartridgeSocket()

    @NesScope
    @Provides
    fun ppuBus(cartridgeSocket: CartridgeSocket): PpuBus = PpuBusNes(
        state = PpuBusState()
    )

    @NesScope
    @Provides
    fun ppu(
        ppuBus: PpuBus,
        cpu6502: Cpu6502,
    ): Ppu = PpuNes(
        state = PpuState(),
        ppuBus = ppuBus,
        onNmi = { cpu6502.requestNmi() },
        frameBuffer = FramebufferNes()
    )

    @NesScope
    @Provides
    fun cpuBus(
        cartridgeSocket: CartridgeSocket,
        ppu: Ppu,
    ): CpuBus {
        val fakeController = object : ControllerPort {
            override fun read(): Int = 0

            override fun write(value: Int) = Unit
        }
        return CpuBusNes(
            ram = IntArray(2048),
            cartridge = cartridgeSocket,
            ppu = ppu,
            dma = OamDmaNes(ppu),
            controller1 = fakeController,
            controller2 = fakeController,
        )
    }

    @NesScope
    @Provides
    fun cpu6502(cpuBus: CpuBus): Cpu6502 = Cpu6502(cpuBus)

}

@Scope
annotation class NesScope