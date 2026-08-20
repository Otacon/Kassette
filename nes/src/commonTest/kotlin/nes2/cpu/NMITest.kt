package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class NMITest : FreeSpec({
    lateinit var memory: IntArray
    lateinit var state: CpuState
    lateinit var bus: FakeBus
    lateinit var cpu: Cpu6502

    beforeTest {
        memory = IntArray(0x10_000)
        state = CpuState()
        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
    }



    "jumps to NMI vector" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
        )

        memory[0xFFFA] = 0x34
        memory[0xFFFB] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.requestNmi()

        val cycles = cpu.step()

        state.pc shouldBe 0x1234
        cycles shouldBe 7
    }

    "NMI is serviced even when interrupt disable is set" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            status = 0x24,
        )

        memory[0xFFFA] = 0x34
        memory[0xFFFB] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.requestNmi()

        cpu.step()

        state.pc shouldBe 0x1234
    }

    "NMI is consumed once" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
        )

        memory[0xFFFA] = 0x00
        memory[0xFFFB] = 0x90

        // Handler starts with NOP.
        memory[0x9000] = 0xEA

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.requestNmi()

        cpu.step() shouldBe 7
        state.pc shouldBe 0x9000

        // No second NMI was requested, so execute NOP.
        cpu.step() shouldBe 2
        state.pc shouldBe 0x9001
    }

    "NMI has priority over IRQ" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            status = 0x20,
        )

        // NMI -> $9000
        memory[0xFFFA] = 0x00
        memory[0xFFFB] = 0x90

        // IRQ -> $A000
        memory[0xFFFE] = 0x00
        memory[0xFFFF] = 0xA0

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.setIrqLine(true)
        cpu.requestNmi()

        cpu.step()

        state.pc shouldBe 0x9000
    }
})