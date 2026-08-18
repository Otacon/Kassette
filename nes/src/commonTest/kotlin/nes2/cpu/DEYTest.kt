package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class DEYTest : FreeSpec({

    "DEY" - {

        "decrements Y" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                y = 0x43,
            )

            memory[state.pc] = 0x88

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.y shouldBe 0x42
            state.z shouldBe false
            state.n shouldBe false
            state.pc shouldBe 0x8001
            cycles shouldBe 2
        }

        "sets zero flag" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                y = 0x01,
            )

            memory[state.pc] = 0x88

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.y shouldBe 0x00
            state.z shouldBe true
            state.n shouldBe false
        }

        "wraps zero to FF and sets negative flag" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                y = 0x00,
            )

            memory[state.pc] = 0x88

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.y shouldBe 0xFF
            state.z shouldBe false
            state.n shouldBe true
        }
    }
})