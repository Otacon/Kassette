package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class ROLTest : FreeSpec({

    "ROL" - {

        "rotates accumulator left with carry clear" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x21,
            ).also {
                it.c = false
            }

            memory[state.pc] = 0x2A

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x42
            state.c shouldBe false
            state.pc shouldBe 0x8001
            cycles shouldBe 2
        }

        "rotates carry into bit zero" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x20,
            ).also {
                it.c = true
            }

            memory[state.pc] = 0x2A

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x41
            state.c shouldBe false
        }

        "moves bit 7 into carry" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x80,
            ).also {
                it.c = false
            }

            memory[state.pc] = 0x2A

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x00
            state.c shouldBe true
            state.z shouldBe true
        }

        "zero page" {
            val memory = IntArray(0x10_000)
            val state = CpuState(pc = 0x8000)

            memory[state.pc] = 0x26
            memory[state.pc + 1] = 0x20
            memory[0x0020] = 0x21

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            memory[0x0020] shouldBe 0x42
            cycles shouldBe 5
        }

        "zero page X" {
            val memory = IntArray(0x10_000)
            val state = CpuState(pc = 0x8000, x = 0x10)

            memory[state.pc] = 0x36
            memory[state.pc + 1] = 0x20
            memory[0x0030] = 0x21

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            memory[0x0030] shouldBe 0x42
            cycles shouldBe 6
        }

        "absolute" {
            val memory = IntArray(0x10_000)
            val state = CpuState(pc = 0x8000)

            memory[state.pc] = 0x2E
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12
            memory[0x1234] = 0x21

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            memory[0x1234] shouldBe 0x42
            cycles shouldBe 6
        }

        "absolute X" {
            val memory = IntArray(0x10_000)
            val state = CpuState(pc = 0x8000, x = 0x01)

            memory[state.pc] = 0x3E
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12
            memory[0x1235] = 0x21

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            memory[0x1235] shouldBe 0x42
            cycles shouldBe 7
        }
    }
})