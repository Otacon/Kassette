package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.FakeBus

class INXTest : FreeSpec({

    "INX" - {

        "increments X" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                x = 0x41,
            )

            memory[state.pc] = 0xE8

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.x shouldBe 0x42
            state.z shouldBe false
            state.n shouldBe false
            state.pc shouldBe 0x8001
            cycles shouldBe 2
        }

        "wraps FF to zero and sets zero flag" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                x = 0xFF,
            )

            memory[state.pc] = 0xE8

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.x shouldBe 0x00
            state.z shouldBe true
            state.n shouldBe false
        }

        "sets negative flag" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                x = 0x7F,
            )

            memory[state.pc] = 0xE8

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.x shouldBe 0x80
            state.z shouldBe false
            state.n shouldBe true
        }

        "does not modify unrelated flags" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                x = 0x10,
            ).also {
                it.c = true
                it.v = true
                it.i = true
                it.d = true
            }

            memory[state.pc] = 0xE8

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.c shouldBe true
            state.v shouldBe true
            state.i shouldBe true
            state.d shouldBe true
        }
    }
})