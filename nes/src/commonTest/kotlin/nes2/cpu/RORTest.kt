package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class RORTest : FreeSpec({
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

    "rotates accumulator right with carry clear" {
        state = CpuState(
            pc = 0x8000,
            a = 0x84,
            status = 0x20,
        )

        memory[state.pc] = 0x6A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0x42
        state.c shouldBe false
        state.pc shouldBe 0x8001
        bus.writes shouldBe emptyList<FakeBus.Write>()
        cycles shouldBe 2
    }

    "rotates carry into bit 7" {
        state = CpuState(
            pc = 0x8000,
            a = 0x02,
            status = 0x21,
        )

        memory[state.pc] = 0x6A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.a shouldBe 0x81
        state.c shouldBe false
        state.n shouldBe true
    }

    "moves bit zero into carry" {
        state = CpuState(
            pc = 0x8000,
            a = 0x01,
            status = 0x20,
        )

        memory[state.pc] = 0x6A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.a shouldBe 0x00
        state.c shouldBe true
        state.z shouldBe true
    }

    "zero page" {
        state = CpuState(pc = 0x8000)

        memory[state.pc] = 0x66
        memory[state.pc + 1] = 0x20
        memory[0x0020] = 0x84

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x0020] shouldBe 0x42
        bus.writes shouldBe listOf(
            FakeBus.Write(address = 0x0020, value = 0x84),
            FakeBus.Write(address = 0x0020, value = 0x42),
        )
        cycles shouldBe 5
    }

    "zero page X" {
        state = CpuState(pc = 0x8000, x = 0x10)

        memory[state.pc] = 0x76
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

        memory[state.pc] = 0x6E
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

        memory[state.pc] = 0x7E
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
