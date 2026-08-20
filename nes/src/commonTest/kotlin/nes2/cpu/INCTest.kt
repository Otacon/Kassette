package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class INCTest : FreeSpec({
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
        state = CpuState(pc = 0x8000)

        memory[state.pc] = 0xE6
        memory[state.pc + 1] = 0x20
        memory[0x0020] = 0x41

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x0020] shouldBe 0x42
        bus.writes shouldBe listOf(
            FakeBus.Write(address = 0x0020, value = 0x41),
            FakeBus.Write(address = 0x0020, value = 0x42),
        )
        state.z shouldBe false
        state.n shouldBe false
        state.pc shouldBe 0x8002
        cycles shouldBe 5
    }

    "zero page X" {
        state = CpuState(
            pc = 0x8000,
            x = 0x10,
        )

        memory[state.pc] = 0xF6
        memory[state.pc + 1] = 0x20
        memory[0x0030] = 0x41

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x0030] shouldBe 0x42
        state.pc shouldBe 0x8002
        cycles shouldBe 6
    }

    "zero page X wraps" {
        state = CpuState(
            pc = 0x8000,
            x = 0x02,
        )

        memory[state.pc] = 0xF6
        memory[state.pc + 1] = 0xFF
        memory[0x0001] = 0x10

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        memory[0x0001] shouldBe 0x11
    }

    "absolute" {
        state = CpuState(pc = 0x8000)

        memory[state.pc] = 0xEE
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12
        memory[0x1234] = 0x41

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x1234] shouldBe 0x42
        state.pc shouldBe 0x8003
        cycles shouldBe 6
    }

    "absolute X" {
        state = CpuState(
            pc = 0x8000,
            x = 0x01,
        )

        memory[state.pc] = 0xFE
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12
        memory[0x1235] = 0x41

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        memory[0x1235] shouldBe 0x42
        cycles shouldBe 7
    }

    "wraps FF to zero and sets zero" {
        state = CpuState(pc = 0x8000)

        memory[state.pc] = 0xE6
        memory[state.pc + 1] = 0x20
        memory[0x0020] = 0xFF

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        memory[0x0020] shouldBe 0x00
        state.z shouldBe true
        state.n shouldBe false
    }

    "sets negative when result has bit 7 set" {
        state = CpuState(pc = 0x8000)

        memory[state.pc] = 0xE6
        memory[state.pc + 1] = 0x20
        memory[0x0020] = 0x7F

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        memory[0x0020] shouldBe 0x80
        state.z shouldBe false
        state.n shouldBe true
    }

    "does not modify carry or overflow" {
        state = CpuState(pc = 0x8000, status = 0x61)

        memory[state.pc] = 0xE6
        memory[state.pc + 1] = 0x20
        memory[0x0020] = 0x10

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.c shouldBe true
        state.v shouldBe true
    }
})
