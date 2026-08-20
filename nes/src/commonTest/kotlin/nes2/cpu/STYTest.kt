package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class STYTest : FreeSpec({
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



    "zero page" {
        state = CpuState(
            pc = 0x8000,
            y = 0x42,
        )

        memory[state.pc] = 0x84
        memory[state.pc + 1] = 0x20

        val initialC = state.c
        val initialZ = state.z
        val initialV = state.v
        val initialN = state.n

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x0020] shouldBe 0x42

        state.y shouldBe 0x42
        state.c shouldBe initialC
        state.z shouldBe initialZ
        state.v shouldBe initialV
        state.n shouldBe initialN

        state.pc shouldBe 0x8002
        cycles shouldBe 3
    }

    "zero page X" {
        state = CpuState(
            pc = 0x8000,
            x = 0x10,
            y = 0x42,
        )

        memory[state.pc] = 0x94
        memory[state.pc + 1] = 0x20

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        // $20 + X($10) = $30
        memory[0x0030] shouldBe 0x42

        state.y shouldBe 0x42
        state.pc shouldBe 0x8002
        cycles shouldBe 4
    }

    "zero page X wraps around zero page" {
        state = CpuState(
            pc = 0x8000,
            x = 0x02,
            y = 0x7A,
        )

        memory[state.pc] = 0x94
        memory[state.pc + 1] = 0xFF

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        // $FF + X($02) = $01
        memory[0x0001] shouldBe 0x7A

        state.pc shouldBe 0x8002
        cycles shouldBe 4
    }

    "absolute" {
        state = CpuState(
            pc = 0x8000,
            y = 0xAB,
        )

        memory[state.pc] = 0x8C
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x1234] shouldBe 0xAB

        state.y shouldBe 0xAB
        state.pc shouldBe 0x8003
        cycles shouldBe 4
    }

    "does not modify flags" {
        state = CpuState(
            pc = 0x8000,
            y = 0x80,
            status = 0xE3,
        )

        memory[state.pc] = 0x84
        memory[state.pc + 1] = 0x20

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.c shouldBe true
        state.z shouldBe true
        state.v shouldBe true
        state.n shouldBe true
    }
})