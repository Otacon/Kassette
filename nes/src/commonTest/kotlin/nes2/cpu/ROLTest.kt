package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class ROLTest : FreeSpec({
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



    "rotates accumulator left with carry clear" {
        state = CpuState(
            pc = 0x8000,
            a = 0x21,
            status = 0x20,
        )

        memory[state.pc] = 0x2A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0x42
        state.c shouldBe false
        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "rotates carry into bit zero" {
        state = CpuState(
            pc = 0x8000,
            a = 0x20,
            status = 0x21,
        )

        memory[state.pc] = 0x2A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.a shouldBe 0x41
        state.c shouldBe false
    }

    "moves bit 7 into carry" {
        state = CpuState(
            pc = 0x8000,
            a = 0x80,
            status = 0x20,
        )

        memory[state.pc] = 0x2A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.a shouldBe 0x00
        state.c shouldBe true
        state.z shouldBe true
    }

    "zero page" {
        state = CpuState(pc = 0x8000)

        memory[state.pc] = 0x26
        memory[state.pc + 1] = 0x20
        memory[0x0020] = 0x21

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x0020] shouldBe 0x42
        cycles shouldBe 5
    }

    "zero page X" {
        state = CpuState(pc = 0x8000, x = 0x10)

        memory[state.pc] = 0x36
        memory[state.pc + 1] = 0x20
        memory[0x0030] = 0x21

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x0030] shouldBe 0x42
        cycles shouldBe 6
    }

    "absolute" {
        state = CpuState(pc = 0x8000)

        memory[state.pc] = 0x2E
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12
        memory[0x1234] = 0x21

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x1234] shouldBe 0x42
        cycles shouldBe 6
    }

    "absolute X" {
        state = CpuState(pc = 0x8000, x = 0x01)

        memory[state.pc] = 0x3E
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12
        memory[0x1235] = 0x21

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x1235] shouldBe 0x42
        cycles shouldBe 7
    }
})