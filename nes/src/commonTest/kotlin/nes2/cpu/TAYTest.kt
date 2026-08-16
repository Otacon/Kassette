package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.CpuBus

class TAYTest : FreeSpec({

    "TAY" - {

        "copies A into Y" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x42,
                y = 0x00,
            )

            memory[state.pc] = 0xA8

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.y shouldBe 0x42
            state.a shouldBe 0x42
            state.z shouldBe false
            state.n shouldBe false
            state.pc shouldBe 0x8001
            cycles shouldBe 2
        }

        "sets zero flag" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x00,
                y = 0xFF,
            )

            memory[state.pc] = 0xA8

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
                a = 0x80,
            )

            memory[state.pc] = 0xA8

            Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.y shouldBe 0x80
            state.z shouldBe false
            state.n shouldBe true
        }

        "does not modify unrelated flags" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x42,
            ).also {
                it.c = true
                it.v = true
            }

            memory[state.pc] = 0xA8

            Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.c shouldBe true
            state.v shouldBe true
        }
    }
})