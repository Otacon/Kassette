package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class RORTest : FreeSpec({

    "ROR" - {

        "rotates accumulator right with carry clear" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x84,
            ).also {
                it.c = false
            }

            memory[state.pc] = 0x6A

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x42
            state.c shouldBe false
            state.pc shouldBe 0x8001
            cycles shouldBe 2
        }

        "rotates carry into bit 7" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x02,
            ).also {
                it.c = true
            }

            memory[state.pc] = 0x6A

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x81
            state.c shouldBe false
            state.n shouldBe true
        }

        "moves bit zero into carry" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x01,
            ).also {
                it.c = false
            }

            memory[state.pc] = 0x6A

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

            memory[state.pc] = 0x66
            memory[state.pc + 1] = 0x20
            memory[0x0020] = 0x84

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

            memory[state.pc] = 0x76
            memory[state.pc + 1] = 0x20
            memory[0x0030] = 0x84

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

            memory[state.pc] = 0x6E
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12
            memory[0x1234] = 0x84

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

            memory[state.pc] = 0x7E
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12
            memory[0x1235] = 0x84

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            memory[0x1235] shouldBe 0x42
            cycles shouldBe 7
        }
    }
})