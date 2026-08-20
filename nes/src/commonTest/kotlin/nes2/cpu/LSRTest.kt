package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class LSRTest : FreeSpec({
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



    "accumulator shifts right" {
        state = CpuState(
            pc = 0x8000,
            a = 0x84,
        )

        memory[state.pc] = 0x4A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0x42
        state.c shouldBe false
        state.z shouldBe false
        state.n shouldBe false
        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "moves bit zero into carry" {
        state = CpuState(
            pc = 0x8000,
            a = 0x01,
        )

        memory[state.pc] = 0x4A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.a shouldBe 0x00
        state.c shouldBe true
        state.z shouldBe true
        state.n shouldBe false
    }

    "zero page" {
        state = CpuState(pc = 0x8000)

        memory[state.pc] = 0x46
        memory[state.pc + 1] = 0x20
        memory[0x0020] = 0x84

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x0020] shouldBe 0x42
        cycles shouldBe 5
    }

    "zero page X" {
        state = CpuState(pc = 0x8000, x = 0x10)

        memory[state.pc] = 0x56
        memory[state.pc + 1] = 0x20
        memory[0x0030] = 0x84

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x0030] shouldBe 0x42
        cycles shouldBe 6
    }

    "absolute" {
        state = CpuState(pc = 0x8000)

        memory[state.pc] = 0x4E
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12
        memory[0x1234] = 0x84

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x1234] shouldBe 0x42
        cycles shouldBe 6
    }

    "absolute X" {
        state = CpuState(pc = 0x8000, x = 0x01)

        memory[state.pc] = 0x5E
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12
        memory[0x1235] = 0x84

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x1235] shouldBe 0x42
        cycles shouldBe 7
    }
})