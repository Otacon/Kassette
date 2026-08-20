package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class FlagInstructionsTest : FreeSpec({
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


    "CLC" {
        state = CpuState(pc = 0x8000, status = 0xA3)

        memory[state.pc] = 0x18

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.c shouldBe false

        state.z shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "SEC" {
        state = CpuState(pc = 0x8000, status = 0xA2)

        memory[state.pc] = 0x38

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.c shouldBe true

        state.z shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "CLI" {
        state = CpuState(pc = 0x8000, status = 0x27)

        memory[state.pc] = 0x58

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.i shouldBe false

        state.c shouldBe true
        state.z shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "CLI delays IRQ recognition by one instruction" {

        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            irqLine = true,
            irqPollI = true,
            status = 0x24,
        )

        memory[0x8000] = 0x58 // CLI
        memory[0x8001] = 0xEA // NOP

        memory[0xFFFE] = 0x00
        memory[0xFFFF] = 0x90

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        // IRQ is masked, so CLI executes.
        cpu.step() shouldBe 2

        state.i shouldBe false
        state.pc shouldBe 0x8001

        // CLI's change hasn't affected polling yet.
        cpu.step() shouldBe 2

        state.pc shouldBe 0x8002

        // Now IRQ is seen.
        cpu.step() shouldBe 7

        state.pc shouldBe 0x9000
    }

    "SEI" {
        state = CpuState(pc = 0x8000, status = 0x23)

        memory[state.pc] = 0x78

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.i shouldBe true

        state.c shouldBe true
        state.z shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "SEI does not cancel an IRQ recognized with the previous I value" {

        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            irqPollI = false,
            status = 0x20,
        )

        memory[0x8000] = 0x78 // SEI

        memory[0xFFFE] = 0x00
        memory[0xFFFF] = 0x90

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.step() shouldBe 2

        state.i shouldBe true

        // Represent IRQ becoming asserted during/just after SEI.
        state.irqLine = true

        // Poll used I=false from before SEI.
        cpu.step() shouldBe 7

        state.pc shouldBe 0x9000
    }

    "CLV" {
        state = CpuState(pc = 0x8000, status = 0xE1)

        memory[state.pc] = 0xB8

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.v shouldBe false

        state.c shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "CLD" {
        state = CpuState(pc = 0x8000, status = 0xA9)

        memory[state.pc] = 0xD8

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.d shouldBe false

        state.c shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "SED" {
        state = CpuState(pc = 0x8000, status = 0xA1)

        memory[state.pc] = 0xF8

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.d shouldBe true

        state.c shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }
})
