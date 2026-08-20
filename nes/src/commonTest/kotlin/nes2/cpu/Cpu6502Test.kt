package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class Cpu6502Test : FreeSpec({

    "reset loads PC from reset vector" {
        val memory = IntArray(0x10_000)
        memory[0xFFFC] = 0x34
        memory[0xFFFD] = 0x12

        val state = CpuState(pc = 0x8000, sp = 0x10, status = 0x00)
        val cpu = Cpu6502(bus = FakeBus(memory), state = state)

        val cycles = cpu.reset()

        state.pc shouldBe 0x1234
        cycles shouldBe 7
    }

    "reset initializes stack pointer and status" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000, sp = 0x10, status = 0x00)
        val cpu = Cpu6502(bus = FakeBus(memory), state = state)

        cpu.reset()

        state.sp shouldBe 0xFD
        state.status shouldBe 0x24
        state.i shouldBe true
    }
})
