package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.CpuBus

class INYTest : FreeSpec({

    "INY" - {

        "increments Y" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                y = 0x41,
            )

            memory[state.pc] = 0xC8

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.y shouldBe 0x42
            state.z shouldBe false
            state.n shouldBe false
            state.pc shouldBe 0x8001
            cycles shouldBe 2
        }

        "wraps FF to zero and sets zero flag" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                y = 0xFF,
            )

            memory[state.pc] = 0xC8

            Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.y shouldBe 0x00
            state.z shouldBe true
            state.n shouldBe false
        }

        "sets negative flag" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                y = 0x7F,
            )

            memory[state.pc] = 0xC8

            Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.y shouldBe 0x80
            state.z shouldBe false
            state.n shouldBe true
        }
    }
})