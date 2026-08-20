package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class INXTest : FreeSpec({
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



    "increments X" {
        state = CpuState(
            pc = 0x8000,
            x = 0x41,
        )

        memory[state.pc] = 0xE8

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.x shouldBe 0x42
        state.z shouldBe false
        state.n shouldBe false
        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "wraps FF to zero and sets zero flag" {
        state = CpuState(
            pc = 0x8000,
            x = 0xFF,
        )

        memory[state.pc] = 0xE8

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.x shouldBe 0x00
        state.z shouldBe true
        state.n shouldBe false
    }

    "sets negative flag" {
        state = CpuState(
            pc = 0x8000,
            x = 0x7F,
        )

        memory[state.pc] = 0xE8

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.x shouldBe 0x80
        state.z shouldBe false
        state.n shouldBe true
    }

    "does not modify unrelated flags" {
        state = CpuState(
            pc = 0x8000,
            x = 0x10,
            status = 0x6D,
        )

        memory[state.pc] = 0xE8

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.c shouldBe true
        state.v shouldBe true
        state.i shouldBe true
        state.d shouldBe true
    }
})