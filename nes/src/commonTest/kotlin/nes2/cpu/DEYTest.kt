package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class DEYTest : FreeSpec({
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



    "decrements Y" {
        state = CpuState(
            pc = 0x8000,
            y = 0x43,
        )

        memory[state.pc] = 0x88

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.y shouldBe 0x42
        state.z shouldBe false
        state.n shouldBe false
        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "sets zero flag" {
        state = CpuState(
            pc = 0x8000,
            y = 0x01,
        )

        memory[state.pc] = 0x88

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.y shouldBe 0x00
        state.z shouldBe true
        state.n shouldBe false
    }

    "wraps zero to FF and sets negative flag" {
        state = CpuState(
            pc = 0x8000,
            y = 0x00,
        )

        memory[state.pc] = 0x88

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.y shouldBe 0xFF
        state.z shouldBe false
        state.n shouldBe true
    }
})