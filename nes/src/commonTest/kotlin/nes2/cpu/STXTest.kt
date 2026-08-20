package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class STXTest : FreeSpec({
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
            x = 0x42,
        )

        memory[state.pc] = 0x86
        memory[state.pc + 1] = 0x20

        val initialC = state.c
        val initialZ = state.z
        val initialV = state.v
        val initialN = state.n

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x0020] shouldBe 0x42

        state.x shouldBe 0x42
        state.c shouldBe initialC
        state.z shouldBe initialZ
        state.v shouldBe initialV
        state.n shouldBe initialN

        state.pc shouldBe 0x8002
        cycles shouldBe 3
    }

    "zero page Y" {
        state = CpuState(
            pc = 0x8000,
            x = 0x42,
            y = 0x10,
        )

        memory[state.pc] = 0x96
        memory[state.pc + 1] = 0x20

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        // $20 + Y($10) = $30
        memory[0x0030] shouldBe 0x42

        state.x shouldBe 0x42
        state.pc shouldBe 0x8002
        cycles shouldBe 4
    }

    "zero page Y wraps around zero page" {
        state = CpuState(
            pc = 0x8000,
            x = 0x7A,
            y = 0x02,
        )

        memory[state.pc] = 0x96
        memory[state.pc + 1] = 0xFF

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        // $FF + Y($02) = $01 in zero page
        memory[0x0001] shouldBe 0x7A

        state.pc shouldBe 0x8002
        cycles shouldBe 4
    }

    "absolute" {
        state = CpuState(
            pc = 0x8000,
            x = 0xAB,
        )

        memory[state.pc] = 0x8E
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x1234] shouldBe 0xAB

        state.x shouldBe 0xAB
        state.pc shouldBe 0x8003
        cycles shouldBe 4
    }

    "does not modify flags" {
        state = CpuState(
            pc = 0x8000,
            x = 0x80,
            status = 0xE3,
        )

        memory[state.pc] = 0x86
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